<template>
  <el-dialog title="引用文件管理" v-model="visible" width="800px" append-to-body @close="handleClose">
    <div style="margin-bottom:12px; text-align: right">
      <el-button type="primary" size="small" :icon="Upload" @click="triggerUpload">上传引用文件</el-button>
      <input ref="fileInputRef" type="file" style="display:none" @change="handleFileChange" />
    </div>
    <el-table :data="refList" border size="small" v-loading="loading">
      <el-table-column prop="refName" label="文件名称" min-width="180" />
      <el-table-column label="解析模式" width="120">
        <template #default="scope">
          {{ scope.row.parseType === 'FIXED' ? '定长' : '分隔符(' + (scope.row.delimiter || '-') + ')' }}
        </template>
      </el-table-column>
      <el-table-column prop="filePath" label="路径" show-overflow-tooltip />
      <el-table-column label="操作" width="150">
        <template #default="scope">
          <el-button type="primary" link size="small" @click="handlePreview(scope.row)">
            <el-icon><View /></el-icon>
          </el-button>
          <el-button type="primary" link size="small" @click="handleDefine(scope.row)">配置映射</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 配置映射弹窗 -->
    <el-dialog title="配置列映射" v-model="mapVisible" width="600px" append-to-body>
      <el-form :model="mapForm" label-width="120px" size="small">
        <el-form-item label="解析模式">
          <el-select v-model="mapForm.parseType" style="width:100%">
            <el-option label="分隔符 (DELIMITER)" value="DELIMITER" />
            <el-option label="定长 (FIXED)" value="FIXED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mapForm.parseType === 'DELIMITER'" label="分隔符">
          <el-input v-model="mapForm.delimiter" placeholder="如 , 或 |" />
        </el-form-item>
        <el-form-item label="列映射 (JSON)">
          <el-input v-model="mapForm.columnMapping" type="textarea" :rows="6" :placeholder="columnMappingPlaceholder" />
        </el-form-item>
      </el-form>
      <!-- 填写指南 -->
      <div v-if="mapForm.parseType === 'FIXED'" style="margin: 10px 0; padding: 10px; background: #f5f7fa; border-radius: 4px; font-size: 12px; color: #606266; line-height: 1.8;">
        <div style="font-weight: bold; margin-bottom: 4px;">填写指南：</div>
        <div>定长格式：{"字段名": {"start": 起始位置, "length": 字段长度}}</div>
        <div>start: 从1开始计算的起始字节位置</div>
        <div>length: 字段的字节长度</div>
        <div>示例：{"name": {"start": 1, "length": 10}, "age": {"start": 11, "length": 3}}</div>
      </div>
      <div v-else style="margin: 10px 0; padding: 10px; background: #f5f7fa; border-radius: 4px; font-size: 12px; color: #606266; line-height: 1.8;">
        <div style="font-weight: bold; margin-bottom: 4px;">填写指南：</div>
        <div>分隔符格式：{"字段名": 列序号}</div>
        <div>列序号从1开始，对应文件表头的第N列</div>
        <div>示例：{"userName": 1, "age": 2, "email": 3}</div>
      </div>
      <template #footer>
        <el-button size="small" @click="mapVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSaveDefine">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文件预览弹窗 -->
    <el-dialog title="文件预览" v-model="previewVisible" width="1000px" append-to-body>
      <div v-if="previewLoading" v-loading="previewLoading" />
      <div v-else>
        <div style="margin-bottom: 15px; line-height: 1.8;">
          <div><strong>解析模式：</strong>{{ previewRow.parseType === 'FIXED' ? '定长' : '分隔符(' + (previewRow.delimiter || '-') + ')' }}</div>
          <div><strong>列映射 (JSON)：</strong>{{ previewRow.columnMapping || '-' }}</div>
        </div>
        <el-table :data="previewLines" border size="small" height="300">
          <el-table-column label="行号" width="80" align="center">
            <template #default="scope">
              {{ scope.$index + 1 }}
            </template>
          </el-table-column>
          <el-table-column label="内容" min-width="800">
            <template #default="scope">
              <div style="font-family: monospace; white-space: pre-wrap; word-break: break-all;">{{ scope.row }}</div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, View } from '@element-plus/icons-vue'
import {
  listRefFiles,
  uploadRefFile,
  defineRefFile,
  deleteRefFile,
  previewRefFile,
} from '@/api/meta-gen'

interface RefFileRecord {
  id?: number
  refName: string
  parseType: string
  delimiter?: string
  filePath?: string
  columnMapping?: string
}

const props = defineProps<{
  dialogVisible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:dialogVisible', v: boolean): void
  (e: 'changed'): void
}>()

const visible = ref(props.dialogVisible)

watch(() => props.dialogVisible, (v) => {
  visible.value = v
  if (v) loadData()
})

watch(visible, (v) => {
  emit('update:dialogVisible', v)
})

const refList = ref<RefFileRecord[]>([])
const loading = ref(false)
const mapVisible = ref(false)
const mapForm = ref<RefFileRecord>({ refName: '', parseType: 'DELIMITER' })
const fileInputRef = ref<HTMLInputElement | null>(null)
const previewVisible = ref(false)
const previewLines = ref<string[]>([])
const previewLoading = ref(false)
const previewRow = ref<RefFileRecord>({ refName: '', parseType: 'DELIMITER' })

const columnMappingPlaceholder = computed(() => {
  if (mapForm.value.parseType === 'FIXED') {
    return '定长格式: {"name":{"start":1,"length":10},"age":{"start":11,"length":3}}'
  }
  return '分隔符格式: {"userName":1,"age":2}'
})

watch(() => mapForm.value.parseType, (newVal, oldVal) => {
  if (newVal === 'FIXED' && oldVal === 'DELIMITER' && !mapForm.value.columnMapping) {
    mapForm.value.columnMapping = '{"name":{"start":1,"length":10},"age":{"start":11,"length":3}}'
  }
})

function loadData() {
  loading.value = true
  listRefFiles().then((res) => { refList.value = (res as unknown as RefFileRecord[]) || [] }).finally(() => { loading.value = false })
}

function triggerUpload() {
  fileInputRef.value?.click()
}

function handleFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  formData.append('refName', file.name)
  uploadRefFile(formData).then(() => {
    ElMessage.success('上传成功')
    loadData()
    emit('changed')
  })
}

function handleDefine(row: RefFileRecord) {
  mapForm.value = { ...row }
  if (!mapForm.value.parseType) {
    mapForm.value.parseType = 'DELIMITER'
  }
  if (!mapForm.value.delimiter) {
    mapForm.value.delimiter = ','
  }
  if (!mapForm.value.columnMapping) {
    if (mapForm.value.parseType === 'FIXED') {
      mapForm.value.columnMapping = '{"name":{"start":1,"length":10},"age":{"start":11,"length":3}}'
    } else {
      mapForm.value.columnMapping = '{"col1":1,"col2":2}'
    }
  }
  mapVisible.value = true
}

function handleSaveDefine() {
  defineRefFile(mapForm.value).then(() => {
    ElMessage.success('配置保存成功')
    mapVisible.value = false
    loadData()
  })
}

function handleDelete(row: RefFileRecord) {
  ElMessageBox.confirm('确定要删除该引用文件吗？删除后无法恢复', '确认删除', {
    type: 'warning',
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
  }).then(() => {
    deleteRefFile(String(row.id!)).then(() => {
      ElMessage.success('删除成功')
      loadData()
      emit('changed')
    })
  }).catch(() => {})
}

function handlePreview(row: RefFileRecord) {
  previewVisible.value = true
  previewLoading.value = true
  previewLines.value = []
  previewRow.value = row
  previewRefFile(String(row.id!), 5).then((res) => {
    const content = (res as string) || ''
    previewLines.value = content ? content.split('\n') : []
  }).finally(() => {
    previewLoading.value = false
  })
}

function handleClose() {
  emit('update:dialogVisible', false)
}
</script>
