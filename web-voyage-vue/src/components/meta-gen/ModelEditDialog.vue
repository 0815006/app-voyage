<template>
  <el-dialog
    :title="isCreate ? '新建模型' : '编辑模型'"
    v-model="visible"
    width="600px"
    append-to-body
    @close="handleClose"
  >
    <el-form :model="form" label-width="100px" size="small">
      <el-form-item label="模型名称" required>
        <el-input v-model="form.modelName" placeholder="如：代发工资批量文件" />
      </el-form-item>
      <el-form-item label="编码" required>
        <el-select v-model="form.encoding">
          <el-option label="UTF-8" value="UTF-8" />
          <el-option label="GBK" value="GBK" />
          <el-option label="ASCII" value="ASCII" />
        </el-select>
        <span v-if="form.splitType === 'FIXED'" style="color:#e6a23c;font-size:11px">定长模式推荐使用GBK</span>
      </el-form-item>
      <el-form-item label="格式" required>
        <el-select v-model="form.splitType" @change="onSplitTypeChange">
          <el-option label="定长 (FIXED)" value="FIXED" />
          <el-option label="分隔符 (DELIMITER)" value="DELIMITER" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.splitType === 'DELIMITER'" label="分隔符">
        <el-input v-model="form.delimiter" placeholder="如 , 或 |" style="width:120px" />
      </el-form-item>
      <el-form-item label="换行符">
        <el-input v-model="form.lineEndingChar" placeholder="默认 \r\n" style="width:120px" />
      </el-form-item>
      <el-form-item label="最大行数">
        <el-input-number v-model="form.maxRowsLimit" :min="1" :max="9999999" style="width:100%" />
        <span style="color:#909399;font-size:11px;margin-left:8px">安全阈值，防止误操作生成海量数据</span>
      </el-form-item>
      <el-form-item label="文件头">
        <el-switch v-model="hasHeader" />
      </el-form-item>
      <el-form-item label="文件尾">
        <el-switch v-model="hasFooter" />
      </el-form-item>
      <el-form-item label="共享给">
        <el-input v-model="form.sharedWith" placeholder="输入员工工号，多个用逗号分隔，如 100001,100002" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="primary" @click="handleSave" :loading="loading">
        {{ isCreate ? '创建' : '保存' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createModel, updateModel } from '@/api/meta-gen'

interface ModelForm {
  id?: number | null
  modelName: string
  splitType: string
  delimiter: string
  encoding: string
  maxRowsLimit: number
  hasHeader: number
  hasFooter: number
  lineEndingChar: string
  sharedWith: string
}

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'listRefresh'): void
}>()

const visible = ref(false)
const loading = ref(false)
const isCreate = ref(false)
const activeModelId = ref<number | null>(null)

const getDefaultForm = (): ModelForm => ({
  modelName: '新模型',
  splitType: 'DELIMITER',
  delimiter: ',',
  encoding: 'UTF-8',
  maxRowsLimit: 100,
  hasHeader: 1,
  hasFooter: 1,
  lineEndingChar: '',
  sharedWith: '',
})

const form = ref<ModelForm>(getDefaultForm())

const hasHeader = computed({
  get: () => form.value.hasHeader === 1,
  set: (val: boolean) => { form.value.hasHeader = val ? 1 : 0 },
})

const hasFooter = computed({
  get: () => form.value.hasFooter === 1,
  set: (val: boolean) => { form.value.hasFooter = val ? 1 : 0 },
})

function init(modelData: ModelForm | null) {
  loading.value = false
  if (modelData) {
    isCreate.value = false
    activeModelId.value = modelData.id ?? null
    form.value = { ...modelData }
    if (form.value.hasHeader === undefined) form.value.hasHeader = 1
    if (form.value.hasFooter === undefined) form.value.hasFooter = 1
    if (!form.value.maxRowsLimit) form.value.maxRowsLimit = 100
    if (!form.value.encoding) form.value.encoding = 'UTF-8'
    if (!form.value.splitType) form.value.splitType = 'DELIMITER'
  } else {
    isCreate.value = true
    activeModelId.value = null
    form.value = getDefaultForm()
  }
  visible.value = true
}

function onSplitTypeChange(_val: string) {
  // UTF-8 now supports both fixed and delimiter modes, no need to force encoding switch
}

async function handleSave() {
  if (!form.value.modelName || !form.value.modelName.trim()) {
    ElMessage.warning('请输入模型名称')
    return
  }
  loading.value = true
  try {
    if (isCreate.value) {
      await createModel({ ...form.value })
      ElMessage.success('模型创建成功')
    } else {
      await updateModel(String(activeModelId.value!), { ...form.value })
      ElMessage.success('保存成功')
    }
    visible.value = false
    emit('refresh')
    emit('listRefresh')
  } finally {
    loading.value = false
  }
}

function handleClose() {
  visible.value = false
}

defineExpose({ init })
</script>
