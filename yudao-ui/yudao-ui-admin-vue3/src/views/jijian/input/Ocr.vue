<template>
  <PageShell
    title="图片/文件识别录入"
    description="支持 Excel、CSV、TXT 直接解析；图片/PDF 通过本地 PaddleOCR 服务识别（需提前启动）。"
  >
    <!-- PaddleOCR 服务说明 -->
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-16px"
    >
      <template #title>
        <span>
          图片/PDF 识别需要本地 PaddleOCR 服务已启动。
          <el-text type="primary" size="small">
            启动命令：cd tools/paddleocr-service &amp;&amp; .venv\Scripts\activate &amp;&amp; uvicorn app:app --host 127.0.0.1 --port 8868
          </el-text>
        </span>
      </template>
    </el-alert>

    <!-- 文件上传区域 -->
    <el-upload
      class="upload-area"
      drag
      :auto-upload="false"
      :show-file-list="false"
      :disabled="loading"
      :on-change="handleUploadChange"
      :accept="ACCEPT_TYPES"
    >
      <Icon icon="ep:upload-filled" class="upload-icon" />
      <div class="el-upload__text">拖拽文件到此处，或点击选择文件</div>
      <template #tip>
        <div class="el-upload__tip">
          支持：<strong>Excel (.xls .xlsx)</strong>、<strong>CSV</strong>、<strong>TXT</strong>、
          图片 (.jpg .jpeg .png)、PDF
          <el-tag type="warning" size="small" class="ml-4px">图片/PDF 需 PaddleOCR 服务</el-tag>
        </div>
      </template>
    </el-upload>

    <!-- 上传进行中 -->
    <el-alert
      v-if="loading && isOcrFile"
      class="mt-12px"
      type="info"
      show-icon
      :closable="false"
      title="PaddleOCR 识别中，请稍候…"
      description="本地 OCR 识别需要 5–60 秒，首次启动模型耗时较长，请耐心等待。"
    />

    <!-- OCR 未配置/未启动提示 -->
    <el-alert
      v-if="ocrErrorMsg"
      class="mt-12px"
      type="error"
      show-icon
      :closable="true"
      @close="ocrErrorMsg = ''"
    >
      <template #title>{{ ocrErrorMsg }}</template>
    </el-alert>

    <!-- 最近导入记录 -->
    <ImportRecordTable
      v-if="records.length > 0"
      class="mt-16px"
      v-loading="loading"
      :records="records"
      :show-actions="true"
      @view-parsed="handleViewParsed"
    />

    <!-- 解析结果预览 -->
    <ParsedDataPanel :parsed-data="parsedData" @confirmed="handleConfirmed" />
  </PageShell>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { getParsedData, uploadExcelImportFile, uploadOcrImportFile } from '@/api/jijian/import'
import PageShell from '../components/PageShell.vue'
import ImportRecordTable from '../components/ImportRecordTable.vue'
import ParsedDataPanel from '../components/ParsedDataPanel.vue'
import type { ImportRecord, ParsedData } from '../types'

const ACCEPT_TYPES = '.xls,.xlsx,.csv,.txt,.jpg,.jpeg,.png,.pdf'
const EXCEL_EXTS  = ['.xls', '.xlsx', '.csv', '.txt']
const OCR_EXTS    = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.pdf']

const loading    = ref(false)
const isOcrFile  = ref(false)
const ocrErrorMsg = ref('')
const records    = ref<ImportRecord[]>([])
const parsedData = ref<ParsedData | null>(null)

function getExt(filename: string): string {
  const dot = filename.lastIndexOf('.')
  return dot >= 0 ? filename.substring(dot).toLowerCase() : ''
}

const handleUploadChange = async (file: UploadFile) => {
  if (!file.raw) { ElMessage.warning('请先选择要上传的文件'); return }
  loading.value    = true
  isOcrFile.value  = OCR_EXTS.includes(getExt(file.raw.name))
  ocrErrorMsg.value = ''
  parsedData.value = null

  try {
    let record: ImportRecord
    if (isOcrFile.value) {
      record = await uploadOcrImportFile(file.raw)
    } else {
      record = await uploadExcelImportFile(file.raw)
    }
    records.value = [record, ...records.value]

    const pd = await getParsedData(record.id) as ParsedData
    parsedData.value = pd

    if (pd.status === 'failed') {
      const msg = pd.errorMsg || '文件解析失败'
      // 判断是否是 OCR 服务未启动
      if (msg.includes('未启动') || msg.includes('PaddleOCR') || msg.includes('OCR')) {
        ocrErrorMsg.value = msg
      } else {
        ElMessage.warning(msg)
      }
    } else {
      const n = getTotalRows(pd)
      const notice = pd.parsedJson ? getOcrNotice(pd.parsedJson) : ''
      ElMessage.success(`已识别为「${pd.formType || '未知类型'}」，共 ${n} 行${notice ? ' — ' + notice : ''}`)
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '上传失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

function getTotalRows(pd: ParsedData): number {
  try {
    const obj = JSON.parse(pd.parsedJson || '{}')
    return typeof obj.totalRows === 'number' ? obj.totalRows : (obj.rows?.length ?? 0)
  } catch { return 0 }
}

function getOcrNotice(parsedJson: string): string {
  try {
    return JSON.parse(parsedJson)?.ocrNotice || ''
  } catch { return '' }
}

function handleViewParsed(record: ImportRecord) {
  loading.value = true
  getParsedData(record.id)
    .then((pd) => { parsedData.value = pd as ParsedData })
    .catch(() => ElMessage.error('获取解析结果失败'))
    .finally(() => { loading.value = false })
}

function handleConfirmed(result: { formType: string; confirmedIds: number[]; confirmedCount: number }) {
  ElMessage.success(`${result.formType} 已写入 ${result.confirmedCount} 条记录`)
}
</script>

<style scoped>
.upload-area { width: 100%; }
.upload-icon { font-size: 48px; color: var(--el-color-primary); }
.mb-16px { margin-bottom: 16px; }
.mt-12px { margin-top: 12px; }
.mt-16px { margin-top: 16px; }
.ml-4px  { margin-left: 4px; }
</style>
