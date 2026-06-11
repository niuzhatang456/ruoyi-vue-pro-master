<template>
  <PageShell title="拖拽录入" description="批量拖拽上传文件，每个文件独立解析并展示状态。支持 Excel(.xls/.xlsx)、CSV，以及图片(jpg/png/bmp)和 PDF（需后端配置 OCR 服务）。">
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
          支持 Excel(.xls/.xlsx)、CSV；图片(jpg/png/bmp/webp)和 PDF 由后端 OCR 服务解析，未配置时将返回错误提示。
        </div>
      </template>
    </el-upload>

    <!-- 批量状态列表 -->
    <div v-if="fileResults.length > 0" class="mt-16px">
      <el-table :data="fileResults" border size="small">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="fileName" label="文件名" min-width="160" show-overflow-tooltip />
        <el-table-column label="解析状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.loading" type="warning" size="small">
              <el-icon class="is-loading"><Loading /></el-icon> 解析中
            </el-tag>
            <el-tag v-else-if="row.status === 'confirmed'" type="success" size="small">已确认</el-tag>
            <el-tag v-else-if="row.status === 'success'"  type="success" size="small">解析成功</el-tag>
            <el-tag v-else-if="row.status === 'failed'"   type="danger"  size="small">失败</el-tag>
            <el-tag v-else type="info" size="small">-</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="识别类型" width="130">
          <template #default="{ row }">
            <div v-if="row.formType" class="form-type-cell">
              <span>{{ row.formTypeName || row.formType }}</span>
              <el-tag
                v-if="row.confidence != null && row.status !== 'confirmed'"
                :type="row.confidence >= 0.7 ? 'success' : 'warning'"
                size="small"
                class="ml-4px"
              >{{ Math.round(row.confidence * 100) }}%</el-tag>
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
                :content="`文件共 ${row.sheetCount} 个 Sheet，当前仅导入第 1 个：「${row.sheetName}」，如需其他 Sheet 请拆分后重新上传`"
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
        <el-table-column label="业务表 / 错误" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.businessTable" type="success" size="small">{{ row.businessTable }}</el-tag>
            <el-tooltip v-else-if="row.errorMsg" :content="row.errorMsg" placement="top">
              <span class="error-text">{{ row.errorMsg.substring(0, 30) }}…</span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <!-- 低置信度：需要选择表单类型 -->
            <el-button
              v-if="row.parsedId && row.needsConfirmation && row.status !== 'confirmed'"
              type="warning"
              size="small"
              link
              @click="openTypeSelector(row)"
            >选择类型</el-button>
            <!-- 预览/编辑 -->
            <el-button
              v-if="row.parsedId && !row.needsConfirmation"
              type="info"
              size="small"
              link
              @click="openPreview(row)"
            >预览/编辑</el-button>
            <!-- 确认写入 -->
            <el-button
              v-if="row.parsedId && row.status === 'success' && !row.needsConfirmation"
              type="primary"
              size="small"
              link
              :loading="row.confirming"
              @click="confirmSingle(row)"
            >确认写入</el-button>
            <el-text v-if="row.status === 'confirmed'" type="success" size="small">
              ✓ 已写入
            </el-text>
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

    <!-- 预览/编辑/确认对话框 -->
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
          v-if="previewRow?.status === 'success'"
          type="primary"
          :loading="previewRow?.confirming"
          @click="confirmFromPreview"
        >确认写入</el-button>
      </template>
    </el-dialog>

    <!-- 表单类型选择对话框（低置信度时） -->
    <el-dialog
      v-model="typeSelectorVisible"
      title="请选择表单类型"
      width="480px"
      destroy-on-close
    >
      <div v-if="typeSelectorRow" class="type-selector">
        <el-alert
          type="warning"
          :closable="false"
          class="mb-16px"
        >
          <template #title>
            系统未能准确识别「{{ typeSelectorRow.fileName }}」的类型
            <span v-if="typeSelectorRow.confidence != null">
              （当前置信度 {{ Math.round(typeSelectorRow.confidence * 100) }}%，低于 70%）
            </span>
            ，请手工选择正确的表单类型后重新解析。
          </template>
        </el-alert>
        <div v-if="typeSelectorRow.matchedHeaders?.length" class="mb-12px">
          <span class="text-gray-500 text-sm">已命中表头：</span>
          <el-tag
            v-for="h in typeSelectorRow.matchedHeaders"
            :key="h"
            size="small"
            class="ml-4px"
          >{{ h }}</el-tag>
        </div>
        <el-form label-width="80px">
          <el-form-item label="表单类型">
            <el-select v-model="selectedFormType" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="opt in FORM_TYPE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <div v-if="typeSelectorRow.candidateTypes?.length" class="candidates-list">
          <div class="text-gray-500 text-sm mb-8px">系统候选排名：</div>
          <div
            v-for="c in typeSelectorRow.candidateTypes.slice(0, 4)"
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
import { Loading }   from '@element-plus/icons-vue'
import type { UploadFile, UploadFiles } from 'element-plus'
import request from '@/config/axios'
import { confirmWrite, getParsedData } from '@/api/jijian/import'
import PageShell from '../components/PageShell.vue'
import ParsedDataPanel from '../components/ParsedDataPanel.vue'
import type { ParsedData } from '../types'

// ── 表单类型常量 ────────────────────────────────────────────────────────────

// 值必须与后端 FormTypeConstants 字符串常量完全一致
const FORM_TYPE_OPTIONS = [
  { value: '考勤日报', label: '考勤日报' },
  { value: '疗休养假', label: '疗休养假' },
  { value: '事假记录', label: '事假记录' },
  { value: '调休记录', label: '调休记录' },
  { value: '出差记录', label: '出差记录' },
  { value: '房产信息', label: '房产信息' },
  { value: '租赁人员', label: '租赁人员' },
  { value: '租赁合同', label: '租赁合同' },
  { value: '食堂供应', label: '食堂供应' },
]

// ── 数据类型 ─────────────────────────────────────────────────────────────────

interface CandidateType { formType: string; displayName: string; confidence: number }

interface FileResult {
  fileName: string
  file: File | null      // 保留原始 File 对象供 reparse 使用
  loading: boolean
  status: string
  formType: string
  formTypeName?: string
  confidence?: number
  matchedHeaders?: string[]
  needsConfirmation: boolean
  candidateTypes?: CandidateType[]
  totalRows?: number
  sheetName?: string     // Excel 当前使用的 sheet 名
  sheetCount?: number    // Excel sheet 总数，>1 时提示用户
  errorMsg?: string
  parsedId?: number
  importRecordId?: number
  businessTable?: string
  businessIds?: string
  confirming: boolean
}

// ── 状态 ──────────────────────────────────────────────────────────────────────

const uploading     = ref(false)
const confirmingAll = ref(false)
const fileResults   = ref<FileResult[]>([])
let uploadTimer: ReturnType<typeof setTimeout> | undefined

// ── 计算属性 ──────────────────────────────────────────────────────────────────

const canConfirmAll = computed(() =>
  fileResults.value.some(r => r.status === 'success' && !r.loading && !r.confirming && !r.needsConfirmation)
)
const pendingCount = computed(() =>
  fileResults.value.filter(r => r.status === 'success' && !r.loading && !r.needsConfirmation).length
)

// ── 上传 ──────────────────────────────────────────────────────────────────────

const handleFileChange = (_file: UploadFile, fileList: UploadFiles) => {
  if (uploadTimer) clearTimeout(uploadTimer)
  uploadTimer = setTimeout(() => {
    const files = fileList
      .map(f => f.raw)
      .filter((f): f is File => Boolean(f))
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
    row.parsedId = result.parsedDataId
    row.status = result.parseStatus || 'failed'
    row.errorMsg = result.errorMsg || ''
    row.formType = result.detectedFormType || ''
    row.formTypeName = result.detectedFormName || ''
    row.confidence = result.confidence ?? 0
    row.matchedHeaders = result.matchedHeaders || []
    row.needsConfirmation = !!result.needsConfirmation
    row.candidateTypes = result.candidateTypes || []
    row.sheetName = result.sheetName || ''
    row.sheetCount = result.sheetCount ?? 1

    // 从 parsedJson 读取行数
    if (row.parsedId) {
      try {
        const pd = await getParsedData(row.importRecordId as number)
        const pj = JSON.parse(pd.parsedJson || '{}')
        row.totalRows = typeof pj.totalRows === 'number' ? pj.totalRows : pj.rows?.length
      } catch { /* ignore */ }
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
  row.confirming = true
  try {
    const result = await confirmWrite(row.parsedId)
    row.status        = 'confirmed'
    row.businessTable = result.businessTable || row.formType
    row.businessIds   = JSON.stringify(result.confirmedIds)
    ElMessage.success(`${row.formTypeName || row.formType} 写入 ${result.confirmedCount} 条`)
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '确认写入失败')
  } finally {
    row.confirming = false
  }
}

async function confirmAll() {
  confirmingAll.value = true
  const pending = fileResults.value.filter(r => r.status === 'success' && !r.loading && !r.needsConfirmation)
  await Promise.all(pending.map(r => confirmSingle(r)))
  confirmingAll.value = false
  ElMessage.success(`全部确认写入完成（${pending.length} 个）`)
}

// ── 预览/编辑 ─────────────────────────────────────────────────────────────────

const previewVisible    = ref(false)
const previewRow        = ref<FileResult | null>(null)
const previewParsedData = ref<ParsedData | null>(null)

async function openPreview(row: FileResult) {
  if (!row.parsedId) return
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

// ── 表单类型选择（低置信度） ──────────────────────────────────────────────────

const typeSelectorVisible = ref(false)
const typeSelectorRow     = ref<FileResult | null>(null)
const selectedFormType    = ref('')
const reparsingLoading    = ref(false)

function openTypeSelector(row: FileResult) {
  typeSelectorRow.value = row
  selectedFormType.value = row.formType || ''
  typeSelectorVisible.value = true
}

async function confirmTypeSelection() {
  const row = typeSelectorRow.value
  if (!row || !selectedFormType.value || !row.file || !row.importRecordId) return
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
    row.parsedId = result.parsedDataId
    row.formType = result.detectedFormType || selectedFormType.value
    row.formTypeName = FORM_TYPE_OPTIONS.find(o => o.value === selectedFormType.value)?.label || selectedFormType.value
    row.confidence = 1.0
    row.needsConfirmation = false
    row.status = result.parseStatus || 'success'
    row.errorMsg = result.errorMsg || ''
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
.ml-8px       { margin-left: 8px; }
.mb-8px       { margin-bottom: 8px; }
.mb-12px      { margin-bottom: 12px; }
.mb-16px      { margin-bottom: 16px; }
.mt-8px       { margin-top: 8px; }
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
.candidate-item:hover { background: #ecf5ff; border-color: #b3d8ff; }
.candidate-item.active { background: #ecf5ff; border-color: #409eff; }
.candidate-bar { width: 160px; }
.mt-2px       { margin-top: 2px; }
.text-center { text-align: center; }
.text-gray-400 { color: #9ca3af; }
.text-gray-500 { color: #6b7280; }
.text-sm { font-size: 12px; }
</style>
