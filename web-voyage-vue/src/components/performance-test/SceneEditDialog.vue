<template>
  <el-dialog title="编辑测试场景" v-model="visible" width="98%" top="2vh">
    <div class="dialog-layout">
      <div class="input-area" v-loading="loading">
        <div style="margin-bottom: 15px;">
          <el-button type="primary" size="small" :icon="RefreshRight" @click="handleInitDefault">初始化默认场景</el-button>
          <el-button type="success" size="small" :icon="Plus" @click="addCustomScene">添加自定义场景</el-button>
        </div>

        <!-- 1. 场景主表展示区 -->
        <el-card class="box-card" shadow="never">
          <template #header>
            <span>场景概览与方案定义</span>
          </template>
          <el-table :data="scenes" border size="small" highlight-current-row>
            <el-table-column label="场景名称" min-width="150">
              <template #default="scope">
                <el-input v-model="scope.row.sceneName" size="small"></el-input>
              </template>
            </el-table-column>
            <el-table-column label="测试目的" min-width="200">
              <template #default="scope">
                <el-input type="textarea" :rows="2" v-model="scope.row.testObjective" size="small"></el-input>
              </template>
            </el-table-column>
            <el-table-column label="实施方法" min-width="200">
              <template #default="scope">
                <el-input type="textarea" :rows="2" v-model="scope.row.implementationMethod" size="small"></el-input>
              </template>
            </el-table-column>
            <el-table-column label="结束条件" min-width="200">
              <template #default="scope">
                <el-input type="textarea" :rows="2" v-model="scope.row.endCondition" size="small"></el-input>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="90">
              <template #default="scope">
                <el-select v-model="scope.row.sceneType" size="small">
                  <el-option :value="1" label="基准"></el-option>
                  <el-option :value="2" label="单负载"></el-option>
                  <el-option :value="3" label="混合负载"></el-option>
                  <el-option :value="4" label="稳定性"></el-option>
                  <el-option :value="5" label="极限"></el-option>
                  <el-option :value="6" label="批量"></el-option>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="TPS比例(%)" width="90">
              <template #default="scope">
                <el-input-number v-model="scope.row.targetTpsRatio" :controls="false" size="small" style="width: 100%;" @change="handleRatioChange(scope.row)"></el-input-number>
              </template>
            </el-table-column>
            <el-table-column label="总TPS" width="80" prop="targetTotalTps"></el-table-column>
            <el-table-column label="持续时间(分)" width="90">
              <template #default="scope">
                <el-input-number v-model="scope.row.globalDuration" :controls="false" size="small" style="width: 100%;"></el-input-number>
              </template>
            </el-table-column>
            <el-table-column label="选中" width="50">
              <template #default="scope">
                <el-checkbox v-model="scope.row.isSelected" :true-value="1" :false-value="0"></el-checkbox>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="50">
              <template #default="scope">
                <el-button link :icon="Delete" style="color: #F56C6C" @click="removeScene(scope.$index)"></el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 2. 场景明细展示区 (按块叠放) -->
        <div v-for="(scene, sIdx) in scenes" :key="scene.id || sIdx" class="scene-block">
          <el-card shadow="hover">
            <template #header>
              <div class="scene-header">
                <span class="scene-title">
                  <el-icon><Operation /></el-icon> {{ scene.sceneName || '未命名场景' }} - 交易配置
                </span>
                <div class="scene-meta">
                  <span>TPS比例: {{ scene.targetTpsRatio }}%</span>
                  <el-divider direction="vertical"></el-divider>
                  <span>总TPS: {{ scene.targetTotalTps }}</span>
                </div>
              </div>
            </template>

            <el-table :data="sceneDetails[sIdx]" border size="small" stripe>
              <el-table-column label="交易名称" prop="tranName" min-width="150"></el-table-column>
              <el-table-column label="预期TPS" width="90">
                <template #default="scope">
                  <el-input-number v-model="scope.row.targetTps" :controls="false" size="small" style="width: 100%;" @change="recalcDetail(scope.row, scene)"></el-input-number>
                </template>
              </el-table-column>
              <el-table-column label="预期RT(s)" width="90">
                <template #default="scope">
                  <el-input-number v-model="scope.row.targetRt" :controls="false" size="small" style="width: 100%;" @change="recalcDetail(scope.row, scene)"></el-input-number>
                </template>
              </el-table-column>
              <el-table-column label="VU" width="80">
                <template #default="scope">
                  <el-input-number v-model="scope.row.vuCount" :controls="false" size="small" style="width: 100%;"></el-input-number>
                </template>
              </el-table-column>
              <el-table-column label="Ramp-up(s)" width="90">
                <template #default="scope">
                  <el-input-number v-model="scope.row.rampUp" :controls="false" size="small" style="width: 100%;"></el-input-number>
                </template>
              </el-table-column>
              <el-table-column label="Pacing" width="80">
                <template #default="scope">
                  <el-input-number v-model="scope.row.pacing" :controls="false" size="small" style="width: 100%;"></el-input-number>
                </template>
              </el-table-column>
              <el-table-column label="吞吐量定时器" width="110">
                <template #default="scope">
                  <el-input-number v-model="scope.row.throughputTimer" :controls="false" size="small" style="width: 100%;"></el-input-number>
                </template>
              </el-table-column>
              <el-table-column v-if="scene.sceneType === 1" label="迭代次数" width="80">
                <template #default="scope">
                  <el-input-number v-model="scope.row.iterations" :controls="false" size="small" style="width: 100%;"></el-input-number>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>

        <div class="form-footer">
          <el-button type="primary" @click="handleSaveAll" :loading="loading">保存所有场景及配置</el-button>
        </div>
      </div>

      <div class="guide-area">
        <div class="guide-content">
          <h3>场景配置说明</h3>
          <p><strong>1. 自动联动逻辑：</strong></p>
          <ul>
            <li>修改场景主表的 <strong>TPS比例</strong>，会自动重算该场景下所有交易的预期TPS、VU、Ramp-up及定时器。</li>
            <li><strong>VU计算公式：</strong> ROUNDUP(TPS * RT * 1.1, 0)</li>
            <li><strong>Ramp-up计算公式：</strong> ROUNDUP(VU / 5, 0)</li>
            <li><strong>定时器计算公式：</strong> ROUNDUP(TPS * 60 * 1.1, -1)</li>
          </ul>
          <p><strong>2. 场景类型说明：</strong></p>
          <ul>
            <li><strong>基准：</strong> VU固定为1，不设TPS目标，固定迭代100次。</li>
            <li><strong>极限：</strong> 默认生成左值(120%)和右值(140%)两个场景。</li>
          </ul>
          <el-divider></el-divider>
          <div class="placeholder-text">注：初始化默认场景会清空当前任务已有的场景配置，请谨慎操作。</div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, RefreshRight, Operation } from '@element-plus/icons-vue'
import { initScenes, getScenes, getSceneDetails, saveAllScenes } from '@/api/performance'

interface Scene {
  id?: number
  taskId?: number | null
  sceneName?: string
  sceneType?: number
  targetTpsRatio?: number
  isSelected?: number
  globalDuration?: number
  targetTotalTps?: number
  testObjective?: string
  implementationMethod?: string
  endCondition?: string
}

interface SceneDetail {
  id?: number
  tranId?: number
  tranName?: string
  targetTps?: number | null
  targetRt?: number
  targetSuccessRate?: number
  vuCount?: number
  rampUp?: number
  pacing?: number
  throughputTimer?: number | null
  iterations?: number
}

interface TranBase {
  id?: number
  tranName?: string
  targetTps?: number
  targetRt?: number
  targetSuccessRate?: number
}

const emit = defineEmits<{
  refresh: []
}>()

const visible = ref<boolean>(false)
const loading = ref<boolean>(false)
const taskId = ref<string | null>(null)
const scenes = ref<Scene[]>([])
const sceneDetails = ref<SceneDetail[][]>([])
const trans = ref<TranBase[]>([]) // 缓存交易基础指标，用于重算

async function init(taskIdVal: string, transData: TranBase[]) {
  visible.value = true
  taskId.value = taskIdVal
  trans.value = transData || []
  await fetchData()
}

async function fetchData() {
  loading.value = true
  try {
    const scenesData = await getScenes(taskId.value!)
    if (scenesData) {
      scenes.value = scenesData as Scene[]
      sceneDetails.value = []
      for (const scene of scenes.value) {
        const dRes = await getSceneDetails(scene.id!)
        sceneDetails.value.push((dRes as SceneDetail[]) || [])
      }
    }
  } finally {
    loading.value = false
  }
}

async function handleInitDefault() {
  try {
    await ElMessageBox.confirm('初始化将清空现有场景配置，是否继续？', '提示', { type: 'warning' })
    loading.value = true
    await initScenes(taskId.value!)
    ElMessage.success('初始化成功')
    await fetchData()
  } catch {
    // cancel or error
  } finally {
    loading.value = false
  }
}

function addCustomScene() {
  const newScene: Scene = {
    taskId: taskId.value ? Number(taskId.value) : null,
    sceneName: '自定义场景',
    sceneType: 5,
    targetTpsRatio: 100,
    isSelected: 1,
    globalDuration: 10,
    targetTotalTps: 0,
    testObjective: '',
    implementationMethod: '',
    endCondition: '',
  }
  scenes.value.push(newScene)
  const details: SceneDetail[] = trans.value.map((t) => {
    const detail: SceneDetail = {
      tranId: t.id,
      tranName: t.tranName,
      targetRt: t.targetRt || 0,
      targetSuccessRate: t.targetSuccessRate || 0,
      targetTps: t.targetTps || 0,
    }
    recalcDetail(detail, newScene)
    return detail
  })
  sceneDetails.value.push(details)
  updateTotalTps(scenes.value.length - 1)
}

function removeScene(index: number) {
  scenes.value.splice(index, 1)
  sceneDetails.value.splice(index, 1)
}

function handleRatioChange(scene: Scene) {
  const idx = scenes.value.indexOf(scene)
  if (idx === -1) return

  const details = sceneDetails.value[idx]
  details.forEach((detail) => {
    const originalTran = trans.value.find((t) => t.id === detail.tranId)
    if (originalTran) {
      const ratio = (scene.targetTpsRatio || 100) / 100
      detail.targetTps = originalTran.targetTps ? Number((originalTran.targetTps * ratio).toFixed(2)) : 0
      recalcDetail(detail, scene)
    }
  })
  updateTotalTps(idx)
}

function recalcDetail(detail: SceneDetail, scene: Scene) {
  if (scene.sceneType === 1) {
    detail.vuCount = 1
    detail.rampUp = 0
    detail.iterations = 100
    detail.targetTps = null
    detail.throughputTimer = null
  } else {
    const tps = detail.targetTps || 0
    const rt = detail.targetRt || 0.5
    detail.vuCount = Math.ceil(tps * rt * 1.1) || 1
    detail.rampUp = Math.ceil(detail.vuCount / 5)
    const timerRaw = tps * 66
    detail.throughputTimer = Math.ceil(timerRaw / 10) * 10
  }
}

function updateTotalTps(idx: number) {
  const details = sceneDetails.value[idx]
  if (!details) return
  const total = details.reduce((sum, d) => sum + (Number(d.targetTps) || 0), 0)
  scenes.value[idx].targetTotalTps = Number(total.toFixed(2))
}

async function handleSaveAll() {
  if (!taskId.value) {
    ElMessage.error('任务ID缺失')
    return
  }
  loading.value = true
  try {
    const data = (scenes.value || []).map((scene, index) => {
      return {
        scene,
        details: sceneDetails.value && sceneDetails.value[index] ? sceneDetails.value[index] : [],
      }
    })
    console.log('Saving scenes data:', data)
    await saveAllScenes(taskId.value, data)
    ElMessage.success('全部保存成功')
    emit('refresh')
    visible.value = false
  } catch (e: unknown) {
    console.error('Save scenes error:', e)
    const message = e instanceof Error ? e.message : '未知错误'
    ElMessage.error('保存失败: ' + message)
  } finally {
    loading.value = false
  }
}

defineExpose({ init })
</script>

<style scoped>
.dialog-layout { display: flex; height: 85vh; }
.input-area { flex: 4; padding-right: 20px; border-right: 1px solid #ebeef5; overflow-y: auto; }
.guide-area { flex: 1; padding-left: 20px; overflow-y: auto; background-color: #fafafa; }
.box-card { margin-bottom: 20px; }
.scene-block { margin-bottom: 25px; }
.scene-header { display: flex; justify-content: space-between; align-items: center; }
.scene-title { font-weight: bold; color: #409EFF; }
.scene-meta { font-size: 13px; color: #606266; }
.form-footer { margin-top: 30px; text-align: center; padding-bottom: 20px; }
.placeholder-text { color: #F56C6C; font-size: 12px; margin-top: 10px; font-weight: bold; }
</style>
