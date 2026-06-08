<template>
  <FormDragInput form-title="食堂供应商信息表" :upload-fn="uploadFn" />
</template>

<script setup lang="ts">
import FormDragInput from './FormDragInput.vue'
import { dragUploadCanteenSupplier } from '@/api/jijian/input'

const uploadFn = dragUploadCanteenSupplier
</script>
