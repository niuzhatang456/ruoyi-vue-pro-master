<template>
  <Dialog :title="dialogTitle" v-model="dialogVisible">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      v-loading="formLoading"
    >
      <el-form-item label="房产地址" prop="propertyAddress">
        <el-input v-model="formData.propertyAddress" placeholder="请输入房产地址" />
      </el-form-item>

      <el-form-item label="房产名称" prop="propertyName">
        <el-input v-model="formData.propertyName" placeholder="请输入房产名称" />
      </el-form-item>

      <el-form-item label="产权信息" prop="ownershipInfo">
        <el-input v-model="formData.ownershipInfo" placeholder="请输入产权信息" />
      </el-form-item>

      <el-form-item label="建筑时间" prop="buildingTime">
        <el-date-picker
          v-model="formData.buildingTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择建筑时间"
          class="!w-1/1"
        />
      </el-form-item>

      <el-form-item label="建筑面积" prop="area">
        <el-input-number
          v-model="formData.area"
          :min="0"
          :precision="2"
          :step="1"
          controls-position="right"
          placeholder="请输入建筑面积"
          class="!w-1/1"
        />
      </el-form-item>

      <el-form-item label="租赁情况" prop="leaseStatus">
        <el-input v-model="formData.leaseStatus" placeholder="请输入租赁情况" />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" placeholder="请输入备注" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">
        确 定
      </el-button>
      <el-button @click="dialogVisible = false">
        取 消
      </el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { PropertyApi, Property } from '@/api/jijian/Property'

const { t } = useI18n()
const message = useMessage()

defineOptions({ name: 'PropertyForm' })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const formData = ref({
  id: undefined as number | undefined,
  propertyAddress: undefined as string | undefined,
  propertyName: undefined as string | undefined,
  ownershipInfo: undefined as string | undefined,
  buildingTime: undefined as string | undefined,
  area: undefined as number | undefined,
  leaseStatus: undefined as string | undefined,
  remark: undefined as string | undefined
})

const formRules = reactive({
  propertyAddress: [{ required: true, message: '请输入房产地址', trigger: 'blur' }],
  propertyName: [{ required: true, message: '请输入房产名称', trigger: 'blur' }],
  ownershipInfo: [{ required: true, message: '请输入产权信息', trigger: 'blur' }],
  area: [{ required: true, message: '请输入建筑面积', trigger: 'blur' }]
})

const formRef = ref()

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()

  if (id) {
    formLoading.value = true
    try {
      formData.value = await PropertyApi.getProperty(id)
    } finally {
      formLoading.value = false
    }
  }
}

defineExpose({ open })

const emit = defineEmits(['success'])

const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true

  try {
    const data = formData.value as unknown as Property

    if (formType.value === 'create') {
      await PropertyApi.createProperty(data)
      message.success(t('common.createSuccess'))
    } else {
      await PropertyApi.updateProperty(data)
      message.success(t('common.updateSuccess'))
    }

    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    id: undefined,
    propertyAddress: undefined,
    propertyName: undefined,
    ownershipInfo: undefined,
    buildingTime: undefined,
    area: undefined,
    leaseStatus: undefined,
    remark: undefined
  }
  formRef.value?.resetFields()
}
</script>