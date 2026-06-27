<template>
  <div class="aigc-layout">
    <!-- 左侧历史对话栏 -->
    <div class="aigc-sidebar">
      <div class="sidebar-top">
        <el-button type="primary" class="new-chat-btn" @click="newConversation">
          <el-icon><Plus /></el-icon> 新建对话
        </el-button>
      </div>
      <div class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          :class="['conv-item', { 'conv-item--active': conv.id === currentConvId }]"
          @click="switchConversation(conv.id)"
        >
          <span class="conv-title">{{ conv.title }}</span>
          <el-button
            v-hasPermi="['jijian:query-history:delete']"
            class="conv-del"
            size="small"
            text
            @click.stop="deleteConversation(conv.id)"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <div v-if="conversations.length === 0" class="conv-empty">暂无历史对话</div>
      </div>
    </div>

    <!-- 主聊天区 -->
    <div class="aigc-main">
      <div class="prompt-bar">
        <span>只分析本地数据库真实数据；数据库没有的内容会如实说明，手机号、身份证、营业执照等敏感字段默认脱敏。</span>
      </div>

      <!-- 欢迎区（无消息时） -->
      <div v-if="currentMessages.length === 0" class="chat-welcome">
        <div class="welcome-icon">🔍</div>
        <h2 class="welcome-title">纪检数据智能分析</h2>
        <p class="welcome-sub">输入自然语言，AI 分析本地数据库真实数据，返回图表与结论</p>
        <div class="prompt-grid">
          <div
            v-for="p in promptExamples"
            :key="p"
            class="prompt-chip"
            @click="sendPrompt(p)"
          >
            {{ p }}
          </div>
        </div>
      </div>

      <!-- 消息流 -->
      <div v-else ref="messagesRef" class="messages-area">
        <div
          v-for="(msg, idx) in currentMessages"
          :key="idx"
          :class="['msg-row', msg.role === 'user' ? 'msg-row--user' : 'msg-row--ai']"
        >
          <div class="msg-avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
          <div class="msg-body">
            <!-- 用户消息 -->
            <div v-if="msg.role === 'user'" class="msg-bubble msg-bubble--user">
              {{ msg.content }}
            </div>

            <!-- AI 消息 -->
            <div v-else class="msg-bubble msg-bubble--ai">
              <!-- loading 状态 -->
              <div v-if="msg.loading" class="msg-loading">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </div>

              <!-- 回答文本 -->
              <div v-else>
                <div class="msg-answer">{{ msg.content }}</div>

                <!-- AI 模式标签 -->
                <div v-if="msg.aiMode" class="msg-mode-tag">
                  <el-tag :type="modeTagType(msg.aiMode)" size="small">{{ aiModeText(msg.aiMode) }}</el-tag>
                </div>

                <!-- 指标卡片 -->
                <el-row v-if="msg.metrics && msg.metrics.length" :gutter="12" class="result-metrics">
                  <el-col
                    v-for="m in msg.metrics"
                    :key="m.key"
                    :xs="12"
                    :sm="8"
                    :md="6"
                    class="mb-8px"
                  >
                    <div class="metric-card">
                      <div class="metric-val">
                        {{ m.value }}<span v-if="m.unit" class="metric-unit">{{ m.unit }}</span>
                      </div>
                      <div class="metric-label">{{ m.label }}</div>
                    </div>
                  </el-col>
                </el-row>

                <!-- 图表区（仅最新 AI 消息渲染，旧消息显示占位） -->
                <div v-if="msg.charts && msg.charts.length">
                  <el-row v-if="idx === latestAiIdx" :gutter="12" class="result-charts">
                    <el-col
                      v-for="(chart, ci) in msg.charts"
                      :key="ci"
                      :xs="24"
                      :md="12"
                      class="mb-12px"
                    >
                      <div class="chart-card">
                        <div class="chart-title">{{ chart.title }}</div>
                        <div :ref="(el) => setChartRef(el, ci)" class="chart-dom"></div>
                      </div>
                    </el-col>
                  </el-row>
                  <div v-else class="chart-placeholder">
                    [包含 {{ msg.charts.length }} 张图表，切换至该对话可查看]
                  </div>
                </div>

                <!-- 数据来源元信息 -->
                <div v-if="msg.databaseContextMeta" class="db-meta">
                  <span>数据来源：本地数据库只读查询</span>
                  <span v-if="msg.databaseContextMeta.tablesUsed?.length">
                    · 涉及表：{{ msg.databaseContextMeta.tablesUsed.join('、') }}
                  </span>
                  <span v-if="msg.databaseContextMeta.truncated" class="text-warning">
                    · 数据量较大，已聚合展示
                  </span>
                </div>

                <!-- SQL 查询痕迹（可折叠） -->
                <div v-if="msg.sqlTrace && msg.sqlTrace.length" class="sql-trace-wrap">
                  <div class="sql-trace-header" @click="msg.sqlTraceExpanded = !msg.sqlTraceExpanded">
                    <span class="sql-trace-toggle">{{ msg.sqlTraceExpanded ? '▾' : '▸' }}</span>
                    <span>只读查询审计（共 {{ msg.sqlTrace.length }} 次数据库查询）</span>
                  </div>
                  <div v-if="msg.sqlTraceExpanded" class="sql-trace-body">
                    <div v-for="(t, ti) in msg.sqlTrace" :key="ti" class="sql-trace-item">
                      <div class="sql-trace-purpose">
                        <span class="sql-trace-idx">#{{ ti + 1 }}</span>
                        {{ t.purpose }}
                        <el-tag v-if="t.error" type="danger" size="small" class="ml-4px">失败</el-tag>
                        <el-tag v-else type="success" size="small" class="ml-4px">{{ t.rowCount }} 行</el-tag>
                      </div>
                      <pre class="sql-trace-sql">{{ t.sql }}</pre>
                      <div v-if="t.error" class="sql-trace-error">{{ t.error }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="latestDatabaseMessage" class="database-panel">
        <div class="database-panel__header">
          <div>
            <div class="database-panel__title">数据库实际返回数据</div>
            <div class="database-panel__meta">
              共 {{ latestDatabaseMessage.pageResult?.total ?? latestDatabaseRows.length }} 条
              <span v-if="latestDatabaseMessage.databaseContextMeta?.tablesUsed?.length">
                · {{ latestDatabaseMessage.databaseContextMeta.tablesUsed.join('、') }}
              </span>
            </div>
          </div>
          <el-button size="small" type="primary" plain @click="handleDispose(latestDatabaseMessage, latestDatabaseMessageIndex)">
            处置
          </el-button>
        </div>
        <el-table :data="latestDatabaseRows" stripe size="small" height="220">
          <el-table-column
            v-for="col in latestDatabaseColumns"
            :key="col.key"
            :prop="col.key"
            :label="col.label"
            min-width="110"
            show-overflow-tooltip
          />
          <el-table-column
            v-if="hasContractOriginalRows(latestDatabaseRows)"
            label="合同原件"
            width="120"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="getContractOriginalUrl(row)"
                type="primary"
                size="small"
                link
                @click="openContractOriginal(row)"
              >
                查看合同原件
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 底部输入区 -->
      <div class="input-area">
        <div class="input-wrap">
          <el-input
            v-model="chatInput"
            :disabled="chatLoading"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入问题，例如：分析本月各部门出勤率 / 查询一年内缺勤最多的部门"
            resize="none"
            @keydown="handleKeydown"
          />
          <el-button
            type="primary"
            :loading="chatLoading"
            :disabled="!chatInput.trim()"
            class="send-btn"
            @click="sendMessage"
          >
            发送
          </el-button>
        </div>
        <div class="input-hint">Enter 发送 · Shift+Enter 换行</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { Plus, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import {
  JijianQueryApi,
  type JijianMetricVO,
  type JijianChartVO,
  type JijianAnalysisTableVO,
  type JijianDatabaseContextMetaVO,
  type JijianSqlTraceVO
} from '@/api/jijian/query'
import { QueryHistoryApi, type QueryHistoryVO } from '@/api/jijian/queryHistory'
import { DisposalRecordApi } from '@/api/jijian/disposalRecord'

// ===== 类型定义 =====
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  loading?: boolean
  aiMode?: string
  metrics?: JijianMetricVO[]
  charts?: JijianChartVO[]
  tables?: JijianAnalysisTableVO[]
  columns?: Array<{ key: string; label: string }>
  pageResult?: { list: Array<Record<string, any>>; total: number }
  databaseContextMeta?: JijianDatabaseContextMetaVO
  sqlTrace?: JijianSqlTraceVO[]
  sqlTraceExpanded?: boolean
  queryHistoryId?: number
  queryResultJson?: string
}

interface Conversation {
  id: string
  title: string
  createdAt: string
  messages: ChatMessage[]
  conversationId?: string
  serverHistoryId?: number
  serverHistoryIds?: number[]
  serverHistories?: QueryHistoryVO[]
}

// ===== 提示词示例 =====
const promptExamples = [
  '综合管理部有哪些人调休，分别调休多久',
  '各部门调休次数和调休时长对比',
  '查询一年内缺勤人数最多的部门是哪个',
  '查询本月缺卡人员，判断是否因请假、出差或疗休养导致',
  '分析各部门疗休养请假天数情况',
  '帮我分析一下最近一年各部门考勤、请假、出差、调休情况，有没有异常',
  '分析食堂供应商不同采价点的价格差异',
]

// ===== localStorage 持久化 =====
const STORAGE_KEY = 'jijian_conversations'

const loadConversations = (): Conversation[] => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

const saveConversations = (list: Conversation[]) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch {
    // localStorage 不可用时静默忽略
  }
}

// ===== 状态 =====
const conversations = ref<Conversation[]>(loadConversations())
const currentConvId = ref<string | null>(
  conversations.value.length > 0 ? conversations.value[0].id : null
)

const currentConv = computed<Conversation | null>(() =>
  conversations.value.find((c) => c.id === currentConvId.value) ?? null
)

const currentMessages = computed<ChatMessage[]>(() => currentConv.value?.messages ?? [])

const latestAiIdx = computed<number>(() => {
  const msgs = currentMessages.value
  for (let i = msgs.length - 1; i >= 0; i--) {
    if (msgs[i].role === 'assistant' && !msgs[i].loading) return i
  }
  return -1
})

const latestDatabaseMessageIndex = computed<number>(() => {
  const msgs = currentMessages.value
  for (let i = msgs.length - 1; i >= 0; i--) {
    if (!msgs[i].loading && msgs[i].pageResult?.list?.length) return i
  }
  return -1
})

const latestDatabaseMessage = computed<ChatMessage | null>(() =>
  latestDatabaseMessageIndex.value >= 0 ? currentMessages.value[latestDatabaseMessageIndex.value] : null
)

const latestDatabaseRows = computed<Array<Record<string, any>>>(() =>
  latestDatabaseMessage.value?.pageResult?.list ?? []
)

const latestDatabaseColumns = computed<Array<{ key: string; label: string }>>(() =>
  latestDatabaseMessage.value?.columns ?? []
)

const chatInput = ref('')
const chatLoading = ref(false)
const messagesRef = ref<HTMLElement>()

// ===== 对话管理 =====
const genId = () => Math.random().toString(36).slice(2) + Date.now().toString(36)

const newConversation = () => {
  disposeCharts()
  const conv: Conversation = {
    id: genId(),
    title: '新对话',
    createdAt: new Date().toISOString(),
    messages: [],
  }
  conversations.value.unshift(conv)
  currentConvId.value = conv.id
  saveConversations(conversations.value)
}

const switchConversation = async (id: string) => {
  if (id === currentConvId.value) return
  disposeCharts()
  currentConvId.value = id
  const conv = conversations.value.find((item) => item.id === id)
  if (conv && conv.messages.length === 0) {
    if (conv.serverHistories?.length) {
      conv.messages = historiesToMessages(conv.serverHistories)
    } else if (conv.serverHistoryId) {
      const history = await QueryHistoryApi.get(conv.serverHistoryId)
      conv.messages = historyToMessages(history)
    }
  }
  nextTick(() => scrollBottom())
}

const deleteConversation = async (id: string) => {
  const conv = conversations.value.find((item) => item.id === id)
  if (conv?.serverHistoryId || conv?.serverHistoryIds?.length) {
    await ElMessageBox.confirm('确认删除这条查询历史吗？', '删除确认', { type: 'warning' })
    const ids = conv.serverHistoryIds?.length ? conv.serverHistoryIds : [conv.serverHistoryId!]
    await Promise.all(ids.map((historyId) => QueryHistoryApi.delete(historyId)))
  }
  if (currentConvId.value === id) {
    disposeCharts()
  }
  conversations.value = conversations.value.filter((c) => c.id !== id)
  if (currentConvId.value === id) {
    currentConvId.value = conversations.value.length > 0 ? conversations.value[0].id : null
  }
  saveConversations(conversations.value)
  ElMessage.success('查询历史已删除')
}

const ensureConversation = (): Conversation => {
  if (!currentConv.value) {
    newConversation()
  }
  return currentConv.value!
}

const updateConvTitle = (conv: Conversation, firstUserMsg: string) => {
  if (conv.title === '新对话' || conv.title === '') {
    conv.title = firstUserMsg.slice(0, 20)
  }
}

// ===== 发送消息 =====
const sendPrompt = (text: string) => {
  chatInput.value = text
  sendMessage()
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

const sendMessage = async () => {
  const msg = chatInput.value.trim()
  if (!msg || chatLoading.value) return

  chatInput.value = ''
  const conv = ensureConversation()

  // 加入用户消息
  const userMsg: ChatMessage = { role: 'user', content: msg, timestamp: Date.now() }
  conv.messages.push(userMsg)
  updateConvTitle(conv, msg)
  await scrollBottom()

  // 加入 loading 占位
  const loadingMsg: ChatMessage = {
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
    loading: true,
  }
  conv.messages.push(loadingMsg)
  saveConversations(conversations.value)

  // 构建历史（不含 loading）
  const history = conv.messages
    .filter((m) => !m.loading)
    .slice(0, -1)
    .slice(-12)
    .map((m) => ({ role: m.role as 'user' | 'assistant', content: m.content }))

  chatLoading.value = true
  try {
    const resp = await JijianQueryApi.agentChat({
      conversationId: conv.conversationId,
      formType: null as any,
      department: null as any,
      timeRange: 'ALL',
      message: msg,
      history,
    })

    conv.conversationId = resp.conversationId
    conv.serverHistoryId = resp.queryHistoryId
    if (resp.queryHistoryId) {
      conv.serverHistoryIds = Array.from(new Set([...(conv.serverHistoryIds || []), resp.queryHistoryId]))
    }

    // 替换 loading 消息
    const idx = conv.messages.indexOf(loadingMsg)
    const aiMsg: ChatMessage = {
      role: 'assistant',
      content: resp.answer ?? '',
      timestamp: Date.now(),
      aiMode: resp.aiMode,
      metrics: resp.metrics ?? [],
      charts: resp.charts ?? [],
      tables: resp.tables ?? [],
      columns: resp.columns ?? [],
      pageResult: resp.pageResult,
      databaseContextMeta: resp.databaseContextMeta,
      sqlTrace: resp.sqlTrace ?? [],
      sqlTraceExpanded: false,
      queryHistoryId: resp.queryHistoryId,
      queryResultJson: JSON.stringify({
        data: resp.data,
        summary: resp.summary,
        columns: resp.columns,
        pageResult: resp.pageResult,
        metrics: resp.metrics,
        charts: resp.charts,
        tables: resp.tables,
        sqlTrace: resp.sqlTrace
      }),
    }
    if (idx >= 0) {
      conv.messages.splice(idx, 1, aiMsg)
    } else {
      conv.messages.push(aiMsg)
    }
  } catch (e: any) {
    const idx = conv.messages.indexOf(loadingMsg)
    const errMsg: ChatMessage = {
      role: 'assistant',
      content: buildErrorMessage(e),
      timestamp: Date.now(),
    }
    if (idx >= 0) {
      conv.messages.splice(idx, 1, errMsg)
    } else {
      conv.messages.push(errMsg)
    }
  } finally {
    chatLoading.value = false
    saveConversations(conversations.value)
    await scrollBottom()
  }
}

const parseJson = (value?: string) => {
  if (!value) return {}
  try { return JSON.parse(value) } catch { return {} }
}

const historyToMessages = (history: QueryHistoryVO): ChatMessage[] => {
  const result = parseJson(history.queryResultJson)
  return [
    {
      role: 'user',
      content: history.question,
      timestamp: new Date(history.createTime).getTime()
    },
    {
      role: 'assistant',
      content: history.answer || history.errorMessage || '',
      timestamp: new Date(history.createTime).getTime(),
      aiMode: history.modelName,
      metrics: result.metrics || [],
      charts: result.charts || [],
      tables: result.tables || [],
      columns: result.columns || [],
      pageResult: result.pageResult,
      databaseContextMeta: parseJson(history.databaseContextMetaJson),
      sqlTrace: result.sqlTrace || [],
      sqlTraceExpanded: false,
      queryHistoryId: history.id,
      queryResultJson: history.queryResultJson
    }
  ]
}

const historiesToMessages = (histories: QueryHistoryVO[]): ChatMessage[] =>
  [...histories]
    .sort((a, b) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime())
    .flatMap(historyToMessages)

const historyConversationId = (history: QueryHistoryVO) => {
  const match = (history.remark || '').match(/conversationId=([^;\s]+)/)
  return match?.[1] || `history-${history.id}`
}

const loadRemoteHistory = async () => {
  try {
    const page = await QueryHistoryApi.getPage({ pageNo: 1, pageSize: 30 })
    if (!page.list.length) return
    const grouped = new Map<string, QueryHistoryVO[]>()
    page.list.forEach((item) => {
      const key = historyConversationId(item)
      grouped.set(key, [...(grouped.get(key) || []), item])
    })
    conversations.value = Array.from(grouped.entries()).map(([conversationId, items]) => {
      const sorted = [...items].sort((a, b) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime())
      const latest = sorted[sorted.length - 1]
      return {
        id: `history-${conversationId}`,
        title: sorted[0].question.slice(0, 20),
        createdAt: latest.createTime,
        messages: [],
        conversationId,
        serverHistoryId: latest.id,
        serverHistoryIds: sorted.map((item) => item.id),
        serverHistories: sorted
      }
    }).sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    currentConvId.value = conversations.value[0]?.id || null
    if (currentConvId.value) {
      await switchConversation(currentConvId.value)
      const first = conversations.value[0]
      if (first && first.messages.length === 0) {
        first.messages = first.serverHistories?.length
          ? historiesToMessages(first.serverHistories)
          : (first.serverHistoryId ? historyToMessages(await QueryHistoryApi.get(first.serverHistoryId)) : [])
      }
    }
  } catch {
    // 后端历史暂不可用时保留 localStorage 兼容数据
  }
}

const handleDispose = async (message: ChatMessage, index: number) => {
  const question = [...currentMessages.value.slice(0, index)]
    .reverse()
    .find((item) => item.role === 'user')?.content
  const { value } = await ElMessageBox.prompt('请输入处置意见', '保存处置记录', {
    confirmButtonText: '保存',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputValidator: (input) => Boolean(input?.trim()) || '处置意见不能为空'
  })
  await DisposalRecordApi.create({
    queryHistoryId: message.queryHistoryId,
    queryQuestion: question,
    queryAnswer: message.content,
    queryResultJson: message.queryResultJson,
    disposalOpinion: value.trim(),
    sourceModule: 'smart_query'
  })
  ElMessage.success('处置记录已保存')
}

const getContractOriginalUrl = (row: Record<string, any>): string => {
  if (!isContractRow(row)) return ''
  return String(row.originalFileUrl || row.original_file_url || row.fileUrl || row.file_url || '').trim()
}

const hasContractOriginalRows = (rows?: Array<Record<string, any>>): boolean =>
  (rows || []).some((row) => Boolean(getContractOriginalUrl(row)))

const openContractOriginal = (row: Record<string, any>) => {
  const url = getContractOriginalUrl(row)
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

const isContractRow = (row: Record<string, any>): boolean => {
  const keys = Object.keys(row || {})
  return keys.some((key) => /contract|合同/.test(key))
    || row.businessTable === 'jijian_lease_contract'
    || row.business_table === 'jijian_lease_contract'
}

const buildErrorMessage = (e: any): string => {
  const status = e?.response?.status ?? e?.status
  const msg = e?.response?.data?.msg ?? e?.message
  if (status === 401) return '查询失败：请重新登录后重试。'
  if (msg && /Network Error|timeout|Failed to fetch/i.test(String(msg)))
    return '查询失败：服务不可用，请确认后端已启动。'
  return '查询失败：' + (msg ?? '服务异常，请稍后重试')
}

// ===== 滚动到底部 =====
const scrollBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// ===== ECharts =====
const chartRefs = ref<(HTMLElement | null)[]>([])
const chartInstances: echarts.ECharts[] = []

const setChartRef = (el: unknown, idx: number) => {
  chartRefs.value[idx] = el as HTMLElement | null
}

const disposeCharts = () => {
  chartInstances.forEach((inst) => {
    try {
      inst.dispose()
    } catch (_) {}
  })
  chartInstances.length = 0
  chartRefs.value = []
}

const renderCharts = async (charts: JijianChartVO[]) => {
  await nextTick()
  disposeCharts()
  charts.forEach((chart, idx) => {
    const el = chartRefs.value[idx]
    if (!el) return
    const inst = echarts.init(el)
    chartInstances.push(inst)
    inst.setOption(buildEchartsOption(chart))
  })
}

const buildEchartsOption = (chart: JijianChartVO): echarts.EChartsOption => {
  if (chart.type === 'pie') {
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', right: 10, top: 'center' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        data: (chart.data ?? []).map((d) => ({ name: d.name, value: d.value })),
      }],
    }
  }
  if (chart.type === 'bar') {
    return {
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0 },
      grid: { left: '3%', right: '4%', bottom: '14%', containLabel: true },
      xAxis: { type: 'category', data: chart.xAxis ?? [], axisLabel: { rotate: 30 } },
      yAxis: { type: 'value' },
      series: (chart.series ?? []).map((s) => ({ name: s.name, type: 'bar', data: s.data })),
    }
  }
  if (chart.type === 'line') {
    return {
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0 },
      grid: { left: '3%', right: '4%', bottom: '14%', containLabel: true },
      xAxis: { type: 'category', data: chart.xAxis ?? [] },
      yAxis: { type: 'value' },
      series: (chart.series ?? []).map((s) => ({ name: s.name, type: 'line', data: s.data, smooth: true })),
    }
  }
  return {}
}

// 当 latestAiIdx 变化且有图表时，重新渲染
watch(latestAiIdx, async (idx) => {
  if (idx < 0) return
  const msg = currentMessages.value[idx]
  if (msg?.charts?.length) {
    await renderCharts(msg.charts)
  } else {
    disposeCharts()
  }
}, { flush: 'post' })

// ===== 工具函数 =====
const aiModeText = (mode?: string) => {
  switch (mode) {
    case 'DEEPSEEK_SQL_AGENT': return 'DeepSeek SQL Agent（真实调用）'
    case 'DEEPSEEK_KEY_MISSING': return 'DeepSeek API Key 未配置'
    case 'DEEPSEEK_DATA_ANALYSIS': return 'DeepSeek 数据分析（真实调用）'
    case 'DEEPSEEK_SUMMARY': return 'DeepSeek 摘要（真实调用）'
    case 'DEEPSEEK_INTENT': return 'DeepSeek 意图解析（真实调用）'
    case 'LOCAL_FALLBACK': return '当前使用规则查询，未调用 DeepSeek'
    default: return mode || '当前使用规则查询，未调用 DeepSeek'
  }
}

const modeTagType = (mode?: string): 'success' | 'warning' | 'info' => {
  if (mode === 'DEEPSEEK_SQL_AGENT' || mode === 'DEEPSEEK_DATA_ANALYSIS' || mode === 'DEEPSEEK_SUMMARY') return 'success'
  if (mode === 'DEEPSEEK_INTENT') return 'warning'
  return 'info'
}

onMounted(loadRemoteHistory)
onUnmounted(() => disposeCharts())
</script>

<style scoped>
/* ===== 整体布局 ===== */
.aigc-layout {
  display: flex;
  height: calc(100vh - 84px);
  background: var(--el-bg-color-page);
  overflow: hidden;
}

/* ===== 左侧历史栏 ===== */
.aigc-sidebar {
  width: 240px;
  min-width: 240px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  overflow: hidden;
}

.sidebar-top {
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.new-chat-btn {
  width: 100%;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.conv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 6px;
  margin: 2px 6px;
  transition: background 0.15s;
}

.conv-item:hover {
  background: var(--el-fill-color-light);
}

.conv-item--active {
  background: var(--el-color-primary-light-9);
}

.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.conv-del {
  opacity: 0;
  margin-left: 4px;
  flex-shrink: 0;
}

.conv-item:hover .conv-del {
  opacity: 1;
}

.conv-empty {
  padding: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

/* ===== 主聊天区 ===== */
.aigc-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.prompt-bar {
  flex-shrink: 0;
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

/* ===== 欢迎区 ===== */
.chat-welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
  text-align: center;
  overflow-y: auto;
}

.welcome-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.welcome-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 8px;
}

.welcome-sub {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin: 0 0 24px;
}

.prompt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
  max-width: 700px;
  width: 100%;
}

.prompt-chip {
  padding: 10px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
  color: var(--el-text-color-regular);
  background: var(--el-bg-color);
  transition: all 0.15s;
  line-height: 1.5;
}

.prompt-chip:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

/* ===== 消息流 ===== */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.database-panel {
  flex-shrink: 0;
  max-height: 300px;
  padding: 10px 16px 12px;
  border-top: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.database-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.database-panel__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.database-panel__meta {
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.msg-row {
  display: flex;
  gap: 10px;
}

.msg-row--user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
}

.msg-row--user .msg-avatar {
  background: var(--el-color-success-light-8);
  color: var(--el-color-success);
}

.msg-body {
  max-width: 80%;
  min-width: 60px;
}

.msg-bubble {
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 15px;
  line-height: 1.8;
  word-break: break-word;
}

.msg-bubble--user {
  background: var(--el-color-primary);
  color: #fff;
  border-radius: 10px 2px 10px 10px;
}

.msg-bubble--ai {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 2px 10px 10px 10px;
}

/* loading 动画 */
.msg-loading {
  display: flex;
  gap: 5px;
  padding: 4px 0;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
  animation: blink 1.2s infinite;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes blink {
  0%, 80%, 100% { opacity: 0.2; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

.msg-answer {
  white-space: pre-wrap;
  margin-bottom: 8px;
}

.msg-mode-tag {
  margin-bottom: 10px;
}

/* 指标卡片 */
.result-metrics {
  margin-top: 12px;
  margin-bottom: 4px;
}

.mb-8px {
  margin-bottom: 8px;
}

.metric-card {
  text-align: center;
  padding: 12px 8px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.metric-val {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1.2;
}

.metric-unit {
  font-size: 13px;
  font-weight: 400;
  margin-left: 2px;
}

.metric-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

/* 图表 */
.result-charts {
  margin-top: 12px;
}

.mb-12px {
  margin-bottom: 12px;
}

.chart-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 12px;
  background: var(--el-fill-color-blank);
}

.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.chart-dom {
  height: 260px;
}

.chart-placeholder {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-style: italic;
}

/* 数据表格 */
.result-table {
  margin-top: 12px;
}

.raw-data-table {
  border-top: 1px dashed var(--el-border-color-light);
  padding-top: 10px;
}

.table-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 6px;
}

.table-title--actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

/* 数据来源 */
.db-meta {
  margin-top: 8px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.text-warning {
  color: var(--el-color-warning);
}

/* ===== SQL 查询痕迹 ===== */
.sql-trace-wrap {
  margin-top: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  font-size: 12px;
  overflow: hidden;
}

.sql-trace-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--el-fill-color-light);
  cursor: pointer;
  color: var(--el-text-color-secondary);
  user-select: none;
}

.sql-trace-header:hover {
  background: var(--el-fill-color);
}

.sql-trace-toggle {
  font-size: 10px;
}

.sql-trace-body {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sql-trace-item {
  border-left: 3px solid var(--el-color-primary-light-5);
  padding-left: 8px;
}

.sql-trace-purpose {
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.sql-trace-idx {
  display: inline-block;
  background: var(--el-color-primary);
  color: white;
  border-radius: 3px;
  padding: 0 4px;
  margin-right: 4px;
  font-size: 10px;
}

.sql-trace-sql {
  background: var(--el-fill-color);
  border-radius: 4px;
  padding: 6px 8px;
  font-family: 'Courier New', monospace;
  font-size: 11px;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.sql-trace-error {
  color: var(--el-color-danger);
  font-size: 11px;
  margin-top: 4px;
}

.ml-4px {
  margin-left: 4px;
}

/* ===== 输入区 ===== */
.input-area {
  border-top: 1px solid var(--el-border-color-light);
  padding: 12px 16px;
  background: var(--el-bg-color);
}

.input-wrap {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-wrap :deep(.el-textarea__inner) {
  resize: none;
  line-height: 1.6;
}

.send-btn {
  flex-shrink: 0;
  height: 36px;
}

.input-hint {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-top: 6px;
  text-align: center;
}
</style>
