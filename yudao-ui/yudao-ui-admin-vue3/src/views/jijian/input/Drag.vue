<template>
  <PageShell
    title="拖拽录入"
    description="通过拖拽方式批量上传文件，后端逐条生成导入记录并模拟识别表单类型。"
  >
    <el-upload
      class="upload-area"
      drag
      multiple
      :auto-upload="false"
      :show-file-list="false"
      :disabled="loading"
      :on-change="handleUploadChange"
    >
      <Icon icon="ep:folder-add" class="upload-icon" />
      <div class="el-upload__text">拖拽多个文件到此处批量导入</div>
      <template #tip>
        <div class="el-upload__tip">
          批量导入记录会写入 sourceType=drag，并由系统自动生成 detectedFormType。
        </div>
      </template>
    </el-upload>

    <ImportRecordTable class="mt-16px" v-loading="loading" :records="records" />
    <el-table v-if="parsedDataList.length" class="mt-16px" :data="parsedDataList" border>
      <el-table-column prop="fileName" label="文件名" min-width="180" />
      <el-table-column prop="status" label="解析状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'failed' ? 'danger' : 'success'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="formType" label="识别类型" width="140" />
      <el-table-column label="解析结果" width="120">
        <template #default="{ row }">
          <el-tag :type="row.id ? 'success' : 'warning'">{{ row.id ? '已生成' : '未生成' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="errorMsg" label="失败原因" min-width="180" show-overflow-tooltip />
    </el-table>
  </PageShell>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadFiles } from 'element-plus'
import { getParsedData, uploadDragImportFiles } from '@/api/jijian/import'
import PageShell from '../components/PageShell.vue'
import ImportRecordTable from '../components/ImportRecordTable.vue'
import type { ImportRecord, ParsedData } from '../types'

const loading = ref(false)
const records = ref<ImportRecord[]>([])
const parsedDataList = ref<ParsedData[]>([])
let uploadTimer: ReturnType<typeof setTimeout> | undefined

const getErrorMessage = (error: unknown) => {
  if (typeof error === 'string') return error
  if (error instanceof Error && error.message) return error.message
  return '导入失败，请稍后重试'
}

const uploadFiles = async (files: File[]) => {
  if (!files.length) {
    ElMessage.warning('请先选择要上传的文件')
    return
  }
  loading.value = true
  try {
    const createdRecords = await uploadDragImportFiles(files)
    records.value = [...createdRecords, ...records.value]
    parsedDataList.value = await Promise.all(createdRecords.map((record) => getParsedData(record.id)))
    ElMessage.success('导入记录已生成')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

const handleUploadChange = (_file: UploadFile, fileList: UploadFiles) => {
  if (uploadTimer) {
    clearTimeout(uploadTimer)
  }
  uploadTimer = setTimeout(() => {
    const files = fileList.map((item) => item.raw).filter((item): item is File => Boolean(item))
    uploadFiles(files)
  }, 0)
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
