<template>
  <FormDragInput form-title="出差表" :upload-fn="uploadFn" />
</template>

<script setup lang="ts">
import FormDragInput from './FormDragInput.vue'
import { dragUploadBusinessTrip } from '@/api/jijian/input'

const uploadFn = dragUploadBusinessTrip
</script>
