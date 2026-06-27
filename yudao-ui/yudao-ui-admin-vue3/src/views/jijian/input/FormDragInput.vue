<template>
  <PageShell :title="formTitle" :description="`上传 ${formTitle} 文件，解析后预览并确认写入正式表。`">

    <!-- ── 上传区域 ── -->
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
      <div class="el-upload__text">拖拽文件到此处，或点击上传</div>
      <template #tip>
        <div class="el-upload__tip">
          当前录入类型：<strong>{{ formTitle }}</strong>
          &nbsp;·&nbsp;支持 Excel(.xls/.xlsx)、CSV、图片(JPG/PNG)、PDF
        </div>
      </template>
    </el-upload>

    <!-- ── 文件列表 ── -->
    <div v-if="fileResults.length > 0" class="mt-16px">
      <el-table
        :data="fileResults"
        border
        size="small"
        highlight-current-row
        @current-change="handleRowSelect"
      >
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column label="解析状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.loading" type="warning" size="small">
              <el-icon class="is-loading"><Loading /></el-icon> 解析中
            </el-tag>
            <el-tag v-else-if="row.status === 'confirmed'" type="success" size="small">已确认</el-tag>
            <el-tag v-else-if="row.status === 'success'" type="success" size="small">解析成功</el-tag>
            <el-tag v-else-if="row.status === 'failed'" type="danger" size="small">失败</el-tag>
            <el-tag v-else type="info" size="small">-</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="行数" width="70">
          <template #default="{ row }">{{ row.totalRows ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="写入状态" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.businessTable" type="success" size="small">{{ row.businessTable }}</el-tag>
            <span v-else-if="row.errorMsg" class="error-text">{{ row.errorMsg }}</span>
            <span v-else-if="row.status === 'success'" class="hint-text">点击此行查看预览</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- ── 预览/编辑/确认区域 ── -->
    <div v-if="selectedParsedData" class="mt-16px">
      <el-divider content-position="left">
        <el-text type="primary">{{ selectedParsedData.fileName || '解析结果预览' }}</el-text>
      </el-divider>
      <ParsedDataPanel
        :parsed-data="selectedParsedData"
        @confirmed="handleConfirmed"
      />
    </div>

    <!-- ── 无数据提示 ── -->
    <el-empty
      v-else-if="fileResults.length === 0"
      description="暂无识别结果，请拖拽文件上传"
      :image-size="80"
      class="mt-24px"
    />

  </PageShell>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import type { UploadFile, UploadFiles } from 'element-plus'
import { getParsedData } from '@/api/jijian/import'
import type { ImportRecordVO, ParsedDataVO } from '@/api/jijian/import'
import PageShell from '../components/PageShell.vue'
import ParsedDataPanel from '../components/ParsedDataPanel.vue'
import type { ParsedData } from '../types'

interface Props {
  formTitle: string
  uploadFn: (file: File) => Promise<ImportRecordVO>
}

const props = defineProps<Props>()

interface FileResult {
  fileName: string
  loading: boolean
  status: string
  totalRows?: number
  errorMsg?: string
  parsedId?: number
  businessTable?: string
  businessIds?: string
  parsedData?: ParsedData
}

const uploading = ref(false)
const fileResults = ref<FileResult[]>([])
const selectedParsedData = ref<ParsedData | null>(null)
let uploadTimer: ReturnType<typeof setTimeout> | undefined

function handleRowSelect(row: FileResult | null) {
  if (row?.parsedData) {
    selectedParsedData.value = row.parsedData
  }
}

const handleFileChange = (_file: UploadFile, fileList: UploadFiles) => {
  if (uploadTimer) clearTimeout(uploadTimer)
  uploadTimer = setTimeout(() => {
    const files = fileList
      .map((f) => f.raw)
      .filter((f) => f != null) as File[]
    const pendingFiles = files
      .filter((f) => !fileResults.value.some((r) => r.fileName === f.name && !r.loading))
    if (pendingFiles.length === 0) return
    uploadFiles(pendingFiles)
  }, 200)
}

async function uploadFiles(files: File[]) {
  uploading.value = true
  const newRows: FileResult[] = files.map((f) => ({
    fileName: f.name, loading: true, status: '', errorMsg: ''
  }))
  fileResults.value = [...fileResults.value, ...newRows]
  const CONCURRENCY = 3
  for (let i = 0; i < files.length; i += CONCURRENCY) {
    await Promise.all(files.slice(i, i + CONCURRENCY).map((f) => uploadSingle(f)))
  }
  uploading.value = false
}

async function uploadSingle(file: File) {
  const row = fileResults.value.find((r) => r.fileName === file.name && r.loading)
  if (!row) return
  try {
    const record = await props.uploadFn(file)
    const pd: ParsedDataVO = await getParsedData(record.id)
    row.status = pd.status
    row.errorMsg = pd.errorMsg || ''
    row.parsedId = pd.id as number
    try {
      const pj = JSON.parse(pd.parsedJson || '{}')
      row.totalRows = typeof pj.totalRows === 'number' ? pj.totalRows : pj.rows?.length
    } catch { /* ignore */ }
    if (pd.businessTable) {
      row.businessTable = pd.businessTable
      row.businessIds = pd.businessIds
    }
    // 存储完整 parsed data 供预览
    row.parsedData = pd as unknown as ParsedData
    // 自动选中最新上传的文件
    if (pd.status === 'success') {
      selectedParsedData.value = pd as unknown as ParsedData
    }
  } catch (err: unknown) {
    row.status = 'failed'
    row.errorMsg = err instanceof Error ? err.message : '上传失败'
  } finally {
    row.loading = false
  }
}

function handleConfirmed(result: { formType: string; businessTable?: string; confirmedIds: number[]; confirmedCount: number }) {
  // 同步文件列表中对应行的状态
  const row = fileResults.value.find((r) => r.parsedData === selectedParsedData.value)
  if (row) {
    row.status = 'confirmed'
    row.businessTable = result.businessTable || result.formType
    row.businessIds = JSON.stringify(result.confirmedIds)
    if (row.parsedData) {
      row.parsedData = { ...row.parsedData, confirmStatus: 'confirmed', businessTable: result.businessTable, businessIds: row.businessIds }
      selectedParsedData.value = row.parsedData
    }
  }
  ElMessage.success(`写入成功：${result.confirmedCount} 条记录 → ${result.businessTable || result.formType}`)
}
</script>

<style scoped>
.upload-area { width: 100%; }
.upload-icon { font-size: 48px; color: var(--el-color-primary); }
.mt-16px { margin-top: 16px; }
.mt-24px { margin-top: 24px; }
.error-text { color: var(--el-color-danger); font-size: 12px; }
.hint-text { color: var(--el-color-info); font-size: 12px; }
</style>
