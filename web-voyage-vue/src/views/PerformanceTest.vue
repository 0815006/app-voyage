<template>
  <div class="performance-page">
    <div class="performance-main-content">
      <!-- 左侧任务列表 -->
      <div class="sidebar-container">
        <div class="task-list-sidebar">
          <div class="sidebar-header">
            <h3>性能测试任务</h3>
            <div class="header-actions">
              <el-button
                type="info"
                size="small"
                :icon="Setting"
                circle
                title="模板维护"
                @click="handleManageTemplates"
              ></el-button>
              <el-button
                type="primary"
                size="small"
                :icon="Plus"
                circle
                title="新建任务"
                @click="handleCreateTask"
              ></el-button>
            </div>
          </div>
          <div class="task-list-content" v-loading="loading">
            <div v-if="taskList.length === 0" class="no-tasks-tip">暂无任务</div>
            <ul class="task-items-list">
              <li
                v-for="item in taskList"
                :key="item.id"
                class="task-item"
                :class="{ active: activeTaskId === String(item.id) }"
                @click="handleSelectTask(String(item.id))"
              >
                <div class="task-info">
                  <div class="task-name" :title="item.taskName">{{ item.taskName || '未命名任务' }}</div>
                  <div class="task-meta">
                    {{ item.batchNo }} · {{ item.productId }}
                  </div>
                </div>
                <el-icon><ArrowRight /></el-icon>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <!-- 右侧详情展示区 -->
      <div class="detail-content-area">
        <div v-if="!activeTaskId" class="empty-state">
          <el-empty description="请选择或新建一个性能测试任务"></el-empty>
        </div>
        <div v-else class="detail-scroll-container">
          <!-- 任务信息区块 -->
          <el-card class="info-block" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="block-title"><el-icon><InfoFilled /></el-icon> 任务信息</span>
                <el-button v-if="isCreator" type="primary" size="small" :icon="Edit" @click="openTaskInfoEdit">编辑</el-button>
              </div>
            </template>
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="任务名称">{{ taskInfo.taskName }}</el-descriptions-item>
              <el-descriptions-item label="测试任务编号">{{ taskInfo.testTaskNo }}</el-descriptions-item>
              <el-descriptions-item label="生产任务编号">{{ taskInfo.prodTaskNo }}</el-descriptions-item>
              <el-descriptions-item label="产品/牵头组件">{{ taskInfo.productId }}</el-descriptions-item>
              <el-descriptions-item label="批次">{{ taskInfo.batchNo }}</el-descriptions-item>
              <el-descriptions-item label="需求编号">{{ taskInfo.reqNo }}</el-descriptions-item>
              <el-descriptions-item label="项目名称">{{ taskInfo.projName }}</el-descriptions-item>
              <el-descriptions-item label="项目编号">{{ taskInfo.projNo }}</el-descriptions-item>
              <el-descriptions-item label="测试部门">{{ taskInfo.testDept }}</el-descriptions-item>
              <el-descriptions-item label="开发部门">{{ taskInfo.devDept }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ taskInfo.startTime }}</el-descriptions-item>
              <el-descriptions-item label="结束时间">{{ taskInfo.endTime }}</el-descriptions-item>
              <el-descriptions-item label="性能测试经理">{{ taskInfo.perfManager }}</el-descriptions-item>
              <el-descriptions-item label="测试架构师">{{ taskInfo.testArch }}</el-descriptions-item>
              <el-descriptions-item label="项目经理">{{ taskInfo.projectManager }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 联机交易区块 -->
          <el-card class="info-block" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="block-title"><el-icon><Connection /></el-icon> 联机交易调研与方案</span>
                <el-button v-if="canEdit" type="primary" size="small" :icon="Edit" @click="openTranEdit">编辑</el-button>
              </div>
            </template>
            <div v-if="taskInfo.totalUserCount || taskInfo.dailyPeakTps || taskInfo.selectedTranTpsSum" class="summary-display">
              <el-tag size="small" type="success">用户总数: {{ taskInfo.totalUserCount }}</el-tag>
              <el-tag size="small" type="success">日均在线: {{ taskInfo.dailyOnlineUserCount }}</el-tag>
              <el-tag size="small" type="warning">日峰值TPS: {{ taskInfo.dailyPeakTps }}</el-tag>
              <el-tag size="small" type="warning">年峰值TPS: {{ taskInfo.annualPeakTps }}</el-tag>
              <el-tag size="small" type="danger" effect="dark">选中交易TPS之和: {{ taskInfo.selectedTranTpsSum || 0 }}</el-tag>
            </div>
            <el-table :data="trans" border stripe size="small">
              <el-table-column prop="moduleName" label="模块" width="100" show-overflow-tooltip></el-table-column>
              <el-table-column prop="tranName" label="交易名称" min-width="150" show-overflow-tooltip></el-table-column>
              <el-table-column prop="tranCode" label="交易代码" width="100" show-overflow-tooltip></el-table-column>
              <el-table-column label="生产现状" align="center">
                <el-table-column prop="tranPeakTps" label="最大TPS" width="80"></el-table-column>
                <el-table-column prop="tranAvgRt" label="平均RT" width="80"></el-table-column>
              </el-table-column>
              <el-table-column label="指标要求" align="center">
                <el-table-column prop="targetTps" label="目标TPS" width="80"></el-table-column>
                <el-table-column prop="targetRt" label="目标RT" width="80"></el-table-column>
                <el-table-column prop="targetSuccessRate" label="成功率%" width="70"></el-table-column>
              </el-table-column>
              <el-table-column label="选中" width="60" align="center">
                <template #default="scope">
                  <el-icon v-if="scope.row.isSelected" style="color: #67C23A"><SuccessFilled /></el-icon>
                  <el-icon v-else style="color: #909399"><InfoFilled /></el-icon>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 批量作业区块 -->
          <el-card class="info-block" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="block-title"><el-icon><Tickets /></el-icon> 批量作业调研与方案</span>
                <el-button v-if="canEdit" type="primary" size="small" :icon="Edit" @click="openBatchEdit">编辑</el-button>
              </div>
            </template>
            <div v-if="taskInfo.batchTotalDuration" class="summary-display">
              <el-tag size="small" type="info">整体时长: {{ taskInfo.batchTotalDuration }}</el-tag>
              <el-tag size="small" type="info">整体数据量: {{ taskInfo.batchTotalDataVolume }}</el-tag>
              <el-tag size="small" type="info">并行度: {{ taskInfo.batchParallelDegree }}</el-tag>
              <el-tag size="small" type="info">最大并行数: {{ taskInfo.batchMaxParallelCount }}</el-tag>
            </div>
            <el-table :data="batches" border stripe size="small">
              <el-table-column prop="jobNo" label="编号" width="70"></el-table-column>
              <el-table-column prop="jobName" label="作业名称" min-width="150" show-overflow-tooltip></el-table-column>
              <el-table-column prop="jobDataType" label="数据类型" width="90" show-overflow-tooltip></el-table-column>
              <el-table-column prop="jobDataVolume" label="预估数据量" width="100" show-overflow-tooltip></el-table-column>
              <el-table-column prop="jobActualDuration" label="实际运行时长" width="120"></el-table-column>
              <el-table-column prop="jobDuration" label="预估时长" min-width="120" show-overflow-tooltip></el-table-column>
              <el-table-column prop="jobExecTimePoint" label="执行时间点" min-width="120" show-overflow-tooltip></el-table-column>
              <el-table-column label="混合/重做" width="90" align="center">
                <template #default="scope">
                  <el-tooltip :content="'叠加联机: ' + scope.row.isMixedLink" placement="top">
                    <el-tag :type="scope.row.isMixedLink === '是' ? 'warning' : 'info'" size="small" style="margin-right: 5px">混</el-tag>
                  </el-tooltip>
                  <el-tooltip :content="'重做机制: ' + scope.row.hasRetry" placement="top">
                    <el-tag :type="scope.row.hasRetry === '是' ? 'success' : 'info'" size="small">重</el-tag>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 数据准备区块 -->
          <el-card class="info-block" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="block-title"><el-icon><Coin /></el-icon> 数据准备方案</span>
                <el-button v-if="canEdit" type="primary" size="small" :icon="Edit" @click="openDataEdit">编辑</el-button>
              </div>
            </template>
            <el-table :data="datas" border stripe size="small">
              <el-table-column label="分类" width="90">
                <template #default="scope">
                  <el-tag :type="scope.row.dataType === 2 ? 'info' : 'primary'" size="small">
                    {{ scope.row.dataType === 2 ? '基础数据' : '核心业务' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="tableNameEn" label="英文表名" min-width="120" show-overflow-tooltip></el-table-column>
              <el-table-column prop="tableNameCn" label="中文表名" min-width="120" show-overflow-tooltip></el-table-column>
              <el-table-column label="生产存量(万)" width="100" align="right">
                <template #default="scope">{{ scope.row.tableRowsCount }}</template>
              </el-table-column>
              <el-table-column label="目标造数(万)" width="100" align="right">
                <template #default="scope">{{ scope.row.targetRowsCount }}</template>
              </el-table-column>
              <el-table-column prop="prepMethod" label="准备方式" width="100"></el-table-column>
              <el-table-column prop="dataDistDesc" label="数据特征分布" show-overflow-tooltip></el-table-column>
            </el-table>
          </el-card>

          <!-- 测试场景区块 -->
          <el-card class="info-block" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="block-title"><el-icon><VideoPlay /></el-icon> 测试场景定义</span>
                <el-button v-if="canEdit" type="primary" size="small" :icon="Edit" @click="openSceneEdit">编辑</el-button>
              </div>
            </template>
            <el-table :data="scenes" border stripe size="small">
              <el-table-column prop="sceneName" label="场景名称" min-width="180"></el-table-column>
              <el-table-column prop="targetTpsRatio" label="TPS比例(%)" width="100"></el-table-column>
              <el-table-column prop="targetTotalTps" label="预期总TPS" width="100"></el-table-column>
              <el-table-column prop="globalDuration" label="持续时间(分)" width="100"></el-table-column>
              <el-table-column label="状态" width="80">
                <template #default="scope">
                  <el-tag :type="scope.row.isSelected ? 'primary' : 'info'" size="small">{{ scope.row.isSelected ? '已选' : '未选' }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 方案报告文档区块 -->
          <el-card class="info-block" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="block-title"><el-icon><Document /></el-icon> 方案报告文档</span>
                <el-button type="success" size="small" :icon="Plus" @click="handleGenerateDoc" :loading="genLoading">生成方案</el-button>
              </div>
            </template>
            <el-table :data="docList" border stripe size="small" v-loading="docLoading" empty-text="暂无生成文档，点击生成方案按钮生成">
              <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip></el-table-column>
              <el-table-column label="大小" width="100" align="center">
                <template #default="scope">{{ formatFileSize(scope.row.fileSize) }}</template>
              </el-table-column>
              <el-table-column label="修改时间" width="160" align="center">
                <template #default="scope">{{ formatDateTime(scope.row.lastModified) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="150" align="center">
                <template #default="scope">
                  <el-button link :icon="Download" @click="handleDownloadDoc(scope.row)">下载</el-button>
                  <el-button link :icon="Delete" style="color:#F56C6C" @click="handleDeleteDoc(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 弹窗组件 -->
    <create-task-dialog ref="createTaskDialogRef" @refresh="fetchTaskList" />
    <task-info-edit-dialog ref="taskInfoEditDialogRef" @refresh="refreshDetail" />
    <tran-edit-dialog ref="tranEditDialogRef" @refresh="refreshDetail" />
    <batch-edit-dialog ref="batchEditDialogRef" @refresh="refreshDetail" />
    <data-edit-dialog ref="dataEditDialogRef" @refresh="refreshDetail" />
    <scene-edit-dialog ref="sceneEditDialogRef" @refresh="refreshDetail" />
    <template-manage-dialog ref="templateManageDialogRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Setting,
  Plus,
  ArrowRight,
  InfoFilled,
  Edit,
  Connection,
  SuccessFilled,
  Tickets,
  Coin,
  VideoPlay,
  Document,
  Download,
  Delete,
} from '@element-plus/icons-vue'
import { listTasks, getTaskDetail, generateDoc, listDocs, deleteDoc, getDocDownloadUrl } from '@/api/performance'
import { getCurrentEmpNo } from '@/utils/currentUser'
import CreateTaskDialog from '@/components/performance-test/CreateTaskDialog.vue'
import TaskInfoEditDialog from '@/components/performance-test/TaskInfoEditDialog.vue'
import TranEditDialog from '@/components/performance-test/TranEditDialog.vue'
import BatchEditDialog from '@/components/performance-test/BatchEditDialog.vue'
import DataEditDialog from '@/components/performance-test/DataEditDialog.vue'
import SceneEditDialog from '@/components/performance-test/SceneEditDialog.vue'
import TemplateManageDialog from '@/components/performance-test/TemplateManageDialog.vue'

// ── 类型定义 ──
interface TaskSummary {
  id?: number
  taskName?: string
  batchNo?: string
  productId?: string
}

interface TaskInfo {
  id?: number
  taskName?: string
  testTaskNo?: string
  prodTaskNo?: string
  productId?: string
  batchNo?: string
  reqNo?: string
  projName?: string
  projNo?: string
  testDept?: string
  devDept?: string
  startTime?: string
  endTime?: string
  perfManager?: string
  testArch?: string
  projectManager?: string
  creatorId?: string
  recorderRange?: string
  totalUserCount?: number
  dailyOnlineUserCount?: number
  dailyPeakTps?: number
  annualPeakTps?: number
  selectedTranTpsSum?: number
  batchTotalDuration?: string
  batchTotalDataVolume?: string
  batchParallelDegree?: string
  batchMaxParallelCount?: string
}

interface TranView {
  moduleName?: string
  tranName?: string
  tranCode?: string
  tranPeakTps?: number
  tranAvgRt?: number
  targetTps?: number
  targetRt?: number
  targetSuccessRate?: number
  isSelected?: number
}

interface BatchView {
  jobNo?: string
  jobName?: string
  jobDataType?: string
  jobDataVolume?: string
  jobActualDuration?: string
  jobDuration?: string
  jobExecTimePoint?: string
  isMixedLink?: string
  hasRetry?: string
}

interface DataView {
  dataType?: number
  tableNameEn?: string
  tableNameCn?: string
  tableRowsCount?: number
  targetRowsCount?: number
  prepMethod?: string
  dataDistDesc?: string
}

interface SceneView {
  sceneName?: string
  targetTpsRatio?: number
  targetTotalTps?: number
  globalDuration?: number
  isSelected?: number
}

interface DocItem {
  fileName: string
  fileSize: number
  lastModified: string
}

// ── 组件实例引用 ──
const createTaskDialogRef = ref<InstanceType<typeof CreateTaskDialog>>()
const taskInfoEditDialogRef = ref<InstanceType<typeof TaskInfoEditDialog>>()
const tranEditDialogRef = ref<InstanceType<typeof TranEditDialog>>()
const batchEditDialogRef = ref<InstanceType<typeof BatchEditDialog>>()
const dataEditDialogRef = ref<InstanceType<typeof DataEditDialog>>()
const sceneEditDialogRef = ref<InstanceType<typeof SceneEditDialog>>()
const templateManageDialogRef = ref<InstanceType<typeof TemplateManageDialog>>()

// ── 状态 ──
const taskList = ref<TaskSummary[]>([])
const activeTaskId = ref<string | null>(null)
const taskInfo = ref<TaskInfo>({})
const trans = ref<TranView[]>([])
const batches = ref<BatchView[]>([])
const datas = ref<DataView[]>([])
const scenes = ref<SceneView[]>([])
const docList = ref<DocItem[]>([])
const docLoading = ref<boolean>(false)
const genLoading = ref<boolean>(false)
const currentUser = getCurrentEmpNo()
const loading = ref<boolean>(false)

// ── 计算属性 ──
const isCreator = computed<boolean>(() => {
  return taskInfo.value.creatorId === currentUser
})

const canEdit = computed<boolean>(() => {
  if (isCreator.value) return true
  if (!taskInfo.value.recorderRange) return false
  const range = taskInfo.value.recorderRange.split(',').map((s: string) => s.trim())
  return range.includes(currentUser)
})

// ── 方法 ──
async function fetchTaskList() {
  loading.value = true
  try {
    const data = await listTasks({})
    taskList.value = (data as TaskSummary[]) || []
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function handleSelectTask(taskId: string) {
  activeTaskId.value = taskId
  try {
    const data = await getTaskDetail(taskId)
    if (data) {
      const detail = data as unknown as {
        task: TaskInfo
        trans: TranView[]
        batches: BatchView[]
        datas: DataView[]
        scenes: SceneView[]
      }
      taskInfo.value = detail.task || {}
      trans.value = detail.trans || []
      batches.value = detail.batches || []
      datas.value = detail.datas || []
      scenes.value = detail.scenes || []
      fetchDocList()
    }
  } catch {
    // error handled by interceptor
  }
}

function refreshDetail() {
  if (activeTaskId.value) {
    handleSelectTask(activeTaskId.value)
  }
}

function handleCreateTask() {
  createTaskDialogRef.value?.init()
}

function handleManageTemplates() {
  templateManageDialogRef.value?.init()
}

function openTaskInfoEdit() {
  taskInfoEditDialogRef.value?.init(taskInfo.value)
}

function openTranEdit() {
  tranEditDialogRef.value?.init(activeTaskId.value!, trans.value, taskInfo.value)
}

function openBatchEdit() {
  batchEditDialogRef.value?.init(activeTaskId.value!, batches.value, taskInfo.value)
}

function openDataEdit() {
  dataEditDialogRef.value?.init(activeTaskId.value!, datas.value)
}

function openSceneEdit() {
  sceneEditDialogRef.value?.init(activeTaskId.value!, trans.value)
}

async function fetchDocList() {
  docLoading.value = true
  try {
    const data = await listDocs()
    docList.value = (data as unknown as DocItem[]) || []
  } catch {
    // error handled by interceptor
  } finally {
    docLoading.value = false
  }
}

function handleGenerateDoc() {
  ElMessageBox.confirm('确认生成方案文档？', '生成方案', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(async () => {
      genLoading.value = true
      try {
        const result = await generateDoc(activeTaskId.value!)
        ElMessage.success('方案文档生成成功: ' + (result as string))
        fetchDocList()
      } catch (e: unknown) {
        const message = e instanceof Error ? e.message : '未知错误'
        ElMessage.error('生成失败: ' + message)
      } finally {
        genLoading.value = false
      }
    })
    .catch(() => {
      // user cancelled
    })
}

function handleDownloadDoc(row: DocItem) {
  window.open(getDocDownloadUrl(row.fileName), '_blank')
}

function handleDeleteDoc(row: DocItem) {
  ElMessageBox.confirm('确认删除 "' + row.fileName + '"？', '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      try {
        await deleteDoc(row.fileName)
        ElMessage.success('删除成功')
        fetchDocList()
      } catch {
        // error handled by interceptor
      }
    })
    .catch(() => {
      // user cancelled
    })
}

// ── 工具函数 (原 filters) ──
function formatFileSize(size: number): string {
  if (!size) return '0 B'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDateTime(val: string | number | Date): string {
  if (!val) return ''
  const d = new Date(val)
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    d.getFullYear() +
    '-' +
    pad(d.getMonth() + 1) +
    '-' +
    pad(d.getDate()) +
    ' ' +
    pad(d.getHours()) +
    ':' +
    pad(d.getMinutes()) +
    ':' +
    pad(d.getSeconds())
  )
}

// ── 生命周期 ──
onMounted(() => {
  fetchTaskList()
})
</script>

<style scoped>
.performance-page {
  padding: 20px;
  height: 100%;
  background: linear-gradient(180deg, #f5f8ff 0%, #eef3fb 100%);
  box-sizing: border-box;
  overflow: hidden;
}

.performance-main-content {
  display: flex;
  gap: 18px;
  height: 100%;
}

.sidebar-container {
  width: 280px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.task-list-sidebar {
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
}

.header-actions {
  display: flex;
  gap: 8px;
}

.task-list-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.task-list-content::-webkit-scrollbar {
  width: 6px;
}

.task-list-content::-webkit-scrollbar-thumb {
  background: #c8d8f0;
  border-radius: 8px;
}

.task-items-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.task-item {
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

.task-item:hover {
  background: #f0f5ff;
  transform: translateY(-1px);
}

.task-item.active {
  background: #eaf4ff;
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.task-info {
  flex: 1;
  min-width: 0;
  margin-right: 10px;
}

.task-name {
  font-size: 13px;
  color: #2c3e50;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 600;
}

.task-meta {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

.task-item .el-icon {
  color: #c0c4cc;
  font-size: 12px;
}

.task-item.active .el-icon {
  color: #409eff;
}

.no-tasks-tip {
  color: #92a1b7;
  text-align: center;
  padding: 20px;
  font-size: 13px;
}

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

.detail-scroll-container {
  flex: 1;
  overflow-y: auto;
  padding-right: 5px;
}

.detail-scroll-container::-webkit-scrollbar {
  width: 6px;
}

.detail-scroll-container::-webkit-scrollbar-thumb {
  background: #c8d8f0;
  border-radius: 8px;
}

.info-block {
  margin-bottom: 20px;
  border-radius: 12px;
  border: 1px solid #ebeef5;
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
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.summary-display {
  margin-bottom: 15px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px;
  background-color: #f8f9fb;
  border-radius: 8px;
}

@media (max-width: 960px) {
  .performance-main-content {
    flex-direction: column;
  }

  .sidebar-container {
    width: 100%;
    height: 300px;
  }
}
</style>
