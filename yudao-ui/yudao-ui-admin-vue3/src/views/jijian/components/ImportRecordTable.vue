<template>
  <el-table :data="records" border>
    <el-table-column prop="id" label="记录编号" width="90" />
    <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
    <el-table-column prop="sourceType" label="来源" width="90">
      <template #default="{ row }">
        <el-tag size="small">{{ sourceText[row.sourceType] ?? row.sourceType }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="detectedFormType" label="识别类型" min-width="100" />
    <el-table-column prop="status" label="状态" width="100">
      <template #default="{ row }">
        <el-tag :type="statusType[row.status] ?? ''" size="small">
          {{ statusText[row.status] ?? row.status }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="createdAt" label="导入时间" width="165" />
    <el-table-column v-if="showActions" label="操作" width="120" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link @click="emit('view-parsed', row)">查看解析</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import type { ImportRecord, ImportSourceType, ImportStatus } from '../types'

withDefaults(defineProps<{
  records: ImportRecord[]
  showActions?: boolean
}>(), { showActions: false })

const emit = defineEmits<{
  (event: 'view-parsed', record: ImportRecord): void
}>()

const sourceText: Record<ImportSourceType, string> = {
  ocr:   'OCR识别',
  excel: 'Excel',
  drag:  '拖拽'
}

const statusText: Record<ImportStatus, string> = {
  pending:    '待处理',
  processing: '处理中',
  success:    '解析成功',
  failed:     '失败',
  confirmed:  '已确认'
}

const statusType: Record<ImportStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
  pending:    'info',
  processing: 'warning',
  success:    'success',
  failed:     'danger',
  confirmed:  'success'
}
</script>
