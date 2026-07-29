<template>
  <footer class="status-bar">
    <span class="status-left">© 2026 Voyage Platform</span>
    <span class="status-right">
      <span class="status-item">🕐 {{ currentTime }}</span>
      <span class="status-item" v-if="loginIp">🌐 Login IP: {{ loginIp }}</span>
    </span>
  </footer>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getSystemInfo } from '@/api/system'

const currentTime = ref<string>('')
const loginIp = ref<string>('')
let timer: ReturnType<typeof setInterval> | null = null

function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

async function fetchLoginIp() {
  try {
    const data = await getSystemInfo()
    loginIp.value = data.loginIp || '127.0.0.1'
  } catch {
    loginIp.value = '127.0.0.1'
  }
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
  fetchLoginIp()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 100%;
  background-color: #f0f2f5;
  border-top: 1px solid #e4e7ed;
  font-size: 12px;
  color: #606266;
  grid-row: 3 / 4;
  grid-column: 2 / 3;
}

.status-right {
  display: flex;
  gap: 16px;
}

.status-item {
  white-space: nowrap;
}
</style>
