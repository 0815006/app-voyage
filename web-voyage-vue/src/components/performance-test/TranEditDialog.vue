<template>
  <el-dialog title="编辑联机交易" v-model="visible" width="98%" top="2vh">
    <div class="dialog-layout">
      <div class="input-area">
        <div style="margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center;">
          <div>
            <el-button type="primary" size="small" :icon="Plus" @click="addRow">添加交易</el-button>
            <el-button type="success" size="small" :icon="Download" @click="handleDownloadTemplate">下载业务交易调查表模板</el-button>
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
          <div class="table-tip">* 带星号字段为必填项</div>
        </div>

        <el-table :data="list" border size="small" height="60vh">
          <el-table-column label="基本信息" align="center">
            <el-table-column label="模块名称*" width="120">
              <template #default="scope"><el-input v-model="scope.row.moduleName" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="交易名称*" width="150">
              <template #default="scope"><el-input v-model="scope.row.tranName" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="交易代码" width="120">
              <template #default="scope"><el-input v-model="scope.row.tranCode" size="small"></el-input></template>
            </el-table-column>
            <el-table-column label="接口类型*" width="120">
              <template #default="scope"><el-input v-model="scope.row.interfaceType" size="small"></el-input></template>
            </el-table-column>
          </el-table-column>

          <el-table-column label="1. 生产现状调研情况" align="center">
            <el-table-column label="日峰值(万)*" width="90">
              <template #default="scope"><el-input-number v-model="scope.row.tranDailyVol" :controls="false" size="small" style="width: 100%;"></el-input-number></template>
            </el-table-column>
            <el-table-column label="高峰时段(万)*" width="110">
              <template #default="scope"><el-input-number v-model="scope.row.tranPeakHourVol" :controls="false" size="small" style="width: 100%;"></el-input-number></template>
            </el-table-column>
            <el-table-column label="最大TPS*" width="80">
              <template #default="scope"><el-input-number v-model="scope.row.tranPeakTps" :controls="false" size="small" style="width: 100%;"></el-input-number></template>
            </el-table-column>
            <el-table-column label="平均RT(s)*" width="90">
              <template #default="scope"><el-input-number v-model="scope.row.tranAvgRt" :controls="false" size="small" style="width: 100%;"></el-input-number></template>
            </el-table-column>
          </el-table-column>

          <el-table-column label="2. 业务交易指标明细要求" align="center">
            <el-table-column label="目标日峰值(万)*" width="110">
              <template #default="scope"><el-input-number v-model="scope.row.targetDailyVol" :controls="false" size="small" style="width: 100%;"></el-input-number></template>
            </el-table-column>
            <el-table-column label="目标TPS*" width="80">
              <template #default="scope"><el-input-number v-model="scope.row.targetTps" :controls="false" size="small" style="width: 100%;"></el-input-number></template>
            </el-table-column>
            <el-table-column label="目标RT(s)*" width="90">
              <template #default="scope"><el-input-number v-model="scope.row.targetRt" :controls="false" size="small" style="width: 100%;"></el-input-number></template>
            </el-table-column>
            <el-table-column label="成功率%*" width="80">
              <template #default="scope"><el-input-number v-model="scope.row.targetSuccessRate" :controls="false" size="small" style="width: 100%;"></el-input-number></template>
            </el-table-column>
          </el-table-column>

          <el-table-column label="3. 交易选择结果" align="center">
            <el-table-column label="选中*" width="60">
              <template #default="scope"><el-checkbox v-model="scope.row.isSelected" :true-value="1" :false-value="0"></el-checkbox></template>
            </el-table-column>
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

          <el-table-column label="4. 指标推算审计" align="center">
            <el-table-column label="指标来源*" width="90">
              <template #default="scope">
                <el-select v-model="scope.row.indicatorSource" size="small" placeholder="请选择">
                  <el-option label="实测" :value="1"></el-option>
                  <el-option label="采样折算" :value="2"></el-option>
                  <el-option label="经验对标" :value="3"></el-option>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="推算过程" min-width="100">
              <template #default="scope">
                <el-input v-model="scope.row.calculationProcess" type="textarea" :rows="1" size="small" placeholder="描述推算过程"></el-input>
              </template>
            </el-table-column>
          </el-table-column>

          <el-table-column label="操作" width="50" fixed="right">
            <template #default="scope"><el-button link :icon="Delete" style="color: #F56C6C" @click="list.splice(scope.$index, 1)"></el-button></template>
          </el-table-column>
        </el-table>

        <div v-if="summaryData" style="margin-top: 20px;">
          <div style="font-weight: bold; margin-bottom: 10px; border-left: 4px solid #409EFF; padding-left: 10px;">系统汇总统计</div>
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="summary-item">
                <span class="label">用户数总数:</span>
                <el-input-number v-model="summaryData.totalUserCount" :controls="false" size="small" style="width: 120px;"></el-input-number>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="summary-item">
                <span class="label">日均在线用户数:</span>
                <el-input-number v-model="summaryData.dailyOnlineUserCount" :controls="false" size="small" style="width: 120px;"></el-input-number>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="summary-item">
                <span class="label">日交易峰值TPS*:</span>
                <el-input-number v-model="summaryData.dailyPeakTps" :controls="false" size="small" style="width: 120px;"></el-input-number>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="summary-item">
                <span class="label">年交易峰值TPS*:</span>
                <el-input-number v-model="summaryData.annualPeakTps" :controls="false" size="small" style="width: 120px;"></el-input-number>
              </div>
            </el-col>
          </el-row>
        </div>

        <div class="form-footer">
          <el-button type="primary" @click="handleSave" :loading="loading">保存交易信息</el-button>
        </div>
      </div>
      <div class="guide-area">
        <div class="guide-content">
          <h3>联机交易调研指南</h3>
          <div class="guide-item">
            <div class="guide-title">☆ 产品/模块名称</div>
            <div class="guide-desc">将应用系统先按模块或产品进行划分，也可继续细分子模块，例如：国内支付</div>
          </div>
          <div class="guide-item">
            <div class="guide-title">☆ 交易名称</div>
            <div class="guide-desc">例如：无折转客户帐</div>
          </div>
          <div class="guide-item">
            <div class="guide-title">☆ 交易代码</div>
            <div class="guide-desc">例如：B353。注：对于查询交易，按不同查询条件进行查询的应分开按不同交易进行填写，例如：客户信息查询（按客户号查询），客户信息查询（按客户姓名查询）</div>
          </div>
          <div class="guide-item">
            <div class="guide-title">☆ 生产现状调研情况</div>
            <div class="guide-desc">
              已投产系统生产现状数据：<br/>
              1、生产日峰值交易量：生产上各交易每日峰值交易量；<br/>
              2、生产高峰时段交易量：生产上高峰时段每小时峰值交易量；<br/>
              3、生产每秒最大交易量TPS：生产上各交易每秒最大交易量；<br/>
              4、生产交易平均响应时间：生产上各交易的平均响应时间；<br/>
              5、生产交易最大响应时间：生产上各交易的最大响应时间；
            </div>
          </div>
          <div class="guide-item">
            <div class="guide-title">☆ 业务交易明细要求</div>
            <div class="guide-desc">
              业务交易的性能要求和性能场景的设置依据：<br/>
              1、日峰值交易量：各交易每日峰值交易量；<br/>
              2、高峰时段交易量：高峰时段每小时峰值交易量；<br/>
              3、每秒最大交易量TPS：各交易高峰时间段每秒最大交易量，如不填写，将根据高峰时段交易量进行推算；<br/>
              4、交易平均响应时间：用户希望的交易响应时间（单步骤的平均响应时间）；<br/>
              5、交易最大响应时间：用户能容忍的最大交易响应时间（最大响应时间）；<br/>
              6、交易成功率：用户希望的交易成功率；<br/>
              7、用户平均操作时间：从用户进入交易画面填写交易要素信息开始，到将交易提交后台所花费的时间（交易被处理及返回的时间不含在内），平台类系统不适用可不填；
            </div>
          </div>
          <div class="guide-item">
            <div class="guide-title">☆ 交易选择结果</div>
            <div class="guide-desc">
              由测试项目组主导评估填写：<br/>
              1、是否选为性能测试交易：是否在性能测试场景设置中包含此交易；<br/>
              2、选取原因参考：<br/>
              <strong>1. 业务覆盖维度 (Business Coverage)</strong><br/>
              - 核心业务路径：属于产品最基础、使用最频繁的核心功能（如：登录、转账、余额查询）<br/>
              - 高频高并发：生产环境中日交易量排名靠前，或高峰时段并发量极高的交易<br/>
              - 资金风险环节：涉及账务处理、支付扣款、资金对账等对准确性要求极高的关键环节<br/>
              - 重要业务保障：因近期重大营销活动（如双11、开门红）或特定政策要求需重点保障<br/>
              <strong>2. 技术复杂度维度 (Technical Complexity)</strong><br/>
              - 高资源消耗：涉及大量 CPU 运算、复杂加密解密或大数据量排序的交易<br/>
              - 长链路交易：跨越多个子系统、涉及多次中间件或第三方接口调用的复杂流程<br/>
              - 数据库重载：涉及多表关联查询（Join）、大表全扫描或高频写锁竞争的交易<br/>
              - 异步处理/重压测试：涉及 MQ 消息堆积处理或大量 IO 读写的后台任务<br/>
              <strong>3. 架构变更维度 (Architecture Changes)</strong><br/>
              - 信创改造/平台迁移：针对国产化数据库、中间件适配后的性能核对<br/>
              - 新上线交易：本批次新开发的需求，无生产运行历史数据参考<br/>
              - 逻辑重大调整：原有交易代码结构、架构模式（如单体转微服务）发生了较大变动<br/>
              - 环境变更/版本升级：系统内核、依赖库或运行环境版本发生重大升级<br/>
              <strong>4. 历史遗留维度 (Historical Performance)</strong><br/>
              - 历史性能瓶颈：历史上曾发生过性能告警、超时或生产事故的交易<br/>
              - 容量水位预警：生产交易量增长过快，已接近当前系统容量设计上限的交易<br/>
              - 定标对比需求：需与往期基准数据、竞品数据或同业标准进行性能对标<br/>
              <strong>5. 其他特殊维度 (Others)</strong><br/>
              - 配合压测场景：作为混合负载场景中的"背景噪音"交易，用以模拟真实生产环境<br/>
              - 监管/审计要求：根据监管部门（如人行、银保监）要求必须展示性能指标的交易
            </div>
          </div>
          <div class="guide-item">
            <div class="guide-title">☆ 系统汇总统计</div>
            <div class="guide-desc">
              一般情况下被测交易只是系统交易总量的一部分，需要填写被测系统整体的统计信息：<br/>
              1、用户数总数：有权限登陆该系统的用户总数，平台类系统无需填写；<br/>
              2、日均在线用户数：系统每日平均同时在线用户数均值，平台类系统无需填写；<br/>
              3、日交易峰值TPS：系统普通日（不包括国庆、春节、电商促销等特殊日）的TPS；<br/>
              4、年交易峰值TPS：系统年峰值（包括国庆、春节、电商促销等特殊日）的TPS；
            </div>
          </div>
          <div class="guide-item">
            <div class="guide-title">☆ 指标来源与推算说明</div>
            <div class="guide-desc">
              在进行性能测试需求调研时，指标的真实性直接决定了测试场景的有效性。请参照以下说明填写"指标来源"与"指标推算过程"。<br/><br/>
              <strong>1. 指标来源 (Indicator Source)</strong><br/>
              请根据数据的获取渠道选择对应的来源类型：<br/>
              [1] <strong>实测 (Actual Measurement)</strong>：直接从生产环境的监控系统（如 APM、Grafana、SkyWalking）、数据库审计日志、或系统运行报告中获取的精确数据。适用于已投产且监控体系完善的存量系统。<br/>
              [2] <strong>采样折算 (Sampling Conversion)</strong>：无法获取全天或峰值精确数据，通过采集某一特定时段（如业务高峰的 15 分钟）的数据，经过数学公式推算出的全量数据。适用于监控日志不完整，或仅有部分统计信息的系统。<br/>
              [3] <strong>经验对标 (Benchmarking)</strong>：新系统上线或业务模式完全变更，无历史数据可参考。通过参考同类业务、竞品数据或行业标准（如：网银转账响应时间应在 2s 内）设定的指标。适用于新项目上线、信创首发系统。<br/><br/>
              <strong>2. 指标推算过程 (Calculation Process) —— 填写范例</strong><br/>
              若您的指标来源选择为 [采样折算] 或 [经验对标]，请务必在推算过程栏中注明逻辑。<br/>
              <strong>场景 A：从日均量推算峰值 TPS (采样折算)</strong><br/>
              范例：参考 2026-03 生产日志，日均 100 万笔，按 8/2 原则计算：高峰 4.8 小时处理 80 万笔，折算高峰 TPS 约 46 笔/秒。<br/>
              <strong>场景 B：新业务上线指标设定 (经验对标)</strong><br/>
              范例：参考旧版"余额查询"TPS（20/s），考虑到新版增加了营销弹窗吸引流量，按 1.5 倍增长系数对标，设定目标 TPS 为 30/s。<br/>
              <strong>场景 C：批量作业时间窗口推算</strong><br/>
              范例：生产日终窗口为 00:00-02:00（120分钟），前导清算作业耗时 80 分钟，本作业（生成报表）必须在 40 分钟内完成，否则将影响后续下发任务。<br/><br/>
              <strong>填写注意事项</strong><br/>
              - 单位对齐：请严格检查单位，交易量统一使用"万笔/日"或"万笔/小时"，响应时间统一使用"秒"。<br/>
              - 极端异常排除：实测最大响应时间（Max RT）时，请排除网络波动导致的单笔异常极端值，取相对合理的峰值。<br/>
              - 证据留存：建议将推算依据的原始 Excel 或监控截图保存，以便在性能方案评审时作为佐证材料。
            </div>
          </div>
          <div class="guide-item" style="color: #F56C6C; font-weight: bold; margin-top: 20px;">
            ☆ 带*号为必填项
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
import { saveTrans, getTemplateDownloadByKeywordUrl, parseTranExcel } from '@/api/performance'

interface TranItem {
  id?: number
  moduleName?: string
  tranName?: string
  tranCode?: string
  interfaceType?: string
  tranDailyVol?: number
  tranPeakHourVol?: number
  tranPeakTps?: number
  tranAvgRt?: number
  targetDailyVol?: number
  targetTps?: number
  targetRt?: number
  targetSuccessRate?: number
  isSelected?: number
  selectReason?: string
  selectReasonArray?: string[]
  indicatorSource?: number
  calculationProcess?: string
}

interface TranSummary {
  totalUserCount: number
  dailyOnlineUserCount: number
  dailyPeakTps: number
  annualPeakTps: number
}

interface SelectReasonOption {
  label: string
  value: string
  children?: SelectReasonOption[]
}

interface TaskInfoForTran {
  totalUserCount?: number
  dailyOnlineUserCount?: number
  dailyPeakTps?: number
  annualPeakTps?: number
}

const emit = defineEmits<{
  refresh: []
}>()

const visible = ref<boolean>(false)
const loading = ref<boolean>(false)
const taskId = ref<string | null>(null)
const list = ref<TranItem[]>([])

const summaryData = reactive<TranSummary>({
  totalUserCount: 0,
  dailyOnlineUserCount: 0,
  dailyPeakTps: 0,
  annualPeakTps: 0,
})

const selectReasonOptions: SelectReasonOption[] = [
  {
    label: '1. 业务覆盖维度',
    value: '业务覆盖维度',
    children: [
      { label: '核心业务路径', value: '核心业务路径' },
      { label: '高频高并发', value: '高频高并发' },
      { label: '资金风险环节', value: '资金风险环节' },
      { label: '重要业务保障', value: '重要业务保障' },
    ],
  },
  {
    label: '2. 技术复杂度维度',
    value: '技术复杂度维度',
    children: [
      { label: '高资源消耗', value: '高资源消耗' },
      { label: '长链路交易', value: '长链路交易' },
      { label: '数据库重载', value: '数据库重载' },
      { label: '异步处理/重压测试', value: '异步处理/重压测试' },
    ],
  },
  {
    label: '3. 架构变更维度',
    value: '架构变更维度',
    children: [
      { label: '信创改造/平台迁移', value: '信创改造/平台迁移' },
      { label: '新上线交易', value: '新上线交易' },
      { label: '逻辑重大调整', value: '逻辑重大调整' },
      { label: '环境变更/版本升级', value: '环境变更/版本升级' },
    ],
  },
  {
    label: '4. 历史遗留维度',
    value: '历史遗留维度',
    children: [
      { label: '历史性能瓶颈', value: '历史性能瓶颈' },
      { label: '容量水位预警', value: '容量水位预警' },
      { label: '定标对比需求', value: '定标对比需求' },
    ],
  },
  {
    label: '5. 其他特殊维度',
    value: '其他特殊维度',
    children: [
      { label: '配合压测场景', value: '配合压测场景' },
      { label: '监管/审计要求', value: '监管/审计要求' },
    ],
  },
]

function init(taskIdVal: string, trans: TranItem[], task?: TaskInfoForTran) {
  visible.value = true
  taskId.value = taskIdVal
  const clonedList: TranItem[] = JSON.parse(JSON.stringify(trans || []))
  clonedList.forEach((item) => {
    if (item.selectReason) {
      item.selectReasonArray = item.selectReason.split(',').filter((s) => s)
    } else {
      item.selectReasonArray = []
    }
  })
  list.value = clonedList
  if (task) {
    summaryData.totalUserCount = task.totalUserCount || 0
    summaryData.dailyOnlineUserCount = task.dailyOnlineUserCount || 0
    summaryData.dailyPeakTps = task.dailyPeakTps || 0
    summaryData.annualPeakTps = task.annualPeakTps || 0
  } else {
    summaryData.totalUserCount = 0
    summaryData.dailyOnlineUserCount = 0
    summaryData.dailyPeakTps = 0
    summaryData.annualPeakTps = 0
  }
}

function addRow() {
  list.value.push({
    moduleName: '',
    tranName: '',
    tranCode: '',
    interfaceType: '',
    tranDailyVol: 0,
    tranPeakHourVol: 0,
    tranPeakTps: 0,
    tranAvgRt: 0,
    targetDailyVol: 0,
    targetTps: 0,
    targetRt: 0,
    targetSuccessRate: 100,
    isSelected: 1,
    selectReason: '',
    selectReasonArray: [],
    indicatorSource: 1,
    calculationProcess: '',
  })
}

function handleReasonChange(row: TranItem) {
  row.selectReason = row.selectReasonArray ? row.selectReasonArray.join(',') : ''
}

function handleDownloadTemplate() {
  window.open(getTemplateDownloadByKeywordUrl('业务交易调查表'), '_blank')
}

async function handleUploadExcel(file: { raw: File }) {
  const formData = new FormData()
  formData.append('file', file.raw)
  loading.value = true
  try {
    const data = await parseTranExcel(formData)
    if (data) {
      ElMessage.success('解析成功')
      const parsed = data as { trans?: TranItem[]; summary?: TranSummary }
      const transList = parsed.trans || []
      transList.forEach((item) => {
        if (item.selectReason) {
          item.selectReasonArray = item.selectReason.split(',').filter((s) => s)
        } else {
          item.selectReasonArray = []
        }
      })
      list.value = transList
      if (parsed.summary) {
        summaryData.totalUserCount = parsed.summary.totalUserCount || 0
        summaryData.dailyOnlineUserCount = parsed.summary.dailyOnlineUserCount || 0
        summaryData.dailyPeakTps = parsed.summary.dailyPeakTps || 0
        summaryData.annualPeakTps = parsed.summary.annualPeakTps || 0
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
    await saveTrans(taskId.value, {
      trans: list.value,
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
.dialog-layout { display: flex; height: 80vh; }
.input-area { flex: 3; padding-right: 20px; border-right: 1px solid #ebeef5; overflow-y: auto; }
.guide-area { flex: 1; padding-left: 20px; overflow-y: auto; background-color: #fafafa; }
.form-footer { margin-top: 20px; text-align: right; }
.placeholder-text { color: #c0c4cc; font-size: 12px; margin-top: 10px; }
.table-tip { font-size: 12px; color: #F56C6C; }
.summary-item { display: flex; align-items: center; margin-bottom: 10px; }
.summary-item .label { font-size: 13px; color: #606266; margin-right: 10px; width: 100px; text-align: right; }
.guide-item { margin-bottom: 15px; line-height: 1.6; }
.guide-title { font-weight: bold; color: #303133; font-size: 13px; margin-bottom: 4px; }
.guide-desc { color: #606266; font-size: 12px; padding-left: 10px; }
</style>
