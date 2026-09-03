<template>
  <aside class="sidebar" :class="{ collapsed }">
    <!-- Logo 区域 -->
    <div class="logo-area" :class="{ 'logo-collapsed': collapsed }">
      <div class="logo-content">
        <img class="logo-icon" src="/logo.svg" alt="Voyage" />
        <span v-show="!collapsed" class="logo-title">Voyage 性能平台</span>
      </div>
      <!-- 折叠按钮 -->
      <button class="collapse-toggle" @click="toggleCollapse">
        <svg
          class="toggle-arrow"
          :class="{ 'arrow-collapsed': collapsed }"
          width="24"
          height="24"
          viewBox="0 0 24 24"
          fill="currentColor"
        >
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
        </svg>
      </button>
    </div>

    <!-- 导航菜单 -->
    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      router
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
      class="sidebar-menu"
    >
      <el-menu-item index="/dashboard">
        <el-icon><Monitor /></el-icon>
        <template #title>仪表盘</template>
      </el-menu-item>
      <el-menu-item index="/demo">
        <el-icon><Document /></el-icon>
        <template #title>规划功能</template>
      </el-menu-item>
      <el-menu-item index="/ai-demo">
        <el-icon><ChatDotRound /></el-icon>
        <template #title>AI 助手</template>
      </el-menu-item>
      <el-menu-item index="/agent-demo">
        <el-icon><Cpu /></el-icon>
        <template #title>Agent 验证</template>
      </el-menu-item>
      <el-menu-item index="/wiki">
        <el-icon><Collection /></el-icon>
        <template #title>Wiki在线</template>
      </el-menu-item>
      <el-menu-item index="/resource-check">
        <el-icon><Search /></el-icon>
        <template #title>资源核查</template>
      </el-menu-item>
      <el-menu-item index="/performance">
        <el-icon><TrendCharts /></el-icon>
        <template #title>性能测试</template>
      </el-menu-item>
      <el-menu-item index="/meta-gen">
        <el-icon><Setting /></el-icon>
        <template #title>批量造数</template>
      </el-menu-item>
      <el-menu-item index="/db-analysis">
        <el-icon><DataAnalysis /></el-icon>
        <template #title>数据库性能分析</template>
      </el-menu-item>

    </el-menu>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Monitor, Document, Search, TrendCharts, Setting, Collection, ChatDotRound, Cpu, DataAnalysis } from '@element-plus/icons-vue'

const props = defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  (e: 'collapse-change', collapsed: boolean): void
}>()

const route = useRoute()
const activeMenu = computed(() => route.path)

function toggleCollapse() {
  emit('collapse-change', !props.collapsed)
}
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #1e3a5f 0%, #263445 100%);
  overflow: hidden;
  grid-row: 1 / 4;
  grid-column: 1 / 2;
}

/* ========== Logo 区域 ========== */
.logo-area {
  height: 76px;
  min-height: 76px;
  box-sizing: border-box;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.logo-collapsed .logo-content {
  flex-direction: row;
  justify-content: center;
  align-items: center;
  gap: 0;
}

.logo-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}

.logo-title {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  letter-spacing: 1px;
}

/* ========== 折叠按钮 - 展开态 ========== */
.collapse-toggle {
  position: absolute;
  top: 50%;
  right: 8px;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  z-index: 10;
  padding: 0;
  transition: background 0.25s, border-color 0.25s, opacity 0.25s, visibility 0.25s;
}

.collapse-toggle:hover {
  background: rgba(94, 170, 255, 0.25);
  border: 1px solid rgba(94, 170, 255, 0.45);
}

/* ========== 折叠按钮 - 折叠态 ========== */
.logo-collapsed .collapse-toggle {
  top: 50%;
  left: 50%;
  right: auto;
  transform: translate(-50%, -50%);
  width: 40px;
  height: 40px;
  background: #304156;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.5);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}

.logo-collapsed:hover .collapse-toggle {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
}

.logo-collapsed .collapse-toggle:hover {
  border-color: rgba(94, 170, 255, 0.6);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.6);
  /* 不改变 background，保持不透明 */
}

/* ========== 箭头 ========== */
.toggle-arrow {
  color: rgba(255, 255, 255, 0.5);
  transition: color 0.25s, transform 0.3s;
}

.collapse-toggle:hover .toggle-arrow {
  color: rgba(255, 255, 255, 0.9);
}

.arrow-collapsed {
  transform: rotate(180deg);
}

/* ========== 菜单 ========== */
.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: none;
}

/* 折叠态菜单项图标居中 */
:deep(.el-menu--collapse) .el-menu-item {
  justify-content: center;
  padding: 0;
}
</style>
