<template>
  <FormDragInput form-title="事假表" :upload-fn="uploadFn" />
</template>

<script setup lang="ts">
import FormDragInput from './FormDragInput.vue'
import { dragUploadPersonalLeave } from '@/api/jijian/input'

const uploadFn = dragUploadPersonalLeave
</script>
