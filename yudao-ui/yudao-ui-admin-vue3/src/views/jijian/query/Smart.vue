<template>
  <PageShell
    title="智能查询"
    description="输入自然语言问题，后续统一检索、问答、统计和分析已结构化存储的数据。"
  >
    <el-input
      v-model="question"
      type="textarea"
      :rows="4"
      placeholder="请输入自然语言问题，例如：查询某人员名下房产信息"
    />
    <div class="actions">
      <el-button type="primary" :disabled="!question" @click="handleSearch">模拟查询</el-button>
      <el-button @click="question = ''">清空</el-button>
    </div>
    <div class="examples">
      <el-tag v-for="item in smartQueryExamples" :key="item" @click="useExample(item)">
        {{ item }}
      </el-tag>
    </div>

    <el-card shadow="never" class="mt-16px">
      <template #header>查询结果</template>
      <el-empty
        v-if="!queryResult"
        description="查询接口接入后，将在此展示问答结果、统计图表或明细列表"
      />
      <div v-else class="query-result">
        <p>{{ queryResult.answer }}</p>
        <el-descriptions :column="1" border>
          <el-descriptions-item
            v-for="record in queryResult.records"
            :key="record.label"
            :label="record.label"
          >
            {{ record.value }}
          </el-descriptions-item>
          <el-descriptions-item label="涉及数据类型">
            <el-tag
              v-for="formType in queryResult.relatedFormTypes"
              :key="formType"
              class="mr-8px"
              type="info"
            >
              {{ formType }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import PageShell from '../components/PageShell.vue'
import { createMockQueryResult, smartQueryExamples } from '../mock'
import type { SmartQueryResult } from '../types'

const question = ref('')
const queryResult = ref<SmartQueryResult>()

const useExample = (item: string) => {
  question.value = item
  queryResult.value = createMockQueryResult(item)
}

const handleSearch = () => {
  queryResult.value = createMockQueryResult(question.value)
}
</script>

<style scoped>
.actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.examples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.examples :deep(.el-tag) {
  cursor: pointer;
}

.query-result p {
  margin: 0 0 16px;
  color: var(--el-text-color-primary);
}
</style>
