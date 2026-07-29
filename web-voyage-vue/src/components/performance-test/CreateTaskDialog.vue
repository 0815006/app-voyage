<template>
  <el-dialog title="新建性能测试任务" v-model="visible" width="500px" @close="handleClose">
    <el-form :model="form" :rules="rules" ref="taskFormRef" label-width="100px" size="default">
      <el-form-item label="批次" prop="batchNo">
        <el-input v-model="form.batchNo" placeholder="如：2606"></el-input>
      </el-form-item>
      <el-form-item label="产品" prop="productId">
        <el-input v-model="form.productId" placeholder="如：BPS-D-AUTO"></el-input>
      </el-form-item>
      <el-form-item label="填报人员" prop="recorderRange">
        <el-input type="textarea" v-model="form.recorderRange" placeholder="7位员工号，逗号分隔"></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取 消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading">确 定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { saveTask } from '@/api/performance'
import { getCurrentEmpNo } from '@/utils/currentUser'

interface CreateTaskForm {
  batchNo: string
  productId: string
  recorderRange: string
  creatorId: string
  status: number
}

const emit = defineEmits<{
  refresh: []
}>()

const visible = ref<boolean>(false)
const loading = ref<boolean>(false)
const taskFormRef = ref<FormInstance>()

const form = reactive<CreateTaskForm>({
  batchNo: '',
  productId: '',
  recorderRange: '',
  creatorId: getCurrentEmpNo(),
  status: 10,
})

const rules: FormRules = {
  batchNo: [{ required: true, message: '请输入批次', trigger: 'blur' }],
  productId: [{ required: true, message: '请输入产品标识', trigger: 'blur' }],
}

function init() {
  visible.value = true
  form.batchNo = ''
  form.productId = ''
  form.recorderRange = ''
  nextTick(() => {
    taskFormRef.value?.clearValidate()
  })
}

async function handleSubmit() {
  if (!taskFormRef.value) return
  try {
    await taskFormRef.value.validate()
    loading.value = true
    await saveTask({ ...form })
    ElMessage.success('创建成功')
    visible.value = false
    emit('refresh')
  } catch {
    // validation error or API error (handled by interceptor)
  } finally {
    loading.value = false
  }
}

function handleClose() {
  taskFormRef.value?.resetFields()
}

defineExpose({ init })
</script>
