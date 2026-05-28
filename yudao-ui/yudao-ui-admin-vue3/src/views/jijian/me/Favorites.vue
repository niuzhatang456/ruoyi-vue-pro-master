<template>
  <PageShell title="常用查询内容" description="维护常用自然语言查询模板，提高重复查询效率。">
    <el-form class="favorite-form" inline @submit.prevent>
      <el-form-item label="模板名称">
        <el-input v-model="form.name" placeholder="例如：房产核查" />
      </el-form-item>
      <el-form-item label="查询内容">
        <el-input v-model="form.content" placeholder="请输入常用查询内容" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="!form.name || !form.content" @click="handleAdd">
          添加
        </el-button>
      </el-form-item>
    </el-form>

    <el-table :data="favorites" border>
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="content" label="常用查询" min-width="240" />
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
    </el-table>
  </PageShell>
</template>

<script setup lang="ts">
import PageShell from '../components/PageShell.vue'
import { mockFavorites } from '../mock'
import type { FavoriteQuery } from '../types'

const favorites = ref<FavoriteQuery[]>([...mockFavorites])
const form = reactive({
  name: '',
  content: ''
})

const handleAdd = () => {
  favorites.value = [
    {
      id: `FAV-${Date.now()}`,
      name: form.name,
      content: form.content,
      updatedAt: new Date().toLocaleString()
    },
    ...favorites.value
  ]
  form.name = ''
  form.content = ''
}
</script>

<style scoped>
.favorite-form {
  margin-bottom: 12px;
}
</style>
