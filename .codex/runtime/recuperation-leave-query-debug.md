# 疗休养查询 0 条问题根因分析与修复

诊断日期：2026-06-05

---

## 1. 手工录入数据最终写入表

`jijian_leave_health`（正式业务表）

P1 确认写入流程：`LeaveHealthConfirmWriteHandler.doConfirm()` → `leaveHealthMapper.insert(do_)` → 写入 `jijian_leave_health`。写入路径正确，不是录入 Bug。

---

## 2. 查询返回 0 的根因（双重过滤陷阱）

### 根因 A：Mapper 时间过滤字段选错

**修复前**：
```java
// LeaveHealthMapper.selectPageForQuery
.ge(LeaveHealthDO::getStartTime, startTime)  // 按疗养假开始时间过滤
```

`startTime` 是疗养假的业务开始时间（Excel "疗养假开始时间" 列）。如果用户录入 Excel 没有该列，或列名不完全匹配（`LeaveHealthConfirmWriteHandler.get(row, "疗养假开始时间", "开始时间")`），则该字段为 NULL。

MySQL 中 `NULL >= '2025-06-05'` 的结果是 NULL（不是 true），所以 WHERE 条件不满足，所有 startTime=NULL 的记录均被过滤掉。

**修复后**：
```java
.ge("create_time", startTime)  // 按记录创建时间过滤
```

`create_time` 由 MyBatis-Plus 在 insert 时自动填充，永远不为 NULL，不会因为业务字段缺失而被误过滤。

### 根因 B：Chat 默认时间范围 ONE_WEEK

**修复前**：
```java
// JijianQueryChatServiceImpl.defaultTimeRange
return JijianQueryTimeRangeEnum.ONE_WEEK.getValue();  // 7天
```

用户 chat 消息"给我分析疗休养数据"不包含时间关键词，推断失败后默认 ONE_WEEK。
`startTime = LocalDate.now().minusDays(7)` → `create_time >= '2026-05-29'`。

如果用户是 5 天前录入的数据，且当天录入后 5 天内再查，还好；但如果是超过 7 天，chat 也返回 0。

**修复后**：
```java
return JijianQueryTimeRangeEnum.ONE_YEAR.getValue();  // 365天
```

### 根因 C：前端默认时间范围 ONE_WEEK / ONE_YEAR

**修复前**：`selectedTimeRange = 'ONE_WEEK'`（上次已改为 ONE_YEAR）

**修复后**：`selectedTimeRange = 'ALL'`（使用 3650 天范围，等效全量）

### 根因 D：空字符串部门未防护

**修复前**：
```java
if (department != null && !"ALL".equals(department)) {
    wrapper.eq(LeaveHealthDO::getDepartment, department);
}
```

若 `department = ""`（空字符串），条件成立，添加 `department = ''`，查不到任何有部门的记录。

**修复后**：
```java
if (department != null && !"ALL".equals(department) && !department.isEmpty()) {
```

---

## 3. 修复文件

| 文件 | 修复内容 |
|---|---|
| `LeaveHealthMapper.java` | selectPageForQuery/selectListForQuery 改用 `create_time` 过滤；空字符串部门防护 |
| `LeavePersonalMapper.java` | 同上 |
| `BusinessTripMapper.java` | 同上 |
| `CompensatoryLeaveMapper.java` | selectPageForQuery/selectListForQuery 改用 `create_time` 过滤 |
| `AttendanceDailyMapper.java` | selectPageForQuery/selectListForSummary 改用 `create_time` 过滤；空字符串部门防护 |
| `JijianQueryChatServiceImpl.java` | defaultTimeRange 兜底改为 ONE_YEAR |
| `JijianActualTableQueryService.java` | getFilterOptions：leave 记录月份提取改为 startTime ?? createTime 兜底 |
| `Smart.vue` | 前端默认 timeRange 改为 ALL |

---

## 4. 修复后预期 HTTP 验证结果

```
GET /admin-api/jijian/query/list?type=RECUPERATION_LEAVE&timeRange=ALL

期望：
{
  "code": 0,
  "data": {
    "pageResult": { "total": 1, "list": [{ "applicantName": "...", "department": "...", ... }] },
    "summary": { "totalCount": 1, "totalLeaveDays": 3, ... },
    "columns": [...]
  }
}
```

```
GET /admin-api/jijian/query/filter-options?type=RECUPERATION_LEAVE

期望：
{
  "code": 0,
  "data": {
    "departments": ["第一纪检监察室"],   // 真实部门
    "months": ["2026-06"],              // 来自 createTime 兜底
    "hasDepartment": true,
    "hasDateField": true
  }
}
```

---

## 5. 是否改动 P1 录入主流程

否。`LeaveHealthConfirmWriteHandler` 未修改。数据写入路径不变。

## 6. 是否删除或插入任何数据

否。用户已手工录入的数据保留不变。

---

## 7. 敏感信息

本文档不包含 token、API key、手机号、身份证等敏感信息。
