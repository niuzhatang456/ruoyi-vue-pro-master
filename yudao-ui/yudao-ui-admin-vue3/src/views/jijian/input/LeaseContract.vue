<template>
  <FormDragInput form-title="租赁合同表" :upload-fn="uploadFn" />
</template>

<script setup lang="ts">
import FormDragInput from './FormDragInput.vue'
import { dragUploadLeaseContract } from '@/api/jijian/input'

const uploadFn = dragUploadLeaseContract
</script>
