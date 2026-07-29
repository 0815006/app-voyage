<template>
  <el-dialog title="编辑批量作业" v-model="visible" width="98%" top="2vh">
    <div class="dialog-layout">
      <div class="input-area">
        <div style="margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center;">
          <div>
            <el-button type="primary" size="small" :icon="Plus" @click="addRow">添加作业</el-button>
            <el-button type="success" size="small" :icon="Download" @click="handleDownloadTemplate">下载批量运行调查表模板</el-button>
            <el-upload
              style="display: inline-block; margin-left: 10px;"
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleUploadExcel"
              accept=".xls,.xlsx"
            >
              <el-button type="warning" size="small" :icon="Upload">上传调查表解析</el-button>
            </el-upload>
          </div>
          <div class="table-tip">* 批量作业调研内容</div>
        </div>

        <el-table :data="list" border size="small" height="55vh">
          <el-table-column label="基础属性" align="center">
            <el-table-column label="作业编号" width="100">
              <template #default="scope"><el-input v-model="scope.row.jobNo" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="作业名称*" width="150">
              <template #default="scope"><el-input v-model="scope.row.jobName" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="作业数" width="80">
              <template #default="scope"><el-input v-model="scope.row.jobCount" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="并行方式" width="100">
              <template #default="scope"><el-input v-model="scope.row.jobParallelMode" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="功能简述" min-width="150">
              <template #default="scope"><el-input v-model="scope.row.jobDesc" type="textarea" :rows="1" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="触发条件" width="120">
              <template #default="scope"><el-input v-model="scope.row.jobTriggerCond" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="前导作业" width="120">
              <template #default="scope"><el-input v-model="scope.row.jobPreName" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="可并行作业" width="120">
              <template #default="scope"><el-input v-model="scope.row.jobConcurrentNames" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="执行频率" width="80">
              <template #default="scope"><el-input v-model="scope.row.jobFrequency" size="small"></el-input></template>
            </el-table-column>
          </el-table-column>

          <el-table-column label="生产调研指标" align="center">
            <el-table-column label="数据类型" width="100">
              <template #default="scope"><el-input v-model="scope.row.jobDataType" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="预估数据量" width="120">
              <template #default="scope"><el-input v-model="scope.row.jobDataVolume" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="实际运行时长" width="100">
              <template #default="scope"><el-input v-model="scope.row.jobActualDuration" size="small" placeholder="手工填写"></el-input></template>
            </el-table-column>
            <el-table-column label="预估时长" width="100">
              <template #default="scope"><el-input v-model="scope.row.jobDuration" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="执行时间点" width="120">
              <template #default="scope"><el-input v-model="scope.row.jobExecTimePoint" size="small"></el-input></template>
            </el-table-column>
          </el-table-column>

          <el-table-column label="方案与机制" align="center">
            <el-table-column label="叠加联机" width="80">
              <template #default="scope"><el-input v-model="scope.row.isMixedLink" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="叠加交易" width="120">
              <template #default="scope"><el-input v-model="scope.row.mixedTranNames" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="重做机制" width="80">
              <template #default="scope"><el-input v-model="scope.row.hasRetry" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="重做简述" width="120">
              <template #default="scope"><el-input v-model="scope.row.retryDesc" size="small"></el-input></template>
            </el-table-column>
          </el-table-column>

          <el-table-column label="交易选择结果" align="center">
            <el-table-column label="选取原因" min-width="150">
              <template #default="scope">
                <el-cascader
                  v-model="scope.row.selectReasonArray"
                  :options="selectReasonOptions"
                  :props="{ multiple: true, emitPath: false }"
                  :show-all-levels="false"
                  clearable
                  size="small"
                  placeholder="请选择选取原因"
                  style="width: 100%"
                  @change="handleReasonChange(scope.row)"
                ></el-cascader>
              </template>
            </el-table-column>
          </el-table-column>

          <el-table-column label="操作" width="50" fixed="right">
            <template #default="scope"><el-button link :icon="Delete" style="color: #F56C6C" @click="list.splice(scope.$index, 1)"></el-button></template>
          </el-table-column>
        </el-table>

        <div v-if="summaryData" style="margin-top: 20px;">
          <div style="font-weight: bold; margin-bottom: 10px; border-left: 4px solid #67C23A; padding-left: 10px;">系统级批量指标</div>
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="summary-item">
                <span class="label">整体批量时长:</span>
                <el-input v-model="summaryData.batchTotalDuration" size="small" style="width: 150px;"></el-input>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="summary-item">
                <span class="label">整体数据量:</span>
                <el-input v-model="summaryData.batchTotalDataVolume" size="small" style="width: 150px;"></el-input>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="summary-item">
                <span class="label">并行度:</span>
                <el-input v-model="summaryData.batchParallelDegree" size="small" style="width: 150px;"></el-input>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="summary-item">
                <span class="label">最大并行数:</span>
                <el-input v-model="summaryData.batchMaxParallelCount" size="small" style="width: 150px;"></el-input>
              </div>
            </el-col>
          </el-row>
        </div>

        <div class="form-footer">
          <el-button type="primary" @click="handleSave" :loading="loading">保存批量信息</el-button>
        </div>
      </div>
      <div class="guide-area">
        <div class="guide-content">
          <h3>批量作业调研指南</h3>
          <div class="guide-item">
            <div class="guide-title">☆ 重点关注</div>
            <div class="guide-desc">逻辑复杂、涉及大表关联、有严格时间窗口要求的作业。</div>
          </div>
          <div class="guide-item">
            <div class="guide-title">☆ 系统级指标</div>
            <div class="guide-desc">
              1、整体批量时长：整个批量流程预计运行的总时间；<br/>
              2、整体数据量：批量处理涉及的核心业务数据总量；<br/>
              3、并行度：批量执行时的任务并发策略；<br/>
              4、最大并行数：系统支持的最大同时运行作业数。
            </div>
          </div>
          <div class="guide-item">
            <div class="guide-title">☆ 选取原因参考</div>
            <div class="guide-desc">
              <strong>A. 业务重要性与影响 (Business &amp; Impact)</strong><br/>
              - 核心清算链路：涉及日终/月终账务清算、资金结算等影响后续业务开展的关键作业。<br/>
              - 监管报送任务：涉及外部监管数据报送，对截止日期及生成时效有严格法律要求的任务。<br/>
              - 关键前导作业：在批量依赖树中处于上游，其延迟会导致后续大规模批量任务整体"开天窗"的作业。<br/>
              - 客户感知明显：如批量发送对账单、短信提醒等直接触达客户，其延迟会导致投诉增加的业务。<br/>
              <strong>B. 性能风险与资源消耗 (Performance &amp; Resource)</strong><br/>
              - 大数据量处理：涉及千万级以上记录的扫描、计算、排序或大文件（如 G 级别文件）解析的作业。<br/>
              - 窄时间窗要求：生产执行时间已逼近其分配的严格时间窗口上限（如必须在早 8 点前完成）。<br/>
              - 高资源占用 (CPU/IO)：历史上曾出现过导致服务器 CPU 爆满、磁盘 IO 等待过高或数据库死锁的作业。<br/>
              - 多线程/高并发并行：配置了高并发执行参数（如高 Thread Count），需要验证并发效率及竞争情况。<br/>
              <strong>C. 架构变更与风险 (Change &amp; Risk)</strong><br/>
              - 信创/环境适配：从原有平台迁移至信创环境（国产库/中间件），需重新验证批量处理效率。<br/>
              - 逻辑重大调整：本批次对该作业的 SQL 逻辑、算法、或底层数据模型做了重大优化或变更。<br/>
              - 新上线批量作业：全新的批量流程，缺乏生产运行数据参考，需进行压力验证。<br/>
              - 外部依赖复杂：涉及大量远程接口调用、跨网段文件传输或第三方系统实时交互的批量。<br/>
              <strong>D. 运维历史与混合场景 (Operations &amp; Scenarios)</strong><br/>
              - 历史瓶颈回顾：生产环境中曾发生过执行超时、自动重跑失败或报错频率较高的作业。<br/>
              - 联机批量混压：需要在联机业务高峰段运行，必须验证批量执行对联机交易 RT（响应时间）影响的作业。<br/>
              - 容灾重跑验证：具备复杂重写/重入逻辑，需测试在异常中断后重新执行的性能表现。
            </div>
          </div>
          <el-divider></el-divider>
          <div v-for="i in 10" :key="i" class="placeholder-text">补充说明内容占位...</div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Download, Upload, Delete } from '@element-plus/icons-vue'
import { saveBatches, getTemplateDownloadByKeywordUrl, parseBatchExcel } from '@/api/performance'

interface BatchItem {
  jobNo?: string
  jobName?: string
  jobCount?: string
  jobParallelMode?: string
  jobDesc?: string
  jobTriggerCond?: string
  jobPreName?: string
  jobConcurrentNames?: string
  jobFrequency?: string
  jobDataType?: string
  jobDataVolume?: string
  jobActualDuration?: string
  jobDuration?: string
  jobExecTimePoint?: string
  isMixedLink?: string
  mixedTranNames?: string
  hasRetry?: string
  retryDesc?: string
  selectReason?: string
  selectReasonArray?: string[]
}

interface BatchSummary {
  batchTotalDuration: string
  batchTotalDataVolume: string
  batchParallelDegree: string
  batchMaxParallelCount: string
}

interface SelectReasonOption {
  label: string
  value: string
  children?: SelectReasonOption[]
}

interface TaskInfoForBatch {
  batchTotalDuration?: string
  batchTotalDataVolume?: string
  batchParallelDegree?: string
  batchMaxParallelCount?: string
}

const emit = defineEmits<{
  refresh: []
}>()

const visible = ref<boolean>(false)
const loading = ref<boolean>(false)
const taskId = ref<string | null>(null)
const list = ref<BatchItem[]>([])

const summaryData = reactive<BatchSummary>({
  batchTotalDuration: '',
  batchTotalDataVolume: '',
  batchParallelDegree: '',
  batchMaxParallelCount: '',
})

const selectReasonOptions: SelectReasonOption[] = [
  {
    label: 'A. 业务重要性与影响',
    value: '业务重要性与影响',
    children: [
      { label: '核心清算链路', value: '核心清算链路' },
      { label: '监管报送任务', value: '监管报送任务' },
      { label: '关键前导作业', value: '关键前导作业' },
      { label: '客户感知明显', value: '客户感知明显' },
    ],
  },
  {
    label: 'B. 性能风险与资源消耗',
    value: '性能风险与资源消耗',
    children: [
      { label: '大数据量处理', value: '大数据量处理' },
      { label: '窄时间窗要求', value: '窄时间窗要求' },
      { label: '高资源占用 (CPU/IO)', value: '高资源占用 (CPU/IO)' },
      { label: '多线程/高并发并行', value: '多线程/高并发并行' },
    ],
  },
  {
    label: 'C. 架构变更与风险',
    value: '架构变更与风险',
    children: [
      { label: '信创/环境适配', value: '信创/环境适配' },
      { label: '逻辑重大调整', value: '逻辑重大调整' },
      { label: '新上线批量作业', value: '新上线批量作业' },
      { label: '外部依赖复杂', value: '外部依赖复杂' },
    ],
  },
  {
    label: 'D. 运维历史与混合场景',
    value: '运维历史与混合场景',
    children: [
      { label: '历史瓶颈回顾', value: '历史瓶颈回顾' },
      { label: '联机批量混压', value: '联机批量混压' },
      { label: '容灾重跑验证', value: '容灾重跑验证' },
    ],
  },
]

function init(taskIdVal: string, batches: BatchItem[], task?: TaskInfoForBatch) {
  visible.value = true
  taskId.value = taskIdVal
  const clonedList: BatchItem[] = JSON.parse(JSON.stringify(batches || []))
  clonedList.forEach((item) => {
    if (item.selectReason) {
      item.selectReasonArray = item.selectReason.split(',').filter((s) => s)
    } else {
      item.selectReasonArray = []
    }
  })
  list.value = clonedList
  if (task) {
    summaryData.batchTotalDuration = task.batchTotalDuration || ''
    summaryData.batchTotalDataVolume = task.batchTotalDataVolume || ''
    summaryData.batchParallelDegree = task.batchParallelDegree || ''
    summaryData.batchMaxParallelCount = task.batchMaxParallelCount || ''
  } else {
    summaryData.batchTotalDuration = ''
    summaryData.batchTotalDataVolume = ''
    summaryData.batchParallelDegree = ''
    summaryData.batchMaxParallelCount = ''
  }
}

function addRow() {
  list.value.push({
    jobNo: '',
    jobName: '',
    jobCount: '1',
    jobParallelMode: '',
    jobDesc: '',
    jobTriggerCond: '',
    jobPreName: '',
    jobConcurrentNames: '',
    jobFrequency: '',
    jobDataType: '',
    jobDataVolume: '',
    jobActualDuration: '',
    jobDuration: '',
    jobExecTimePoint: '',
    isMixedLink: '否',
    mixedTranNames: '',
    hasRetry: '是',
    retryDesc: '',
    selectReason: '',
    selectReasonArray: [],
  })
}

function handleReasonChange(row: BatchItem) {
  row.selectReason = row.selectReasonArray ? row.selectReasonArray.join(',') : ''
}

function handleDownloadTemplate() {
  window.open(getTemplateDownloadByKeywordUrl('批量运行调查表'), '_blank')
}

async function handleUploadExcel(file: { raw: File }) {
  const formData = new FormData()
  formData.append('file', file.raw)
  loading.value = true
  try {
    const data = await parseBatchExcel(formData)
    if (data) {
      ElMessage.success('解析成功')
      const batches = (data as { batches?: BatchItem[]; summary?: BatchSummary }).batches || []
      batches.forEach((item) => {
        if (item.selectReason) {
          item.selectReasonArray = item.selectReason.split(',').filter((s) => s)
        } else {
          item.selectReasonArray = []
        }
      })
      list.value = batches
      const summaryResult = (data as { summary?: BatchSummary }).summary
      if (summaryResult) {
        summaryData.batchTotalDuration = summaryResult.batchTotalDuration || ''
        summaryData.batchTotalDataVolume = summaryResult.batchTotalDataVolume || ''
        summaryData.batchParallelDegree = summaryResult.batchParallelDegree || ''
        summaryData.batchMaxParallelCount = summaryResult.batchMaxParallelCount || ''
      }
    }
  } catch {
    ElMessage.error('解析失败')
  }
  loading.value = false
}

async function handleSave() {
  if (!taskId.value) return
  loading.value = true
  try {
    await saveBatches(taskId.value, {
      batches: list.value,
      summary: { ...summaryData },
    })
    ElMessage.success('保存成功')
    emit('refresh')
    visible.value = false
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
.guide-area { flex: 1; padding-left: 20px; overflow-y: auto; background-color: #fafafa; }
.form-footer { margin-top: 20px; text-align: right; }
.placeholder-text { color: #c0c4cc; font-size: 12px; margin-top: 10px; }
.table-tip { font-size: 12px; color: #F56C6C; }
.summary-item { display: flex; align-items: center; margin-bottom: 10px; }
.summary-item .label { font-size: 13px; color: #606266; margin-right: 10px; width: 110px; text-align: right; }
.guide-item { margin-bottom: 15px; line-height: 1.6; }
.guide-title { font-weight: bold; color: #303133; font-size: 13px; margin-bottom: 4px; }
.guide-desc { color: #606266; font-size: 12px; padding-left: 10px; }
</style>
