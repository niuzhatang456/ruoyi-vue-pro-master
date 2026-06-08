<template>
  <FormDragInput form-title="租赁人员表" :upload-fn="uploadFn" />
</template>

<script setup lang="ts">
import FormDragInput from './FormDragInput.vue'
import { dragUploadLessee } from '@/api/jijian/input'

const uploadFn = dragUploadLessee
</script>
