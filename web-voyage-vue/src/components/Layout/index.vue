<template>
  <div class="layout-wrapper">
    <Header />
    <Sidebar />
    <main class="main-content">
      <router-view />
    </main>
    <StatusBar />
  </div>
</template>

<script setup lang="ts">
import Header from './Header.vue'
import Sidebar from './Sidebar.vue'
import StatusBar from './StatusBar.vue'
</script>

<style scoped>
.layout-wrapper {
  display: grid;
  grid-template-columns: 240px 1fr;
  grid-template-rows: auto 1fr 34px;
  height: 100dvh;
  width: 100%;
  overflow: hidden;
}

.main-content {
  background-color: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
  grid-row: 2 / 3;
  grid-column: 2 / 3;
}
</style>
