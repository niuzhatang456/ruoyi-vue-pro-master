<template>
  <PageShell title="当前账号信息" description="展示当前登录账号、部门、角色等基础信息。">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="姓名">{{ accountInfo.name }}</el-descriptions-item>
      <el-descriptions-item label="账号">{{ accountInfo.username }}</el-descriptions-item>
      <el-descriptions-item label="部门">{{ accountInfo.department }}</el-descriptions-item>
      <el-descriptions-item label="角色">{{ accountInfo.role }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag type="success">{{ accountInfo.status }}</el-tag>
      </el-descriptions-item>
    </el-descriptions>
  </PageShell>
</template>

<script setup lang="ts">
import PageShell from '../components/PageShell.vue'
import { mockAccountInfo } from '../mock'

const accountInfo = mockAccountInfo
</script>
