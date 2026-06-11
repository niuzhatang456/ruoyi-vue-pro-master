<template>
  <PageShell title="处置记录" description="查看当前账号基于智能查询结果形成的处置意见。">
    <el-form inline @submit.prevent>
      <el-form-item label="原始问题">
        <el-input v-model="queryParams.queryQuestion" clearable placeholder="请输入问题关键字" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="disposalTime" label="处置时间" width="180" />
      <el-table-column prop="disposalUserName" label="处置人" width="120" />
      <el-table-column prop="queryQuestion" label="原始问题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="queryAnswer" label="AI 简要回答" min-width="240" show-overflow-tooltip />
      <el-table-column prop="disposalOpinion" label="处置意见" min-width="220" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button
            v-hasPermi="['jijian:disposal:delete']"
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

    <el-dialog v-model="detailVisible" title="处置记录详情" width="900px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="处置人">{{ detail.disposalUserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处置时间">{{ detail.disposalTime }}</el-descriptions-item>
        <el-descriptions-item label="原始问题" :span="2">{{ detail.queryQuestion }}</el-descriptions-item>
        <el-descriptions-item label="AI 回答" :span="2">
          <div class="pre-wrap">{{ detail.queryAnswer || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="处置意见" :span="2">
          <div class="pre-wrap">{{ detail.disposalOpinion }}</div>
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
      <el-empty v-else description="该处置记录没有可展示的明细数据" />
    </el-dialog>
  </PageShell>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import PageShell from '../components/PageShell.vue'
import { DisposalRecordApi, type DisposalRecordVO } from '@/api/jijian/disposalRecord'

const loading = ref(false)
const list = ref<DisposalRecordVO[]>([])
const total = ref(0)
const queryParams = reactive({ pageNo: 1, pageSize: 10, queryQuestion: '' })
const detailVisible = ref(false)
const detail = ref<DisposalRecordVO>()
const detailRows = ref<Record<string, any>[]>([])
const detailColumns = computed(() => Object.keys(detailRows.value[0] || {}))

const extractRows = (value?: string): Record<string, any>[] => {
  if (!value) return []
  try {
    const result = JSON.parse(value)
    if (Array.isArray(result?.pageResult?.list)) return result.pageResult.list
    const table = Array.isArray(result?.tables) ? result.tables.find((item: any) => item?.rows?.length) : null
    if (table) return table.rows
    if (Array.isArray(result?.data)) return result.data
  } catch {}
  return []
}

const getList = async () => {
  loading.value = true
  try {
    const data = await DisposalRecordApi.getPage(queryParams)
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
  queryParams.queryQuestion = ''
  handleQuery()
}

const openDetail = async (id: number) => {
  detail.value = await DisposalRecordApi.get(id)
  detailRows.value = extractRows(detail.value.queryResultJson)
  detailVisible.value = true
}

const handleDelete = async (id: number) => {
  await ElMessageBox.confirm('删除后无法恢复，确认删除这条处置记录吗？', '二次确认', {
    type: 'warning',
    confirmButtonText: '确认删除'
  })
  await DisposalRecordApi.delete(id)
  ElMessage.success('处置记录已删除')
  await getList()
}

onMounted(getList)
</script>

<style scoped>
.pre-wrap { white-space: pre-wrap; }
.mt-16px { margin-top: 16px; }
</style>
