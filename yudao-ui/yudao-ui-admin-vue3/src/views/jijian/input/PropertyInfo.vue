<template>
  <FormDragInput form-title="房产情况表" :upload-fn="uploadFn" />
</template>

<script setup lang="ts">
import FormDragInput from './FormDragInput.vue'
import { dragUploadPropertyInfo } from '@/api/jijian/input'

const uploadFn = dragUploadPropertyInfo
</script>
