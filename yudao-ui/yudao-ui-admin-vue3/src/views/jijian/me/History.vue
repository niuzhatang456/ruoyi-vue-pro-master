<template>
  <PageShell title="历史查询对话" description="查看当前账号的智能查询历史、结果快照和数据来源。">
    <el-form inline @submit.prevent>
      <el-form-item label="用户问题">
        <el-input v-model="queryParams.question" clearable placeholder="请输入问题关键字" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="question" label="用户问题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="answer" label="AI 回答" min-width="280" show-overflow-tooltip />
      <el-table-column prop="modelName" label="查询模式" width="150" />
      <el-table-column prop="success" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="查询时间" width="180" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button
            v-hasPermi="['jijian:query-history:delete']"
            link
            type="danger"
            @click="handleDelete(row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />

    <el-dialog v-model="detailVisible" title="查询历史详情" width="900px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="用户问题" :span="2">{{ detail.question }}</el-descriptions-item>
        <el-descriptions-item label="AI 回答" :span="2">
          <div class="pre-wrap">{{ detail.answer || detail.errorMessage || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="查询时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="数据类型">{{ detail.formType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数据来源" :span="2">
          {{ detailMeta.dataSource || '本地数据库只读查询' }}
        </el-descriptions-item>
        <el-descriptions-item label="涉及表" :span="2">
          {{ detailMeta.tablesUsed?.join('、') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="记录数" :span="2">
          {{ rowCountText }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table v-if="detailRows.length" :data="detailRows" border class="mt-16px" max-height="360">
        <el-table-column
          v-for="column in detailColumns"
          :key="column"
          :prop="column"
          :label="column"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <el-empty v-else description="本次查询没有可展示的明细数据" />
    </el-dialog>
  </PageShell>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import PageShell from '../components/PageShell.vue'
import { QueryHistoryApi, type QueryHistoryVO } from '@/api/jijian/queryHistory'

const loading = ref(false)
const list = ref<QueryHistoryVO[]>([])
const total = ref(0)
const queryParams = reactive({ pageNo: 1, pageSize: 10, question: '' })
const detailVisible = ref(false)
const detail = ref<QueryHistoryVO>()
const detailRows = ref<Record<string, any>[]>([])
const detailColumns = computed(() => Object.keys(detailRows.value[0] || {}))
const detailMeta = computed<Record<string, any>>(() => parseJson(detail.value?.databaseContextMetaJson))
const rowCountText = computed(() => {
  const counts = detailMeta.value.rowCounts
  if (!counts) return detailRows.value.length ? String(detailRows.value.length) : '-'
  return Object.entries(counts).map(([key, value]) => `${key}: ${value}`).join('，')
})

const parseJson = (value?: string) => {
  if (!value) return {}
  try { return JSON.parse(value) } catch { return {} }
}

const extractRows = (value?: string): Record<string, any>[] => {
  const result = parseJson(value)
  if (Array.isArray(result?.pageResult?.list)) return result.pageResult.list
  const table = Array.isArray(result?.tables) ? result.tables.find((item: any) => item?.rows?.length) : null
  if (table) return table.rows
  if (Array.isArray(result?.data)) return result.data
  return []
}

const getList = async () => {
  loading.value = true
  try {
    const data = await QueryHistoryApi.getPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const handleReset = () => {
  queryParams.question = ''
  handleQuery()
}

const openDetail = async (id: number) => {
  detail.value = await QueryHistoryApi.get(id)
  detailRows.value = extractRows(detail.value.queryResultJson)
  detailVisible.value = true
}

const handleDelete = async (id: number) => {
  await ElMessageBox.confirm('确认删除这条查询历史吗？', '删除确认', { type: 'warning' })
  await QueryHistoryApi.delete(id)
  ElMessage.success('查询历史已删除')
  await getList()
}

onMounted(getList)
</script>

<style scoped>
.pre-wrap { white-space: pre-wrap; }
.mt-16px { margin-top: 16px; }
</style>
