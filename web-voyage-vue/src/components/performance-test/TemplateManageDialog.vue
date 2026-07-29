<template>
  <el-dialog title="模板文件维护" v-model="visible" width="600px">
    <div class="template-manage-container">
      <div class="upload-section">
        <el-upload
          class="upload-demo"
          :action="uploadUrl"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
          :show-file-list="false"
        >
          <el-button size="small" type="primary" :icon="Upload">上传新模板</el-button>
          <template #tip>
            <div class="el-upload__tip">上传文件将替换同名文件，建议包含"业务交易调查表"或"批量运行调查表"关键字</div>
          </template>
        </el-upload>
      </div>

      <el-table :data="fileList" border size="small" style="margin-top: 20px">
        <el-table-column prop="fileName" label="文件名" min-width="200"></el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="scope">{{ (scope.row.fileSize / 1024).toFixed(2) }} KB</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link size="small" @click="handleDownload(scope.row.fileName)">下载</el-button>
            <el-button link size="small" style="color: #F56C6C" @click="handleDelete(scope.row.fileName)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { listTemplates, deleteTemplate, getTemplateDownloadUrl } from '@/api/performance'

interface TemplateFile {
  fileName: string
  fileSize: number
}

const visible = ref<boolean>(false)
const fileList = ref<TemplateFile[]>([])
const uploadUrl = '/api/performance/file/upload'

function init() {
  visible.value = true
  fetchFileList()
}

async function fetchFileList() {
  const data = await listTemplates()
  fileList.value = data as TemplateFile[]
}

function handleUploadSuccess() {
  ElMessage.success('上传成功')
  fetchFileList()
}

function handleUploadError() {
  ElMessage.error('上传失败')
}

function handleDownload(fileName: string) {
  window.open(getTemplateDownloadUrl(fileName), '_blank')
}

async function handleDelete(fileName: string) {
  try {
    await ElMessageBox.confirm(`确定删除模板文件 ${fileName} 吗？`, '提示', { type: 'warning' })
    await deleteTemplate(fileName)
    ElMessage.success('删除成功')
    fetchFileList()
  } catch {
    // cancel or error (handled by interceptor)
  }
}

defineExpose({ init })
</script>

<style scoped>
.template-manage-container {
  padding: 10px;
}
.upload-section {
  text-align: right;
}
</style>
