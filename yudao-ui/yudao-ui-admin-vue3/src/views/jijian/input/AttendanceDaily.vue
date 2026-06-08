<template>
  <FormDragInput form-title="考勤日报表" :upload-fn="uploadFn" />
</template>

<script setup lang="ts">
import FormDragInput from './FormDragInput.vue'
import { dragUploadAttendanceDaily } from '@/api/jijian/input'

const uploadFn = dragUploadAttendanceDaily
</script>
