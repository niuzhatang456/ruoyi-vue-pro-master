<template>
  <PageShell title="数据查询" description="普通条件查询与受控 AI 智能问答，当前支持考勤信息。">

    <!-- ① 查询条件区 -->
    <el-card shadow="never" class="mb-16px">
      <div class="filter-row">
        <el-form inline>
          <el-form-item label="数据类型">
            <el-select
              v-model="selectedFormType"
              style="width: 160px"
              @change="onFormTypeChange"
            >
              <el-option
                v-for="ft in formTypes"
                :key="ft.value"
                :label="ft.label"
                :value="ft.value"
                :disabled="!ft.supported"
              >
                <span>{{ ft.label }}</span>
                <span v-if="!ft.supported" style="color: #ccc; margin-left: 6px; font-size: 12px">
                  （待扩展）
                </span>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="部门">
            <el-select v-model="selectedDepartment" style="width: 160px">
              <el-option
                v-for="dept in departments"
                :key="dept.value"
                :label="dept.label"
                :value="dept.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="时间范围">
            <el-select v-model="selectedTimeRange" style="width: 140px">
              <el-option v-for="tr in timeRangeOptions" :key="tr.value" :label="tr.label" :value="tr.value" />
            </el-select>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="queryLoading" @click="handleQuery">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- ② 普通查询结果区 -->
    <template v-if="genericResult">
      <!-- 统计摘要卡片 — 考勤 -->
      <template v-if="selectedFormType === 'ATTENDANCE' && attendanceSummary">
        <el-row :gutter="16" class="mb-16px">
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <div class="stat-num">{{ attendanceSummary.totalCount }}</div>
              <div class="stat-label">总记录数</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <div class="stat-num">{{ attendanceSummary.departmentCount }}</div>
              <div class="stat-label">涉及部门数</div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" class="mb-16px">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>按部门统计</template>
              <el-table :data="attendanceSummary.byDepartment" size="small" stripe max-height="240">
                <el-table-column prop="department" label="部门" />
                <el-table-column prop="count" label="数量" width="80" align="right" />
              </el-table>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>按签到状态统计</template>
              <el-table :data="attendanceSummary.byAttendanceStatus" size="small" stripe max-height="240">
                <el-table-column prop="status" label="状态" />
                <el-table-column prop="count" label="数量" width="80" align="right" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </template>

      <!-- 明细分页表格 — 动态列 -->
      <el-card shadow="never" class="mb-16px">
        <template #header>{{ currentFormLabel }}明细</template>
        <el-table :data="genericResult.pageResult.list" stripe size="small">
          <el-table-column
            v-for="col in genericResult.columns"
            :key="col.key"
            :prop="col.key"
            :label="col.label"
            min-width="100"
          />
        </el-table>
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="genericResult.pageResult.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="mt-12px"
          @change="handleQuery"
        />
      </el-card>
    </template>

    <!-- ③ 智能问答区 -->
    <el-card shadow="never">
      <template #header>
        <span>智能问答</span>
        <el-tag size="small" :type="chatModeTagType" class="ml-8px">{{ chatModeLabel }}</el-tag>
      </template>

      <!-- 消息列表 -->
      <div ref="chatBoxRef" class="chat-box">
        <div v-if="chatMessages.length === 0" class="chat-empty">
          <el-text type="info">输入自然语言问题，例如：一周内全单位有多少人出勤，各部门分别多少人</el-text>
        </div>
        <div
          v-for="(msg, idx) in chatMessages"
          :key="idx"
          :class="['chat-msg', msg.role === 'user' ? 'chat-msg--user' : 'chat-msg--assistant']"
        >
          <div class="chat-bubble">
            <div class="chat-content">{{ msg.content }}</div>
            <div v-if="msg.role === 'assistant' && msg.aiMode" class="chat-hint">
              <template v-if="msg.aiMode === 'LOCAL_FALLBACK'">当前为本地规则解析模式</template>
              <template v-else>{{ msg.aiMode }}</template>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="chat-input-area">
        <el-input
          v-model="chatInput"
          :disabled="chatLoading"
          placeholder="输入自然语言问题，回车发送…"
          @keyup.enter="handleChat"
        />
        <el-button
          type="primary"
          :loading="chatLoading"
          :disabled="!chatInput.trim()"
          @click="handleChat"
        >
          发送
        </el-button>
      </div>
    </el-card>

    <!-- AI 结构化统计同步展示 -->
    <template v-if="chatSummaryRaw">
      <el-divider>智能问答统计结果</el-divider>

      <!-- 考勤 chat 统计 -->
      <template v-if="chatLastFormType === 'ATTENDANCE'">
        <el-row :gutter="16" class="mb-16px">
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <div class="stat-num">{{ (chatSummaryRaw as any).totalCount }}</div>
              <div class="stat-label">总记录数</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <div class="stat-num">{{ (chatSummaryRaw as any).departmentCount }}</div>
              <div class="stat-label">涉及部门数</div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>按部门统计（AI 查询）</template>
              <el-table :data="(chatSummaryRaw as any).byDepartment" size="small" stripe max-height="200">
                <el-table-column prop="department" label="部门" />
                <el-table-column prop="count" label="数量" width="80" align="right" />
              </el-table>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>按签到状态统计（AI 查询）</template>
              <el-table :data="(chatSummaryRaw as any).byAttendanceStatus" size="small" stripe max-height="200">
                <el-table-column prop="status" label="状态" />
                <el-table-column prop="count" label="数量" width="80" align="right" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </template>

    </template>

  </PageShell>
</template>

<script setup lang="ts">
import PageShell from '../components/PageShell.vue'
import {
  JijianQueryApi,
  type QueryFormTypeVO,
  type QueryDepartmentVO,
  type JijianQueryPageRespVO,
  type AttendanceSummaryDTO,
  type ChatHistoryItem
} from '@/api/jijian/query'
import { ElMessage } from 'element-plus'

// ============================================================
// 时间范围选项（前端固定）
// ============================================================
const timeRangeOptions = [
  { value: 'ONE_YEAR', label: '一年' },
  { value: 'HALF_YEAR', label: '半年' },
  { value: 'THREE_MONTHS', label: '三个月' },
  { value: 'ONE_MONTH', label: '一个月' },
  { value: 'ONE_WEEK', label: '一周' },
  { value: 'ONE_DAY', label: '一日' }
]

// ============================================================
// 筛选条件
// ============================================================
const selectedFormType = ref('ATTENDANCE')
const selectedDepartment = ref('ALL')
const selectedTimeRange = ref('ONE_WEEK')

// ============================================================
// 数据类型 & 部门下拉
// ============================================================
const formTypes = ref<QueryFormTypeVO[]>([])
const departments = ref<QueryDepartmentVO[]>([{ value: 'ALL', label: '全单位' }])

const currentFormLabel = computed(() => {
  const ft = formTypes.value.find((f) => f.value === selectedFormType.value)
  return ft?.label ?? '数据'
})

const loadFormTypes = async () => {
  try {
    formTypes.value = await JijianQueryApi.getFormTypes()
  } catch (e) {
    console.error('加载数据类型失败', e)
  }
}

const loadDepartments = async (formType: string) => {
  try {
    departments.value = await JijianQueryApi.getDepartments(formType)
  } catch (e) {
    departments.value = [{ value: 'ALL', label: '全单位' }]
  }
}

const onFormTypeChange = (val: string) => {
  selectedDepartment.value = 'ALL'
  selectedTimeRange.value = 'ONE_WEEK'
  pageNo.value = 1
  genericResult.value = null
  loadDepartments(val)
}

// ============================================================
// 普通条件查询 — 使用通用 /query/page 接口
// ============================================================
const queryLoading = ref(false)
const genericResult = ref<JijianQueryPageRespVO | null>(null)
const pageNo = ref(1)
const pageSize = ref(10)

/** 从通用 summary 中提取考勤摘要 */
const attendanceSummary = computed<AttendanceSummaryDTO | null>(() => {
  const s = genericResult.value?.summary
  if (!s) return null
  const a = s as AttendanceSummaryDTO
  return 'byDepartment' in a ? a : null
})

const handleQuery = async () => {
  const ft = formTypes.value.find((f) => f.value === selectedFormType.value)
  if (ft && !ft.supported) {
    ElMessage.warning('该数据类型查询能力待扩展')
    return
  }
  queryLoading.value = true
  try {
    genericResult.value = await JijianQueryApi.page({
      formType: selectedFormType.value,
      department: selectedDepartment.value,
      timeRange: selectedTimeRange.value,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    })
  } catch (e: any) {
    ElMessage.error('查询失败：' + (e?.message ?? '未知错误'))
  } finally {
    queryLoading.value = false
  }
}

const handleReset = () => {
  selectedFormType.value = 'ATTENDANCE'
  selectedDepartment.value = 'ALL'
  selectedTimeRange.value = 'ONE_WEEK'
  pageNo.value = 1
  genericResult.value = null
  loadDepartments('ATTENDANCE')
}

// ============================================================
// 智能问答
// ============================================================
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  aiMode?: string
}

const chatMessages = ref<ChatMessage[]>([])
const chatInput = ref('')
const chatLoading = ref(false)
const chatBoxRef = ref<HTMLElement>()
const conversationId = ref<string | undefined>(undefined)
/** 原始 data 对象，类型依 chatLastFormType 而定 */
const chatSummaryRaw = ref<Record<string, unknown> | null>(null)
/** 最近一次 chat 使用的 formType */
const chatLastFormType = ref<string>('ATTENDANCE')

const scrollChatBottom = async () => {
  await nextTick()
  if (chatBoxRef.value) {
    chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  }
}

const handleChat = async () => {
  const msg = chatInput.value.trim()
  if (!msg) return

  chatMessages.value.push({ role: 'user', content: msg })
  chatInput.value = ''
  await scrollChatBottom()

  const historySlice = chatMessages.value.slice(-13, -1)
  const history: ChatHistoryItem[] = historySlice.map((m) => ({
    role: m.role,
    content: m.content
  }))

  chatLoading.value = true
  try {
    const resp = await JijianQueryApi.chat({
      conversationId: conversationId.value,
      formType: selectedFormType.value,
      department: selectedDepartment.value,
      timeRange: selectedTimeRange.value,
      message: msg,
      history
    })
    conversationId.value = resp.conversationId
    chatMessages.value.push({
      role: 'assistant',
      content: resp.answer,
      aiMode: resp.aiMode
    })
    if (resp.data) {
      chatSummaryRaw.value = resp.data as Record<string, unknown>
      // 记录本次 chat 的 formType，以便正确渲染统计区域
      chatLastFormType.value = (resp.queryIntent?.formType as string) ?? selectedFormType.value
    }
  } catch (e: any) {
    chatMessages.value.push({
      role: 'assistant',
      content: '查询失败：' + (e?.message ?? '服务异常，请稍后重试')
    })
  } finally {
    chatLoading.value = false
    await scrollChatBottom()
  }
}

// ============================================================
// Chat mode display helpers
// ============================================================
const latestAiMode = computed(() => {
  const msgs = chatMessages.value
  for (let i = msgs.length - 1; i >= 0; i--) {
    if (msgs[i].role === 'assistant' && msgs[i].aiMode) {
      return msgs[i].aiMode as string
    }
  }
  return ''
})

const chatModeLabel = computed(() => {
  switch (latestAiMode.value) {
    case 'DEEPSEEK_SUMMARY': return 'DeepSeek 解析 · DeepSeek 总结'
    case 'DEEPSEEK_INTENT': return 'DeepSeek 解析 · 本地总结'
    case 'LOCAL_FALLBACK': return '本地规则解析'
    default: return '本地规则解析'
  }
})

const chatModeTagType = computed((): 'success' | 'info' | 'warning' => {
  switch (latestAiMode.value) {
    case 'DEEPSEEK_SUMMARY': return 'success'
    case 'DEEPSEEK_INTENT': return 'warning'
    default: return 'info'
  }
})

// ============================================================
// 初始化
// ============================================================
onMounted(async () => {
  await loadFormTypes()
  await loadDepartments(selectedFormType.value)
})
</script>

<style scoped>
.filter-row .el-form--inline .el-form-item {
  margin-bottom: 0;
}

.stat-card {
  text-align: center;
  padding: 8px 0;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.chat-box {
  height: 320px;
  overflow-y: auto;
  padding: 8px 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.chat-msg {
  display: flex;
}

.chat-msg--user {
  justify-content: flex-end;
}

.chat-msg--assistant {
  justify-content: flex-start;
}

.chat-bubble {
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 14px;
  line-height: 1.6;
}

.chat-msg--user .chat-bubble {
  background: var(--el-color-primary-light-9);
}

.chat-content {
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-hint {
  margin-top: 4px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.chat-input-area {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.chat-input-area .el-input {
  flex: 1;
}

.ml-8px {
  margin-left: 8px;
}

.mt-12px {
  margin-top: 12px;
}

.mb-16px {
  margin-bottom: 16px;
}
</style>
