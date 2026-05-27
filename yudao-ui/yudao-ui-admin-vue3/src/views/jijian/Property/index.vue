<template>
  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="100px"
    >
      <el-form-item label="房产地址" prop="propertyAddress">
        <el-input
          v-model="queryParams.propertyAddress"
          placeholder="请输入房产地址"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>

      <el-form-item label="房产名称" prop="propertyName">
        <el-input
          v-model="queryParams.propertyName"
          placeholder="请输入房产名称"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>

      <el-form-item label="产权信息" prop="ownershipInfo">
        <el-input
          v-model="queryParams.ownershipInfo"
          placeholder="请输入产权信息"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>

      <el-form-item label="建筑时间" prop="buildingTime">
        <el-date-picker
          v-model="queryParams.buildingTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-220px"
        />
      </el-form-item>

      <el-form-item label="建筑面积" prop="area">
        <el-input
          v-model="queryParams.area"
          placeholder="请输入建筑面积"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>

      <el-form-item label="租赁情况" prop="leaseStatus">
        <el-input
          v-model="queryParams.leaseStatus"
          placeholder="请输入租赁情况"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="queryParams.remark"
          placeholder="请输入备注"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>

      <el-form-item label="创建时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-220px"
        />
      </el-form-item>

      <el-form-item>
        <el-button @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" />
          搜索
        </el-button>
        <el-button @click="resetQuery">
          <Icon icon="ep:refresh" class="mr-5px" />
          重置
        </el-button>
        <el-button
          type="primary"
          plain
          @click="openForm('create')"
          v-hasPermi="['jijian:property:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" />
          新增
        </el-button>
        <el-button
          type="success"
          plain
          @click="handleExport"
          :loading="exportLoading"
          v-hasPermi="['jijian:property:export']"
        >
          <Icon icon="ep:download" class="mr-5px" />
          导出
        </el-button>
        <el-button
          type="danger"
          plain
          :disabled="isEmpty(checkedIds)"
          @click="handleDeleteBatch"
          v-hasPermi="['jijian:property:delete']"
        >
          <Icon icon="ep:delete" class="mr-5px" />
          批量删除
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table
      row-key="id"
      v-loading="loading"
      :data="list"
      :stripe="true"
      :show-overflow-tooltip="true"
      @selection-change="handleRowCheckboxChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column label="主键编号" align="center" prop="id" />
      <el-table-column label="房产地址" align="center" prop="propertyAddress" />
      <el-table-column label="房产名称" align="center" prop="propertyName" />
      <el-table-column label="产权信息" align="center" prop="ownershipInfo" />
      <el-table-column
        label="建筑时间"
        align="center"
        prop="buildingTime"
        :formatter="dateFormatter"
        width="180px"
      />
      <el-table-column label="建筑面积" align="center" prop="area" />
      <el-table-column label="租赁情况" align="center" prop="leaseStatus" />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        :formatter="dateFormatter"
        width="180px"
      />
      <el-table-column label="操作" align="center" min-width="120px">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['jijian:property:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['jijian:property:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <PropertyForm ref="formRef" @success="getList" />
</template>

<script setup lang="ts">
import { isEmpty } from '@/utils/is'
import { dateFormatter } from '@/utils/formatTime'
import download from '@/utils/download'
import { PropertyApi, Property } from '@/api/jijian/Property'
import PropertyForm from './PropertyForm.vue'

defineOptions({ name: 'Property' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const list = ref<Property[]>([])
const total = ref(0)

const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  propertyAddress: undefined,
  propertyName: undefined,
  ownershipInfo: undefined,
  buildingTime: [],
  area: undefined,
  leaseStatus: undefined,
  remark: undefined,
  createTime: []
})

const queryFormRef = ref()
const exportLoading = ref(false)

const getList = async () => {
  loading.value = true
  try {
    const data = await PropertyApi.getPropertyPage(queryParams)
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

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await PropertyApi.deleteProperty(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

const checkedIds = ref<number[]>([])

const handleRowCheckboxChange = (records: Property[]) => {
  checkedIds.value = records.map((item) => item.id!)
}

const handleDeleteBatch = async () => {
  try {
    await message.delConfirm()
    await PropertyApi.deletePropertyList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await PropertyApi.exportProperty(queryParams)
    download.excel(data, '房产情况.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>
