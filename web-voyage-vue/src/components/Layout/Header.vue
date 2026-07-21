<template>
  <header class="header">
    <div class="header-left">
      <h1 class="logo">⛵ Voyage 全栈平台</h1>
    </div>
    <div class="header-right">
      <!-- 工号 Tag / Input 无缝就地编辑 (Inline Edit) -->
      <template v-if="!isEditing">
        <el-tag
          :type="empNo ? 'success' : 'info'"
          class="emp-tag"
          @click="startEdit"
        >
          {{ empNo || '点击设置工号' }}
        </el-tag>
      </template>
      <template v-else>
        <el-input
          ref="inputRef"
          v-model="inputValue"
          class="emp-input"
          maxlength="7"
          placeholder="输入7位工号"
          @keyup.enter="confirmEdit"
          @blur="confirmEdit"
        />
      </template>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getCurrentEmpNo, setCurrentEmpNo, isEmpNoValid } from '@/utils/currentUser'

const empNo = ref<string>(getCurrentEmpNo())
const isEditing = ref<boolean>(false)
const inputValue = ref<string>('')
const inputRef = ref<InstanceType<typeof import('element-plus').ElInput> | null>(null)

function startEdit() {
  inputValue.value = empNo.value
  isEditing.value = true
  nextTick(() => {
    inputRef.value?.focus()
  })
}

function confirmEdit() {
  if (!isEditing.value) return
  const trimmed = inputValue.value.trim()

  if (!trimmed) {
    // 允许清空工号
    localStorage.removeItem('voyage_emp_no')
    empNo.value = ''
    isEditing.value = false
    return
  }

  if (!isEmpNoValid(trimmed)) {
    ElMessage.warning('工号必须为7位数字')
    return
  }

  try {
    setCurrentEmpNo(trimmed)
    empNo.value = trimmed
    isEditing.value = false
    ElMessage.success(`身份已切换为: ${trimmed}`)
  } catch (e) {
    ElMessage.error('工号设置失败')
  }
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 100%;
  background: linear-gradient(135deg, #1e3a5f 0%, #2a5298 100%);
  color: #fff;
  grid-row: 1 / 2;
  grid-column: 1 / 3;
}

.logo {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.emp-tag {
  cursor: pointer;
  user-select: none;
}

.emp-input {
  width: 140px;
}
</style>
