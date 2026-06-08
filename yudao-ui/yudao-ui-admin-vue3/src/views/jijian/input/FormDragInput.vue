<template>
  <PageShell :title="formTitle" :description="`上传 ${formTitle} 文件，解析后确认写入正式表。`">
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

    <div v-if="fileResults.length > 0" class="mt-16px">
      <el-table :data="fileResults" border size="small">
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
        <el-table-column label="业务表" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.businessTable" type="success" size="small">{{ row.businessTable }}</el-tag>
            <span v-else-if="row.errorMsg" class="error-text">{{ row.errorMsg }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button
              v-if="row.parsedId && row.status === 'success'"
              type="primary"
              size="small"
              link
              :loading="row.confirming"
              @click="confirmSingle(row)"
            >确认写入</el-button>
            <el-text v-else-if="row.status === 'confirmed'" type="success" size="small">
              已写入 {{ row.businessIds ? JSON.parse(row.businessIds).join(',') : '' }}
            </el-text>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="canConfirmAll" class="mt-12px">
      <el-button type="primary" :loading="confirmingAll" @click="confirmAll">
        全部确认写入（{{ pendingCount }} 个）
      </el-button>
    </div>
  </PageShell>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import type { UploadFile, UploadFiles } from 'element-plus'
import { confirmWrite, getParsedData } from '@/api/jijian/import'
import type { ImportRecordVO } from '@/api/jijian/import'
import PageShell from '../components/PageShell.vue'

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
  confirming: boolean
}

const uploading = ref(false)
const confirmingAll = ref(false)
const fileResults = ref<FileResult[]>([])
let uploadTimer: ReturnType<typeof setTimeout> | undefined

const canConfirmAll = computed(() =>
  fileResults.value.some((r) => r.status === 'success' && !r.loading && !r.confirming)
)
const pendingCount = computed(() =>
  fileResults.value.filter((r) => r.status === 'success' && !r.loading).length
)

const handleFileChange = (_file: UploadFile, fileList: UploadFiles) => {
  if (uploadTimer) clearTimeout(uploadTimer)
  uploadTimer = setTimeout(() => {
    const files = fileList
      .map((f) => f.raw)
      .filter((f): f is File => Boolean(f))
      .filter((f) => !fileResults.value.some((r) => r.fileName === f.name))
    if (files.length === 0) return
    uploadFiles(files)
  }, 200)
}

async function uploadFiles(files: File[]) {
  uploading.value = true
  const newRows: FileResult[] = files.map((f) => ({
    fileName: f.name, loading: true, status: '', errorMsg: '', confirming: false
  }))
  fileResults.value = [...fileResults.value, ...newRows]
  const CONCURRENCY = 3
  for (let i = 0; i < files.length; i += CONCURRENCY) {
    await Promise.all(files.slice(i, i + CONCURRENCY).map((f) => uploadSingle(f)))
  }
  uploading.value = false
}

async function uploadSingle(file: File) {
  const row = fileResults.value.find((r) => r.fileName === file.name)
  if (!row) return
  try {
    const record = await props.uploadFn(file)
    const pd = await getParsedData(record.id)
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
  } catch (err: unknown) {
    row.status = 'failed'
    row.errorMsg = err instanceof Error ? err.message : '上传失败'
  } finally {
    row.loading = false
  }
}

async function confirmSingle(row: FileResult) {
  if (!row.parsedId) return
  row.confirming = true
  try {
    const result = await confirmWrite(row.parsedId)
    row.status = 'confirmed'
    row.businessTable = result.businessTable || props.formTitle
    row.businessIds = JSON.stringify(result.confirmedIds)
    ElMessage.success(`${props.formTitle} 写入 ${result.confirmedCount} 条`)
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '确认写入失败')
  } finally {
    row.confirming = false
  }
}

async function confirmAll() {
  confirmingAll.value = true
  const pending = fileResults.value.filter((r) => r.status === 'success' && !r.loading)
  await Promise.all(pending.map((r) => confirmSingle(r)))
  confirmingAll.value = false
  ElMessage.success(`全部确认写入完成（${pending.length} 个）`)
}
</script>

<style scoped>
.upload-area { width: 100%; }
.upload-icon { font-size: 48px; color: var(--el-color-primary); }
.mt-16px { margin-top: 16px; }
.mt-12px { margin-top: 12px; }
.error-text { color: var(--el-color-danger); font-size: 12px; }
</style>
