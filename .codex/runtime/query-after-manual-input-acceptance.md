# 录入后查询验收步骤

生成日期：2026-06-05  
适用范围：本地开发环境，后端已启动，数据库为本地库。

---

## 1. 清空现有业务数据

在 MySQL 客户端（Navicat / DBeaver / mysql CLI）中执行：

```
.codex/runtime/clear-jijian-business-data.sql
```

执行后确认：
```sql
SELECT 'jijian_property' AS tbl, COUNT(*) cnt FROM jijian_property
UNION ALL SELECT 'jijian_attendance_daily', COUNT(*) FROM jijian_attendance_daily
UNION ALL SELECT 'jijian_import_record', COUNT(*) FROM jijian_import_record;
-- 期望：所有 cnt = 0
```

---

## 2. 手动录入一条考勤日报

1. 打开前端 → 纪检系统 → 录入模块
2. 上传一个考勤 Excel（Sheet 名含"考勤"关键字）
3. 前端 ParsedDataPanel 显示预览，确认字段识别正确
4. 点击"确认写入"
5. 期望：`code=0`，`confirmedCount > 0`，`businessTable = jijian_attendance_daily`

---

## 3. 在查询模块验证考勤数据

1. 打开前端 → 纪检系统 → 数据查询
2. 数据类型下拉选择"考勤日报表"（值：ATTENDANCE_DAILY）
3. 时间范围选择"全部"（值：ALL）或"一年"（值：ONE_YEAR）
4. 部门下拉自动加载刚才录入数据的真实部门
5. 点击"查询"
6. 期望：
   - `pageResult.list` 包含刚才录入的考勤记录
   - `pageResult.total > 0`
   - `columns` 正常（部门、姓名、考勤日期、打卡结果等）
   - `summary.totalCount > 0`

---

## 4. 手动录入一条事假 / 出差 / 调休

1. 同上，分别上传事假、出差、调休 Excel
2. 每次确认写入后，对应表记录数 +1

验证查询：
- 数据类型分别选"事假表"、"出差表"、"调休表"
- 时间范围选"全部"
- 期望各表都能查到刚才录入的记录

---

## 5. 验证部门下拉来自真实数据

1. 录入考勤数据（包含部门字段）后
2. 查询页面选择"考勤日报表"
3. 部门下拉应自动出现刚才录入数据中的部门名称（来自 selectDistinctDepartments()）
4. 选择某一具体部门 → 点击查询 → 结果仅显示该部门数据

对于无部门字段的表（房产情况表、租赁人员表、租赁合同表、食堂供应商表）：
- 部门下拉应显示为禁用状态或仅显示"全部"
- 不得出现写死的"第一纪检监察室"等假部门

---

## 6. 验证 chat 分析基于新录入数据

1. 录入考勤数据后，进入"智能问答"
2. 输入：帮我查一下这个月缺勤人员
3. 期望：
   - `aiMode = DEEPSEEK_DATA_ANALYSIS`（DeepSeek 可用时）或 `LOCAL_FALLBACK`
   - `databaseContextMeta.tablesUsed` 包含 5 张考勤表
   - `metrics` 中 `totalAttendanceRecords` 数值与录入数量一致
   - `answer` 为中文，内容基于真实数据（而非编造）

---

## 7. HTTP 接口验收用例

### form-types（应返回 9 张实际表，无兼容别名）
```
GET /admin-api/jijian/query/form-types
Authorization: Bearer <token>
```
期望：返回 PROPERTY_INFO / LESSEE / LEASE_CONTRACT / ATTENDANCE_DAILY /
RECUPERATION_LEAVE / PERSONAL_LEAVE / BUSINESS_TRIP / COMPENSATORY_LEAVE / CANTEEN_SUPPLIER
不包含 ATTENDANCE / REAL_ESTATE / CANTEEN_SUPPLY

### filter-options（部门和月份来自真实数据）
```
GET /admin-api/jijian/query/filter-options?type=ATTENDANCE_DAILY
Authorization: Bearer <token>
```
空库期望：`{ "departments": [], "months": [], "hasDepartment": true, "hasDateField": false }`
有数据期望：`departments` 包含真实部门，`months` 包含真实年月

### list 空库验证（所有表应返回空，不 NPE）
```
GET /admin-api/jijian/query/list?type=ATTENDANCE_DAILY&timeRange=ALL
GET /admin-api/jijian/query/list?type=PROPERTY_INFO&timeRange=ALL
GET /admin-api/jijian/query/list?type=CANTEEN_SUPPLIER&timeRange=ALL
```
期望：`code=0`，`pageResult.list=[]`，`pageResult.total=0`，`columns` 非空

### 录入后查询
```
GET /admin-api/jijian/query/list?type=ATTENDANCE_DAILY&timeRange=ALL
```
期望：`pageResult.total` 与录入行数一致
```
