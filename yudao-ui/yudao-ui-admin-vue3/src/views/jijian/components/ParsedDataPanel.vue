<template>
  <div v-if="parsedData" class="parsed-panel">
    <!-- ── 基本信息 ── -->
    <el-descriptions :column="3" border size="small">
      <el-descriptions-item label="识别类型">
        <el-tag v-if="parsedData.formType" type="primary">{{ parsedData.formType }}</el-tag>
        <el-tag v-else type="warning">未识别</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="解析状态">
        <el-tag :type="statusTagType">{{ statusText }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="文件名">{{ parsedData.fileName }}</el-descriptions-item>
      <el-descriptions-item label="总行数">{{ totalRowCount > 0 ? totalRowCount : '-' }}</el-descriptions-item>
      <el-descriptions-item label="表头列数">{{ tableHeaders.length > 0 ? tableHeaders.length : '-' }}</el-descriptions-item>
      <el-descriptions-item label="置信度">{{ parsedData.confidence ?? '-' }}</el-descriptions-item>
      <!-- 确认后追溯信息 -->
      <template v-if="isConfirmed && (parsedData.businessTable || parsedData.businessIds)">
        <el-descriptions-item label="写入业务表">
          <el-tag type="success" size="small">{{ parsedData.businessTable || confirmedFormType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="业务记录ID">
          <span class="mono-text">{{ displayBusinessIds }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="确认时间">{{ parsedData.confirmTime || '-' }}</el-descriptions-item>
      </template>
      <el-descriptions-item v-if="parsedData.errorMsg" label="错误信息" :span="3">
        <el-text type="danger">{{ parsedData.errorMsg }}</el-text>
      </el-descriptions-item>
    </el-descriptions>

    <!-- ── 解析失败 ── -->
    <el-alert
      v-if="parsedData.status === 'failed'"
      class="mt-12px"
      type="error"
      :closable="false"
      show-icon
      title="解析/识别失败"
      :description="parsedData.errorMsg || '文件解析失败，请检查格式后重新上传'"
    />

    <!-- ── 确认写入区域 ── -->
    <div v-else class="confirm-area mt-12px">
      <!-- 已确认 -->
      <el-alert v-if="isConfirmed" type="success" :closable="false" show-icon>
        <template #title>
          <span>
            已确认写入「{{ confirmedFormType }}」{{ confirmedBusinessTable ? '→ ' + confirmedBusinessTable : '' }}，共 {{ confirmedCount }} 条记录
            <template v-if="confirmedIds.length > 0">，记录 ID：{{ confirmedIds.join('、') }}</template>
            <el-tag v-if="wasIdempotent" type="info" size="small" class="ml-8px">重复操作已幂等</el-tag>
          </span>
        </template>
      </el-alert>

      <!-- 可操作 -->
      <div v-else-if="canConfirm" class="action-row">
        <el-button type="success" :loading="saving" plain @click="handleSaveCorrection">保存校正</el-button>
        <el-button type="primary" :loading="confirming" @click="handleConfirm">确认写入正式数据</el-button>
        <el-text v-if="correctionSaved" type="success" size="small">✓ 校正已保存</el-text>
        <el-text v-if="hasEdited && !correctionSaved" type="warning" size="small">有未保存的修改</el-text>
      </div>
    </div>

    <!-- ── 超行数警告 ── -->
    <el-alert
      v-if="showPreviewLimitBanner"
      class="mt-8px"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #title>
        当前仅预览前 {{ PREVIEW_LIMIT }} 行（共 {{ totalRowCount }} 行）。
        <strong>确认写入仍以完整数据为准。</strong>
      </template>
    </el-alert>

    <!-- ── 数据展示 / 编辑 ── -->
    <el-tabs class="mt-12px" v-model="activeTab">
      <!-- 多行表格 -->
      <el-tab-pane
        v-if="tableHeaders.length > 0"
        :label="`数据表格（${visibleRows.length} 行${totalRowCount > PREVIEW_LIMIT ? '，预览' : ''}）`"
        name="table"
      >
        <el-text v-if="canConfirm" type="info" size="small" class="block mb-8px">
          字段值可直接编辑，修改后点击「保存校正」再确认写入
        </el-text>
        <div class="table-wrapper">
          <el-table :data="visibleRows" border size="small" class="correction-table">
            <el-table-column type="index" label="#" width="50" fixed />
            <el-table-column
              v-for="header in tableHeaders"
              :key="header"
              :prop="header"
              :label="header"
              min-width="130"
            >
              <template #default="{ row }">
                <el-input v-if="canConfirm" v-model="row[header]" size="small" @change="markEdited" />
                <span v-else>{{ row[header] ?? '' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 键值对 -->
      <el-tab-pane v-else-if="kvFields.length > 0" label="字段详情" name="kv">
        <el-text v-if="canConfirm" type="info" size="small" class="block mb-8px">
          字段值可直接编辑，修改后点击「保存校正」再确认写入
        </el-text>
        <el-form label-width="160px" size="small">
          <el-form-item v-for="kv in kvFields" :key="kv.key" :label="kv.key">
            <el-input v-if="canConfirm" v-model="kv.value" @change="markEdited" />
            <span v-else>{{ kv.value }}</span>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 原始文本 -->
      <el-tab-pane label="原始文本" name="raw">
        <el-input :model-value="parsedData.rawText || '（无原始文本）'" type="textarea" :rows="6" readonly />
      </el-tab-pane>

      <!-- JSON 视图 -->
      <el-tab-pane label="结构化 JSON" name="json">
        <pre class="json-view">{{ activeJsonPretty }}</pre>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmWrite, saveCorrection } from '@/api/jijian/import'
import type { ParsedData } from '../types'

const PREVIEW_LIMIT = 100

const props = defineProps<{ parsedData?: ParsedData | null }>()

const emit = defineEmits<{
  (event: 'confirmed', result: { formType: string; businessTable?: string; confirmedIds: number[]; confirmedCount: number }): void
}>()

// ── 状态 ──────────────────────────────────────────────────────────────────
const confirming    = ref(false)
const saving        = ref(false)
const correctionSaved = ref(false)
const hasEdited     = ref(false)
const activeTab     = ref('table')
const localResult   = ref<{ formType: string; businessTable?: string; confirmedIds: number[]; confirmedCount: number; idempotent: boolean } | null>(null)

// ── 表格数据 ──────────────────────────────────────────────────────────────
const tableHeaders = ref<string[]>([])
const tableRows    = ref<Record<string, string>[]>([])
const allRows      = ref<Record<string, string>[]>([])
const kvFields     = ref<{ key: string; value: string }[]>([])
const totalRowCount = ref(0)

function normalizeHeaders(headers: unknown[]): { name: string; index: number }[] {
  const seen = new Map<string, number>()
  return headers
    .map((header, index) => ({ name: header == null ? '' : String(header).trim(), index }))
    .filter(({ name }) => name.length > 0)
    .map(({ name, index }) => {
      const count = (seen.get(name) || 0) + 1
      seen.set(name, count)
      return { name: count === 1 ? name : `${name}_${count}`, index }
    })
}

function initEditableData() {
  tableHeaders.value = []
  tableRows.value    = []
  allRows.value      = []
  kvFields.value     = []
  totalRowCount.value = 0
  hasEdited.value    = false
  correctionSaved.value = false
  localResult.value  = null
  activeTab.value    = 'table'

  const source = props.parsedData?.correctedJson || props.parsedData?.parsedJson
  if (!source) return
  try {
    const obj = JSON.parse(source)
    if (Array.isArray(obj.rows) && obj.rows.length > 0) {
      totalRowCount.value = typeof obj.totalRows === 'number' ? obj.totalRows : obj.rows.length
      const firstRow = obj.rows[0]
      const sourceHeaders = Array.isArray(obj.headers)
        ? obj.headers
        : (Array.isArray(firstRow) ? firstRow.map((_: unknown, index: number) => `列${index + 1}`) : Object.keys(firstRow))
      const headerDefs = normalizeHeaders(sourceHeaders)
      tableHeaders.value = headerDefs.map(({ name }) => name)
      allRows.value = obj.rows.map((row: unknown) => {
        if (Array.isArray(row)) {
          return Object.fromEntries(headerDefs.map(({ name, index }) => [name, row[index] == null ? '' : String(row[index])]))
        }
        const record = row as Record<string, unknown>
        return Object.fromEntries(Object.entries(record).map(([key, value]) => [key, value == null ? '' : String(value)]))
      })
      tableRows.value = allRows.value.slice(0, PREVIEW_LIMIT)
    } else if (obj.textPreview) {
      String(obj.textPreview).split('\n').forEach((line) => {
        const idx = line.includes('：') ? line.indexOf('：') : line.indexOf(':')
        if (idx > 0 && idx < line.length - 1) {
          const k = line.substring(0, idx).trim()
          const v = line.substring(idx + 1).trim()
          if (k && v) kvFields.value.push({ key: k, value: v })
        }
      })
      activeTab.value = 'kv'
    }
  } catch {
    activeTab.value = 'raw'
  }
}

watch(() => props.parsedData, initEditableData, { immediate: true, deep: false })

// ── 计算属性 ──────────────────────────────────────────────────────────────
const visibleRows = computed(() => tableRows.value)
const showPreviewLimitBanner = computed(() =>
  totalRowCount.value > PREVIEW_LIMIT && tableHeaders.value.length > 0
)

const isConfirmed = computed(() =>
  props.parsedData?.confirmStatus === 'confirmed' ||
  props.parsedData?.status === 'confirmed' ||   // 兼容旧记录：老代码把确认态写在 status 字段
  localResult.value !== null
)
const canConfirm = computed(() =>
  props.parsedData?.status === 'success' && !isConfirmed.value
)
const confirmedFormType = computed(() =>
  localResult.value?.formType || props.parsedData?.formType || ''
)
const confirmedBusinessTable = computed(() =>
  localResult.value?.businessTable || props.parsedData?.businessTable || ''
)
const confirmedIds = computed(() => localResult.value?.confirmedIds ?? [])
const confirmedCount = computed(() =>
  localResult.value?.confirmedCount ?? (props.parsedData?.confirmedPropertyId ? 1 : 0)
)
const wasIdempotent = computed(() => localResult.value?.idempotent ?? false)

const displayBusinessIds = computed(() => {
  const raw = props.parsedData?.businessIds
  if (!raw) return '-'
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.join(', ') : raw
  } catch {
    return raw
  }
})

const statusTagType = computed(() => {
  if (props.parsedData?.confirmStatus === 'confirmed') return 'success'
  switch (props.parsedData?.status) {
    case 'success':    return 'success'
    case 'failed':     return 'danger'
    case 'processing': return 'warning'
    default: return 'info'
  }
})
const statusText = computed(() => {
  if (props.parsedData?.confirmStatus === 'confirmed') return '已确认'
  switch (props.parsedData?.status) {
    case 'success':    return '解析成功'
    case 'failed':     return '解析失败'
    case 'processing': return '处理中'
    default: return '待处理'
  }
})
const activeJsonPretty = computed(() => {
  const raw = props.parsedData?.correctedJson || props.parsedData?.parsedJson || '{}'
  try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
})

// ── 操作 ──────────────────────────────────────────────────────────────────
function markEdited() { hasEdited.value = true; correctionSaved.value = false }

async function handleSaveCorrection() {
  if (!props.parsedData) return
  let correctedJson: string
  try {
    const base = JSON.parse(props.parsedData.correctedJson || props.parsedData.parsedJson || '{}')
    if (tableRows.value.length > 0) {
      base.headers = tableHeaders.value
      base.rows = [...tableRows.value, ...allRows.value.slice(tableRows.value.length)]
    } else if (kvFields.value.length > 0) {
      base.textPreview = kvFields.value.map(kv => `${kv.key}：${kv.value}`).join('\n')
    }
    correctedJson = JSON.stringify(base)
  } catch {
    ElMessage.error('序列化校正数据失败，请刷新页面后重试')
    return
  }
  saving.value = true
  try {
    await saveCorrection(props.parsedData.id, correctedJson)
    correctionSaved.value = true
    hasEdited.value = false
    ElMessage.success('校正已保存')
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败，请重试')
  } finally {
    saving.value = false
  }
}

async function handleConfirm() {
  if (!props.parsedData) return
  if (hasEdited.value) {
    ElMessage.warning('您有未保存的修改，请先点击「保存校正」再确认写入')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将当前解析结果写入正式「${props.parsedData.formType || '业务'}」数据库？`,
      '确认写入',
      { confirmButtonText: '确认写入', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  confirming.value = true
  try {
    const result = await confirmWrite(props.parsedData.id)
    localResult.value = {
      formType: result.formType,
      businessTable: result.businessTable,
      confirmedIds: result.confirmedIds,
      confirmedCount: result.confirmedCount,
      idempotent: result.idempotent
    }
    const suffix = result.idempotent ? '（已写入，本次幂等）' : '，写入成功'
    const tableHint = result.businessTable ? ` → ${result.businessTable}` : ''
    ElMessage.success(`${result.formType}${tableHint} 共 ${result.confirmedCount} 条${suffix}`)
    emit('confirmed', {
      formType: result.formType,
      businessTable: result.businessTable,
      confirmedIds: result.confirmedIds,
      confirmedCount: result.confirmedCount
    })
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '确认写入失败，请稍后重试')
  } finally {
    confirming.value = false
  }
}
</script>

<style scoped>
.parsed-panel { margin-top: 16px; }
.confirm-area { min-height: 36px; }
.action-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.table-wrapper { overflow-x: auto; max-height: 500px; overflow-y: auto; }
.correction-table { width: 100%; }
.json-view {
  max-height: 400px; margin: 0; padding: 12px; overflow: auto;
  background: var(--el-fill-color-light); border: 1px solid var(--el-border-color-light);
  border-radius: 4px; white-space: pre-wrap; word-break: break-word;
  font-size: 12px; line-height: 1.6;
}
.mono-text { font-family: monospace; font-size: 12px; }
.block { display: block; }
.mb-8px { margin-bottom: 8px; }
.ml-8px { margin-left: 8px; }
.mt-12px { margin-top: 12px; }
.mt-8px  { margin-top: 8px; }
</style>
