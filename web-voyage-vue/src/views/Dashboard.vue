<template>
  <div class="dashboard" v-loading="loading">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>系统状态</span>
            </div>
          </template>
          <el-tag :type="healthData?.status === 'UP' ? 'success' : 'danger'">
            {{ healthData?.status || '未知' }}
          </el-tag>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>Java 版本</span>
            </div>
          </template>
          <div class="card-value">{{ healthData?.java || '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>当前操作员</span>
            </div>
          </template>
          <div class="card-value">{{ healthData?.operator || '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>服务器时间</span>
            </div>
          </template>
          <div class="card-value">{{ healthData?.timestamp || '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="mt-20" shadow="hover">
      <template #header>
        <span>欢迎使用 Voyage 全栈平台</span>
      </template>
      <p>这是一个基于 <strong>Java 21 (虚拟线程) + Spring Boot 3.4 + Vue 3 + Element Plus</strong> 的全栈脚手架项目。</p>
      <p class="mt-8">请在右上角设置您的 <strong>7位工号</strong> 以开始使用。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getHealthInfo, type HealthResponse } from '@/api/health'

const loading = ref<boolean>(false)
const healthData = ref<HealthResponse | null>(null)

async function fetchHealth() {
  loading.value = true
  try {
    healthData.value = await getHealthInfo()
  } catch {
    // ElMessage 已在 request 拦截器中全局处理
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchHealth()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-value {
  font-size: 14px;
  color: #606266;
  word-break: break-all;
}

.mt-20 {
  margin-top: 20px;
}

.mt-8 {
  margin-top: 8px;
}
</style>
