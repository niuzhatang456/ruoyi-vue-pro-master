<template>
  <div v-if="parsedData" class="parsed-panel">
    <el-descriptions :column="2" border>
      <el-descriptions-item label="识别类型">{{ parsedData.formType }}</el-descriptions-item>
      <el-descriptions-item label="解析状态">
        <el-tag :type="parsedData.status === 'failed' ? 'danger' : 'success'">
          {{ parsedData.status }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="文件名">{{ parsedData.fileName }}</el-descriptions-item>
      <el-descriptions-item label="置信度">{{ parsedData.confidence ?? '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="parsedData.errorMsg" label="失败原因" :span="2">
        {{ parsedData.errorMsg }}
      </el-descriptions-item>
    </el-descriptions>

    <el-tabs class="mt-12px">
      <el-tab-pane label="原始文本">
        <el-input :model-value="parsedData.rawText" type="textarea" :rows="8" readonly />
      </el-tab-pane>
      <el-tab-pane label="结构化 JSON">
        <pre class="json-view">{{ prettyJson }}</pre>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import type { ParsedData } from '../types'

const props = defineProps<{
  parsedData?: ParsedData | null
}>()

const prettyJson = computed(() => {
  if (!props.parsedData?.parsedJson) return '{}'
  try {
    return JSON.stringify(JSON.parse(props.parsedData.parsedJson), null, 2)
  } catch {
    return props.parsedData.parsedJson
  }
})
</script>

<style scoped>
.parsed-panel {
  margin-top: 16px;
}

.json-view {
  max-height: 320px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
