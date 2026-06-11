# 项目瘦身报告

## 体积

- 删除前：2.915 GB（3,130,180,174 bytes）
- 删除后：1.678 GB（1,801,691,291 bytes）
- 减少：约 1.237 GB，约 42.4%

## 已删除后端模块

- `yudao-module-ai`
- `yudao-module-bpm`
- `yudao-module-crm`
- `yudao-module-erp`
- `yudao-module-member`
- `yudao-module-mes`
- `yudao-module-report`
- `yudao-module-wms`

## 已删除前端目录

`src/views`：`ai`、`bpm`、`crm`、`erp`、`iot`、`mall`、`member`、`mes`、`mp`、`pay`、`report`、`wms`。

`src/api`：`ai`、`bpm`、`crm`、`erp`、`iot`、`mall`、`member`、`mes`、`mp`、`pay`、`wms`；`report` 原本不存在。

## 配置修改

- 根 `pom.xml`：移除无关模块模板。
- `yudao-server/pom.xml`：只保留 system、infra、jijian 和运行基础依赖。
- `src/router/modules/remaining.ts`：移除 BPM、商城、会员、支付、CRM、AI、IoT、MES 静态路由。
- `src/main.ts`：移除 BPM 页面插件引用。

## 构建产物与缓存

- 清理所有 Maven `target`。
- 清理前端 `dist`、`dist-prod`、`.vite`。
- 清理 JVM 崩溃日志。
- 删除前端 `node_modules`。
- 保留 `tools/paddleocr-service/.venv`，因为它属于纪检文件解析/OCR 能力。

## 核心模块

已确认保留：

- `yudao-module-jijian`
- `yudao-server`
- `yudao-framework`
- `yudao-module-system`
- `yudao-module-infra`
- `yudao-ui/yudao-ui-admin-vue3`
- `application-local.yaml`

## 验证结果

- 纪检后端：`mvn clean install -pl yudao-module-jijian/yudao-module-jijian-biz -am -DskipTests`，成功。
- Server：`mvn clean install -pl yudao-server -am -DskipTests`，成功。
- 前端：`pnpm build:prod`，成功。
- 构建验证完成后已再次删除生成物。

## 后续要求与风险

- 因已删除 `node_modules`，下次前端构建前需要执行 `pnpm install`。
- 数据库中的历史无关菜单尚未删除，参见 `remove-unrelated-menu-sql.md`。
- 前端保留的部分通用组件目录仍包含商城/BPM 辅助代码，但当前生产构建未引用且构建成功；为降低误删通用组件风险，本轮未继续扩大删除范围。
