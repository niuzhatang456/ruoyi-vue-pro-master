<template>
  <PageShell title="当前账号信息" description="账号数据来自系统用户中心。">
    <el-descriptions v-loading="loading" :column="1" border>
      <el-descriptions-item label="姓名">{{ accountInfo?.nickname || '-' }}</el-descriptions-item>
      <el-descriptions-item label="账号">{{ accountInfo?.username || '-' }}</el-descriptions-item>
      <el-descriptions-item label="部门">{{ accountInfo?.dept?.name || '-' }}</el-descriptions-item>
      <el-descriptions-item label="角色">
        {{ accountInfo?.roles?.map((item) => item.name).join('、') || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="accountInfo?.status === 0 ? 'success' : 'danger'">
          {{ accountInfo?.status === 0 ? '正常' : '停用' }}
        </el-tag>
      </el-descriptions-item>
    </el-descriptions>
  </PageShell>
</template>

<script setup lang="ts">
import PageShell from '../components/PageShell.vue'
import { getUserProfile, type ProfileVO } from '@/api/system/user/profile'

const loading = ref(false)
const accountInfo = ref<ProfileVO>()

onMounted(async () => {
  loading.value = true
  try {
    accountInfo.value = await getUserProfile()
  } finally {
    loading.value = false
  }
})
</script>
