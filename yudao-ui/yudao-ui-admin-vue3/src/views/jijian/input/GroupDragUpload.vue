<template>
  <PageShell :title="props.title" :description="props.description">
    <el-upload
      class="upload-area"
      drag
      multiple
      :auto-upload="false"
      :show-file-list="false"
      :disabled="uploading"
      :on-change="handleFileChange"
    >
      <Icon icon="ep:folder-add" class="upload-icon" />
      <div class="el-upload__text">拖拽多个文件到此处批量导入</div>
      <template #tip>
        <div class="el-upload__tip">
          支持 Excel(.xls/.xlsx)、CSV；图片(jpg/png/bmp/webp)和 PDF 由 OCR 服务解析。
          当前入口仅处理：<strong>{{ allowedLabel }}</strong>
        </div>
      </template>
    </el-upload>

    <!-- 文件列表 -->
    <div v-if="fileResults.length > 0" class="mt-16px">
      <el-table :data="fileResults" border size="small" @row-click="openPreview">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="fileName" label="文件名" min-width="160" show-overflow-tooltip />
        <el-table-column label="解析状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.loading" type="warning" size="small">
              <el-icon class="is-loading"><Loading /></el-icon> 解析中
            </el-tag>
            <el-tag v-else-if="row.outOfScope" type="danger" size="small">不在范围</el-tag>
            <el-tag v-else-if="row.status === 'confirmed'" type="success" size="small">已确认</el-tag>
            <el-tag v-else-if="row.status === 'success'" type="success" size="small">解析成功</el-tag>
            <el-tag v-else-if="row.status === 'failed'" type="danger" size="small">失败</el-tag>
            <el-tag v-else type="info" size="small">-</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="识别类型" width="130">
          <template #default="{ row }">
            <div v-if="row.formType" class="form-type-cell">
              <span>{{ row.formTypeName || row.formType }}</span>
              <el-tag
                v-if="row.confidence != null && row.status !== 'confirmed' && !row.outOfScope"
                :type="row.confidence >= 0.7 ? 'success' : 'warning'"
                size="small"
                class="ml-4px"
              >{{ Math.round(row.confidence * 100) }}%</el-tag>
              <el-tag v-if="row.outOfScope" type="danger" size="small" class="ml-4px">超范围</el-tag>
            </div>
            <el-text v-else-if="!row.loading" type="warning" size="small">未识别</el-text>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="行数/Sheet" width="130">
          <template #default="{ row }">
            <span>{{ row.totalRows ?? '-' }} 行</span>
            <template v-if="row.sheetName">
              <br />
              <el-tooltip
                v-if="(row.sheetCount ?? 1) > 1"
                :content="`文件共 ${row.sheetCount} 个 Sheet，当前仅导入第 1 个：「${row.sheetName}」`"
                placement="top"
              >
                <el-tag type="warning" size="small" class="mt-2px">
                  {{ row.sheetName }} ⚠{{ row.sheetCount }}
                </el-tag>
              </el-tooltip>
              <el-tag v-else type="info" size="small" class="mt-2px">{{ row.sheetName }}</el-tag>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="业务表 / 提示" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.businessTable" type="success" size="small">{{ row.businessTable }}</el-tag>
            <el-tooltip v-else-if="row.outOfScope" :content="row.outOfScopeMsg" placement="top">
              <span class="error-text">{{ row.outOfScopeMsg.substring(0, 36) }}…</span>
            </el-tooltip>
            <el-tooltip v-else-if="row.errorMsg" :content="row.errorMsg" placement="top">
              <span class="error-text">{{ row.errorMsg.substring(0, 30) }}…</span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <!-- 超范围：只提示，不允许操作 -->
            <el-tooltip v-if="row.outOfScope" :content="row.outOfScopeMsg" placement="top">
              <el-button type="danger" size="small" link disabled>⚠ 请换入口</el-button>
            </el-tooltip>
            <!-- 低置信度需选类型 -->
            <el-button
              v-else-if="row.parsedId && row.needsConfirmation && row.status !== 'confirmed'"
              type="warning"
              size="small"
              link
              @click="openTypeSelector(row)"
            >选择类型</el-button>
            <!-- 正常：预览 + 确认 -->
            <template v-else-if="row.parsedId && !row.needsConfirmation && !row.outOfScope">
              <el-button
                type="info"
                size="small"
                link
                @click="openPreview(row)"
              >预览/编辑</el-button>
              <el-button
                v-if="row.status === 'success'"
                type="primary"
                size="small"
                link
                :loading="row.confirming"
                @click="confirmSingle(row)"
              >确认写入</el-button>
            </template>
            <el-text v-if="row.status === 'confirmed'" type="success" size="small">✓ 已写入</el-text>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 全部确认按钮 -->
    <div v-if="canConfirmAll" class="mt-12px">
      <el-button type="primary" :loading="confirmingAll" @click="confirmAll">
        全部确认写入（{{ pendingCount }} 个）
      </el-button>
    </div>

    <!-- 内嵌预览（autoPreviewOnSuccess 入口：解析成功后自动在下方展开） -->
    <div v-if="autoPreviewOnSuccess && inlinePreviewRow" class="mt-16px">
      <el-divider content-position="left">预览：{{ inlinePreviewRow.fileName }}</el-divider>
      <div v-if="inlinePreviewLoading" class="text-center py-24px">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <div class="mt-8px text-gray-400">加载预览中…</div>
      </div>
      <ParsedDataPanel
        v-else-if="inlinePreviewParsedData"
        :parsed-data="inlinePreviewParsedData"
        @confirmed="onInlinePanelConfirmed"
      />
    </div>

    <!-- 预览/编辑对话框 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewRow ? `预览：${previewRow.fileName}` : '预览'"
      width="860px"
      destroy-on-close
    >
      <ParsedDataPanel
        v-if="previewParsedData"
        :parsed-data="previewParsedData"
        @confirmed="onPanelConfirmed"
      />
      <div v-else class="text-center py-24px">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <div class="mt-8px text-gray-400">加载中…</div>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button
          v-if="previewRow?.status === 'success' && !previewRow?.outOfScope"
          type="primary"
          :loading="previewRow?.confirming"
          @click="confirmFromPreview"
        >确认写入</el-button>
      </template>
    </el-dialog>

    <!-- 类型选择对话框（低置信度，仅显示当前入口允许的类型） -->
    <el-dialog
      v-model="typeSelectorVisible"
      title="请选择表单类型"
      width="480px"
      destroy-on-close
    >
      <div v-if="typeSelectorRow" class="type-selector">
        <el-alert type="warning" :closable="false" class="mb-16px">
          <template #title>
            系统未能准确识别「{{ typeSelectorRow.fileName }}」的类型
            <span v-if="typeSelectorRow.confidence != null">
              （当前置信度 {{ Math.round(typeSelectorRow.confidence * 100) }}%，低于 70%）
            </span>
            ，请手工选择正确的表单类型后重新解析。
          </template>
        </el-alert>
        <el-alert
          type="info"
          :closable="false"
          class="mb-16px"
        >
          当前入口只支持：<strong>{{ allowedLabel }}</strong>
        </el-alert>
        <div v-if="typeSelectorRow.matchedHeaders?.length" class="mb-12px">
          <span class="text-gray-500 text-sm">已命中表头：</span>
          <el-tag v-for="h in typeSelectorRow.matchedHeaders" :key="h" size="small" class="ml-4px">{{ h }}</el-tag>
        </div>
        <el-form label-width="80px">
          <el-form-item label="表单类型">
            <el-select v-model="selectedFormType" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="opt in filteredFormTypeOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <!-- 候选排名（只显示在允许范围内的） -->
        <div v-if="filteredCandidates.length" class="candidates-list">
          <div class="text-gray-500 text-sm mb-8px">系统候选排名（当前入口范围内）：</div>
          <div
            v-for="c in filteredCandidates.slice(0, 4)"
            :key="c.formType"
            class="candidate-item"
            :class="{ active: selectedFormType === c.formType }"
            @click="selectedFormType = c.formType"
          >
            <span>{{ c.displayName }}</span>
            <el-progress
              :percentage="Math.round(c.confidence * 100)"
              :color="c.confidence >= 0.7 ? '#67c23a' : '#e6a23c'"
              :stroke-width="6"
              class="candidate-bar"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="typeSelectorVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!selectedFormType"
          :loading="reparsingLoading"
          @click="confirmTypeSelection"
        >以此类型重新解析</el-button>
      </template>
    </el-dialog>
  </PageShell>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import type { UploadFile, UploadFiles } from 'element-plus'
import request from '@/config/axios'
import { confirmWrite, getParsedData } from '@/api/jijian/import'
import PageShell from '../components/PageShell.vue'
import ParsedDataPanel from '../components/ParsedDataPanel.vue'
import type { ParsedData } from '../types'

// ── Props ────────────────────────────────────────────────────────────────────

interface Props {
  title: string
  description: string
  /** 当前入口允许写入的 formType 列表（与后端 detectedFormType 值一致） */
  allowedFormTypes: string[]
  /** 提示用户去哪个入口，例如"考勤情况"或"拖拽录入" */
  alternativeHint?: string
  /** 解析成功且类型在范围内时，自动在页面下方展开 ParsedDataPanel 预览（食堂供应入口使用） */
  autoPreviewOnSuccess?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  alternativeHint: '通用拖拽录入',
  autoPreviewOnSuccess: false
})

// ── 全量表单类型常量（值与后端 FormTypeConstants 字符串一致） ──────────────────

const ALL_FORM_TYPE_OPTIONS = [
  { value: '考勤日报', label: '考勤日报' },
  { value: '疗休养假', label: '疗休养假' },
  { value: '事假记录', label: '事假记录' },
  { value: '调休记录', label: '调休记录' },
  { value: '出差记录', label: '出差记录' },
  { value: '房产信息', label: '房产信息' },
  { value: '租赁人员', label: '租赁人员' },
  { value: '租赁合同', label: '租赁合同' },
  { value: '食堂供应', label: '食堂供应' },
  { value: '民生价格公告', label: '民生价格公告' },
]

/** 当前入口允许的选项（用于类型选择器） */
const filteredFormTypeOptions = computed(() =>
  ALL_FORM_TYPE_OPTIONS.filter(o => props.allowedFormTypes.includes(o.value))
)

/** 当前入口允许类型的中文描述 */
const allowedLabel = computed(() => props.allowedFormTypes.join('、'))

// ── 数据类型 ─────────────────────────────────────────────────────────────────

interface CandidateType { formType: string; displayName: string; confidence: number }

interface FileResult {
  fileName: string
  file: File | null
  loading: boolean
  status: string
  formType: string
  formTypeName?: string
  confidence?: number
  matchedHeaders?: string[]
  needsConfirmation: boolean
  candidateTypes?: CandidateType[]
  totalRows?: number
  sheetName?: string
  sheetCount?: number
  errorMsg?: string
  parsedId?: number
  importRecordId?: number
  businessTable?: string
  businessIds?: string
  confirming: boolean
  /** true = 识别出的 formType 不在当前入口 allowedFormTypes 范围内 */
  outOfScope?: boolean
  /** 超范围时的用户提示信息 */
  outOfScopeMsg?: string
}

// ── 状态 ──────────────────────────────────────────────────────────────────────

const uploading     = ref(false)
const confirmingAll = ref(false)
const fileResults   = ref<FileResult[]>([])
let uploadTimer: ReturnType<typeof setTimeout> | undefined

// ── 计算属性 ──────────────────────────────────────────────────────────────────

const canConfirmAll = computed(() =>
  fileResults.value.some(r =>
    r.status === 'success' && !r.loading && !r.confirming && !r.needsConfirmation && !r.outOfScope
  )
)
const pendingCount = computed(() =>
  fileResults.value.filter(r =>
    r.status === 'success' && !r.loading && !r.needsConfirmation && !r.outOfScope
  ).length
)

// ── 范围校验 ──────────────────────────────────────────────────────────────────

function checkScope(row: FileResult) {
  if (!row.formType) return
  if (!props.allowedFormTypes.includes(row.formType)) {
    row.outOfScope = true
    row.outOfScopeMsg =
      `当前入口仅支持「${allowedLabel.value}」，` +
      `识别到「${row.formType}」，请到「${props.alternativeHint}」入口上传`
    // 显示弹窗提示
    ElMessage.warning(row.outOfScopeMsg)
  } else {
    row.outOfScope = false
    row.outOfScopeMsg = ''
  }
}

// ── 上传 ──────────────────────────────────────────────────────────────────────

const handleFileChange = (_file: UploadFile, fileList: UploadFiles) => {
  if (uploadTimer) clearTimeout(uploadTimer)
  uploadTimer = setTimeout(() => {
    const files = (fileList
      .map(f => f.raw)
      .filter(f => f != null) as File[])
      .filter(f => !fileResults.value.some(r => r.fileName === f.name))
    if (files.length === 0) return
    uploadFiles(files)
  }, 200)
}

async function uploadFiles(files: File[]) {
  uploading.value = true
  const newRows: FileResult[] = files.map(f => ({
    fileName: f.name, file: f, loading: true, status: '', formType: '',
    needsConfirmation: false, confirming: false
  }))
  fileResults.value = [...fileResults.value, ...newRows]
  const CONCURRENCY = 3
  for (let i = 0; i < files.length; i += CONCURRENCY) {
    await Promise.all(files.slice(i, i + CONCURRENCY).map(f => uploadSingle(f)))
  }
  uploading.value = false
}

async function uploadSingle(file: File) {
  const row = fileResults.value.find(r => r.fileName === file.name)
  if (!row) return
  try {
    const formData = new FormData()
    formData.append('file', file)
    const result = await request.post<any>({
      url: '/jijian/import/drag/detect',
      data: formData,
      headersType: 'multipart/form-data'
    })
    row.importRecordId = result.importRecordId
    row.parsedId       = result.parsedDataId
    row.status         = result.parseStatus || 'failed'
    row.errorMsg       = result.errorMsg || ''
    row.formType       = result.detectedFormType || ''
    row.formTypeName   = result.detectedFormName || ''
    row.confidence     = result.confidence ?? 0
    row.matchedHeaders = result.matchedHeaders || []
    row.needsConfirmation = !!result.needsConfirmation
    row.candidateTypes = result.candidateTypes || []
    row.sheetName      = result.sheetName || ''
    row.sheetCount     = result.sheetCount ?? 1

    // 解析成功后做范围校验
    if (row.status === 'success' || row.needsConfirmation) {
      checkScope(row)
    }

    // 读取行数
    if (row.parsedId && !row.outOfScope) {
      try {
        const pd = await getParsedData(row.importRecordId as number)
        const pj = JSON.parse(pd.parsedJson || '{}')
        row.totalRows = typeof pj.totalRows === 'number' ? pj.totalRows : pj.rows?.length
      } catch { /* ignore */ }
    }

    // 解析成功且在入口范围内：自动在页面下方展开预览
    if (props.autoPreviewOnSuccess && row.status === 'success'
        && !row.needsConfirmation && !row.outOfScope && row.parsedId) {
      await showInlinePreview(row)
    }
  } catch (err: unknown) {
    row.status = 'failed'
    row.errorMsg = err instanceof Error ? err.message : '上传失败'
  } finally {
    row.loading = false
  }
}

// ── 确认写入 ──────────────────────────────────────────────────────────────────

async function confirmSingle(row: FileResult) {
  if (!row.parsedId) return
  // 再次校验范围（防止状态异常）
  if (row.outOfScope || (row.formType && !props.allowedFormTypes.includes(row.formType))) {
    ElMessage.error(`当前入口不支持「${row.formType}」，请返回对应入口重新上传`)
    return
  }
  row.confirming = true
  try {
    const result = await confirmWrite(row.parsedId)
    row.status        = 'confirmed'
    row.businessTable = result.businessTable || row.formType
    row.businessIds   = JSON.stringify(result.confirmedIds)
    ElMessage.success(buildConfirmMessage(row.formTypeName || row.formType, result))
    // 若该行正在内嵌预览中，刷新预览以显示"已确认"状态
    if (inlinePreviewRow.value === row && row.importRecordId) {
      try {
        inlinePreviewParsedData.value = await getParsedData(row.importRecordId) as unknown as ParsedData
      } catch { /* ignore */ }
    }
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '确认写入失败')
  } finally {
    row.confirming = false
  }
}

function buildConfirmMessage(formType: string, result: {
  businessTable?: string
  confirmedCount: number
  totalRows?: number
  skippedRows?: number
  failedRows?: number
  idempotent?: boolean
}) {
  const tableHint = result.businessTable ? ` → ${result.businessTable}` : ''
  const stat = result.totalRows != null
    ? `共解析 ${result.totalRows} 行，成功 ${result.confirmedCount} 行，跳过 ${result.skippedRows ?? 0} 行，失败 ${result.failedRows ?? 0} 行`
    : `成功写入 ${result.confirmedCount} 条`
  return `${formType}${tableHint}：${stat}${result.idempotent ? '（重复确认已幂等处理）' : ''}`
}

async function confirmAll() {
  confirmingAll.value = true
  const pending = fileResults.value.filter(
    r => r.status === 'success' && !r.loading && !r.needsConfirmation && !r.outOfScope
  )
  await Promise.all(pending.map(r => confirmSingle(r)))
  confirmingAll.value = false
  ElMessage.success(`全部确认写入完成（${pending.length} 个）`)
}

// ── 预览/编辑 ─────────────────────────────────────────────────────────────────

const previewVisible    = ref(false)
const previewRow        = ref<FileResult | null>(null)
const previewParsedData = ref<ParsedData | null>(null)

// ── 内嵌预览（autoPreviewOnSuccess 模式）────────────────────────────────────

const inlinePreviewRow        = ref<FileResult | null>(null)
const inlinePreviewParsedData = ref<ParsedData | null>(null)
const inlinePreviewLoading    = ref(false)

async function showInlinePreview(row: FileResult) {
  if (!row.importRecordId) return
  inlinePreviewRow.value = row
  inlinePreviewLoading.value = true
  inlinePreviewParsedData.value = null
  try {
    inlinePreviewParsedData.value = await getParsedData(row.importRecordId) as unknown as ParsedData
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '加载预览失败')
    inlinePreviewRow.value = null
  } finally {
    inlinePreviewLoading.value = false
  }
}

function onInlinePanelConfirmed(result: {
  formType: string; businessTable?: string; confirmedIds: number[]; confirmedCount: number
}) {
  if (inlinePreviewRow.value) {
    inlinePreviewRow.value.status = 'confirmed'
    inlinePreviewRow.value.businessTable = result.businessTable || result.formType
    inlinePreviewRow.value.businessIds = JSON.stringify(result.confirmedIds)
  }
}

async function openPreview(row: FileResult) {
  if (!row.parsedId) return
  // autoPreviewOnSuccess 模式下，"预览/编辑"按钮也指向下方内嵌预览，保持单一编辑入口
  if (props.autoPreviewOnSuccess) {
    await showInlinePreview(row)
    return
  }
  previewRow.value = row
  previewParsedData.value = null
  previewVisible.value = true
  try {
    previewParsedData.value = await getParsedData(row.importRecordId as number) as unknown as ParsedData
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '加载预览失败')
    previewVisible.value = false
  }
}

async function confirmFromPreview() {
  if (!previewRow.value) return
  // 范围保护
  if (previewRow.value.outOfScope || !props.allowedFormTypes.includes(previewRow.value.formType)) {
    ElMessage.error(`当前入口不支持「${previewRow.value.formType}」，请返回对应入口重新上传`)
    return
  }
  await confirmSingle(previewRow.value)
  if (previewRow.value.status === 'confirmed' && previewRow.value.importRecordId) {
    try {
      previewParsedData.value = await getParsedData(previewRow.value.importRecordId) as unknown as ParsedData
    } catch { /* ignore */ }
  }
}

function onPanelConfirmed(result: {
  formType: string; businessTable?: string; confirmedIds: number[]; confirmedCount: number
}) {
  if (previewRow.value) {
    previewRow.value.status = 'confirmed'
    previewRow.value.businessTable = result.businessTable || result.formType
    previewRow.value.businessIds = JSON.stringify(result.confirmedIds)
  }
  previewVisible.value = false
  ElMessage.success(`${result.formType} 写入 ${result.confirmedCount} 条`)
}

// ── 类型选择（低置信度）──────────────────────────────────────────────────────

const typeSelectorVisible = ref(false)
const typeSelectorRow     = ref<FileResult | null>(null)
const selectedFormType    = ref('')
const reparsingLoading    = ref(false)

/** 候选排名中只保留当前入口允许的类型 */
const filteredCandidates = computed(() => {
  if (!typeSelectorRow.value?.candidateTypes) return []
  return typeSelectorRow.value.candidateTypes.filter(
    c => props.allowedFormTypes.includes(c.formType)
  )
})

function openTypeSelector(row: FileResult) {
  typeSelectorRow.value = row
  // 默认选中已识别类型（若在范围内），否则清空
  selectedFormType.value = props.allowedFormTypes.includes(row.formType) ? row.formType : ''
  typeSelectorVisible.value = true
}

async function confirmTypeSelection() {
  const row = typeSelectorRow.value
  if (!row || !selectedFormType.value || !row.file || !row.importRecordId) return

  // 校验选择的类型是否在范围内
  if (!props.allowedFormTypes.includes(selectedFormType.value)) {
    ElMessage.error(`当前入口不支持「${selectedFormType.value}」，请选择范围内的类型`)
    return
  }

  reparsingLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', row.file)
    formData.append('formType', selectedFormType.value)
    const result = await request.post<any>({
      url: `/jijian/import/drag/reparse/${row.importRecordId}`,
      data: formData,
      headersType: 'multipart/form-data'
    })
    row.parsedId  = result.parsedDataId
    row.formType  = result.detectedFormType || selectedFormType.value
    row.formTypeName = filteredFormTypeOptions.value.find(o => o.value === selectedFormType.value)?.label || selectedFormType.value
    row.confidence = 1.0
    row.needsConfirmation = false
    row.status = result.parseStatus || 'success'
    row.errorMsg = result.errorMsg || ''
    // 重新校验范围
    checkScope(row)
    typeSelectorVisible.value = false
    ElMessage.success(`已以「${row.formTypeName}」类型重新解析`)
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '重新解析失败')
  } finally {
    reparsingLoading.value = false
  }
}
</script>

<style scoped>
.upload-area  { width: 100%; }
.upload-icon  { font-size: 48px; color: var(--el-color-primary); }
.mt-16px      { margin-top: 16px; }
.mt-12px      { margin-top: 12px; }
.ml-4px       { margin-left: 4px; }
.mb-8px       { margin-bottom: 8px; }
.mb-12px      { margin-bottom: 12px; }
.mb-16px      { margin-bottom: 16px; }
.mt-8px       { margin-top: 8px; }
.mt-2px       { margin-top: 2px; }
.py-24px      { padding: 24px 0; }
.error-text   { color: var(--el-color-danger); font-size: 12px; }
.form-type-cell { display: flex; align-items: center; flex-wrap: wrap; gap: 2px; }
.type-selector  { padding: 0 8px; }
.candidates-list { background: #f8f9fa; border-radius: 6px; padding: 10px 12px; }
.candidate-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 8px; cursor: pointer; border-radius: 4px; margin-bottom: 4px;
  border: 1px solid transparent; transition: all 0.2s;
}
.candidate-item:hover  { background: #ecf5ff; border-color: #b3d8ff; }
.candidate-item.active { background: #ecf5ff; border-color: #409eff; }
.candidate-bar  { width: 160px; }
.text-center    { text-align: center; }
.text-gray-400  { color: #9ca3af; }
.text-gray-500  { color: #6b7280; }
.text-sm        { font-size: 12px; }
</style>
