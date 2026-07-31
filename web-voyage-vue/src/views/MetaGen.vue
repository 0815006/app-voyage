<template>
  <div class="meta-gen-page">
    <div class="meta-main-content">
      <!-- Left sidebar: model list -->
      <div class="sidebar-container">
        <div class="model-list-sidebar">
          <div class="sidebar-header">
            <h3>造数模型</h3>
            <div class="sidebar-header-btns">
              <el-button type="primary" size="small" :icon="CollectionTag" circle title="枚举库管理" @click="showEnumDialog = true" />
              <el-button type="primary" size="small" :icon="FolderOpened" circle title="引用文件管理" @click="showRefFileDialog = true" />
              <el-button type="primary" size="small" :icon="Setting" circle title="模板维护" @click="handleManageTemplates" />
              <el-button type="primary" size="small" :icon="Plus" circle title="新建模型" @click="handleCreateModel" />
            </div>
          </div>
          <div class="model-list-content" v-loading="modelLoading">
            <div v-if="models.length === 0" class="no-models-tip">暂无模型</div>
            <ul class="model-items-list">
              <li
                v-for="item in models"
                :key="item.id"
                class="model-item"
                :class="{ active: activeModelId === item.id }"
                @click="handleSelectModel(item.id)"
              >
                <div class="model-info">
                  <div class="model-name" :title="item.modelName">{{ item.modelName || '未命名模型' }}</div>
                  <div class="model-meta">
                    <el-tag :type="item.status === 'PUBLISHED' ? 'success' : item.status === 'DRAFT' ? 'info' : 'danger'" size="small">{{ item.status }}</el-tag>
                    <span style="margin-left:4px">{{ item.encoding }} | {{ item.splitType === 'FIXED' ? '定长' : '分隔符' }}</span>
                  </div>
                </div>
                <el-icon><ArrowRight /></el-icon>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <!-- Right detail area -->
      <div class="detail-content-area">
        <div v-if="!activeModelId" class="empty-state">
          <el-empty description="请选择或新建一个造数模型" />
        </div>
        <div v-else class="detail-scroll-container" v-loading="detailLoading">
          <!-- Model properties block -->
          <el-card class="info-block" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="block-title"><el-icon><InfoFilled /></el-icon> 模型属性</span>
                <div class="header-actions">
                  <el-button type="primary" size="small" :icon="Edit" @click="openModelEdit">编辑</el-button>
                  <el-button v-if="modelInfo.status === 'DRAFT'" type="success" size="small" :icon="UploadFilled" @click="handlePublishConfirm">发布</el-button>
                  <el-button v-if="modelInfo.status === 'PUBLISHED'" type="warning" size="small" :icon="Back" @click="handleUnpublish">退回草稿</el-button>
                  <el-button type="primary" size="small" :icon="Promotion" @click="showGenDialog = true">批量生成</el-button>
                  <el-button type="danger" size="small" :icon="Delete" @click="handleDeleteConfirm">删除</el-button>
                </div>
              </div>
            </template>
            <el-descriptions :column="4" border size="small">
              <el-descriptions-item label="模型名称">{{ modelInfo.modelName }}</el-descriptions-item>
              <el-descriptions-item label="编码">{{ modelInfo.encoding }}</el-descriptions-item>
              <el-descriptions-item label="格式">{{ modelInfo.splitType === 'FIXED' ? '定长 (FIXED)' : '分隔符 (DELIMITER)' }}</el-descriptions-item>
              <el-descriptions-item v-if="modelInfo.splitType === 'DELIMITER'" label="分隔符">{{ modelInfo.delimiter }}</el-descriptions-item>
              <el-descriptions-item label="换行符">{{ modelInfo.lineEndingChar || '\\r\\n' }}</el-descriptions-item>
              <el-descriptions-item label="最大行数">{{ modelInfo.maxRowsLimit }}</el-descriptions-item>
              <el-descriptions-item label="文件头">{{ modelInfo.hasHeader ? '有' : '无' }}</el-descriptions-item>
              <el-descriptions-item label="文件尾">{{ modelInfo.hasFooter ? '有' : '无' }}</el-descriptions-item>
              <el-descriptions-item label="共享给">{{ modelInfo.sharedWith || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="modelInfo.status === 'PUBLISHED' ? 'success' : modelInfo.status === 'DRAFT' ? 'info' : 'danger'" size="small">{{ modelInfo.status }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="版本">{{ modelInfo.modelVersion }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- Preview block -->
          <el-card class="info-block preview-block" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="block-title"><el-icon><View /></el-icon> 实时预览</span>
                <el-button link size="small" :icon="Refresh" @click="handlePreview" :loading="previewLoading">刷新预览</el-button>
              </div>
            </template>
            <div class="byte-ruler" v-if="previewText" ref="rulerRef">
              <span class="ruler-text" ref="rulerTextRef">{{ rulerContent }}</span>
            </div>
            <pre class="preview-box" :class="{ empty: !previewText }" ref="previewBoxRef" @scroll="syncRulerScroll">{{ previewText || '点击"刷新预览"查看生成效果（3行Body）' }}</pre>
          </el-card>

          <!-- Section field cards: FILENAME / HEADER / BODY / FOOTER -->
          <el-card v-for="sec in visibleSections" :key="sec.key" class="info-block" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="block-title"><el-icon><component :is="sec.iconComponent" /></el-icon> {{ sec.label }}</span>
                <el-button type="primary" size="small" :icon="Edit" @click="openFieldEdit(sec.key)">编辑</el-button>
              </div>
            </template>
            <el-table :data="getSectionFields(sec.key)" border stripe size="small" v-if="getSectionFields(sec.key).length > 0">
              <el-table-column label="序号" width="60" align="center">
                <template #default="scope">
                  <span style="font-family:monospace;color:#909399">{{ computeSortOrder(scope.$index, sec.key) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="fieldKey" label="变量名" min-width="50" show-overflow-tooltip />
              <el-table-column prop="fieldName" label="字段描述" min-width="100" show-overflow-tooltip />
              <el-table-column label="长度(B)" width="70" align="center">
                <template #default="scope">{{ scope.row.length || '-' }}</template>
              </el-table-column>
              <el-table-column label="必填" width="55" align="center">
                <template #default="scope">
                  <span :style="{ color: scope.row.isRequired ? '#F56C6C' : '#C0C4CC' }">{{ scope.row.isRequired ? '是' : '否' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="补齐" width="60" align="center">
                <template #default="scope">
                  <span>{{ scope.row.paddingDirection !== 'NONE' ? (scope.row.paddingDirection === 'LEFT' ? '左补' : '右补') : '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="补齐字符" width="75" align="center">
                <template #default="scope">
                  <span>{{ scope.row.paddingDirection !== 'NONE' ? (scope.row.paddingChar === ' ' ? '空格' : scope.row.paddingChar) : '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="ruleType" label="规则类型" width="100">
                <template #default="scope">
                  <el-tag size="small" type="info">{{ ruleTypeToChinese(scope.row.ruleType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="规则配置JSON" min-width="200" show-overflow-tooltip>
                <template #default="scope">
                  <span style="font-family:monospace;font-size:11px;color:#606266">{{ scope.row.ruleConfigJson || '-' }}</span>
                </template>
              </el-table-column>
            </el-table>
            <div v-else style="color:#909399;font-size:12px;text-align:center;padding:16px">暂无字段定义，点击"编辑"添加</div>
          </el-card>

          <!-- Generation history -->
          <el-card class="info-block" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="block-title"><el-icon><Clock /></el-icon> 生成历史</span>
                <el-button type="primary" size="small" :icon="UploadFilled" @click="showFtpDialog = true; loadFtpConfigs()">FTP配置</el-button>
              </div>
            </template>
            <el-table :data="historyList" border stripe size="small" v-loading="historyLoading">
              <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
              <el-table-column label="类型" width="80">
                <template #default="scope">{{ scope.row.fileType === 'PREVIEW' ? '预览' : '正式' }}</template>
              </el-table-column>
              <el-table-column prop="rowCount" label="行数" width="80" />
              <el-table-column label="状态" width="100">
                <template #default="scope">
                  <el-tag v-if="scope.row.status === 'SUCCESS'" type="success" size="small">成功</el-tag>
                  <el-tag v-else-if="scope.row.status === 'RUNNING'" type="warning" size="small">生成中</el-tag>
                  <el-tag v-else type="danger" size="small">失败</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="durationMs" label="耗时(ms)" width="90" />
              <el-table-column prop="createTime" label="创建时间" width="160" />
              <el-table-column label="操作" width="160">
                <template #default="scope">
                  <el-button v-if="scope.row.status === 'SUCCESS'" link size="small" @click="openUploadDialog(scope.row)">上传</el-button>
                  <el-button v-if="scope.row.status === 'SUCCESS'" link size="small" @click="handleDownload(scope.row)">下载</el-button>
                  <el-button link size="small" style="color:#f56c6c" @click="handleDeleteFile(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </div>
    </div>

    <!-- ========== Dialog components ========== -->
    <ModelEditDialog ref="modelEditDialogRef" @refresh="refreshDetail" @listRefresh="loadModels" />
    <FieldEditDialog ref="fieldEditDialogRef" @refresh="refreshDetail" :enumKeys="enumKeys" :refFiles="refFiles" />
    <EnumManageDialog v-model:dialogVisible="showEnumDialog" @changed="loadResources" />
    <RefFileDialog v-model:dialogVisible="showRefFileDialog" @changed="loadResources" />
    <TemplateManageDialog ref="templateManageDialogRef" />

    <!-- FTP Config dialog -->
    <el-dialog title="FTP配置管理" v-model="showFtpDialog" width="700px" append-to-body @closed="resetFtpForm">
      <el-table :data="ftpConfigs" border stripe size="small" v-loading="ftpLoading" style="margin-bottom:16px">
        <el-table-column prop="name" label="名称" min-width="100" />
        <el-table-column prop="ftpIp" label="FTP IP" width="130" />
        <el-table-column prop="ftpPort" label="端口" width="60" />
        <el-table-column prop="username" label="用户名" width="100" />
        <el-table-column prop="remotePath" label="远程路径" min-width="140" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="最后修改" width="160" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link size="small" @click="editFtpConfig(scope.row)">编辑</el-button>
            <el-button link size="small" style="color:#f56c6c" @click="handleDeleteFtp(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">{{ ftpFormMode === 'create' ? '新增配置' : '编辑配置' }}</el-divider>
      <el-form :model="ftpForm" :rules="ftpRules" ref="ftpFormRef" label-width="80px" size="small">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="ftpForm.name" placeholder="如：生产服务器" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="FTP IP" prop="ftpIp">
              <el-input v-model="ftpForm.ftpIp" placeholder="如：192.168.1.100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="端口" prop="ftpPort">
              <el-input-number v-model="ftpForm.ftpPort" :min="1" :max="65535" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="ftpForm.username" placeholder="FTP用户名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input v-model="ftpForm.password" type="password" show-password placeholder="FTP密码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="远程路径" prop="remotePath">
              <el-input v-model="ftpForm.remotePath" placeholder="如：/data/files/" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" size="small" @click="submitFtpForm" :loading="ftpSubmitting">{{ ftpFormMode === 'create' ? '新增' : '保存修改' }}</el-button>
          <el-button v-if="ftpFormMode === 'edit'" size="small" @click="resetFtpForm">取消编辑</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- FTP Upload dialog -->
    <el-dialog title="上传文件到FTP" v-model="showUploadDialog" width="480px" append-to-body @closed="uploadFileRow = null; selectedFtpId = null">
      <div v-if="uploadFileRow" style="margin-bottom:12px;color:#606266;font-size:13px">
        文件：<b>{{ uploadFileRow.fileName }}</b>
      </div>
      <el-form label-width="80px" size="small">
        <el-form-item label="目标FTP">
          <el-select v-model="selectedFtpId" placeholder="请选择FTP配置" style="width:100%">
            <el-option
              v-for="item in ftpConfigs"
              :key="item.id"
              :label="item.name + ' (' + item.ftpIp + ':' + item.remotePath + ')'"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="showUploadDialog = false">关闭</el-button>
        <el-button size="small" type="primary" @click="doUploadToFtp" :loading="uploadLoading" :disabled="!selectedFtpId">上传</el-button>
      </template>
    </el-dialog>

    <!-- Batch generation dialog -->
    <el-dialog title="批量生成" v-model="showGenDialog" width="420px" append-to-body>
      <el-form label-width="80px" size="small">
        <el-form-item label="生成行数">
          <el-input-number v-model="genRowCount" :min="1" :max="9999999" style="width:100%" />
          <div v-if="genRowCount > (modelInfo.maxRowsLimit || 100000)" style="color:#f56c6c;font-size:12px;margin-top:4px">
            超过最大行数限制 {{ modelInfo.maxRowsLimit }}，无法生成！
          </div>
        </el-form-item>
        <el-form-item label="批次名称">
          <el-input v-model="genBatchName" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="showGenDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doGenerate" :loading="genLoading" :disabled="genRowCount > (modelInfo.maxRowsLimit || 100000)">开始生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  CollectionTag, FolderOpened, Setting, Plus, ArrowRight,
  InfoFilled, Edit, UploadFilled, Back, Promotion, Delete,
  View, Refresh, Clock, Document, Grid, Menu, Tickets,
} from '@element-plus/icons-vue'
import {
  listModels, getModelDetail, deleteModel, publishModel, updateModel,
  preview, generate, getHistory, getTaskStatus, deleteEntityFile,
  listFtpConfigs, saveFtpConfig, deleteFtpConfig, uploadToFtp,
  getEnumKeys, listRefFiles,
} from '@/api/meta-gen'
import ModelEditDialog from '@/components/meta-gen/ModelEditDialog.vue'
import FieldEditDialog from '@/components/meta-gen/FieldEditDialog.vue'
import EnumManageDialog from '@/components/meta-gen/EnumManageDialog.vue'
import RefFileDialog from '@/components/meta-gen/RefFileDialog.vue'
import TemplateManageDialog from '@/components/meta-gen/TemplateManageDialog.vue'

// ─── Types ───────────────────────────────────────────────────

interface ModelRecord {
  id: number
  modelName: string
  status: string
  encoding: string
  splitType: string
}

interface ModelDetailData {
  id?: number
  modelName?: string
  encoding?: string
  splitType?: string
  delimiter?: string
  lineEndingChar?: string
  maxRowsLimit?: number
  hasHeader?: number
  hasFooter?: number
  sharedWith?: string
  status?: string
  modelVersion?: string
  [key: string]: unknown
}

interface FieldItem {
  id?: number | string
  section: string
  level: number
  fieldKey: string
  fieldName: string
  length: number | null
  isRequired: number
  paddingDirection: string
  paddingChar: string
  ruleType: string
  ruleConfigJson: string | null
  sortIndex: number
  parentId?: number | string | null
}

interface HistoryRecord {
  id: number
  fileName: string
  fileType: string
  rowCount: number
  status: string
  durationMs: number
  createTime: string
  errorMsg?: string
}

interface FtpConfig {
  id?: number | null
  name: string
  ftpIp: string
  ftpPort: number
  username: string
  password: string
  remotePath: string
  updateTime?: string
}

interface DetailResponse {
  model?: ModelDetailData
  fields?: FieldItem[]
}

interface SectionInfo {
  key: string
  label: string
  iconComponent: typeof Document
}

// ─── Component Refs ──────────────────────────────────────────

const modelEditDialogRef = ref<InstanceType<typeof ModelEditDialog> | null>(null)
const fieldEditDialogRef = ref<InstanceType<typeof FieldEditDialog> | null>(null)
const templateManageDialogRef = ref<InstanceType<typeof TemplateManageDialog> | null>(null)

const rulerRef = ref<HTMLElement | null>(null)
const rulerTextRef = ref<HTMLElement | null>(null)
const previewBoxRef = ref<HTMLElement | null>(null)
const ftpFormRef = ref<FormInstance>()

// ─── Models ──────────────────────────────────────────────────

const models = ref<ModelRecord[]>([])
const modelLoading = ref(false)
const activeModelId = ref<number | null>(null)

// ─── Model Detail ────────────────────────────────────────────

const modelInfo = ref<ModelDetailData>({})
const allFields = ref<FieldItem[]>([])
const detailLoading = ref(false)

// ─── Preview ─────────────────────────────────────────────────

const previewText = ref('')
const previewLoading = ref(false)

// ─── History ─────────────────────────────────────────────────

const historyList = ref<HistoryRecord[]>([])
const historyLoading = ref(false)

// ─── Generation ──────────────────────────────────────────────

const showGenDialog = ref(false)
const genRowCount = ref(100)
const genBatchName = ref('')
const genLoading = ref(false)

// ─── Resources ───────────────────────────────────────────────

const enumKeys = ref<string[]>([])
const refFiles = ref<{ id: number; refName: string }[]>([])
const showEnumDialog = ref(false)
const showRefFileDialog = ref(false)

// ─── FTP Upload ──────────────────────────────────────────────

const showUploadDialog = ref(false)
const uploadFileRow = ref<HistoryRecord | null>(null)
const selectedFtpId = ref<number | null>(null)
const uploadLoading = ref(false)

// ─── FTP Config ──────────────────────────────────────────────

const showFtpDialog = ref(false)
const ftpConfigs = ref<FtpConfig[]>([])
const ftpLoading = ref(false)
const ftpSubmitting = ref(false)
const ftpFormMode = ref<'create' | 'edit'>('create')

const ftpForm = reactive<FtpConfig>({
  id: null,
  name: '',
  ftpIp: '',
  ftpPort: 21,
  username: '',
  password: '',
  remotePath: '',
})

const ftpRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  ftpIp: [{ required: true, message: '请输入FTP IP', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  remotePath: [{ required: true, message: '请输入远程路径', trigger: 'blur' }],
}

// ─── Computed ────────────────────────────────────────────────

const visibleSections = computed<SectionInfo[]>(() => {
  const secs: SectionInfo[] = [{ key: 'FILENAME', label: '文件名 (FILENAME)', iconComponent: Document }]
  if (modelInfo.value.hasHeader) {
    secs.push({ key: 'HEADER', label: '文件头 (HEADER)', iconComponent: Grid })
  }
  secs.push({ key: 'BODY', label: '文件体 (BODY)', iconComponent: Menu })
  if (modelInfo.value.hasFooter) {
    secs.push({ key: 'FOOTER', label: '文件尾 (FOOTER)', iconComponent: Tickets })
  }
  return secs
})

const rulerContent = computed(() => {
  const base = '1234567890'
  if (!previewText.value) {
    let result = ''
    for (let i = 0; i < 10; i++) result += base
    return result
  }
  let maxWidth = 0
  const lines = previewText.value.split('\n')
  for (const line of lines) {
    let width = 0
    for (const ch of line) {
      width += isFullWidthChar(ch) ? 2 : 1
    }
    if (width > maxWidth) maxWidth = width
  }
  const total = Math.max(maxWidth + 10, 100)
  let result = ''
  for (let i = 0; i < Math.ceil(total / 10); i++) {
    result += base
  }
  return result
})

// ─── Lifecycle ───────────────────────────────────────────────

onMounted(() => {
  loadModels()
  loadResources()
})

// ─── Model List ──────────────────────────────────────────────

async function loadModels() {
  modelLoading.value = true
  try {
    const res = await listModels({ page: 1, size: 200 })
    const pageData = res as unknown as { records?: ModelRecord[] }
    models.value = pageData?.records || []
  } finally {
    modelLoading.value = false
  }
}

async function handleSelectModel(modelId: number) {
  activeModelId.value = modelId
  detailLoading.value = true
  try {
    const detail = (await getModelDetail(String(modelId))) as unknown as DetailResponse
    modelInfo.value = (detail.model || detail) as ModelDetailData
    const fields = (detail.fields || []) as FieldItem[]
    fields.sort((a, b) => (a.sortIndex || 0) - (b.sortIndex || 0))
    allFields.value = fields
    previewText.value = ''
    loadHistory()
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail() {
  if (activeModelId.value) {
    await handleSelectModel(activeModelId.value)
  }
}

function handleCreateModel() {
  modelEditDialogRef.value?.init(null)
}

function handleManageTemplates() {
  templateManageDialogRef.value?.init()
}

function openModelEdit() {
  modelEditDialogRef.value?.init({ ...modelInfo.value, modelName: modelInfo.value.modelName || '' } as Parameters<typeof modelEditDialogRef.value.init>[0])
}

// ─── Field Sections ──────────────────────────────────────────

function getSectionFields(section: string): FieldItem[] {
  return allFields.value.filter(f => f.section === section)
}

function computeSortOrder(idx: number, section: string): string {
  const fields = getSectionFields(section)
  if (idx >= fields.length) return ''
  const f = fields[idx]
  if (f.level === 1) {
    let count = 0
    for (let i = 0; i <= idx; i++) {
      if (fields[i].level === 1) count++
    }
    return count + '.0'
  }
  for (let i = idx - 1; i >= 0; i--) {
    if (fields[i].level === 1) {
      let sub = 0
      for (let j = i + 1; j <= idx; j++) {
        if (fields[j].level === 2) sub++
      }
      return (i + 1) + '.' + sub
    }
  }
  return '?.?'
}

function openFieldEdit(section: string) {
  fieldEditDialogRef.value?.init(
    activeModelId.value!,
    section,
    getSectionFields(section),
    (modelInfo.value.modelName as string) || '',
  )
}

// ─── Publish / Delete / Unpublish ────────────────────────────

function handlePublishConfirm() {
  ElMessageBox.confirm('确定要发布该模型吗？发布后字段将不可编辑，需退回草稿才能修改。', '发布确认', {
    confirmButtonText: '确定发布',
    type: 'warning',
  }).then(() => {
    publishModel(String(activeModelId.value!)).then(() => {
      ElMessage.success('发布成功')
      refreshDetail()
      loadModels()
    })
  }).catch(() => {})
}

function handleUnpublish() {
  ElMessageBox.confirm('确定退回草稿吗？退回后可以重新编辑字段定义。', '退回确认', {
    confirmButtonText: '确定退回',
    type: 'info',
  }).then(() => {
    const data = { ...modelInfo.value, status: 'DRAFT' }
    updateModel(String(activeModelId.value!), data).then(() => {
      ElMessage.success('已退回草稿')
      refreshDetail()
      loadModels()
    }).catch((err: Error) => {
      ElMessage.error('退回草稿失败: ' + (err.message || '未知错误'))
    })
  }).catch(() => {})
}

function handleDeleteConfirm() {
  ElMessageBox.confirm('确定要删除该模型及其所有字段定义吗？此操作不可恢复。', '删除确认', {
    confirmButtonText: '确定删除',
    type: 'warning',
  }).then(() => {
    deleteModel(String(activeModelId.value!)).then(() => {
      ElMessage.success('删除成功')
      activeModelId.value = null
      modelInfo.value = {}
      allFields.value = []
      loadModels()
    })
  }).catch(() => {})
}

// ─── Preview ─────────────────────────────────────────────────

function handlePreview() {
  previewLoading.value = true
  preview(String(activeModelId.value!))
    .then((res: unknown) => {
      previewText.value = (res as string) || ''
      nextTick(() => syncRulerWidth())
    })
    .catch((err: Error) => {
      previewText.value = '预览请求失败: ' + (err.message || '未知错误')
    })
    .finally(() => { previewLoading.value = false })
}

function syncRulerScroll() {
  if (rulerRef.value && previewBoxRef.value) {
    rulerRef.value.scrollLeft = previewBoxRef.value.scrollLeft
  }
}

function syncRulerWidth() {
  const preview = previewBoxRef.value
  const rulerText = rulerTextRef.value
  if (preview && rulerText) {
    rulerText.style.display = 'inline-block'
    rulerText.style.minWidth = (preview.scrollWidth || preview.offsetWidth || 0) + 'px'
  }
}

function isFullWidthChar(ch: string): boolean {
  const cp = ch.codePointAt(0)
  if (!cp) return false
  if (cp >= 0x4E00 && cp <= 0x9FFF) return true
  if (cp >= 0x3400 && cp <= 0x4DBF) return true
  if (cp >= 0xF900 && cp <= 0xFAFF) return true
  if (cp >= 0x20000 && cp <= 0x2FFFF) return true
  if (cp >= 0xFF01 && cp <= 0xFF60) return true
  if (cp >= 0xFFE0 && cp <= 0xFFE6) return true
  if (cp >= 0x3000 && cp <= 0x303F) return true
  if (cp >= 0x3040 && cp <= 0x30FF) return true
  if (cp >= 0xAC00 && cp <= 0xD7AF) return true
  if (cp >= 0x1100 && cp <= 0x11FF) return true
  return false
}

// ─── Batch Generation ────────────────────────────────────────

function doGenerate() {
  if (genRowCount.value > (modelInfo.value.maxRowsLimit || 100000)) {
    ElMessage.error('生成行数超过模型最大行数限制，请调整')
    return
  }
  genLoading.value = true
  generate({ modelId: String(activeModelId.value!), rowCount: genRowCount.value, batchName: genBatchName.value })
    .then((res: unknown) => {
      ElMessage.success('生成任务已提交')
      showGenDialog.value = false
      loadHistory()
      const taskId = (res as Record<string, unknown>)?.id as number | undefined
      if (taskId) {
        const timer = setInterval(() => {
          getTaskStatus(String(taskId)).then((r: unknown) => {
            const record = r as HistoryRecord
            if (record && record.status !== 'RUNNING') {
              clearInterval(timer)
              loadHistory()
              if (record.status === 'SUCCESS') ElMessage.success('文件生成完成')
              else ElMessage.error('文件生成失败: ' + (record.errorMsg || ''))
            }
          }).catch(() => { clearInterval(timer) })
        }, 2000)
      }
    })
    .catch((err: Error) => {
      ElMessage.error(err.message || '请求生成失败')
    })
    .finally(() => { genLoading.value = false })
}

// ─── History ─────────────────────────────────────────────────

function loadHistory() {
  if (!activeModelId.value) return
  historyLoading.value = true
  getHistory(String(activeModelId.value)).then((res: unknown) => {
    historyList.value = (res as HistoryRecord[]) || []
  }).finally(() => { historyLoading.value = false })
}

function handleDownload(row: HistoryRecord) {
  window.open('/api/meta/execute/download/' + row.id, '_blank')
}

function handleDeleteFile(row: HistoryRecord) {
  ElMessageBox.confirm('确定删除该文件记录？', '提示', { type: 'warning' }).then(() => {
    deleteEntityFile(String(row.id)).then(() => {
      ElMessage.success('删除成功')
      loadHistory()
    })
  }).catch(() => {})
}

// ─── FTP Upload ──────────────────────────────────────────────

function openUploadDialog(row: HistoryRecord) {
  uploadFileRow.value = row
  selectedFtpId.value = null
  showUploadDialog.value = true
  loadFtpConfigs()
}

function doUploadToFtp() {
  if (!selectedFtpId.value || !uploadFileRow.value) return
  uploadLoading.value = true
  uploadToFtp({ fileId: uploadFileRow.value.id, ftpConfigId: selectedFtpId.value })
    .then(() => {
      ElMessage.success('上传成功')
    })
    .catch((err: Error) => {
      ElMessage.error('上传失败: ' + (err.message || '未知错误'))
    })
    .finally(() => { uploadLoading.value = false })
}

// ─── FTP Config ──────────────────────────────────────────────

function loadFtpConfigs() {
  ftpLoading.value = true
  listFtpConfigs().then((res: unknown) => {
    ftpConfigs.value = (res as FtpConfig[]) || []
  }).finally(() => { ftpLoading.value = false })
}

function editFtpConfig(row: FtpConfig) {
  ftpFormMode.value = 'edit'
  ftpForm.id = row.id ?? null
  ftpForm.name = row.name
  ftpForm.ftpIp = row.ftpIp
  ftpForm.ftpPort = row.ftpPort || 21
  ftpForm.username = row.username
  ftpForm.password = row.password
  ftpForm.remotePath = row.remotePath
}

function resetFtpForm() {
  ftpFormMode.value = 'create'
  ftpForm.id = null
  ftpForm.name = ''
  ftpForm.ftpIp = ''
  ftpForm.ftpPort = 21
  ftpForm.username = ''
  ftpForm.password = ''
  ftpForm.remotePath = ''
  ftpFormRef.value?.resetFields()
}

function submitFtpForm() {
  ftpFormRef.value?.validate((valid: boolean) => {
    if (!valid) return
    ftpSubmitting.value = true
    saveFtpConfig({ ...ftpForm }).then(() => {
      ElMessage.success(ftpFormMode.value === 'create' ? '新增成功' : '修改成功')
      resetFtpForm()
      loadFtpConfigs()
    }).finally(() => { ftpSubmitting.value = false })
  })
}

function handleDeleteFtp(row: FtpConfig) {
  ElMessageBox.confirm(`确定删除FTP配置"${row.name}"？`, '提示', { type: 'warning' }).then(() => {
    deleteFtpConfig(String(row.id!)).then(() => {
      ElMessage.success('删除成功')
      loadFtpConfigs()
    })
  }).catch(() => {})
}

// ─── Resources ───────────────────────────────────────────────

function loadResources() {
  getEnumKeys().then((res: unknown) => { enumKeys.value = (res as string[]) || [] })
  listRefFiles().then((res: unknown) => { refFiles.value = (res as { id: number; refName: string }[]) || [] })
}

// ─── Utility ─────────────────────────────────────────────────

function ruleTypeToChinese(ruleType: string): string {
  if (!ruleType) return '-'
  const map: Record<string, string> = {
    'FIXED': '固定值',
    'DATE': '日期',
    'ENUM': '枚举',
    'SEQUENCE': '序列号',
    'RANDOM_CN': '随机汉字',
    'RANDOM_NUM': '随机数字',
    'RANDOM_UUID': '随机UUID',
    'REF_FILE': '引用文件',
    'REF_FIELD': '引用字段',
    'SUM': '汇总金额',
    'COUNT': '统计行数',
    'BATCH_NO': '批次号',
    'AMOUNT': '金额',
    'EXPR': '表达式',
  }
  return map[ruleType] || ruleType
}
</script>

<style scoped>
.meta-gen-page {
  padding: 20px;
  height: 100%;
  background: linear-gradient(180deg, #f5f8ff 0%, #eef3fb 100%);
  box-sizing: border-box;
  overflow: hidden;
}
.meta-main-content {
  display: flex;
  height: 100%;
  gap: 18px;
}

/* Left sidebar */
.sidebar-container {
  width: 280px;
  display: flex;
  flex-direction: column;
  height: 100%;
}
.model-list-sidebar {
  padding: 16px 14px;
  border: 1px solid #d9e3f2;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(16, 43, 98, 0.08);
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  flex-shrink: 0;
}
.sidebar-header h3 {
  margin: 0;
  font-size: 14px;
  color: #1f2d3d;
  flex-shrink: 0;
}
.sidebar-header-btns {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.model-list-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
.model-list-content::-webkit-scrollbar { width: 6px; }
.model-list-content::-webkit-scrollbar-thumb {
  background: #c8d8f0;
  border-radius: 8px;
}
.no-models-tip {
  color: #92a1b7;
  text-align: center;
  padding: 20px;
  font-size: 13px;
}
.model-items-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.model-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-radius: 10px;
  background: #f8faff;
  margin-bottom: 10px;
  transition: all 0.2s;
  cursor: pointer;
  border: 1px solid transparent;
}
.model-item:hover {
  background: #f0f5ff;
  transform: translateY(-1px);
}
.model-item.active {
  background: #eaf4ff;
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}
.model-info { flex: 1; min-width: 0; margin-right: 10px; }
.model-name { font-size: 13px; color: #2c3e50; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-weight: 600; }
.model-meta { font-size: 11px; color: #909399; margin-top: 4px; }

/* Right detail */
.detail-content-area {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid #d9e3f2;
  border-radius: 16px;
  padding: 20px;
  background: #ffffff;
  box-shadow: 0 12px 24px rgba(16, 43, 98, 0.08);
  overflow: hidden;
}
.detail-content-area > .empty-state {
  background: transparent;
}
.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.detail-scroll-container {
  flex: 1;
  overflow-y: auto;
  padding: 0 5px 12px 0;
}
.detail-scroll-container::-webkit-scrollbar { width: 6px; }
.detail-scroll-container::-webkit-scrollbar-thumb {
  background: #c8d8f0;
  border-radius: 8px;
}

.info-block {
  margin-bottom: 20px;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  overflow: hidden;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.block-title {
  font-weight: bold;
  color: #303133;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.block-title .el-icon {
  color: #409eff;
}
.header-actions {
  display: flex;
  gap: 8px;
}

/* Preview */
.preview-block { background: #1e1e1e; border-color: #333; }
.preview-block :deep(.el-card__header) { border-bottom-color: #333; }
.preview-block :deep(.card-header) { color: #ccc; }
.preview-block :deep(.block-title .el-icon) { color: #67c23a; }
.byte-ruler {
  height: 20px;
  background: #2d2d2d;
  position: relative;
  border-bottom: 1px solid #444;
  overflow: hidden;
  margin: 0 -16px;
  padding: 0 10px 0 26px;
}
.ruler-text {
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  color: #888;
  white-space: pre;
}
.preview-box {
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  padding: 10px;
  min-height: 60px;
  white-space: pre;
  overflow-x: auto;
  margin: 0;
  border-radius: 0 0 4px 4px;
}
.preview-box.empty { color: #666; }
</style>
