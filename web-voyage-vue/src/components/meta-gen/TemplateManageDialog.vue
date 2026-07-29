<template>
  <el-dialog title="模板文件维护" v-model="visible" width="700px" append-to-body>
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
            <div class="el-upload__tip">上传模型/字段定义模板文件，支持 .json /.xlsx 格式</div>
          </template>
        </el-upload>
      </div>

      <el-table :data="fileList" border size="small" v-loading="loading" style="margin-top: 20px">
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="scope">
            {{ ((scope.row.fileSize || 0) / 1024).toFixed(2) }} KB
          </template>
        </el-table-column>
        <el-table-column prop="lastModified" label="修改时间" width="160">
          <template #default="scope">
            {{ formatTime(scope.row.lastModified) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="handleDownload(scope.row.fileName)">下载</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(scope.row.fileName)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="fileList.length === 0 && !loading" style="text-align:center;color:#909399;padding:20px">暂无模板文件</div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { listTemplates, deleteTemplate, getTemplateDownloadUrl } from '@/api/meta-gen'

interface TemplateFile {
  fileName: string
  fileSize: number
  lastModified: number
}

const visible = ref(false)
const loading = ref(false)
const fileList = ref<TemplateFile[]>([])
const uploadUrl = '/api/meta/template/upload'

function init() {
  visible.value = true
  fetchFileList()
}

async function fetchFileList() {
  loading.value = true
  try {
    const res = await listTemplates()
    fileList.value = (res as TemplateFile[]) || []
  } finally {
    loading.value = false
  }
}

function formatTime(timestamp: number): string {
  if (!timestamp) return '-'
  const d = new Date(timestamp)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
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
    // cancelled
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
