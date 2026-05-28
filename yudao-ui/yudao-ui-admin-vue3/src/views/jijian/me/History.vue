<template>
  <PageShell title="历史查询对话" description="展示当前账号历史智能查询对话，便于追溯问答过程。">
    <el-table :data="historyList" border>
      <el-table-column prop="question" label="查询内容" min-width="220" />
      <el-table-column prop="summary" label="结果摘要" min-width="260" />
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="selectedQuestion = row.question">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-alert
      v-if="selectedQuestion"
      class="mt-16px"
      type="info"
      :closable="false"
      :title="`当前查看：${selectedQuestion}`"
    />
  </PageShell>
</template>

<script setup lang="ts">
import PageShell from '../components/PageShell.vue'
import { mockHistoryList } from '../mock'

const historyList = ref(mockHistoryList)
const selectedQuestion = ref('')
</script>
