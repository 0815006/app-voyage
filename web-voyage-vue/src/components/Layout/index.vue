<template>
  <div
    class="layout-wrapper"
    :style="{ gridTemplateColumns: collapsed ? '64px 1fr' : '240px 1fr' }"
  >
    <Header />
    <Sidebar :collapsed="collapsed" @collapse-change="onCollapseChange" />
    <main class="main-content" :class="{ 'main-content--full-height': isFullHeightRoute }">
      <router-view />
    </main>
    <StatusBar />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import Header from './Header.vue'
import Sidebar from './Sidebar.vue'
import StatusBar from './StatusBar.vue'

const route = useRoute()
const collapsed = ref(false)

const isFullHeightRoute = computed(() => route.path.startsWith('/wiki'))

function onCollapseChange(val: boolean) {
  collapsed.value = val
}
</script>

<style scoped>
.layout-wrapper {
  display: grid;
  grid-template-columns: 240px 1fr;
  grid-template-rows: 76px 1fr 34px;
  height: 100dvh;
  width: 100%;
  overflow: hidden;
  transition: grid-template-columns 0.25s ease;
}

.main-content {
  background-color: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
  grid-row: 2 / 3;
  grid-column: 2 / 3;
}

.main-content--full-height {
  padding: 0;
  overflow: hidden;
}
</style>
