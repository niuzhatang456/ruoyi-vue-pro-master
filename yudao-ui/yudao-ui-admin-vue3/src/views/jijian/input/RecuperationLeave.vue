<template>
  <FormDragInput form-title="疗休养请假表" :upload-fn="uploadFn" />
</template>

<script setup lang="ts">
import FormDragInput from './FormDragInput.vue'
import { dragUploadRecuperationLeave } from '@/api/jijian/input'

const uploadFn = dragUploadRecuperationLeave
</script>
