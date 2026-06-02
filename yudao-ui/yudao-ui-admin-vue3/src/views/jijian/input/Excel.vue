<template>
  <PageShell
    title="Excel录入"
    description="上传 Excel 文件，后端自动识别业务类型并展示预览，支持人工校正后确认写入。"
  >
    <el-upload
      class="upload-area"
      drag
      accept=".xls,.xlsx"
      :auto-upload="false"
      :show-file-list="false"
      :disabled="loading"
      :on-change="handleUploadChange"
    >
      <Icon icon="ep:document" class="upload-icon" />
      <div class="el-upload__text">拖拽 Excel 到此处，或点击选择文件</div>
      <template #tip>
        <div class="el-upload__tip">
          支持 .xls 和 .xlsx 格式，系统将自动识别业务类型并展示数据预览。
        </div>
      </template>
    </el-upload>

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
import { getParsedData, uploadExcelImportFile } from '@/api/jijian/import'
import PageShell from '../components/PageShell.vue'
import ImportRecordTable from '../components/ImportRecordTable.vue'
import ParsedDataPanel from '../components/ParsedDataPanel.vue'
import type { ImportRecord, ParsedData } from '../types'

const loading = ref(false)
const records = ref<ImportRecord[]>([])
const parsedData = ref<ParsedData | null>(null)

const handleUploadChange = async (file: UploadFile) => {
  if (!file.raw) {
    ElMessage.warning('请先选择要上传的 Excel 文件')
    return
  }
  loading.value = true
  parsedData.value = null
  try {
    const record = await uploadExcelImportFile(file.raw)
    records.value = [record, ...records.value]
    const pd = await getParsedData(record.id)
    parsedData.value = pd as ParsedData
    if (pd.status === 'failed') {
      ElMessage.warning(pd.errorMsg || '文件解析失败，请检查格式')
    } else {
      const n = getTotalRows(pd)
      ElMessage.success(`已识别为「${pd.formType || '未知'}」，共 ${n} 行`)
    }
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function getTotalRows(pd: any): number {
  try {
    const obj = JSON.parse(pd.parsedJson || '{}')
    return typeof obj.totalRows === 'number' ? obj.totalRows : (obj.rows?.length ?? 0)
  } catch { return 0 }
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
.mt-16px { margin-top: 16px; }
</style>
