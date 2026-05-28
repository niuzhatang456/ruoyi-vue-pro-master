<template>
  <PageShell
    title="Excel录入"
    description="上传 Excel 文件，后端生成导入记录并返回自动识别类型。"
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
          Excel 录入使用 file 字段上传，成功后会写入最近导入记录。
        </div>
      </template>
    </el-upload>

    <ImportRecordTable class="mt-16px" v-loading="loading" :records="records" />
    <ParsedDataPanel :parsed-data="parsedData" />
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

const getErrorMessage = (error: unknown) => {
  if (typeof error === 'string') return error
  if (error instanceof Error && error.message) return error.message
  return '导入失败，请稍后重试'
}

const handleUploadChange = async (file: UploadFile) => {
  if (!file.raw) {
    ElMessage.warning('请先选择要上传的 Excel 文件')
    return
  }
  loading.value = true
  try {
    const record = await uploadExcelImportFile(file.raw)
    records.value = [record, ...records.value]
    parsedData.value = await getParsedData(record.id)
    ElMessage.success('导入记录已生成')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.upload-area {
  width: 100%;
}

.upload-icon {
  font-size: 48px;
  color: var(--el-color-primary);
}
</style>
