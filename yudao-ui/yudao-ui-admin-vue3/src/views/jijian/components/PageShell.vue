<template>
  <div class="jijian-page">
    <div class="page-head">
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>
    <slot></slot>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<style scoped>
.jijian-page {
  padding: 20px;
}

.page-head {
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.page-head p {
  margin: 0;
  color: var(--el-text-color-regular);
}
</style>
