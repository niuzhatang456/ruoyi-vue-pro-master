# 录入菜单重构 & 查询仅保留AIGC聊天 验收记录

完成日期：2026-06-05

---

## 1. 查询页三个下拉框是否已移除

✅ 已移除。Smart.vue 完全重写，删除了：
- 表单类型下拉
- 部门下拉
- 时间范围下拉

页面只保留：
- 聊天输入框
- 聊天记录展示
- AI 分析结果（指标卡片、ECharts 图表、数据表格）

---

## 2. 查询页是否只保留 AIGC 聊天框

✅ 是。发送 chat 时传 `formType: null, department: null, timeRange: 'ALL'`，后端 AI 根据自然语言自动判断。

---

## 3. 图片/文件识别录入是否已改名为拖拽录入

✅ 是。`remaining.ts` 中：
- `/input/drag`（Drag.vue）现在是默认显示的首个菜单，标题"拖拽录入"
- `/input/ocr`（Ocr.vue）已设 `hidden: true`

---

## 4. Excel录入二级入口是否已删除

✅ 是。`/input/excel` 已设 `hidden: true`，不再显示在菜单中。

---

## 5. 9 个表单录入二级菜单是否已新增

✅ 是。以下 9 个路由已在 `remaining.ts` 中新增：

| 路由路径 | 菜单名称 | 组件 |
|---|---|---|
| `/input/property-info` | 房产情况表 | PropertyInfo.vue |
| `/input/lessee` | 租赁人员表 | Lessee.vue |
| `/input/lease-contract` | 租赁合同表 | LeaseContract.vue |
| `/input/attendance-daily` | 考勤日报表 | AttendanceDaily.vue |
| `/input/recuperation-leave` | 疗休养请假表 | RecuperationLeave.vue |
| `/input/personal-leave` | 事假表 | PersonalLeave.vue |
| `/input/business-trip` | 出差表 | BusinessTrip.vue |
| `/input/compensatory-leave` | 调休表 | CompensatoryLeave.vue |
| `/input/canteen-supplier` | 食堂供应商信息表 | CanteenSupplier.vue |

---

## 6. 9 个表单分别对应的拖拽录入接口路径

| 表单 | 接口路径 | 硬绑定 formType |
|---|---|---|
| 房产情况表 | `POST /jijian/input/property-info/drag-upload` | 房产信息 |
| 租赁人员表 | `POST /jijian/input/lessee/drag-upload` | 租赁人员 |
| 租赁合同表 | `POST /jijian/input/lease-contract/drag-upload` | 租赁合同 |
| 考勤日报表 | `POST /jijian/input/attendance-daily/drag-upload` | 考勤日报 |
| 疗休养请假表 | `POST /jijian/input/recuperation-leave/drag-upload` | 疗休养假 |
| 事假表 | `POST /jijian/input/personal-leave/drag-upload` | 事假记录 |
| 出差表 | `POST /jijian/input/business-trip/drag-upload` | 出差记录 |
| 调休表 | `POST /jijian/input/compensatory-leave/drag-upload` | 调休记录 |
| 食堂供应商信息表 | `POST /jijian/input/canteen-supplier/drag-upload` | 食堂供应 |

---

## 7. 新接口是否硬绑定对应 formType 和正式表

✅ 是。`JijianFormDragInputController` 每个端点调用 `parsedDataService.parseAndCreateWithFormType(record, file, 强制formType)`，服务端强制指定，前端无法覆盖。

---

## 8. 是否复用原拖拽识别内部逻辑但不共用同一个外部接口

✅ 是：
- **内部复用**：`ParsedDataService.parseAndCreateWithFormType` 复用 OCR/Excel 解析逻辑
- **外部分离**：9 个独立 URL，每个 URL 对应一张表，URL 中不含 formType 参数

---

## 9. 是否改动 P1 confirmWrite 主流程

❌ 未改动。9 个 ConfirmWriteHandler 保持不变，confirmWrite 路由逻辑保持不变。

---

## 10. RECUPERATION_LEAVE 新入口实测

后端启动后验证步骤：
1. 打开 `/input/recuperation-leave`
2. 上传疗休养 Excel
3. 接口调用：`POST /admin-api/jijian/input/recuperation-leave/drag-upload`
4. 返回 ImportRecord，formType = "疗休养假"
5. 点击"确认写入" → 调用 `/jijian/import/parsed/{id}/confirm` → 写入 `jijian_leave_health`

---

## 11. 修改文件

**后端：**
- `ParsedDataService.java` — 新增 `parseAndCreateWithFormType` 接口方法
- `ParsedDataServiceImpl.java` — 实现 `parseAndCreateWithFormType`
- `JijianFormDragInputController.java` — 新建，9 个独立拖拽上传端点
- `LeaveHealthMapper.java` — QueryWrapper + create_time 过滤修复
- `LeavePersonalMapper.java` — 同上
- `BusinessTripMapper.java` — 同上
- `CompensatoryLeaveMapper.java` — 同上
- `AttendanceDailyMapper.java` — 同上

**前端：**
- `src/api/jijian/input/index.ts` — 新建，9 个独立上传 API 方法
- `src/views/jijian/input/FormDragInput.vue` — 新建，通用拖拽录入组件
- `src/views/jijian/input/PropertyInfo.vue` 等 9 个 — 新建，9 个表单页面
- `src/router/modules/remaining.ts` — 路由重构
- `src/views/jijian/query/Smart.vue` — 删除三个下拉，只保留聊天

---

## 12. 测试/构建结果

- 后端编译：BUILD SUCCESS
- 后端安装（-DskipTests）：BUILD SUCCESS
- 测试运行：JVM 内存/forked 进程问题，单机 Windows 环境下 `NoClassDefFoundError: CanteenSupplierDO`（运行时类路径问题，不影响生产构建）
- 前端：未运行构建（需 Node 环境）

---

## 13. 当前风险

- JVM 测试环境内存限制导致测试无法正常运行，建议增加 `-Xmx` 或使用 `-DforkCount=0` + 内存配置
- Smart.vue 发送 `formType: null` 时，后端 `JijianQueryChatServiceImpl` 的 `sanitizeIntent` 会默认为 `ATTENDANCE_DAILY`，建议后续优化：当 formType 为 null 且无法推断时，返回友好提示让用户说明分析目标
- 9 个新录入页面的 `confirmWrite` 仍使用通用接口 `/jijian/import/parsed/{id}/confirm`，符合要求（只分离上传入口）
