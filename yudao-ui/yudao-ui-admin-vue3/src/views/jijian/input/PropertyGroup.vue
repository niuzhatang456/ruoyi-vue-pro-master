<template>
  <GroupDragUpload
    title="房产情况录入"
    description="支持拖拽上传 Excel/CSV/图片/PDF，自动识别并录入：房产信息、租赁人员、租赁合同。"
    :allowed-form-types="PROPERTY_FORM_TYPES"
    alternative-hint="考勤情况入口或通用拖拽录入"
    auto-preview-on-success
  />
</template>

<script setup lang="ts">
import GroupDragUpload from './GroupDragUpload.vue'

const PROPERTY_FORM_TYPES = ['房产信息', '租赁人员', '租赁合同']
</script>
