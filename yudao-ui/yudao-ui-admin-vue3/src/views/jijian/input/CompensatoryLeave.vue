<template>
  <FormDragInput form-title="调休表" :upload-fn="uploadFn" />
</template>

<script setup lang="ts">
import FormDragInput from './FormDragInput.vue'
import { dragUploadCompensatoryLeave } from '@/api/jijian/input'

const uploadFn = dragUploadCompensatoryLeave
</script>
