<template>
  <GroupDragUpload
    title="食堂供应录入"
    description="支持拖拽上传 Excel/CSV/图片/PDF，自动识别并录入食堂供应数据和民生商品市场零售价格公告。"
    :allowed-form-types="CANTEEN_FORM_TYPES"
    alternative-hint="通用拖拽录入入口"
    auto-preview-on-success
  />
</template>

<script setup lang="ts">
import GroupDragUpload from './GroupDragUpload.vue'
const CANTEEN_FORM_TYPES = ['食堂供应', '民生价格公告']
</script>
