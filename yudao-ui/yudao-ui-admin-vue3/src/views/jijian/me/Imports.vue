<template>
  <PageShell
    title="最近导入记录"
    description="展示最近通过 OCR、Excel、拖拽方式导入并由后端保存的数据记录。"
  >
    <el-button class="mb-12px" type="primary" @click="loadRecords">刷新</el-button>
    <ImportRecordTable
      v-loading="loading"
      :records="records"
      show-actions
      @view-parsed="openParsedDialog"
      @delete-business-data="handleDeleteBusinessData"
    />

    <el-dialog v-model="parsedDialogVisible" title="解析结果" width="760px">
      <ParsedDataPanel :parsed-data="parsedData" />
    </el-dialog>
  </PageShell>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { getImportRecordList, getParsedData, deleteImportRecord } from '@/api/jijian/import'
import PageShell from '../components/PageShell.vue'
import ImportRecordTable from '../components/ImportRecordTable.vue'
import ParsedDataPanel from '../components/ParsedDataPanel.vue'
import type { ImportRecord, ParsedData } from '../types'

const loading = ref(false)
const records = ref<ImportRecord[]>([])
const parsedDialogVisible = ref(false)
const parsedData = ref<ParsedData | null>(null)

const getErrorMessage = (error: unknown) => {
  if (typeof error === 'string') return error
  if (error instanceof Error && error.message) return error.message
  return '导入记录加载失败'
}

const loadRecords = async () => {
  loading.value = true
  try {
    records.value = await getImportRecordList()
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

const openParsedDialog = async (record: ImportRecord) => {
  try {
    parsedData.value = await getParsedData(record.id)
    parsedDialogVisible.value = true
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  }
}

const handleDeleteBusinessData = async (record: ImportRecord) => {
  try {
    await ElMessageBox.confirm(
      `确认删除导入批次「${record.fileName}」及其解析/业务数据？\n` +
      `（仅删除通过该批次导入的数据，不影响其他批次或手工录入数据）`,
      '二次确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return // 用户取消
  }

  try {
    const result = await deleteImportRecord(record.id)
    ElMessage.success(result.message || `已删除 ${result.deletedRows} 条数据`)
    await loadRecords()
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  }
}

onMounted(() => {
  loadRecords()
})
</script>
