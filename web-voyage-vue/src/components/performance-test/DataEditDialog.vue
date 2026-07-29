<template>
  <el-dialog title="编辑数据准备方案" v-model="visible" width="98%" top="2vh">
    <div class="dialog-layout">
      <!-- 左侧：明细表格 -->
      <div class="input-area">
        <div style="margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center;">
          <div>
            <el-button type="primary" size="small" :icon="Plus" @click="addRow">添加表</el-button>
            <el-button type="success" size="small" :icon="Download" @click="handleDownloadTemplate">下载数据准备明细模板</el-button>
            <el-upload
              style="display: inline-block; margin-left: 10px;"
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleUploadExcel"
              accept=".xls,.xlsx"
            >
              <el-button type="warning" size="small" :icon="Upload">上传明细解析</el-button>
            </el-upload>
          </div>
          <div class="table-tip">单位：万行</div>
        </div>

        <el-table :data="list" border size="small" height="65vh">
          <el-table-column label="数据分类" width="100">
            <template #default="scope">
              <el-select v-model="scope.row.dataType" size="small">
                <el-option :value="1" label="核心业务表"></el-option>
                <el-option :value="2" label="基础数据"></el-option>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="英文表名*" width="150">
            <template #default="scope"><el-input v-model="scope.row.tableNameEn" size="small"></el-input></template>
          </el-table-column>
          <el-table-column label="中文表名*" width="150">
            <template #default="scope"><el-input v-model="scope.row.tableNameCn" size="small"></el-input></template>
          </el-table-column>
          <el-table-column label="生产存量" width="100">
            <template #default="scope"><el-input-number v-model="scope.row.tableRowsCount" :controls="false" size="small" style="width: 100%"></el-input-number></template>
          </el-table-column>
          <el-table-column label="年增长率%" width="90">
            <template #default="scope"><el-input-number v-model="scope.row.tableGrowthRate" :controls="false" size="small" style="width: 100%"></el-input-number></template>
          </el-table-column>
          <el-table-column label="目标造数*" width="100">
            <template #default="scope"><el-input-number v-model="scope.row.targetRowsCount" :controls="false" size="small" style="width: 100%"></el-input-number></template>
          </el-table-column>
          <el-table-column label="数据特征分布" min-width="150">
            <template #default="scope"><el-input v-model="scope.row.dataDistDesc" type="textarea" :rows="1" size="small"></el-input></template>
          </el-table-column>
          <el-table-column label="准备方式" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.prepMethod" size="small"></el-input>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="50" fixed="right">
            <template #default="scope"><el-button link :icon="Delete" style="color: #F56C6C" @click="list.splice(scope.$index, 1)"></el-button></template>
          </el-table-column>
        </el-table>

        <div class="form-footer">
          <el-button type="primary" @click="handleSaveDetails" :loading="loading">保存明细数据</el-button>
        </div>
      </div>

      <!-- 右侧：定性描述方案 -->
      <div class="plan-area">
        <div class="plan-content">
          <h3>数据准备定性描述</h3>
          <el-form :model="planForm" label-position="top" size="default">
            <el-form-item label="2.1 相关数据内容分析">
              <el-input type="textarea" :rows="4" v-model="planForm.modelAnalysis" placeholder="推荐：分析系统核心业务实体及其关联关系..."></el-input>
            </el-form-item>
            <el-form-item label="2.4 数据约束说明">
              <el-input type="textarea" :rows="3" v-model="planForm.dataConstraint" placeholder="推荐：说明主外键约束、唯一性索引要求等..."></el-input>
            </el-form-item>
            <el-form-item label="3.1 数据来源说明">
              <el-input type="textarea" :rows="3" v-model="planForm.dataSourceDesc" placeholder="推荐：生产环境脱敏借数、自动化脚本自造等..."></el-input>
            </el-form-item>
            <el-form-item label="3.2 数据构造/准备方法描述">
              <el-input type="textarea" :rows="4" v-model="planForm.prepMethodDesc" placeholder="推荐：详细描述造数脚本逻辑或脱敏工具使用流程..."></el-input>
            </el-form-item>
            <el-form-item label="3.3 数据脱敏/清洗规则">
              <el-input type="textarea" :rows="3" v-model="planForm.cleaningRule" placeholder="推荐：敏感字段（姓名、证件号）的掩码规则..."></el-input>
            </el-form-item>
          </el-form>
          <div class="form-footer">
            <el-button type="success" @click="handleSavePlan" :loading="loading">保存定性方案</el-button>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Download, Upload, Delete } from '@element-plus/icons-vue'
import { saveDataDetails, getDataPlan, saveDataPlan, getTemplateDownloadByKeywordUrl, parseDataExcel } from '@/api/performance'

interface DataDetail {
  dataType?: number
  tableNameEn?: string
  tableNameCn?: string
  tableRowsCount?: number
  tableGrowthRate?: number
  targetRowsCount?: number
  dataDistDesc?: string
  prepMethod?: string
}

interface DataPlan {
  id?: number | null
  taskId?: number | null
  modelAnalysis?: string
  dataConstraint?: string
  dataSourceDesc?: string
  prepMethodDesc?: string
  cleaningRule?: string
}

const emit = defineEmits<{
  refresh: []
}>()

const visible = ref<boolean>(false)
const loading = ref<boolean>(false)
const taskId = ref<string | null>(null)
const list = ref<DataDetail[]>([])

const planForm = reactive<DataPlan>({
  id: null,
  taskId: null,
  modelAnalysis: '',
  dataConstraint: '',
  dataSourceDesc: '',
  prepMethodDesc: '',
  cleaningRule: '',
})

async function init(taskIdVal: string, details: DataDetail[]) {
  visible.value = true
  taskId.value = taskIdVal
  list.value = JSON.parse(JSON.stringify(details || []))
  planForm.taskId = Number(taskIdVal)

  const data = await getDataPlan(taskIdVal)
  if (data) {
    Object.assign(planForm, data as DataPlan)
  } else {
    planForm.modelAnalysis = '本系统核心数据模型包含客户信息、账户信息及交易流水。数据间存在强关联，需保证业务链路完整性。'
    planForm.dataConstraint = '1. 证件号与客户号唯一性约束；\n2. 账户余额与流水汇总一致性；\n3. 状态机流转约束。'
    planForm.dataSourceDesc = '核心业务数据采用生产脱敏借数方式；基础码表及配置数据采用全量同步方式。'
    planForm.prepMethodDesc = '1. 使用ETL工具从生产环境抽取数据；\n2. 经过脱敏平台进行关键字段掩码；\n3. 编写SQL脚本进行数据偏移处理以适配测试日期。'
    planForm.cleaningRule = '姓名：保留姓氏，名字掩码；\n证件号：保留前6后4位，中间掩码；\n手机号：保留前3后4位。'
  }
}

function addRow() {
  list.value.push({
    dataType: 1,
    tableNameEn: '',
    tableNameCn: '',
    tableRowsCount: 0,
    tableGrowthRate: 0,
    targetRowsCount: 0,
    dataDistDesc: '',
    prepMethod: '脚本自造',
  })
}

function handleDownloadTemplate() {
  window.open(getTemplateDownloadByKeywordUrl('数据准备'), '_blank')
}

async function handleUploadExcel(file: { raw: File }) {
  const formData = new FormData()
  formData.append('file', file.raw)
  loading.value = true
  try {
    const data = await parseDataExcel(formData)
    if (data) {
      ElMessage.success('解析成功')
      list.value = data as DataDetail[]
    }
  } catch {
    ElMessage.error('解析失败')
  }
  loading.value = false
}

async function handleSaveDetails() {
  if (!taskId.value) return
  loading.value = true
  try {
    await saveDataDetails(taskId.value, list.value)
    ElMessage.success('明细保存成功')
    emit('refresh')
  } catch {
    // error handled by interceptor
  }
  loading.value = false
}

async function handleSavePlan() {
  loading.value = true
  try {
    await saveDataPlan({ ...planForm })
    ElMessage.success('定性方案保存成功')
  } catch {
    // error handled by interceptor
  }
  loading.value = false
}

defineExpose({ init })
</script>

<style scoped>
.dialog-layout { display: flex; height: 85vh; }
.input-area { flex: 3; padding-right: 20px; border-right: 1px solid #ebeef5; overflow-y: auto; }
.plan-area { flex: 2; padding-left: 20px; overflow-y: auto; background-color: #fcfcfc; }
.form-footer { margin-top: 20px; text-align: right; }
.table-tip { font-size: 12px; color: #909399; }
.plan-content h3 { font-size: 15px; color: #303133; margin-bottom: 20px; border-bottom: 1px solid #eee; padding-bottom: 10px; }
</style>
