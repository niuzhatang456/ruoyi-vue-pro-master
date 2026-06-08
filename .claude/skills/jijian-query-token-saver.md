---
name: jijian-query-token-saver
description: 纪检信息系统查询模块开发上下文压缩技能。用于后续 Claude/Codex 开发任务，避免重复粘贴完整项目背景。触发条件：用户提到"纪检"、"查询模块"、"9张表"、"PROPERTY_INFO"、"CANTEEN_SUPPLIER"、"DeepSeek接入"或引用本项目路径时自动加载。
---

# 纪检查询模块开发 Skill

## 项目

**项目路径：** `D:\VScode\data\ruoyi-vue-pro-master`

项目是基于 RuoYi-Vue-Pro 的纪检信息系统，后端 Spring Boot，前端 Vue3。

---

## 绝对禁止

- 不处理 Git、push、workflow、PAT。
- 不修改 P1 录入模块（OCR、Excel、拖拽录入、confirmWrite、导入暂存、人工确认、正式写入逻辑）。
- 不允许 AI 生成 SQL；不允许执行 AI 返回的 SQL。
- 不发送数据库表结构、Mapper XML、SQL、全量明细数据给 DeepSeek。
- 不把真实 API key 写进代码或配置文件。
- 不基于表头不存在的字段做分析；不做跨表虚假合并分析。

---

## AI 接入状态

DeepSeek 官方 API 已真实验证成功。配置使用环境变量：

```yaml
jijian:
  ai:
    deepseek:
      enabled: true
      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
      model: ${DEEPSEEK_MODEL:deepseek-chat}
      api-key: ${DEEPSEEK_API_KEY:}
      timeout-seconds: 30
```

DeepSeek 只做两件事：
1. 自然语言解析为受控 intent。
2. 基于后端 summary 做中文总结。

无 key、401、非 2xx、网络异常、非法 JSON 时走 `LocalFallbackAiIntentClient`。

---

## 当前查询模块方向

必须按 **9 张实际数据表**独立查询、独立 summary、独立字段白名单、独立 DeepSeek 总结。

| formType | 表名 |
|---|---|
| `PROPERTY_INFO` | 房产情况表 |
| `LESSEE` | 租赁人员表 |
| `LEASE_CONTRACT` | 租赁合同表 |
| `ATTENDANCE_DAILY` | 考勤日报表 |
| `RECUPERATION_LEAVE` | 疗休养请假表 |
| `PERSONAL_LEAVE` | 事假表 |
| `BUSINESS_TRIP` | 出差表 |
| `COMPENSATORY_LEAVE` | 调休表 |
| `CANTEEN_SUPPLIER` | 食堂供应商信息表 |

旧值 `REAL_ESTATE / ATTENDANCE / CANTEEN_SUPPLY` 只作为兼容入口，不作为主下拉项。

---

## 9 张表字段白名单

### PROPERTY_INFO 房产情况表
**字段：** 房产地址、房产名称、产权信息、建筑时间、面积、租赁情况、备注  
**允许分析：** 房产总数、面积合计/均值/最大/最小、租赁情况分布、产权信息分布、建筑年份分布  
**禁止分析：** 合同金额、租赁人员、支付情况

### LESSEE 租赁人员表
**字段：** 个人/组织、联系人、手机号、身份证、营业执照、是否单位内部人员、备注  
**允许分析：** 总数、个人/组织分布、内外部人员数、各证件完整性统计  
**⚠️ 手机号、身份证、营业执照不得原文发送给 DeepSeek，只做完整性统计**

### LEASE_CONTRACT 租赁合同表
**字段：** 房产、租赁人员、合同开始/结束时间、金额、支付情况、水电费管理、合同内容摘要、备注  
**允许分析：** 合同总数、金额统计、支付/水电费分布、到期情况、内容摘要完整性  
**禁止分析：** 房产面积、产权、身份证等其他表字段

### ATTENDANCE_DAILY 考勤日报表
**字段：** 姓名、员工编号、部门、上班/下班打卡时间/结果/地点/备注、考勤日期  
**允许分析：** 总数、人数、部门数、打卡结果分布、缺卡/迟到/早退/异常次数  
**禁止分析：** 请假、出差、调休；不得直接判断违纪

### RECUPERATION_LEAVE 疗休养请假表
**字段：** 部门、申请人、员工编号、休假地点、开始/结束时间、请假天数、工作年限、参加工作时间、备注  
**允许分析：** 记录数、人数、部门数、请假天数统计、地点分布、工作年限分布

### PERSONAL_LEAVE 事假表
**字段：** 部门、申请人、员工编号、请假类型、请假事由、开始/结束时间、请假天数、是否出义、出义地点、请假状态、请假月份、备注  
**允许分析：** 记录数、人数、天数统计、类型/状态/出义/月份分布

### BUSINESS_TRIP 出差表
**字段：** 同事假表字段  
**允许分析：** 同事假表分析项（"出差天数"替代"请假天数"）

### COMPENSATORY_LEAVE 调休表
**字段：** 申请人、员工编号、部门、加班开始/结束时间/班次、调休开始/结束时间/班次、调休时长、是否出义、出义地址、备注  
**允许分析：** 记录数、人数、部门数、调休时长统计、出义分布、班次分布

### CANTEEN_SUPPLIER 食堂供应商信息表
**字段：** 项目名称、规格/等级、单位、价格、采价点  
**允许分析：** 记录总数、项目数量、各分组统计、价格最高/最低/均值、差额、差异比例  
**禁止分析：** 部门、人员、浪费、违规、异常、审批、备注、供应次数

---

## 关键源码位置

```
yudao-module-jijian/yudao-module-jijian-biz/src/main/java/cn/iocoder/yudao/module/jijian/
├── enums/query/JijianQueryFormTypeEnum.java          # 9 主表枚举
├── controller/admin/query/JijianQueryController.java  # /form-types /list /page /chat
├── service/query/
│   ├── JijianQueryChatServiceImpl.java               # chat 主逻辑，含跨表/白名单检查
│   ├── JijianActualTableQueryService.java            # 按实际表 Mapper 分发
│   ├── handler/                                      # 9 张表独立 Handler
│   │   ├── PropertyInfoQueryHandler.java
│   │   ├── LesseeQueryHandler.java
│   │   ├── LeaseContractQueryHandler.java
│   │   ├── AttendanceDailyQueryHandler.java
│   │   ├── RecuperationLeaveQueryHandler.java
│   │   ├── PersonalLeaveQueryHandler.java
│   │   ├── BusinessTripQueryHandler.java
│   │   ├── CompensatoryLeaveQueryHandler.java
│   │   └── CanteenSupplierQueryHandler.java
│   └── ai/
│       ├── JijianQueryFieldWhitelist.java            # 每表字段白名单
│       ├── JijianDeepSeekIntentClient.java           # DeepSeek HTTP 调用
│       └── LocalFallbackAiIntentClient.java          # 本地规则回退
└── service/query/dto/JijianAiQueryIntent.java        # intent DTO（含 originalMessage 字段）

yudao-ui/yudao-ui-admin-vue3/src/
├── views/jijian/query/Smart.vue                      # 前端查询页
└── api/jijian/query/index.ts                         # 前端 API
```

---

## 重要技术说明

### 跨表拒绝机制
`JijianQueryChatServiceImpl.isCrossTableQuestion(intent, rawMessage)` 同时检查：
- `intent.getAnalysisGoal()`（AI 精炼后的目标）
- `rawMessage`（用户原始输入，通过方法参数直接传入）

**关键**：必须传 `rawMessage` 参数，否则 AI 精炼后 "合同金额" 等词可能从 analysisGoal 中消失导致检测失效。

### PowerShell HTTP 测试编码问题
PowerShell `Invoke-WebRequest` 默认非 UTF-8，发送中文会乱码。正确做法：
```powershell
$bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($rawBody)
Invoke-WebRequest -Body $bodyBytes -ContentType "application/json; charset=utf-8"
```

### DeepSeek 模型名
`application-local.yaml` 中默认模型应为 `deepseek-chat`（非 `deepseek-v4-pro`）。

### Maven 增量编译陷阱
修改 Java 源文件后，若 `.class` 时间戳比源文件新，Maven 不会重编译。解决：
```powershell
(Get-Item "path/to/File.java").LastWriteTime = Get-Date
```

---

## 当前已完成状态

1. `/admin-api/jijian/query/form-types` 只返回 9 个 primary 表，全部 `supported=true`
2. 9 个实际表独立 Handler 已注册，`JijianFormQueryHandlerRegistry` 路由正确
3. `JijianActualTableQueryService` 按实际表固定 Mapper 分发
4. 每张表有独立分页、columns、summary（空数据不 NPE）
5. DeepSeek 官方 API 真实调用验证通过（aiMode=DEEPSEEK_SUMMARY）
6. chat 支持 formType / department / timeRange / history（最近 6 轮）
7. 字段白名单按 9 张表隔离，forbidden 检查使用 rawMessage 保证有效性
8. 跨表问题（如"房产面积+合同金额"）正确拒绝虚假合并
9. LESSEE 敏感字段（手机号/身份证/营业执照）只做完整性统计，不原文输出
10. CANTEEN_SUPPLIER 正确拒绝"部门浪费"等非白名单分析
11. 前端 Smart.vue 展示 9 表下拉、summary 面板、DeepSeek/本地分析标签

---

## 标准验证命令

```bash
# 单元测试（24 tests，0 failures）
mvn test -pl yudao-module-jijian/yudao-module-jijian-biz \
  "-Dtest=HandlerRegistryTest,LocalFallbackFormTypeTest,AiModeSemanticTest"

# 后端构建
mvn clean install -pl yudao-module-jijian/yudao-module-jijian-biz -am -DskipTests
mvn clean install -pl yudao-server -am -DskipTests

# 前端构建（注意：build:prod 需要足够内存，可用 4096MB）
cd yudao-ui/yudao-ui-admin-vue3
node --max-old-space-size=4096 node_modules/.bin/vite.CMD build --mode prod
# 或正常：
pnpm build:prod
```

---

## 回答格式

每轮完成后只输出：

```
1. 本轮完成内容
2. 修改文件
3. 测试 / 构建结果
4. HTTP 验证结果
5. 未完成事项 / 风险
```
