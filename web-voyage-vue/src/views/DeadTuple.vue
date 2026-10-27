<template>
  <div class="dead-tuple">
    <!-- ====== 标题栏 ====== -->
    <div class="demo-header">
      <div class="header-left">
        <h2 class="header-title">🩺 高斯数据库死元组监测</h2>
        <span class="header-sub">高频采样 · 波动可视化 · 清理效率评估</span>
      </div>
    </div>

    <div class="split-body">
      <!-- ====== 左侧：采样任务配置区 (Master) ====== -->
      <aside class="task-sidebar">
        <div class="sidebar-head">
          <el-button type="primary" size="small" @click="openCreate">
            <el-icon class="btn-icon"><Plus /></el-icon>新建采样任务
          </el-button>
          <div class="sidebar-head-actions">
            <el-tooltip content="刷新列表" placement="bottom">
              <el-button size="small" text :loading="loadingTasks" @click="loadTasks">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </el-tooltip>
            <!-- 齿轮：复刻数据库性能分析模块的连接配置入口 -->
            <el-tooltip content="管理数据库连接配置" placement="bottom">
              <el-button size="small" text @click="showConnDialog = true">
                <el-icon><Setting /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>

        <el-scrollbar class="task-list" v-loading="loadingTasks">
          <div
            v-for="t in tasks"
            :key="t.id"
            class="task-item"
            :class="{ active: currentTaskId === t.id }"
            @click="selectTask(t.id!)"
          >
            <div class="task-line1">
              <span class="task-name">{{ t.taskName }}</span>
              <el-tag :type="statusTag(t.status!)" size="small" effect="light">{{ statusLabel(t.status!) }}</el-tag>
            </div>
            <div class="task-line2">
              <span class="task-conn">🔗 {{ connLabel(t.connectionId) }}</span>
              <span class="task-freq">{{ t.frequency }}s/次</span>
            </div>
          </div>
          <el-empty
            v-if="!loadingTasks && tasks.length === 0"
            description="暂无采样任务，点击上方按钮新建"
            :image-size="60"
          />
        </el-scrollbar>
      </aside>

      <!-- ====== 右侧：概要 + 看板 ====== -->
      <main class="right-pane">
        <template v-if="currentTask">
          <!-- 任务概要与控制区 -->
          <section class="summary-bar">
            <div class="summary-grid">
              <div class="summary-item">
                <span class="summary-label">数据库连接</span>
                <span class="summary-value">{{ connDetail(currentTask.connectionId) }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">采样频率</span>
                <span class="summary-value">{{ currentTask.frequency }} 秒/次</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">起止窗口</span>
                <span class="summary-value">{{ fmtTime(currentTask.startTime) }} ～ {{ fmtTime(currentTask.endTime) }}</span>
              </div>
              <div class="summary-item summary-status">
                <span class="summary-label">状态</span>
                <el-tag :type="statusTag(currentTask.status!)" size="small">{{ statusLabel(currentTask.status!) }}</el-tag>
              </div>
            </div>
            <div class="summary-sql">
              <span class="summary-label">自定义 SQL</span>
              <pre class="sql-preview">{{ currentTask.customSql }}</pre>
            </div>
            <div class="summary-actions">
              <el-button
                v-if="currentTask.status !== 'RUNNING'"
                type="success"
                size="small"
                :loading="actioning"
                @click="handleStart"
              >启动</el-button>
              <el-button
                v-else
                type="warning"
                size="small"
                :loading="actioning"
                @click="handleStop"
              >停止</el-button>
              <el-button size="small" :disabled="currentTask.status === 'RUNNING'" @click="openEdit">编辑任务</el-button>
              <el-button type="danger" plain size="small" :loading="actioning" @click="handleDelete">删除任务</el-button>
            </div>
          </section>

          <!-- 死元组波动图看板区 -->
          <section class="dashboard">
            <div class="dash-toolbar">
              <div class="toolbar-item">
                <span class="toolbar-label">展示列</span>
                <el-select v-model="selectedMetrics" multiple collapse-tags collapse-tags-tooltip
                           :max-collapse-tags="2" placeholder="纵轴指标列（默认死元组）" class="metric-select">
                  <el-option v-for="m in metricCandidates" :key="m" :label="m" :value="m" />
                </el-select>
              </div>
              <div class="toolbar-item">
                <span class="toolbar-label">表范围</span>
                <el-select v-model="selectedEntities" multiple filterable collapse-tags collapse-tags-tooltip
                           :max-collapse-tags="2" placeholder="全部表" class="entity-select">
                  <el-option v-for="name in entityNames" :key="name" :label="name" :value="name" />
                </el-select>
              </div>
              <div class="toolbar-item">
                <span class="toolbar-label">时间范围</span>
                <el-radio-group v-model="rangePreset" size="small">
                  <el-radio-button value="1h">最近 1 小时</el-radio-button>
                  <el-radio-button value="6h">最近 6 小时</el-radio-button>
                  <el-radio-button value="custom">自定义</el-radio-button>
                </el-radio-group>
                <el-date-picker
                  v-if="rangePreset === 'custom'"
                  v-model="customRange"
                  type="datetimerange"
                  size="small"
                  range-separator="至"
                  start-placeholder="开始"
                  end-placeholder="结束"
                  format="MM-DD HH:mm"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  style="width: 320px; margin-left: 8px"
                />
              </div>
              <el-button size="small" :loading="loadingHistory" @click="loadHistory">
                <el-icon class="btn-icon"><Refresh /></el-icon>刷新数据
              </el-button>
            </div>

            <!-- 核心指标卡片 -->
            <div class="metric-cards">
              <div class="metric-card">
                <div class="metric-title">最新值 · {{ primaryMetric || '未选展示列' }}</div>
                <div class="metric-value danger">{{ metricPrimaryValue }}</div>
                <div class="metric-sub">{{ selectedEntities.length ? `${selectedEntities.length} 张表求和` : '全部表求和' }}</div>
              </div>
              <div class="metric-card">
                <div class="metric-title">当前死元组占比</div>
                <div class="metric-value">{{ metricRatio }}</div>
                <div class="metric-sub">dead / (dead + live)</div>
              </div>
              <div class="metric-card">
                <div class="metric-title">最近一次清理时间</div>
                <div class="metric-value vacuum">{{ metricLastVacuum }}</div>
                <div class="metric-sub">{{ vacuumColumn || '未在采样列中' }}</div>
              </div>
              <div class="metric-card">
                <div class="metric-title">采样点数</div>
                <div class="metric-value">{{ history.length }}</div>
                <div class="metric-sub">{{ currentTask.status === 'RUNNING' ? '运行中 · 5 秒自动刷新' : '任务未运行' }}</div>
              </div>
            </div>

            <!-- 波动图表区 -->
            <div class="chart-block">
              <div class="chart-title">波动趋势（横轴=采样时间 · 每条线=表 × 展示列，观察波峰累积与波谷清理）</div>
              <TrendChart :labels="chartLabels" :series="chartSeries" />
            </div>
          </section>
        </template>

        <!-- 未选中任务的空态 -->
        <el-empty v-else description="请在左侧选择或新建一个死元组采样任务" />
      </main>
    </div>

    <!-- 新建/编辑任务弹窗 -->
    <TaskDialog
      v-model:visible="showTaskDialog"
      :task="editingTask"
      :connections="connections"
      @saved="onTaskSaved"
    />

    <!-- 数据库连接配置弹窗（复刻数据库性能分析模块入口，直接复用组件） -->
    <ConnectionDialog
      v-model:visible="showConnDialog"
      :connections="connections"
      @changed="loadConnections"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Setting } from '@element-plus/icons-vue'
import type { SamplingColumn, SamplingHistoryRow, SamplingTask } from '@/api/sampling'
import { deleteTask, listHistory, listTasks, previewColumns, startTask, stopTask } from '@/api/sampling'
import type { DbConnectionConfig } from '@/api/db-analysis'
import { listConnections } from '@/api/db-analysis'
import ConnectionDialog from '@/components/db-analysis/ConnectionDialog.vue'
import TaskDialog from '@/components/sampling/TaskDialog.vue'
import TrendChart, { type TrendSeries } from '@/components/sampling/TrendChart.vue'

/* ==================== 状态 ==================== */
const tasks = ref<SamplingTask[]>([])
const connections = ref<DbConnectionConfig[]>([])
const history = ref<SamplingHistoryRow[]>([])
/** 当前任务的 SQL 结果集列结构（后端 JDBC 元数据解析，失败时为空→退化正则识别） */
const columns = ref<SamplingColumn[]>([])
const currentTaskId = ref<string>('')
/** 展示列（纵轴指标，多选）与表范围（维度值，多选，空=全部） */
const selectedMetrics = ref<string[]>([])
const selectedEntities = ref<string[]>([])
const rangePreset = ref<'1h' | '6h' | 'custom'>('1h')
const customRange = ref<[string, string] | null>(null)

const loadingTasks = ref(false)
const loadingHistory = ref(false)
const actioning = ref(false)
const showTaskDialog = ref(false)
const showConnDialog = ref(false)
const editingTask = ref<SamplingTask | null>(null)

const currentTask = computed(
  () => tasks.value.find(t => t.id === currentTaskId.value) || null
)

/* ==================== 加载 ==================== */
async function loadTasks() {
  loadingTasks.value = true
  try {
    tasks.value = await listTasks()
    // 任务被外部删除后清掉失效选中
    if (currentTaskId.value && !tasks.value.some(t => t.id === currentTaskId.value)) {
      currentTaskId.value = ''
      history.value = []
    }
  } catch {
    // 异常已由拦截器提示
  } finally {
    loadingTasks.value = false
  }
}

async function loadConnections() {
  try {
    connections.value = await listConnections()
  } catch {
    // 异常已由拦截器提示
  }
}

async function loadHistory() {
  if (!currentTaskId.value) {
    history.value = []
    return
  }
  loadingHistory.value = true
  try {
    const [start, end] = resolveRange()
    history.value = await listHistory(currentTaskId.value, start, end)
  } catch {
    // 异常已由拦截器提示
  } finally {
    loadingHistory.value = false
  }
}

/** 拉取当前任务 SQL 的列结构；失败时置空，画图退回正则兑底识别 */
async function loadColumns() {
  const task = currentTask.value
  if (!task?.connectionId || !task.customSql) {
    columns.value = []
    return
  }
  try {
    columns.value = await previewColumns(task.connectionId, task.customSql)
  } catch {
    columns.value = []
  }
  applyMetricDefaults()
}

/** 展示列默认勾选：优先死元组列，否则首个数值列 */
function applyMetricDefaults() {
  const nums = columns.value.filter(c => c.numeric).map(c => c.column)
  const dead = nums.find(n => /dead.*tup/i.test(n)) || nums.find(n => /dead/i.test(n))
  selectedMetrics.value = dead ? [dead] : nums.slice(0, 1)
}

/** 依据时间范围选项解析查询窗口（ISO 字符串） */
function resolveRange(): [string | undefined, string | undefined] {
  if (rangePreset.value === 'custom') {
    return customRange.value ? [customRange.value[0], customRange.value[1]] : [undefined, undefined]
  }
  const hours = rangePreset.value === '1h' ? 1 : 6
  const now = new Date()
  const from = new Date(now.getTime() - hours * 3600 * 1000)
  return [toIso(from), toIso(now)]
}

function toIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/* ==================== 通用 JSON 行 → 死元组业务解析（前端垂直闭环） ==================== */

/** 在行 JSON 的键中按候选特征匹配列名（大小写不敏感、子串命中） */
function pickColumn(keys: string[], ...patterns: RegExp[]): string | null {
  for (const p of patterns) {
    const hit = keys.find(k => p.test(k))
    if (hit) return hit
  }
  return null
}

/** 当前任务采样结果的列语义映射（从原始 JSON 动态解析，不预设死板列名） */
const columnMap = computed(() => {
  const keys = new Set<string>()
  history.value.forEach(h => Object.keys(h.row || {}).forEach(k => keys.add(k)))
  const all = [...keys]
  return {
    dead: pickColumn(all, /dead.*tup/i, /dead/i),
    live: pickColumn(all, /live.*tup/i, /live/i),
    entity: pickColumn(all, /^relname$/i, /(^|_)table(_name)?$/i, /name/i),
    // 模式列优先精确匹配 schemaname，再匹配泛化模式名；
    // 注意表名列的 /name/i 兜底可能误命中 schemaname，故实体识别依赖精确的 relname/table_name 规则先行
    schema: pickColumn(all, /^schemaname$/i, /^schema$/i, /(^|_)schema(_?name)?/i),
    vacuum: pickColumn(all, /autovacuum/i, /vacuum/i),
  }
})

const vacuumColumn = computed(() => columnMap.value.vacuum)

/** 行→实体展示名：维度列（非数值列，如 schemaname/relname）取值拼接为 "模式.表名"；
 * 无列元数据时退回正则识别的表名列 */
function qualifiedName(row: Record<string, unknown>): string {
  let parts: string[]
  if (dimensionCols.value.length > 0) {
    parts = dimensionCols.value.map(c => String(row[c.column] ?? ''))
  } else {
    const { entity, schema } = columnMap.value
    parts = [schema ? String(row[schema] ?? '') : '', entity ? String(row[entity] ?? '') : '']
  }
  const vals = parts.filter(v => v !== '')
  return vals.length ? vals.join('.') : '合计'
}

/** 维度列组合后剩余的纯文本列（时间列不算维度） */
const dimensionCols = computed(() =>
  columns.value.filter(c => !c.numeric && !c.temporal)
)

/** 展示列候选：全部数值列 */
const metricCandidates = computed(() => columns.value.filter(c => c.numeric).map(c => c.column))

/** 主展示列（指标卡片联动用） */
const primaryMetric = computed(() => selectedMetrics.value[0] || '')

/** 实体（模式.表名）去重清单，供表范围多选 */
const entityNames = computed(() => {
  const set = new Set<string>()
  history.value.forEach(h => {
    const name = qualifiedName(h.row)
    if (name !== '合计') set.add(name)
  })
  return [...set].sort()
})

/** 数值安全转换 */
function numOf(v: unknown): number {
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : 0
}

/** 按采样时间分组 → 时间轴 + 实体×展示列的取值桶（后绘图时每条线=表×展示列） */
const trendData = computed(() => {
  const times = [...new Set(history.value.map(h => h.samplingTime))].sort()
  const metrics = selectedMetrics.value
  if (times.length === 0 || metrics.length === 0) {
    return { labels: [] as string[], map: new Map<string, Map<string, number>>(), times: [] as string[] }
  }

  // 表范围过滤（空=全部）
  const inScope = (row: Record<string, unknown>) =>
    selectedEntities.value.length === 0 || selectedEntities.value.includes(qualifiedName(row))

  // 实体 -> 展示列 -> (samplingTime -> 求和)
  const perEntity = new Map<string, Map<string, Map<string, number>>>()
  for (const h of history.value) {
    if (!inScope(h.row)) continue
    const ent = qualifiedName(h.row)
    let byMetric = perEntity.get(ent)
    if (!byMetric) {
      byMetric = new Map()
      perEntity.set(ent, byMetric)
    }
    for (const m of metrics) {
      let bucket = byMetric.get(m)
      if (!bucket) {
        bucket = new Map()
        byMetric.set(m, bucket)
      }
      bucket.set(h.samplingTime, (bucket.get(h.samplingTime) || 0) + numOf(h.row[m]))
    }
  }

  // 线数卡口：实体×展示列组合最多 12 条；超出时按主展示列死元组量级取前 8 张表
  const deadHint = columnMap.value.dead
  let entities = [...perEntity.keys()].sort()
  if (entities.length * metrics.length > 12) {
    const latest = times[times.length - 1]
    entities.sort((a, b) => {
      const va = perEntity.get(a)?.get(deadHint || metrics[0])?.get(latest) ?? 0
      const vb = perEntity.get(b)?.get(deadHint || metrics[0])?.get(latest) ?? 0
      return vb - va
    })
    entities = entities.slice(0, 8)
  }

  // 抽稀：单条线最多约 400 点，高频长时间段仍流畅
  const step = Math.max(1, Math.ceil(times.length / 400))
  const keptTimes = times.filter((_, i) => i % step === 0 || i === times.length - 1)
  const labels = keptTimes.map(t => t.slice(11, 19) || t)

  // key = "实体||展示列"，取值桶按保留时间点重建
  const map = new Map<string, Map<string, number>>()
  for (const ent of entities) {
    const byMetric = perEntity.get(ent)!
    for (const m of metrics) {
      const src = byMetric.get(m)
      if (!src) continue
      const m2 = new Map<string, number>()
      keptTimes.forEach(t => {
        if (src.has(t)) m2.set(t, src.get(t)!)
      })
      map.set(`${ent}||${m}`, m2)
    }
  }
  return { labels, map, times: keptTimes }
})

const PALETTE = ['#f56c6c', '#409eff', '#67c23a', '#e6a23c', '#909399', '#9254de', '#13c2c2', '#fa541c']
/** 同一实体内不同展示列的线型（实线/虚线/点线循环） */
const DASHES = ['', '6 4', '2 3', '10 4 2 4']

const chartLabels = computed(() => trendData.value.labels)

const chartSeries = computed<TrendSeries[]>(() => {
  const data = trendData.value
  if (data.labels.length === 0) return []
  const multiMetric = selectedMetrics.value.length > 1
  const entityOrder = [...new Set([...data.map.keys()].map(k => k.split('||')[0]))]
  const series: TrendSeries[] = []
  data.map.forEach((bucket, key) => {
    const [ent, metric] = key.split('||')
    const color = PALETTE[entityOrder.indexOf(ent) % PALETTE.length]
    const dash = multiMetric ? DASHES[selectedMetrics.value.indexOf(metric) % DASHES.length] : ''
    series.push({
      label: multiMetric ? `${ent} · ${metric}` : ent,
      color,
      dash,
      points: data.times.map(t => bucket.get(t) ?? 0),
    })
  })
  return series
})

/* ==================== 指标卡片 ==================== */

/** 取筛选范围内（表多选+时间范围）最新时间点的全部行 */
function latestRows(): SamplingHistoryRow[] {
  const times = history.value.map(h => h.samplingTime).sort()
  if (times.length === 0) return []
  const latest = times[times.length - 1]
  return history.value.filter(h => {
    if (h.samplingTime !== latest) return false
    if (selectedEntities.value.length > 0) {
      return selectedEntities.value.includes(qualifiedName(h.row))
    }
    return true
  })
}

/** 主展示列在最新时间点的筛选范围求和 */
const metricPrimaryValue = computed(() => {
  const m = primaryMetric.value
  if (!m) return '-'
  const sum = latestRows().reduce((acc, h) => acc + numOf(h.row[m]), 0)
  return sum.toLocaleString()
})

const metricRatio = computed(() => {
  let deadCol = primaryMetric.value && /dead/i.test(primaryMetric.value) ? primaryMetric.value : null
  let liveCol = columns.value.find(c => c.numeric && /live/i.test(c.column))?.column || null
  // 列元数据缺失/不含 dead+live 时退回正则识别（兼容旧任务）
  if (!deadCol || !liveCol) {
    const { dead, live } = columnMap.value
    if (!dead || !live) return '-'
    deadCol = dead
    liveCol = live
  }
  const rows = latestRows()
  const deadSum = rows.reduce((a, h) => a + numOf(h.row[deadCol!]), 0)
  const liveSum = rows.reduce((a, h) => a + numOf(h.row[liveCol!]), 0)
  const total = deadSum + liveSum
  if (total === 0) return '0%'
  return ((deadSum / total) * 100).toFixed(2) + '%'
})

const metricLastVacuum = computed(() => {
  const col = columnMap.value.vacuum
  if (!col) return '-'
  let latest = ''
  for (const h of history.value) {
    if (selectedEntities.value.length > 0 && !selectedEntities.value.includes(qualifiedName(h.row))) continue
    const v = h.row[col]
    if (v === null || v === undefined) continue
    const s = String(v)
    if (s > latest) latest = s
  }
  return latest ? latest.replace('T', ' ').slice(0, 19) : '暂无清理记录'
})

/* ==================== 任务操作 ==================== */
function selectTask(id: string) {
  if (currentTaskId.value === id) return
  currentTaskId.value = id
  selectedMetrics.value = []
  selectedEntities.value = []
  loadColumns()
  loadHistory()
}

function openCreate() {
  editingTask.value = null
  showTaskDialog.value = true
}

function openEdit() {
  if (!currentTask.value) return
  editingTask.value = { ...currentTask.value }
  showTaskDialog.value = true
}

async function onTaskSaved() {
  await loadTasks()
  if (!currentTaskId.value && tasks.value.length) {
    selectTask(tasks.value[0].id!)
  } else {
    // SQL 可能已改，刷新列候选与明细
    await loadColumns()
    loadHistory()
  }
}

async function handleStart() {
  if (!currentTaskId.value) return
  actioning.value = true
  try {
    await startTask(currentTaskId.value)
    ElMessage.success('采样任务已启动')
    await loadTasks()
    scheduleHistoryRefresh()
  } catch {
    // 异常已由拦截器提示
  } finally {
    actioning.value = false
  }
}

async function handleStop() {
  if (!currentTaskId.value) return
  actioning.value = true
  try {
    await stopTask(currentTaskId.value)
    ElMessage.success('采样任务已停止')
    await loadTasks()
    loadHistory()
  } catch {
    // 异常已由拦截器提示
  } finally {
    actioning.value = false
  }
}

async function handleDelete() {
  const task = currentTask.value
  if (!task?.id) return
  try {
    await ElMessageBox.confirm(
      `确定删除任务【${task.taskName}】？其全部采样历史将一并清除`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  actioning.value = true
  try {
    await deleteTask(task.id)
    ElMessage.success('任务已删除')
    currentTaskId.value = ''
    history.value = []
    await loadTasks()
  } catch {
    // 异常已由拦截器提示
  } finally {
    actioning.value = false
  }
}

/* ==================== 自动刷新（运行中任务） ==================== */
let pollTimer: ReturnType<typeof setInterval> | null = null

function scheduleHistoryRefresh() {
  loadHistory()
}

function ensurePolling() {
  pollTimer = setInterval(async () => {
    await loadTasks()
    const cur = currentTask.value
    if (cur && cur.status === 'RUNNING') {
      await loadHistory()
    }
  }, 5000)
}

watch(rangePreset, () => loadHistory())
watch(customRange, () => {
  if (rangePreset.value === 'custom') loadHistory()
})

/* ==================== 展示辅助 ==================== */
function connById(connectionId: string): DbConnectionConfig | undefined {
  return connections.value.find(c => c.id === connectionId)
}

function connLabel(connectionId: string): string {
  return connById(connectionId)?.alias || '连接已失效'
}

function connDetail(connectionId: string): string {
  const c = connById(connectionId)
  if (!c) return '连接已失效（请检查连接配置）'
  return `${c.alias}（${c.dialect} · ${c.host}:${c.port}/${c.dbName}）`
}

function statusLabel(status: string): string {
  const map: Record<string, string> = { RUNNING: '运行中', STOPPED: '已停止', COMPLETED: '已完成' }
  return map[status] || status
}

function statusTag(status: string): 'success' | 'info' | 'warning' {
  if (status === 'RUNNING') return 'success'
  if (status === 'COMPLETED') return 'info'
  return 'warning'
}

function fmtTime(t?: string): string {
  return t ? t.replace('T', ' ').slice(0, 19) : '-'
}

/* ==================== 生命周期 ==================== */
onMounted(async () => {
  await Promise.all([loadTasks(), loadConnections()])
  if (tasks.value.length) {
    currentTaskId.value = tasks.value[0].id!
    await Promise.all([loadColumns(), loadHistory()])
  }
  ensurePolling()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.dead-tuple {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 12px 16px;
  box-sizing: border-box;
}

/* ====== 标题栏（与数据库性能分析工作室同风格） ====== */
.demo-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.header-title {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.header-sub {
  font-size: 12px;
  color: #909399;
}

/* ====== 左右分栏 ====== */
.split-body {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 12px;
  flex: 1;
  min-height: 0;
  margin-top: 12px;
}

.task-sidebar {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  min-height: 0;
  overflow: hidden;
}

.sidebar-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 4px;
  padding: 10px;
  border-bottom: 1px solid #ebeef5;
}

.sidebar-head-actions {
  display: flex;
  align-items: center;
}

.task-list {
  flex: 1;
  min-height: 0;
}

.task-item {
  padding: 10px 12px;
  margin: 6px 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.task-item:hover {
  border-color: #c6e2ff;
  background: #f5f9ff;
}

.task-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.task-line1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
}

.task-name {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-line2 {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.task-conn {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ====== 右侧 ====== */
.right-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow: auto;
}

.summary-bar {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  padding: 12px 16px;
  flex-shrink: 0;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px 20px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.summary-label {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.summary-value {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-sql {
  margin-top: 8px;
}

.sql-preview {
  margin: 4px 0 0;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #606266;
  max-height: 92px;
  overflow: auto;
  white-space: pre-wrap;
}

.summary-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

/* ====== 看板区 ====== */
.dashboard {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  padding: 12px 16px;
  flex: 1;
  min-height: 0;
}

.dash-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.toolbar-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-label {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.entity-select {
  width: 240px;
}

.metric-select {
  width: 220px;
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin: 12px 0;
}

.metric-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  background: linear-gradient(180deg, #fff 0%, #fafbfd 100%);
}

.metric-title {
  font-size: 12px;
  color: #909399;
}

.metric-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin: 6px 0 2px;
  word-break: break-all;
}

.metric-value.danger {
  color: #f56c6c;
}

.metric-value.vacuum {
  font-size: 14px;
  color: #67c23a;
}

.metric-sub {
  font-size: 12px;
  color: #a8abb2;
}

.chart-block {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
}

.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}

.btn-icon {
  margin-right: 4px;
}
</style>
