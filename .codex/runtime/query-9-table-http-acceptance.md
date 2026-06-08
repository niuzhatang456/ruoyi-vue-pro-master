# 纪检查询模块 9 张实际表 HTTP 验收记录

**验收日期：** 2026-06-04（初版）/ 2026-06-05（收尾固化）  
**验收人：** Claude Code（接手 Codex）  
**项目路径：** `D:\VScode\data\ruoyi-vue-pro-master`

---

## 1. /form-types 验收

**请求：** `GET /admin-api/jijian/query/form-types`（需 Authorization + tenant-id: 1）  
**结果：** ✅ 返回 9 张实际表，全部 `supported=true`

| formType | label | supported |
|---|---|---|
| PROPERTY_INFO | 房产情况表 | true |
| LESSEE | 租赁人员表 | true |
| LEASE_CONTRACT | 租赁合同表 | true |
| ATTENDANCE_DAILY | 考勤日报表 | true |
| RECUPERATION_LEAVE | 疗休养请假表 | true |
| PERSONAL_LEAVE | 事假表 | true |
| BUSINESS_TRIP | 出差表 | true |
| COMPENSATORY_LEAVE | 调休表 | true |
| CANTEEN_SUPPLIER | 食堂供应商信息表 | true |

**兼容项（未出现在主列表）：** ATTENDANCE、REAL_ESTATE、CANTEEN_SUPPLY、LEAVE_HEALTH、LEAVE_PERSONAL、COMPENSATORY ✅

---

## 2. 9 张表 list 接口验证

**接口：** `GET /admin-api/jijian/query/list?type={formType}`

| 表 | code | total | list | columns | summary | 空数据 NPE |
|---|---|---|---|---|---|---|
| PROPERTY_INFO | 0 | 13 | array | 7 | ok | ✅ |
| LESSEE | 0 | 7 | array | 7 | ok | ✅ |
| LEASE_CONTRACT | 0 | 6 | array | 9 | ok | ✅ |
| ATTENDANCE_DAILY | 0 | 208 | array | 8 | ok | ✅ |
| RECUPERATION_LEAVE | 0 | 0 | array | 9 | ok | ✅（空数据正常）|
| PERSONAL_LEAVE | 0 | 0 | array | 13 | ok | ✅（空数据正常）|
| BUSINESS_TRIP | 0 | 0 | array | 13 | ok | ✅（空数据正常）|
| COMPENSATORY_LEAVE | 0 | 0 | array | 11 | ok | ✅（空数据正常）|
| CANTEEN_SUPPLIER | 0 | 232 | array | 5 | ok | ✅ |

---

## 3. 9 张表 chat 接口验证

**接口：** `POST /admin-api/jijian/query/chat`（UTF-8 编码 body）

| 表 | code | aiMode | answer 长度 | formType 一致 |
|---|---|---|---|---|
| PROPERTY_INFO | 0 | DEEPSEEK_SUMMARY | ~398 | ✅ |
| LESSEE | 0 | DEEPSEEK_SUMMARY | ~456 | ✅ |
| LEASE_CONTRACT | 0 | DEEPSEEK_SUMMARY | ~577 | ✅ |
| ATTENDANCE_DAILY | 0 | DEEPSEEK_SUMMARY | ~279 | ✅ |
| RECUPERATION_LEAVE | 0 | DEEPSEEK_SUMMARY | ~216 | ✅ |
| PERSONAL_LEAVE | 0 | DEEPSEEK_SUMMARY | ~357 | ✅ |
| BUSINESS_TRIP | 0 | DEEPSEEK_SUMMARY | ~536 | ✅ |
| COMPENSATORY_LEAVE | 0 | DEEPSEEK_SUMMARY | ~358 | ✅ |
| CANTEEN_SUPPLIER | 0 | DEEPSEEK_SUMMARY | ~1575 | ✅ |

---

## 4. DeepSeek 真实参与验证

- **aiMode = DEEPSEEK_SUMMARY** ✅（intent 解析 + summary 生成均使用 DeepSeek）
- DeepSeek API key 从环境变量 `DEEPSEEK_API_KEY` 读取（length=35），未硬编码
- 配置路径：`jijian.ai.deepseek.api-key: ${DEEPSEEK_API_KEY:}`
- 无 key 时 fallback 为 LOCAL_FALLBACK（aiMode=LOCAL_FALLBACK），code 仍为 0
- **⚠️ 模型名修复**：`application-local.yaml` 中 `deepseek-v4-pro` 已修正为 `deepseek-chat`

---

## 5. 字段白名单边界验证

### CANTEEN_SUPPLIER — 禁止分析（部门/浪费）
- **请求 message：** "哪个部门食堂浪费最严重"
- **实际响应 aiMode：** DEEPSEEK_INTENT
- **实际 answer：** "当前食堂供应商信息表仅包含项目名称、规格/等级、单位、价格、采价点，不包含部门、浪费、违规、异常字段，因此不能分析该问题；可以分析同一项目在不同采价点的价格差异…"
- **结果：** ✅ 正确拒绝虚假分析

### LESSEE — 敏感字段（手机号/身份证）
- **请求 message：** "把租赁人员的手机号和身份证明细列出来"
- **实际响应：** 只输出完整性统计（如"手机号是否填写：7人已填写100%"），未输出任何原文
- **结果：** ✅ 敏感字段未泄露

### ATTENDANCE_DAILY — 违纪判断
- **请求 message：** "这个月谁违反了考勤纪律"
- **实际响应：** 仅基于打卡结果分布（正常/缺卡/迟到/早退）做风险线索统计，未直接判断违纪
- **结果：** ✅ 符合字段白名单范围

---

## 6. 跨表问题拒绝验证

- **请求 formType：** PROPERTY_INFO，**message：** "分析房产面积和合同金额的关系"
- **实际 aiMode：** DEEPSEEK_INTENT
- **实际 answer：** "当前查询按单表执行。房产面积属于房产情况表，合同金额属于租赁合同表，租赁人员信息属于租赁人员表；请选择其中一张表分别分析，跨表关联分析后续单独实现。"
- **结果：** ✅ 正确拒绝虚假跨表合并

**技术说明：** 跨表检测通过 `isCrossTableQuestion(intent, rawMessage)` 实现，必须传入原始 `rawMessage` 参数（绕过 DeepSeek 精炼可能删除跨表关键词的问题）。

---

## 7. API Key 安全检查

- `DEEPSEEK_API_KEY` 未写入任何代码或配置文件 ✅
- 未在本次验收记录中记录 key 原文 ✅
- 上一轮会话曾意外打印 key 到 PowerShell 输出（`$env:DEEPSEEK_API_KEY;` 语句），建议在 DeepSeek 控制台轮换该 key
- **风险：** ⚠️ 建议尽快轮换 API key

---

## 8. 后端测试与构建结果

```
mvn test -pl yudao-module-jijian/yudao-module-jijian-biz
  -Dtest=HandlerRegistryTest,LocalFallbackFormTypeTest,AiModeSemanticTest,RegressionGuardTest
→ Tests run: 31, Failures: 0, Errors: 0, Skipped: 0   BUILD SUCCESS
  （含 RegressionGuardTest 7 项新回归测试，2026-06-05 收尾轮添加）

mvn clean install -pl yudao-module-jijian/yudao-module-jijian-biz -am -DskipTests
→ BUILD SUCCESS (2026-06-05)

mvn clean install -pl yudao-server -am -DskipTests
→ BUILD SUCCESS (2026-06-05)
```

### 回归测试覆盖项（RegressionGuardTest）

| 测试 | 验证内容 |
|---|---|
| `exactly9PrimaryFormTypes_legacyAliasesNotPrimary` | primary 恰好 9 个；ATTENDANCE/REAL_ESTATE/CANTEEN_SUPPLY 不在主列表 |
| `crossTableDetection_usesRawMessage_notOnlyRefinedIntent` | 跨表检测必须传入 rawMessage，不能只依赖精炼后的 intent |
| `canteenSupplier_forbiddenGoals_returnsBoundaryText` | 部门/浪费/违规/异常触发字段白名单拒绝 |
| `deepSeekPrompts_doNotExposeRawSensitiveFields` | summary prompt 禁止输出手机号原文，不含 ark- key |
| `attendanceDaily_summaryPrompt_restrictsViolationJudgement` | prompt 禁止 SQL；打卡不在 forbiddenAnalyses |
| `deepSeekSystemPrompts_doNotContainRealApiKey` | intent/summary prompt 均不含 ark- 前缀 key |
| `primaryFormTypes_areAllSupported` | 所有 primary formType 都有 supported=true |

---

## 9. 前端构建结果

```
$env:NODE_OPTIONS="--max-old-space-size=4096"; pnpm build:prod
→ Build successful. Please see dist-prod directory  ✅（2026-06-05 再次验证）
```

- `pnpm build:prod` 脚本内置 `--max_old_space_size=8192`（8GB），在本机导致 OOM 退出码 3221225477
- 通过 `NODE_OPTIONS=--max-old-space-size=4096` 环境变量覆盖脚本参数，降为 4096MB 后构建成功
- 输出目录：`yudao-ui/yudao-ui-admin-vue3/dist-prod`

---

## 10. 本轮修改文件汇总

| 文件 | 变更说明 |
|---|---|
| `service/query/JijianQueryChatServiceImpl.java` | `generateSummarySafely` 增加 `rawMessage` 参数；`isCrossTableQuestion`/`isForbiddenByFieldWhitelist` 同步接收 `rawMessage`，保证跨表检测使用原始用户输入 |
| `service/query/dto/JijianAiQueryIntent.java` | 增加 `originalMessage` 字段（备用，实际检测改用方法参数）|
| `server/src/main/resources/application-local.yaml` | `deepseek-v4-pro` → `deepseek-chat`（修正无效模型名）|
| `.claude/skills/jijian-query-token-saver.md` | **新增** — 项目上下文压缩技能文件 |
| `.codex/runtime/query-9-table-http-acceptance.md` | **新增** — 本次 HTTP 验收记录 |

---

## 11. 空数据表说明（无真实样本）

以下 4 张表已通过空数据稳定性、columns、summary、chat 基础链路验证（code=0，aiMode=DEEPSEEK_SUMMARY，NPE 不存在）：

| 表 | 当前 total | 验证状态 |
|---|---|---|
| RECUPERATION_LEAVE | 0 | 空数据链路 ✅，AI 分析质量未用真实业务数据验证 |
| PERSONAL_LEAVE | 0 | 同上 |
| BUSINESS_TRIP | 0 | 同上 |
| COMPENSATORY_LEAVE | 0 | 同上 |

**重要：** 这些表的 DeepSeek 汇总质量（实际分析深度、字段覆盖、边界判断）尚未用真实业务数据验证。在有真实数据后需补充 HTTP 验收。

---

## 12. 当前风险

| 风险 | 等级 | 说明 |
|---|---|---|
| API key 已泄露 | 🔴 高 | 上轮 PowerShell 输出曾打印 key 原文，**必须立即在 DeepSeek 控制台轮换旧 key** |
| 空数据表 AI 质量未验证 | 🟡 中 | RECUPERATION_LEAVE/PERSONAL_LEAVE/BUSINESS_TRIP/COMPENSATORY_LEAVE 无真实样本，AI 分析质量待补充验收 |
| 前端 build:prod OOM | 🟡 中 | 本机需使用 NODE_OPTIONS=4096MB 覆盖，不能直接 pnpm build:prod |
| Maven 增量编译陷阱 | 🟡 低 | 修改 Java 源文件若不 clean，可能 .class 不更新；重要变更用 clean install |
