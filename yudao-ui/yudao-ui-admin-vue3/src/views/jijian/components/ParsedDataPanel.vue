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
      <template v-if="isConfirmed">
        <el-alert :type="confirmedAlertType" :closable="false" show-icon>
          <template #title>
            <span>
              已确认写入「{{ confirmedFormType }}」{{ confirmedBusinessTable ? ' → ' + confirmedBusinessTable : '' }}
              <template v-if="localResult?.totalRows != null">
                ：共解析 <strong>{{ localResult.totalRows }}</strong> 行，
                成功 <strong>{{ localResult.confirmedCount }}</strong> 行，
                跳过 <strong>{{ localResult.skippedRows ?? 0 }}</strong> 行，
                失败 <strong>{{ localResult.failedRows ?? 0 }}</strong> 行<template v-if="(localResult.duplicateSkippedCount ?? 0) > 0">，跳过重复 <strong>{{ localResult.duplicateSkippedCount }}</strong> 条</template>
              </template>
              <template v-else>
                ，共 {{ confirmedCount }} 条记录
                <template v-if="confirmedIds.length > 0">，记录 ID：{{ confirmedIds.slice(0, 5).join('、') }}{{ confirmedIds.length > 5 ? '…' : '' }}</template>
              </template>
              <el-tag v-if="wasIdempotent" type="info" size="small" class="ml-8px">重复操作已幂等</el-tag>
            </span>
          </template>
        </el-alert>

        <!-- 跳过行详情（可折叠） -->
        <el-collapse v-if="(localResult?.skippedRows ?? 0) > 0" class="mt-8px detail-collapse">
          <el-collapse-item :title="`跳过行明细（${localResult?.skippedRows} 行，均为空白行或合计行）`" name="skipped">
            <ul class="detail-list">
              <li v-for="(msg, idx) in (localResult?.skippedMessages ?? [])" :key="idx" class="skipped-item">{{ msg }}</li>
              <li v-if="(localResult?.skippedMessages?.length ?? 0) < (localResult?.skippedRows ?? 0)" class="more-hint">
                ……（更多行未展示，请查看后端日志）
              </li>
            </ul>
          </el-collapse-item>
        </el-collapse>

        <!-- 失败行详情（可折叠，醒目展示） -->
        <el-collapse v-if="(localResult?.failedRows ?? 0) > 0" class="mt-8px detail-collapse">
          <el-collapse-item :title="`失败行明细（${localResult?.failedRows} 行，有业务字段但关键字段缺失）`" name="failed">
            <ul class="detail-list">
              <li v-for="(msg, idx) in (localResult?.failedMessages ?? [])" :key="idx" class="failed-item">{{ msg }}</li>
              <li v-if="(localResult?.failedMessages?.length ?? 0) < (localResult?.failedRows ?? 0)" class="more-hint">
                ……（更多行未展示，请查看后端日志）
              </li>
            </ul>
          </el-collapse-item>
        </el-collapse>
      </template>

      <!-- 可操作 -->
      <div v-else-if="canConfirm" class="action-row">
        <el-button type="success" :loading="saving" plain @click="handleSaveCorrection">保存校正</el-button>
        <el-button type="primary" :loading="confirming" @click="handleConfirm">确认写入正式数据</el-button>
        <el-text v-if="correctionSaved" type="success" size="small">✓ 校正已保存</el-text>
        <el-text v-if="hasEdited && !correctionSaved" type="warning" size="small">有未保存的修改</el-text>
      </div>
    </div>

    <!-- ── 解析通知（如食堂供应未识别到日期等非阻断提示） ── -->
    <el-alert
      v-if="parsedNotice && !isConfirmed"
      class="mt-8px"
      type="warning"
      :closable="true"
      show-icon
      :title="parsedNotice"
    />

    <!-- ── 大数据提示 ── -->
    <el-alert
      v-if="isLargeData"
      class="mt-8px"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #title>
        当前文件共 <strong>{{ totalRowCount }}</strong> 行，采用分页预览（每页 {{ PAGE_SIZE }} 行）。
        <strong>保存校正 / 确认写入均对完整数据有效。</strong>
      </template>
    </el-alert>

    <!-- ── 数据展示 / 编辑 ── -->
    <el-tabs class="mt-12px" v-model="activeTab">
      <!-- 租赁合同纵向预览 -->
      <el-tab-pane v-if="isLeaseContractPreview" label="租赁合同预览" name="lease">
        <div class="lease-preview">
          <el-form label-position="top" size="small" class="lease-form">
            <el-form-item v-for="field in leaseFields" :key="field.key" :label="field.label">
              <el-input
                v-if="canConfirm"
                v-model="field.value"
                :type="field.multiline ? 'textarea' : 'text'"
                :autosize="field.multiline ? { minRows: 2, maxRows: 5 } : undefined"
                @change="markEdited"
              />
              <span v-else class="lease-readonly">{{ field.value || '-' }}</span>
            </el-form-item>
          </el-form>

          <div class="rent-section">
            <div class="rent-section-header">
              <span>租金与交纳日期明细</span>
              <el-button v-if="canConfirm" size="small" plain @click="addLeaseRentItem">
                <el-icon><Plus /></el-icon> 新增明细
              </el-button>
            </div>
            <div v-if="leaseRentItems.length === 0" class="empty-rent">暂无租金明细</div>
            <div v-for="(item, index) in leaseRentItems" :key="item.index" class="rent-item">
              <div class="rent-title">第 {{ index + 1 }} 项</div>
              <div class="rent-grid">
                <label>租赁年份</label>
                <el-input v-if="canConfirm" v-model="item.year" size="small" @change="markEdited" />
                <span v-else>{{ item.year || '-' }}</span>
                <label>租金交纳日期</label>
                <el-input v-if="canConfirm" v-model="item.paymentDate" size="small" @change="markEdited" />
                <span v-else>{{ item.paymentDate || '-' }}</span>
                <label>房屋租金</label>
                <el-input v-if="canConfirm" v-model="item.rentAmount" size="small" @change="markEdited" />
                <span v-else>{{ item.rentAmount || '-' }}</span>
              </div>
              <el-button v-if="canConfirm" type="danger" size="small" link @click="deleteLeaseRentItem(index)">删除</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 多行表格 -->
      <el-tab-pane
        v-if="tableHeaders.length > 0 && !isLeaseContractPreview"
        :label="`数据表格（${totalRowCount} 行${isLargeData ? '，分页预览' : ''}）`"
        name="table"
      >
        <div class="table-toolbar mb-8px">
          <el-space wrap>
            <el-tag type="info" size="small">共 {{ totalRowCount }} 行 · 当前页 {{ pageStart + 1 }}–{{ pageEnd }} 行</el-tag>
            <el-button v-if="canConfirm" size="small" plain @click="addRow">
              <el-icon><Plus /></el-icon> 新增一行
            </el-button>
            <el-text type="info" size="small" v-if="canConfirm">
              字段值可直接编辑，修改后点击「保存校正」再确认写入
            </el-text>
          </el-space>
        </div>
        <div class="table-wrapper">
          <el-table :data="tableRows" border size="small" class="correction-table" height="100%">
            <el-table-column type="index" :index="pageStart + 1" label="#" width="60" fixed />
            <el-table-column
              v-for="header in tableHeaders"
              :key="header"
              :prop="header"
              :label="header"
              :min-width="getColumnMinWidth(header)"
            >
              <template #default="{ row }">
                <el-input
                  v-if="canConfirm"
                  v-model="row[header]"
                  type="textarea"
                  :autosize="{ minRows: 1, maxRows: 4 }"
                  resize="none"
                  class="cell-editor"
                  size="small"
                  @change="markEdited"
                />
                <span v-else class="cell-text">{{ row[header] ?? '' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="canConfirm" label="操作" width="70" fixed="right">
              <template #default="{ $index }">
                <el-button type="danger" size="small" link @click="deleteRow($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 分页控件（数据量 > PAGE_SIZE 时显示） -->
        <div v-if="isLargeData" class="pagination-bar mt-8px">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="PAGE_SIZE"
            :total="totalRowCount"
            layout="prev, pager, next, jumper, total"
            small
            @current-change="onPageChange"
          />
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

      <!-- JSON 视图（大文件只显示元信息，避免卡顿） -->
      <el-tab-pane label="结构化 JSON" name="json">
        <pre class="json-view">{{ jsonPreview }}</pre>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { confirmWrite, saveCorrection } from '@/api/jijian/import'
import type { ParsedData } from '../types'

type LeaseField = {
  key: string
  label: string
  value: string
  multiline?: boolean
}

type LeaseRentItem = {
  index: number
  year: string
  paymentDate: string
  rentAmount: string
}

// ── 常量 ────────────────────────────────────────────────────────────────────
/** 每页展示行数（Vue 响应式对象上限）。超过此值时启用分页预览。 */
const PAGE_SIZE = 200
/** 超过此行数时才显示大数据提示和分页控件 */
const LARGE_DATA_THRESHOLD = PAGE_SIZE

// ── Props / Emits ─────────────────────────────────────────────────────────
const props = defineProps<{ parsedData?: ParsedData | null }>()

const emit = defineEmits<{
  (event: 'confirmed', result: {
    formType: string
    businessTable?: string
    confirmedIds: number[]
    confirmedCount: number
  }): void
}>()

// ── 非响应式全量存储（避免 Vue 深度代理 15000+ 行对象导致卡死） ─────────────
// 这是一个模块级普通数组，不被 Vue 跟踪，切换 parsedData 时手动清空重建。
let _allRowsRaw: Record<string, string>[] = []

// ── 响应式状态 ──────────────────────────────────────────────────────────────
const confirming      = ref(false)
const saving          = ref(false)
const correctionSaved = ref(false)
const hasEdited       = ref(false)
const activeTab       = ref('table')
const localResult     = ref<{
  formType: string
  businessTable?: string
  confirmedIds: number[]
  confirmedCount: number
  idempotent: boolean
  totalRows?: number
  skippedRows?: number
  failedRows?: number
  skippedMessages?: string[]
  failedMessages?: string[]
  duplicateSkippedCount?: number
  duplicateSkippedRows?: string[]
} | null>(null)

const tableHeaders  = ref<string[]>([])
/** 当前页的行数据（已深拷贝，可安全 v-model 编辑） */
const tableRows     = ref<Record<string, string>[]>([])
const kvFields      = ref<{ key: string; value: string }[]>([])
const leaseFields   = ref<LeaseField[]>([])
const leaseRentItems = ref<LeaseRentItem[]>([])
const totalRowCount = ref(0)
const currentPage   = ref(1)

// ── 计算属性 ────────────────────────────────────────────────────────────────
const isLargeData  = computed(() => totalRowCount.value > LARGE_DATA_THRESHOLD)

const isLeaseContractPreview = computed(() => {
  if (props.parsedData?.formType === '租赁合同' || props.parsedData?.businessTable === 'jijian_lease_contract') {
    return true
  }
  const source = props.parsedData?.correctedJson || props.parsedData?.parsedJson
  if (!source) return false
  try {
    const obj = JSON.parse(source)
    return obj.formType === '租赁合同' || obj.businessTable === 'jijian_lease_contract'
  } catch { return false }
})

/** 读取 parsedJson 中的 ocrNotice 字段，用于展示非阻断提示（如食堂日期未识别） */
const parsedNotice = computed<string>(() => {
  const source = props.parsedData?.correctedJson || props.parsedData?.parsedJson
  if (!source) return ''
  try {
    const obj = JSON.parse(source)
    return typeof obj.ocrNotice === 'string' ? obj.ocrNotice : ''
  } catch { return '' }
})
const pageStart    = computed(() => (currentPage.value - 1) * PAGE_SIZE)
const pageEnd      = computed(() => Math.min(currentPage.value * PAGE_SIZE, totalRowCount.value))

const isConfirmed = computed(() =>
  props.parsedData?.confirmStatus === 'confirmed' ||
  props.parsedData?.status === 'confirmed' ||
  localResult.value !== null
)
const canConfirm = computed(() =>
  props.parsedData?.status === 'success' && !isConfirmed.value
)
const confirmedFormType      = computed(() => localResult.value?.formType || props.parsedData?.formType || '')
const confirmedBusinessTable = computed(() => localResult.value?.businessTable || props.parsedData?.businessTable || '')
const confirmedIds           = computed(() => localResult.value?.confirmedIds ?? [])
const confirmedCount         = computed(() =>
  localResult.value?.confirmedCount ?? (props.parsedData?.confirmedPropertyId ? 1 : 0)
)
const wasIdempotent = computed(() => localResult.value?.idempotent ?? false)

/** 有失败行时用 warning，否则用 success */
const confirmedAlertType = computed(() => {
  const failed = localResult.value?.failedRows ?? 0
  return failed > 0 ? 'warning' : 'success'
})

const displayBusinessIds = computed(() => {
  const raw = props.parsedData?.businessIds
  if (!raw) return '-'
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.join(', ') : raw
  } catch { return raw }
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

/**
 * JSON 视图：大文件时只展示元信息，避免序列化 50MB+ JSON 字符串卡死浏览器。
 */
const jsonPreview = computed(() => {
  const raw = props.parsedData?.correctedJson || props.parsedData?.parsedJson || '{}'
  if (totalRowCount.value > LARGE_DATA_THRESHOLD) {
    try {
      const obj = JSON.parse(raw)
      const meta = {
        fileName:  obj.fileName,
        sheetName: obj.sheetName,
        totalRows: obj.totalRows,
        headers:   obj.headers,
        rowsCount: Array.isArray(obj.rows) ? obj.rows.length : 0,
        notice:    '（行数过多，完整数据未展示，请使用「数据表格」分页查看）',
      }
      return JSON.stringify(meta, null, 2)
    } catch { return '（JSON 解析失败）' }
  }
  try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
})

// ── 数据初始化 ──────────────────────────────────────────────────────────────

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
  // 重置所有状态
  tableHeaders.value  = []
  tableRows.value     = []
  kvFields.value      = []
  leaseFields.value   = []
  leaseRentItems.value = []
  totalRowCount.value = 0
  hasEdited.value     = false
  correctionSaved.value = false
  localResult.value   = null
  activeTab.value     = 'table'
  currentPage.value   = 1
  _allRowsRaw         = []   // 清空非响应式存储

  const source = props.parsedData?.correctedJson || props.parsedData?.parsedJson
  if (!source) return
  try {
    const obj = JSON.parse(source)

    if (Array.isArray(obj.rows) && obj.rows.length > 0) {
      totalRowCount.value = typeof obj.totalRows === 'number' ? obj.totalRows : obj.rows.length
      const firstRow = obj.rows[0]
      const sourceHeaders = Array.isArray(obj.headers)
        ? obj.headers
        : (Array.isArray(firstRow)
            ? firstRow.map((_: unknown, i: number) => `列${i + 1}`)
            : Object.keys(firstRow))
      const headerDefs = normalizeHeaders(sourceHeaders)
      tableHeaders.value = headerDefs.map(({ name }) => name)

      // 全量数据存入非响应式数组（不创建 Vue Proxy，避免 15000+ 行卡死）
      _allRowsRaw = obj.rows.map((row: unknown) => {
        if (Array.isArray(row)) {
          return Object.fromEntries(
            headerDefs.map(({ name, index }) => [name, row[index] == null ? '' : String(row[index])])
          )
        }
        const record = row as Record<string, unknown>
        return Object.fromEntries(
          Object.entries(record).map(([k, v]) => [k, v == null ? '' : String(v)])
        )
      })
      totalRowCount.value = _allRowsRaw.length

      // 仅将第一页放入响应式状态
      loadPage(1)
      if (isLeaseContractPreview.value) {
        initLeaseContractEditor(obj)
      }

    } else if (obj.textPreview) {
      String(obj.textPreview).split('\n').forEach((line: string) => {
        const idx = line.includes('：') ? line.indexOf('：') : line.indexOf(':')
        if (idx > 0 && idx < line.length - 1) {
          const k = line.substring(0, idx).trim()
          const v = line.substring(idx + 1).trim()
          if (k && v) kvFields.value.push({ key: k, value: v })
        }
      })
      activeTab.value = 'kv'
    }
    if (isLeaseContractPreview.value) {
      activeTab.value = 'lease'
    }
  } catch {
    activeTab.value = 'raw'
  }
}

function initLeaseContractEditor(parsedObj: Record<string, any>) {
  const row = _allRowsRaw[0] || {}
  const fields: Array<Omit<LeaseField, 'value'>> = [
    { key: '合同编号', label: '合同编号' },
    { key: '合同签订日期', label: '合同签订日期' },
    { key: '出租方', label: '出租方' },
    { key: '承租方', label: '承租方' },
    { key: '承租人身份证号', label: '承租人身份证号' },
    { key: '承租人联系电话', label: '承租人联系电话' },
    { key: '房屋状况', label: '房屋状况', multiline: true },
    { key: '租赁开始时间', label: '租赁开始时间' },
    { key: '租赁结束时间', label: '租赁结束时间' },
    { key: '租赁年份', label: '租赁年份' },
    { key: '租赁用途', label: '租赁用途' },
    { key: '保证金', label: '保证金' },
    { key: '水费', label: '水费' },
    { key: '电费', label: '电费' },
    { key: '备注', label: '备注', multiline: true }
  ]
  leaseFields.value = fields.map(field => ({
    ...field,
    value: row[field.key] || ''
  }))
  leaseRentItems.value = parseLeaseRentItems(row, parsedObj)
}

function parseLeaseRentItems(row: Record<string, string>, parsedObj: Record<string, any>): LeaseRentItem[] {
  const json = row['租金明细JSON'] || row.rentInfoJson || parsedObj.rentInfoJson
  if (json) {
    try {
      const arr = typeof json === 'string' ? JSON.parse(json) : json
      if (Array.isArray(arr)) {
        return arr.map((item, idx) => ({
          index: Number(item.index || idx + 1),
          year: item.year == null ? '' : String(item.year),
          paymentDate: item.paymentDate == null ? String(item.paymentText || '') : String(item.paymentDate),
          rentAmount: item.rentAmount == null ? String(item.rentText || '') : String(item.rentAmount)
        })).filter(item => item.year || item.paymentDate || item.rentAmount)
      }
    } catch {}
  }
  const items: LeaseRentItem[] = []
  for (let i = 1; i <= 50; i++) {
    const rentAmount = row[`房屋租金${i}`] || ''
    const paymentDate = row[`租金交纳日期${i}`] || ''
    if (!rentAmount && !paymentDate) continue
    const year = ((rentAmount + ' ' + paymentDate).match(/20\d{2}/)?.[0]) || ''
    items.push({ index: items.length + 1, year, paymentDate, rentAmount })
  }
  return items
}

function syncLeaseContractToRows(base?: Record<string, any>) {
  if (!isLeaseContractPreview.value || _allRowsRaw.length === 0) return
  const row = { ..._allRowsRaw[0] }
  for (const field of leaseFields.value) {
    row[field.key] = field.value || ''
  }
  const rentItems = leaseRentItems.value
    .map((item, idx) => ({
      index: idx + 1,
      year: item.year || '',
      paymentDate: item.paymentDate || '',
      rentAmount: item.rentAmount || ''
    }))
    .filter(item => item.year || item.paymentDate || item.rentAmount)
  row['租金明细JSON'] = JSON.stringify(rentItems)
  for (let i = 1; i <= 50; i++) {
    delete row[`房屋租金${i}`]
    delete row[`租金交纳日期${i}`]
  }
  rentItems.forEach((item, idx) => {
    row[`房屋租金${idx + 1}`] = item.rentAmount
    row[`租金交纳日期${idx + 1}`] = item.paymentDate
  })
  _allRowsRaw[0] = row
  tableRows.value = [{ ...row }]
  tableHeaders.value = buildLeaseHeaders(rentItems.length)
  if (base) {
    base.headers = tableHeaders.value
    base.rentInfoJson = row['租金明细JSON']
  }
}

function buildLeaseHeaders(rentCount: number): string[] {
  const headers = [
    '合同编号', '合同签订日期', '出租方', '承租方', '承租人身份证号', '承租人联系电话',
    '房屋状况', '租赁开始时间', '租赁结束时间', '租赁年份', '租赁用途'
  ]
  for (let i = 1; i <= Math.max(rentCount, 1); i++) {
    headers.push(`房屋租金${i}`, `租金交纳日期${i}`)
  }
  headers.push('保证金', '水费', '电费', '备注')
  return headers
}

/**
 * 将指定页的行深拷贝到 tableRows（响应式）。
 * 深拷贝确保编辑不会直接污染 _allRowsRaw。
 */
function loadPage(page: number) {
  const start = (page - 1) * PAGE_SIZE
  const end   = Math.min(start + PAGE_SIZE, _allRowsRaw.length)
  tableRows.value = _allRowsRaw.slice(start, end).map(r => ({ ...r }))
}

/**
 * 将当前页编辑内容同步回非响应式存储。
 * 在翻页、保存校正前必须调用。
 */
function syncCurrentPageToRaw() {
  const start = (currentPage.value - 1) * PAGE_SIZE
  for (let i = 0; i < tableRows.value.length; i++) {
    if (start + i < _allRowsRaw.length) {
      _allRowsRaw[start + i] = { ...tableRows.value[i] }
    }
  }
}

function onPageChange(page: number) {
  if (hasEdited.value) {
    syncCurrentPageToRaw()   // 翻页前先保存当前页编辑
  }
  currentPage.value = page
  loadPage(page)
}

function getColumnMinWidth(header: string) {
  if (/房屋租金|租金交纳日期/.test(header)) return 300
  if (/房屋状况|备注|原始|OCR/.test(header)) return 360
  if (/出租方|承租方/.test(header)) return 220
  if (/合同编号|日期|时间|电话|身份证/.test(header)) return 180
  return 140
}

watch(() => props.parsedData, initEditableData, { immediate: true, deep: false })

onUnmounted(() => {
  _allRowsRaw = []
})

// ── 操作 ───────────────────────────────────────────────────────────────────

function markEdited() {
  hasEdited.value = true
  correctionSaved.value = false
}

function addLeaseRentItem() {
  leaseRentItems.value.push({
    index: leaseRentItems.value.length + 1,
    year: '',
    paymentDate: '',
    rentAmount: ''
  })
  markEdited()
}

function deleteLeaseRentItem(index: number) {
  leaseRentItems.value.splice(index, 1)
  leaseRentItems.value.forEach((item, idx) => { item.index = idx + 1 })
  markEdited()
}

function addRow() {
  // 将空行添加到全量数据末尾，并跳转到最后一页
  const emptyRow = Object.fromEntries(tableHeaders.value.map(h => [h, '']))
  syncCurrentPageToRaw()
  _allRowsRaw.push(emptyRow)
  totalRowCount.value = _allRowsRaw.length
  const lastPage = Math.ceil(totalRowCount.value / PAGE_SIZE)
  currentPage.value = lastPage
  loadPage(lastPage)
  markEdited()
}

function deleteRow(index: number) {
  // 从当前页 tableRows 移除，同步到全量
  syncCurrentPageToRaw()
  const globalIdx = pageStart.value + index
  if (globalIdx < _allRowsRaw.length) {
    _allRowsRaw.splice(globalIdx, 1)
  }
  totalRowCount.value = _allRowsRaw.length
  loadPage(currentPage.value)
  markEdited()
}

async function handleSaveCorrection() {
  if (!props.parsedData) return

  // 先将当前页编辑同步到全量存储
  syncCurrentPageToRaw()

  let correctedJson: string
  try {
    const base = JSON.parse(props.parsedData.correctedJson || props.parsedData.parsedJson || '{}')
    if (isLeaseContractPreview.value) {
      syncLeaseContractToRows(base)
    }
    if (_allRowsRaw.length > 0) {
      base.headers   = tableHeaders.value
      base.rows      = [..._allRowsRaw]   // 完整数据（非响应式副本）
      base.totalRows = _allRowsRaw.length
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
      `确认将当前解析结果写入正式「${props.parsedData.formType || '业务'}」数据库？`
      + (isLargeData.value ? `\n共 ${totalRowCount.value} 行，将分批写入，请耐心等待。` : ''),
      '确认写入',
      { confirmButtonText: '确认写入', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  confirming.value = true
  try {
    const result = await confirmWrite(props.parsedData.id)
    const extendedResult = result as typeof result & {
      duplicateSkippedCount?: number
      duplicateSkippedRows?: string[]
    }
    localResult.value = {
      formType: result.formType,
      businessTable: result.businessTable,
      confirmedIds: result.confirmedIds,
      confirmedCount: result.confirmedCount,
      idempotent: result.idempotent,
      totalRows: result.totalRows,
      skippedRows: result.skippedRows ?? 0,
      failedRows: result.failedRows ?? 0,
      skippedMessages: result.skippedMessages ?? [],
      failedMessages: result.failedMessages ?? [],
      duplicateSkippedCount: extendedResult.duplicateSkippedCount ?? 0,
      duplicateSkippedRows: extendedResult.duplicateSkippedRows ?? []
    }
    const suffix = result.idempotent ? '（已写入，本次幂等）' : ''
    const tableHint = result.businessTable ? ` → ${result.businessTable}` : ''
    const dupHint = (extendedResult.duplicateSkippedCount ?? 0) > 0 ? `，跳过重复 ${extendedResult.duplicateSkippedCount} 条` : ''
    const totalHint = result.totalRows != null
      ? `共解析 ${result.totalRows} 行，成功 ${result.confirmedCount} 行，跳过 ${result.skippedRows ?? 0} 行，失败 ${result.failedRows ?? 0} 行${dupHint}`
      : `共 ${result.confirmedCount} 条${dupHint}`
    ElMessage.success(`${result.formType}${tableHint}：${totalHint}${suffix}`)
    emit('confirmed', {
      formType: result.formType,
      businessTable: result.businessTable,
      confirmedIds: result.confirmedIds,
      confirmedCount: result.confirmedCount
    })
  } catch (err: unknown) {
    // 后端返回的错误信息包含行号和字段原因，直接展示
    const msg = err instanceof Error ? err.message : '确认写入失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    confirming.value = false
  }
}
</script>

<style scoped>
.parsed-panel { margin-top: 16px; }
:global(.jijian-parsed-preview-dialog .el-dialog__body) {
  max-height: 82vh;
  overflow: auto;
}
.confirm-area { min-height: 36px; }
.action-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.table-wrapper {
  position: relative;
  overflow: auto;
  height: min(62vh, 560px);
  min-height: 320px;
  padding-bottom: 10px;
  scrollbar-gutter: stable both-edges;
}
.table-wrapper :deep(.el-scrollbar__bar.is-horizontal) {
  position: sticky !important;
  bottom: 0;
  display: block !important;
  opacity: 1 !important;
  z-index: 4;
}
.table-wrapper :deep(.el-table__body-wrapper) {
  overflow-x: auto;
}
.correction-table { width: 100%; }
.correction-table :deep(.cell) {
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
  line-height: 1.45;
}
.cell-editor :deep(.el-textarea__inner) {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.45;
}
.cell-text {
  display: inline-block;
  white-space: pre-wrap;
  word-break: break-word;
}
.lease-preview {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.lease-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}
.lease-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.lease-form :deep(.el-form-item:nth-child(7)),
.lease-form :deep(.el-form-item:nth-child(15)) {
  grid-column: 1 / -1;
}
.lease-readonly {
  min-height: 24px;
  white-space: pre-wrap;
  word-break: break-word;
}
.rent-section {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 12px;
}
.rent-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-weight: 600;
}
.empty-rent {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.rent-item {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 10px 0;
}
.rent-item:first-of-type {
  border-top: 0;
}
.rent-title {
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
  font-weight: 600;
}
.rent-grid {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 8px 12px;
  align-items: center;
}
.rent-grid label {
  color: var(--el-text-color-secondary);
}
.pagination-bar { display: flex; justify-content: flex-end; }
.json-view {
  max-height: 400px; margin: 0; padding: 12px; overflow: auto;
  background: var(--el-fill-color-light); border: 1px solid var(--el-border-color-light);
  border-radius: 4px; white-space: pre-wrap; word-break: break-word;
  font-size: 12px; line-height: 1.6;
}
.mono-text { font-family: monospace; font-size: 12px; }
.block { display: block; }
.mb-8px  { margin-bottom: 8px; }
.mt-8px  { margin-top: 8px; }
.mt-12px { margin-top: 12px; }
.ml-8px  { margin-left: 8px; }
.detail-collapse { border: none; }
.detail-list { margin: 4px 0; padding-left: 16px; font-size: 12px; line-height: 1.8; }
.skipped-item { color: var(--el-color-info); }
.failed-item  { color: var(--el-color-danger); }
.more-hint    { color: var(--el-color-info-light-3); font-style: italic; }
</style>
