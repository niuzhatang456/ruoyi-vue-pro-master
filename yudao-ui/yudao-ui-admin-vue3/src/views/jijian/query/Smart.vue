<template>
  <PageShell title="智能查询" description="输入自然语言，AI 分析纪检数据并返回图表与结论。">

    <!-- 聊天消息区 -->
    <el-card shadow="never" class="mb-16px">
      <div ref="chatBoxRef" class="chat-box">
        <div v-if="chatMessages.length === 0" class="chat-empty">
          <el-text type="info">
            直接输入问题，例如：<br>
            "分析这个月疗休养请假情况" &nbsp;·&nbsp; "查一下本月缺勤人员" &nbsp;·&nbsp; "统计各部门出勤率"
          </el-text>
        </div>
        <div
          v-for="(msg, idx) in chatMessages"
          :key="idx"
          :class="['chat-msg', msg.role === 'user' ? 'chat-msg--user' : 'chat-msg--assistant']"
        >
          <div class="chat-bubble">
            <div class="chat-content">{{ msg.content }}</div>
            <div v-if="msg.role === 'assistant' && msg.aiMode" class="chat-hint">
              <el-tag :type="modeTagType(msg.aiMode)" size="small">{{ aiModeText(msg.aiMode) }}</el-tag>
              <span v-if="msg.formType" class="ml-8px text-gray">{{ msg.formType }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input-area">
        <el-input
          v-model="chatInput"
          :disabled="chatLoading"
          placeholder="输入问题，按回车发送（无需选择表单类型）"
          @keyup.enter="handleChat"
        />
        <el-button type="primary" :loading="chatLoading" :disabled="!chatInput.trim()" @click="handleChat">
          发送
        </el-button>
      </div>
    </el-card>

    <!-- 分析结果区（指标、图表、表格） -->
    <template v-if="chatAnalysisData">
      <el-divider>智能分析结果</el-divider>

      <!-- 指标卡片 -->
      <el-row v-if="chatMetrics.length" :gutter="16" class="mb-16px">
        <el-col v-for="m in chatMetrics" :key="m.key" :xs="12" :sm="8" :md="6" class="mb-8px">
          <el-card shadow="never" class="stat-card">
            <div class="stat-num">{{ m.value }}<span v-if="m.unit" class="stat-unit">{{ m.unit }}</span></div>
            <div class="stat-label">{{ m.label }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 图表区 -->
      <el-row v-if="chatCharts.length" :gutter="16" class="mb-16px">
        <el-col v-for="(chart, idx) in chatCharts" :key="idx" :xs="24" :md="12" class="mb-16px">
          <el-card shadow="never">
            <template #header>{{ chart.title }}</template>
            <div :ref="(el) => setChartRef(el, idx)" style="height: 280px"></div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 数据表格 -->
      <template v-for="(tbl, idx) in chatTables" :key="idx">
        <el-card shadow="never" class="mb-16px">
          <template #header>{{ tbl.title }}</template>
          <el-table :data="tbl.rows" stripe size="small" max-height="320">
            <el-table-column
              v-for="col in tbl.columns"
              :key="col.key"
              :prop="col.key"
              :label="col.label"
              min-width="100"
              show-overflow-tooltip
            />
          </el-table>
        </el-card>
      </template>
    </template>

    <!-- 普通 summary（非图表化时） -->
    <template v-if="chatSummaryRaw && !chatAnalysisData">
      <el-divider>统计结果</el-divider>
      <SummaryPanel :summary="chatSummaryRaw" />
    </template>
  </PageShell>
</template>

<script setup lang="ts">
import PageShell from '../components/PageShell.vue'
import { computed, defineComponent, nextTick, onMounted, onUnmounted, ref, watch, type PropType } from 'vue'
import * as echarts from 'echarts'
import {
  JijianQueryApi,
  type JijianMetricVO,
  type JijianChartVO,
  type JijianAnalysisTableVO
} from '@/api/jijian/query'
import { ElMessage } from 'element-plus'

// ===== SummaryPanel =====
const SummaryPanel = defineComponent({
  name: 'SummaryPanel',
  props: { summary: { type: Object as PropType<Record<string, unknown>>, required: true } },
  setup(props) {
    const primitiveEntries = computed(() =>
      Object.entries(props.summary)
        .filter(([, v]) => !Array.isArray(v) && v !== null && typeof v !== 'object')
        .map(([key, value]) => ({ key, label: key, value: String(value) }))
    )
    const arrayEntries = computed(() =>
      Object.entries(props.summary)
        .filter(([, v]) => Array.isArray(v))
        .map(([key, value]) => ({ key, label: key, rows: value as Record<string, unknown>[] }))
    )
    const rowColumns = (rows: Record<string, unknown>[]) => {
      const first = rows.find((r) => r && typeof r === 'object')
      return first ? Object.keys(first).slice(0, 8) : []
    }
    return { primitiveEntries, arrayEntries, rowColumns }
  },
  template: `
    <div>
      <el-row v-if="primitiveEntries.length" :gutter="16" class="mb-16px">
        <el-col v-for="item in primitiveEntries" :key="item.key" :xs="12" :sm="8" :md="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-num">{{ item.value }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </el-card>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col v-for="entry in arrayEntries" :key="entry.key" :xs="24" :md="12" class="mb-16px">
          <el-card shadow="never">
            <template #header>{{ entry.label }}</template>
            <el-table :data="entry.rows" size="small" stripe max-height="260">
              <el-table-column v-for="col in rowColumns(entry.rows)" :key="col" :prop="col" :label="col" min-width="100" show-overflow-tooltip />
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </div>
  `
})

// ===== 状态 =====
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  aiMode?: string
  formType?: string
}

const chatMessages = ref<ChatMessage[]>([])
const chatInput = ref('')
const chatLoading = ref(false)
const chatBoxRef = ref<HTMLElement>()
const conversationId = ref<string | undefined>(undefined)

const chatAnalysisData = ref(false)
const chatMetrics = ref<JijianMetricVO[]>([])
const chatCharts = ref<JijianChartVO[]>([])
const chatTables = ref<JijianAnalysisTableVO[]>([])
const chatSummaryRaw = ref<Record<string, unknown> | null>(null)

// ===== ECharts =====
const chartRefs = ref<(HTMLElement | null)[]>([])
const chartInstances: echarts.ECharts[] = []

const setChartRef = (el: unknown, idx: number) => {
  chartRefs.value[idx] = el as HTMLElement | null
}

const disposeCharts = () => {
  chartInstances.forEach((inst) => { try { inst.dispose() } catch (_) {} })
  chartInstances.length = 0
}

const renderCharts = async () => {
  await nextTick()
  disposeCharts()
  chartRefs.value.forEach((el, idx) => {
    if (!el) return
    const chart = chatCharts.value[idx]
    if (!chart) return
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
      series: [{ type: 'pie', radius: ['40%', '70%'], center: ['40%', '50%'],
        data: (chart.data ?? []).map((d) => ({ name: d.name, value: d.value })) }]
    }
  }
  if (chart.type === 'bar') {
    return {
      tooltip: { trigger: 'axis' }, legend: { bottom: 0 },
      grid: { left: '3%', right: '4%', bottom: '14%', containLabel: true },
      xAxis: { type: 'category', data: chart.xAxis ?? [], axisLabel: { rotate: 30 } },
      yAxis: { type: 'value' },
      series: (chart.series ?? []).map((s) => ({ name: s.name, type: 'bar', data: s.data }))
    }
  }
  if (chart.type === 'line') {
    return {
      tooltip: { trigger: 'axis' }, legend: { bottom: 0 },
      grid: { left: '3%', right: '4%', bottom: '14%', containLabel: true },
      xAxis: { type: 'category', data: chart.xAxis ?? [] },
      yAxis: { type: 'value' },
      series: (chart.series ?? []).map((s) => ({ name: s.name, type: 'line', data: s.data, smooth: true }))
    }
  }
  return {}
}

watch(chatCharts, () => { if (chatCharts.value.length) renderCharts() }, { flush: 'post' })

const scrollChatBottom = async () => {
  await nextTick()
  if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
}

// ===== 发送 chat =====
const handleChat = async () => {
  const msg = chatInput.value.trim()
  if (!msg) return
  chatMessages.value.push({ role: 'user', content: msg })
  chatInput.value = ''
  await scrollChatBottom()

  const history = chatMessages.value
    .slice(0, -1)
    .slice(-12)
    .map((m) => ({ role: m.role, content: m.content }))

  chatLoading.value = true
  try {
    const resp = await JijianQueryApi.chat({
      conversationId: conversationId.value,
      formType: null as any,      // 不传 formType，由后端/AI 自动判断
      department: null as any,
      timeRange: 'ALL',
      message: msg,
      history
    })
    conversationId.value = resp.conversationId
    chatMessages.value.push({
      role: 'assistant',
      content: resp.answer,
      aiMode: resp.aiMode,
      formType: resp.formType ?? resp.queryIntent?.formType as string
    })

    if (resp.aiMode === 'DEEPSEEK_DATA_ANALYSIS' && resp.metrics?.length) {
      chatAnalysisData.value = true
      chatMetrics.value = resp.metrics ?? []
      chatCharts.value = resp.charts ?? []
      chatTables.value = resp.tables ?? []
      chatSummaryRaw.value = null
    } else {
      chatAnalysisData.value = false
      const s = (resp.summary ?? resp.data ?? {}) as Record<string, unknown>
      chatSummaryRaw.value = Object.keys(s).length > 0 ? s : null
    }
  } catch (e: any) {
    chatMessages.value.push({ role: 'assistant', content: buildChatErrorMessage(e) })
  } finally {
    chatLoading.value = false
    await scrollChatBottom()
  }
}

const aiModeText = (mode?: string) => {
  switch (mode) {
    case 'DEEPSEEK_DATA_ANALYSIS': return 'DeepSeek 数据分析'
    case 'DEEPSEEK_SUMMARY': return 'DeepSeek 摘要'
    case 'DEEPSEEK_INTENT': return 'DeepSeek 解析'
    case 'LOCAL_FALLBACK': return '本地规则'
    default: return mode || '本地规则'
  }
}

const modeTagType = (mode?: string): 'success' | 'info' | 'warning' => {
  if (mode === 'DEEPSEEK_DATA_ANALYSIS' || mode === 'DEEPSEEK_SUMMARY') return 'success'
  if (mode === 'DEEPSEEK_INTENT') return 'warning'
  return 'info'
}

const buildChatErrorMessage = (e: any) => {
  const msg = e?.response?.data?.msg ?? e?.message
  if (/401/.test(String(e?.response?.status ?? e?.status))) return '查询失败：请重新登录。'
  if (msg && /Network Error|timeout|Failed to fetch/i.test(String(msg))) return '查询失败：服务不可用，请确认后端已启动。'
  return '查询失败：' + (msg ?? '服务异常，请稍后重试')
}

onMounted(() => {})
onUnmounted(() => { disposeCharts() })
</script>

<style scoped>
.chat-box { height: 360px; overflow-y: auto; padding: 8px 0; display: flex; flex-direction: column; gap: 12px; }
.chat-empty { display: flex; align-items: center; justify-content: center; height: 100%; text-align: center; }
.chat-msg { display: flex; }
.chat-msg--user { justify-content: flex-end; }
.chat-msg--assistant { justify-content: flex-start; }
.chat-bubble { max-width: 85%; padding: 8px 12px; border-radius: 8px; background: var(--el-fill-color-light); font-size: 14px; line-height: 1.6; }
.chat-msg--user .chat-bubble { background: var(--el-color-primary-light-9); }
.chat-content { white-space: pre-wrap; word-break: break-word; }
.chat-hint { margin-top: 6px; display: flex; align-items: center; gap: 4px; }
.text-gray { font-size: 11px; color: var(--el-text-color-placeholder); }
.chat-input-area { display: flex; gap: 8px; margin-top: 12px; }
.chat-input-area .el-input { flex: 1; }
.stat-card { text-align: center; padding: 8px 0; }
.stat-num { font-size: 24px; font-weight: 700; color: var(--el-color-primary); line-height: 1.2; }
.stat-unit { font-size: 14px; font-weight: 400; margin-left: 2px; }
.stat-label { font-size: 13px; color: var(--el-text-color-secondary); margin-top: 4px; }
.mb-16px { margin-bottom: 16px; }
.mb-8px { margin-bottom: 8px; }
.ml-8px { margin-left: 8px; }
</style>
