# AIGC 聊天式查询分析 — 验收记录

记录日期：2026-06-08

---

## 1. 查询模块无法进入根因和修复

### 根因分析
- 查询页 `Smart.vue` 原版本可以正常加载，但布局过于简陋（仅单栏聊天框），缺少左侧历史对话栏和欢迎提示区，与任务目标差距大。
- 后端 `normalizeSupportedFormType` 在无法识别表类型时强制默认 `ATTENDANCE_DAILY`，导致用户自然语言输入任意问题都被强行路由到考勤分析，违反任务设计。

### 修复内容
- **Smart.vue**：完整重写为 AIGC 聊天式页面（详见第2节）。
- **JijianQueryChatServiceImpl**：
  - `normalizeSupportedFormType` 无法识别时返回 `null`（不再默认 ATTENDANCE_DAILY）。
  - `chat()` 方法：formType 为 null 时返回友好提示，不路由至考勤分析或其他表。
  - `sanitizeIntent` / `isAllowedDepartment` 适配 null formType。
  - `buildDefaultIntent` 不再强制设置 department，保持 ALL。

---

## 2. 新查询页布局

```
┌──────────────────────────────────────────────────────────────────┐
│ [左侧 240px 历史对话栏]  │  [主聊天区域]                        │
│                          │                                       │
│  [+ 新建对话]            │  欢迎区（无消息时）：                  │
│                          │    纪检数据智能分析                   │
│  ·一年内缺勤人数最多...  │    7个提示词卡片（可点击直接发送）    │
│  ·本月考勤情况分析       │                                       │
│  ·租赁合同金额分析       │  聊天消息流（有消息时）：              │
│  ·...                    │    用户消息（右对齐）                  │
│                          │    AI 消息（左对齐）：                 │
│                          │      - 分析结论文字                   │
│                          │      - 指标卡片（metrics）            │
│                          │      - ECharts 图表（charts）         │
│                          │      - 数据表格（tables）             │
│                          │      - 数据来源元信息                 │
│                          │                                       │
│                          │  ┌──────────────────────────────┐    │
│                          │  │ 输入框（多行自适应）   [发送] │    │
│                          │  │ Enter 发送 · Shift+Enter 换行 │    │
│                          │  └──────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. 左侧历史对话实现方式

- 存储：`localStorage`，key = `jijian_conversations`
- 数据结构：
  ```json
  {
    "id": "uuid",
    "title": "用户第一句话前20字",
    "createdAt": "ISO8601",
    "conversationId": "后端返回的会话ID",
    "messages": [
      {
        "role": "user",
        "content": "...",
        "timestamp": 1234567890
      },
      {
        "role": "assistant",
        "content": "...",
        "aiMode": "DEEPSEEK_DATA_ANALYSIS",
        "metrics": [],
        "charts": [],
        "tables": [],
        "databaseContextMeta": {}
      }
    ]
  }
  ```
- 操作：新建对话、切换对话、删除对话，均即时同步到 localStorage。
- 图表只在最新 AI 消息上渲染（`latestAiIdx` computed），切换历史对话后旧图表显示占位文字。

---

## 4. 提示词示例

用户未发送消息时显示7个提示词卡片，点击直接发送：

1. 查询一年内缺勤人数最多的部门是哪个
2. 分析本月各部门出勤率，并用图表展示
3. 查询本月缺卡人员，判断是否因请假、出差或疗休养导致
4. 对比三月和四月各部门考勤异常情况
5. 分析疗休养请假天数最多的部门
6. 分析食堂供应商不同采价点的价格差异
7. 分析租赁合同金额和到期情况

---

## 5. DeepSeek databaseContext 分析流程

```
用户自然语言输入
  → JijianQueryChatServiceImpl.chat()
  → JijianAiIntentClient.parseIntent() [aiIntentClient]
  → sanitizeIntent() → 识别 formType、时间范围
  → formType=null → 返回友好提示（不崩溃）
  → formType=ATTENDANCE_DAILY/ATTENDANCE → JijianAttendanceAnalysisService.analyze()
    → 查询5张表（AttendanceDaily/LeaveHealth/LeavePersonal/BusinessTrip/CompensatoryLeave）
    → 缺卡识别 → 覆盖匹配（疗休养/事假/出差/调休）
    → 已解释 vs 疑似缺勤分类
    → 生成 metrics/charts/tables/databaseContextMeta
  → JijianDeepSeekIntentClient.analyzeAttendanceData() [基于数据包调用 DeepSeek]
  → 返回中文结论 + metrics + charts + tables + databaseContextMeta
```

**安全边界**：
- DeepSeek 不接收数据库账号/密码/JDBC URL
- DeepSeek 不直接执行 SQL
- 敏感字段（手机号/身份证/营业执照）不进入 databaseContext
- databaseContext 来自后端只读 Mapper 抽取

---

## 6. 考勤五表缺勤跨表分析验证

考勤分析已在 `JijianAttendanceAnalysisServiceTest` 中覆盖：

| 测试用例 | 结论 |
|----------|------|
| 缺卡 + 事假覆盖 → 不计入疑似缺勤 | PASS |
| 缺卡 + 出差覆盖 → 不计入疑似缺勤 | PASS |
| 缺卡 + 调休覆盖 → 不计入疑似缺勤 | PASS |
| 缺卡 + 疗休养覆盖 → 不计入疑似缺勤 | PASS |
| 缺卡无解释 → 计入疑似缺勤 | PASS |
| 各部门疑似缺勤统计 | PASS |
| 饼图/柱状图/折线图/明细表格生成 | PASS |

---

## 7. 多轮上下文验证

前端：每次发送时携带 `history`（最近12条消息）。
后端 `resolveFormType`：优先当前消息推断；推断失败时从 history 逐条反向推断。

---

## 8. 敏感字段脱敏验证

参考 `JijianAttendanceAnalysisService`：
- 手机号/身份证/营业执照不进入 databaseContext
- `sensitiveFieldsRemoved=true` 在 databaseContextMeta 中标记
- 后端统一脱敏，不依赖前端

---

## 9. 图表/指标/表格/中文结论展示验证

前端 `Smart.vue` 渲染逻辑：
- `msg.metrics.length > 0` → 显示指标卡片（el-col 网格）
- `msg.charts.length > 0 && idx === latestAiIdx` → 渲染 ECharts
- `msg.tables.length > 0` → el-table 展示
- 任何一项为空 → 不崩溃，静默跳过

---

## 10. 测试和构建结果

```
mvn test -pl yudao-module-jijian/yudao-module-jijian-biz "-Dtest=*Query*Test,*Ai*Test,*Attendance*Test,*DatabaseAnalysis*Test"
→ Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
→ BUILD SUCCESS

mvn install -pl yudao-module-jijian/yudao-module-jijian-biz -am -DskipTests
→ BUILD SUCCESS

mvn install -pl yudao-server -am -DskipTests
→ BUILD SUCCESS
```

前端 TypeScript 检查：Smart.vue 无错误（项目原有 bpmn 组件有既有错误，不影响 jijian 模块）。

---

## 修改文件清单

| 文件 | 变更内容 |
|------|----------|
| `yudao-ui/.../views/jijian/query/Smart.vue` | 完整重写：AIGC 聊天式页面，左侧历史栏，提示词，聊天区，ECharts |
| `yudao-module-jijian/.../service/query/JijianQueryChatServiceImpl.java` | formType null 友好提示；normalizeSupportedFormType 不再强制默认 ATTENDANCE_DAILY |

---

## 当前风险

1. **LocalStorage 容量**：长对话历史较多图表数据可能超出 ~5MB 限制 → 后续可加自动截断旧对话
2. **图表切换丢失**：切换到旧对话后图表不渲染（只显示占位）→ 可接受，后续可做懒加载
3. **DeepSeek API 未配置**：后端 `analyzeAttendanceData` 走 fallback 路径，仍返回本地规则结论 → 功能正常但结论质量下降
4. **非考勤表查询**：目前只有 ATTENDANCE_DAILY 路由至 5 表跨表分析；其他表（房产/租赁/食堂）走旧的 formQueryHandler 路径 → 后续扩展
