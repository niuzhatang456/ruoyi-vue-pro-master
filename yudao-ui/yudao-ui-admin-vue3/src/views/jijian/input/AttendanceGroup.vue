<template>
  <GroupDragUpload
    title="考勤情况录入"
    description="支持拖拽上传 Excel/CSV/图片/PDF，自动识别并录入：考勤日报、疗休养假、事假记录、出差记录、调休记录。"
    :allowed-form-types="ATTENDANCE_FORM_TYPES"
    alternative-hint="房产情况入口或通用拖拽录入"
  />
</template>

<script setup lang="ts">
import GroupDragUpload from './GroupDragUpload.vue'

const ATTENDANCE_FORM_TYPES = ['考勤日报', '疗休养假', '事假记录', '调休记录', '出差记录']
</script>
