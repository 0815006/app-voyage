<template>
  <el-dialog
    title="上传环境资源清单"
    v-model="visible"
    width="550px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="uploadFormRef"
      :model="form"
      label-width="100px"
      :rules="formRules"
      size="small"
    >
      <!-- 产品标识 -->
      <el-form-item label="产品标识" prop="productId">
        <el-input
          v-model="form.productId"
          placeholder="请输入产品标识，如：BPS-D-AUTO"
          style="width: 100%"
          clearable
        />
      </el-form-item>

      <!-- 批次 -->
      <el-form-item label="批次" prop="batchNo">
        <el-input
          v-model="form.batchNo"
          placeholder="请输入批次，如：2606"
          style="width: 100%"
          clearable
        />
      </el-form-item>

      <!-- 文件来源 -->
      <el-form-item label="文件来源" prop="fileSource">
        <el-select
          v-model="form.fileSource"
          placeholder="请选择文件来源"
          style="width: 100%"
        >
          <el-option label="部署方案" value="部署方案" />
          <el-option label="资源申请表" value="资源申请表" />
        </el-select>
      </el-form-item>

      <!-- 文件上传 -->
      <el-form-item label="Excel文件" prop="file">
        <el-upload
          ref="uploadRef"
          action=""
          :auto-upload="false"
          :on-change="handleFileChange"
          :file-list="fileList"
          :limit="1"
          accept=".xlsx,.xls"
          :show-file-list="true"
        >
          <template #trigger>
            <el-button size="small" type="primary">选取文件</el-button>
          </template>
          <template #tip>
            <div class="el-upload__tip">支持 .xls、.xlsx 格式，大小不超过 10MB</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>

    <!-- 上传结果 -->
    <el-alert
      v-if="result"
      :title="result"
      :type="result.includes('成功') ? 'success' : 'error'"
      show-icon
      :closable="false"
      style="margin-top: 15px"
    />

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false" size="small">取 消</el-button>
        <el-button type="primary" @click="submitUpload" :loading="uploading" size="small">
          {{ uploading ? '上传中...' : '立即上传' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick } from 'vue'
import { ElMessage, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import { uploadResource } from '@/api/resource'

// ===================== Emits =====================

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

// ===================== 响应式数据 =====================

const visible = ref(false)

const form = reactive({
  productId: '',
  batchNo: '',
  fileSource: '部署方案',
})

const fileList = ref<UploadFile[]>([])
const file = ref<File | null>(null)
const uploading = ref(false)
const result = ref('')

// ===================== Form 引用 & 校验规则 =====================

const uploadFormRef = ref<FormInstance>()
const uploadRef = ref()

const formRules: FormRules = {
  productId: [{ required: true, message: '请输入产品标识', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次', trigger: 'blur' }],
  fileSource: [{ required: true, message: '请选择文件来源', trigger: 'change' }],
  file: [
    {
      required: true,
      validator: (_rule, _value, callback) => {
        if (!file.value) {
          callback(new Error('请上传文件'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
}

// ===================== 公开方法（通过 defineExpose 暴露给父组件） =====================

function init(productId: string, batchNo: string) {
  visible.value = true
  form.productId = productId || ''
  form.batchNo = batchNo || ''
  form.fileSource = '部署方案'
  fileList.value = []
  file.value = null
  result.value = ''
  nextTick(() => {
    uploadFormRef.value?.clearValidate()
  })
}

defineExpose({ init })

// ===================== 方法 =====================

function handleFileChange(fileItem: UploadFile, fileItems: UploadFile[]) {
  const { name } = fileItem
  if (!/\.(xlsx|xls)$/.test(name)) {
    ElMessage.error('只支持上传 Excel 文件（.xls, .xlsx）')
    fileList.value = []
    file.value = null
    return
  }
  file.value = fileItem.raw as File
  fileList.value = fileItems
}

async function submitUpload() {
  try {
    await uploadFormRef.value?.validate()
  } catch {
    return
  }

  if (!file.value) {
    ElMessage.error('请选择要上传的文件')
    return
  }

  uploading.value = true
  result.value = ''

  try {
    const res = await uploadResource(
      form.productId,
      form.batchNo,
      file.value,
      form.fileSource
    )
    result.value = '✅ 上传成功！'
    ElMessage.success(
      (res as { message?: string }).message || '文件上传成功'
    )
    emit('refresh')
    setTimeout(() => {
      visible.value = false
    }, 1500)
  } catch {
    result.value = '❌ 网络错误或接口异常'
    ElMessage.error('请求失败，请检查接口是否可达')
  } finally {
    uploading.value = false
  }
}

function handleClose() {
  resetForm()
}

function resetForm() {
  uploadFormRef.value?.resetFields()
  fileList.value = []
  file.value = null
  result.value = ''
}
</script>

<style scoped>
:deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-dialog__header) {
  background: #f6f9ff;
  border-bottom: 1px solid #e6eef9;
}

:deep(.el-input__inner),
:deep(.el-button) {
  border-radius: 8px;
}
</style>
