<template>
  <div class="db-sampler">
    <!-- ====== 标题栏 ====== -->
    <div class="demo-header">
      <div class="header-left">
        <h2 class="header-title">📊 数据库采样统计</h2>
        <span class="header-sub">通用 SQL 采样 · 筛选列 + 展示列存任务定义 · 每个展示列一张图 · 每个筛选项一条线</span>
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

      <!-- ====== 右侧：概要 + 图表 ====== -->
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
              <span class="summary-label">采样 SQL</span>
              <pre class="sql-preview">{{ currentTask.customSql }}</pre>
            </div>
            <div class="summary-actions">
              <el-button
                v-if="currentTask.status !== 'RUNNING'"
                type="success"
                size="small"
                :loading="actioning"
                @click="handleStart"
              >启动采样</el-button>
              <el-button
                v-else
                type="warning"
                size="small"
                :loading="actioning"
                @click="handleStop"
              >停止采样</el-button>
              <el-tooltip
                :content="editLocked
                  ? '运行中或已有采样记录时不可编辑任务定义，请先停止并清理已采集记录'
                  : '编辑任务定义（含筛选列/展示列）'"
                placement="top"
              >
                <span>
                  <el-button size="small" :disabled="editLocked" @click="openEdit">编辑任务</el-button>
                </span>
              </el-tooltip>
              <el-tooltip
                :content="sampledCount ? '删除该任务全部采样记录（不可恢复），清完才能改任务定义' : '暂无已采集记录'"
                placement="top"
              >
                <span>
                  <el-button
                    type="warning"
                    plain
                    size="small"
                    :loading="actioning"
                    :disabled="currentTask.status === 'RUNNING' || sampledCount === 0"
                    @click="handleClearHistory"
                  >清理已采集记录</el-button>
                </span>
              </el-tooltip>
              <el-button type="danger" plain size="small" :loading="actioning" @click="handleDelete">删除任务</el-button>
            </div>
          </section>

          <!-- 图表配置 + 可视化区 -->
          <section class="dashboard">
            <div class="dash-toolbar">
              <div class="toolbar-item">
                <span class="toolbar-label">筛选列</span>
                <el-select v-model="filterCols" multiple filterable collapse-tags collapse-tags-tooltip
                           :max-collapse-tags="2" placeholder="绘制时作为筛选/分组条件的列" class="dim-select"
                           @change="onFilterColsChange">
                  <el-option v-for="c in allColumnNames" :key="c" :label="c" :value="c" />
                </el-select>
              </div>
              <div class="toolbar-item">
                <span class="toolbar-label">筛选项</span>
                <el-select v-model="selectedFilterValues" multiple filterable collapse-tags collapse-tags-tooltip
                           :max-collapse-tags="2" placeholder="选定纳入绘制的筛选项（空=全部）" class="entity-select">
                  <el-option v-for="name in filterItemNames" :key="name" :label="name" :value="name" />
                </el-select>
              </div>
              <div class="toolbar-item">
                <span class="toolbar-label">展示列</span>
                <el-select v-model="displayCols" multiple collapse-tags collapse-tags-tooltip
                           :max-collapse-tags="2" placeholder="每个展示列单独一张图" class="metric-select">
                  <el-option v-for="c in displayColCandidates" :key="c" :label="c" :value="c" />
                </el-select>
              </div>
              <div class="toolbar-item">
                <span class="toolbar-label">时间范围</span>
                <el-radio-group v-model="rangePreset" size="small">
                  <el-radio-button value="1h">1 小时</el-radio-button>
                  <el-radio-button value="6h">6 小时</el-radio-button>
                  <el-radio-button value="all">全部</el-radio-button>
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
              <span class="toolbar-note">筛选列/展示列默认取任务定义；此处临时调整仅影响当前查看，固化请「编辑任务」</span>
            </div>

            <!-- 概览卡片 -->
            <div class="metric-cards">
              <div class="metric-card">
                <div class="metric-title">采样明细行</div>
                <div class="metric-value">{{ history.length }}</div>
                <div class="metric-sub">{{ currentTask.status === 'RUNNING' ? '运行中 · 手动点击刷新' : '任务未运行' }}</div>
              </div>
              <div class="metric-card">
                <div class="metric-title">纳入筛选项</div>
                <div class="metric-value">{{ scopeItemCount }}</div>
                <div class="metric-sub">{{ filterCols.length ? '按 ' + filterCols.join(' + ') : '未选筛选列 · 单组合计' }}</div>
              </div>
              <div class="metric-card">
                <div class="metric-title">展示列 · 图数</div>
                <div class="metric-value accent">{{ displayCols.length }}</div>
                <div class="metric-sub">每列单独一张图</div>
              </div>
              <div class="metric-card">
                <div class="metric-title">时间点数</div>
                <div class="metric-value">{{ timePointCount }}</div>
                <div class="metric-sub">横轴采样点</div>
              </div>
            </div>

            <!-- 图表：每个展示列一张图，图内每个筛选项一条线 -->
            <div v-if="trendModel.length" class="charts-wrap">
              <div v-for="c in trendModel" :key="c.metric" class="chart-block">
                <div class="chart-title">{{ c.metric }} · 波动趋势（横轴=采样时间 · 每条线=一个筛选项，不同颜色区分）</div>
                <TrendChart :labels="c.labels" :series="c.series" />
              </div>
            </div>
            <el-empty v-else description="请选择展示列（及筛选列/筛选项），点击“刷新数据”绘制波动图" :image-size="60" />

            <!-- 采样数据明细表 -->
            <div class="table-block">
              <div class="chart-title">
                采样数据明细（共 {{ detailRows.length }} 行）
              </div>
              <el-table :data="pagedRows" size="small" max-height="320" border stripe>
                <el-table-column prop="samplingTime" label="采样时间" width="180" fixed />
                <el-table-column
                  v-for="col in columns"
                  :key="col.column"
                  :prop="col.column"
                  :label="col.column"
                  min-width="140"
                  show-overflow-tooltip
                >
                  <template #default="{ row }">{{ row[col.column] ?? '' }}</template>
                </el-table-column>
              </el-table>
              <div class="table-pager">
                <el-pagination
                  v-model:current-page="currentPage"
                  :page-size="pageSize"
                  :total="detailRows.length"
                  layout="prev, pager, next, total"
                  small
                  background
                />
              </div>
            </div>
          </section>
        </template>

        <!-- 未选中任务的空态 -->
        <el-empty v-else description="请在左侧选择或新建一个采样统计任务" />
      </main>
    </div>

    <!-- 新建/编辑任务弹窗 -->
    <GenericTaskDialog
      v-model:visible="showTaskDialog"
      :task="editingTask"
      :connections="connections"
      @saved="onTaskSaved"
    />

    <!-- 数据库连接配置弹窗（复用数据库性能分析模块入口组件） -->
    <ConnectionDialog
      v-model:visible="showConnDialog"
      :connections="connections"
      @changed="loadConnections"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Setting } from '@element-plus/icons-vue'
import type { SamplingColumn, SamplingHistoryRow, SamplingTask } from '@/api/sampling'
import { clearHistory, deleteTask, listHistory, listTasks, previewColumns, startTask, stopTask } from '@/api/sampling'
import { alignChartConfig } from '@/components/sampling/chartConfig'
import type { DbConnectionConfig } from '@/api/db-analysis'
import { listConnections } from '@/api/db-analysis'
import ConnectionDialog from '@/components/db-analysis/ConnectionDialog.vue'
import GenericTaskDialog from '@/components/sampling/GenericTaskDialog.vue'
import TrendChart, { type TrendSeries } from '@/components/sampling/TrendChart.vue'

/** 本页面任务类型标识（复用通用采样底座，与死元组监测区分） */
const SAMPLING_TYPE = 'DB_SAMPLER'

/* ==================== 状态 ==================== */
const tasks = ref<SamplingTask[]>([])
const connections = ref<DbConnectionConfig[]>([])
const history = ref<SamplingHistoryRow[]>([])
const columns = ref<SamplingColumn[]>([])
const currentTaskId = ref<string>('')

/* 图表配置：筛选列/展示列 默认取任务定义的 chart_config（后端黑盒存取），
 * 在此临时调整仅影响当前查看；筛选项（数据值）始终属查看态，不落库。
 * 每个展示列 → 单独一张图；每张图内 → 每个筛选项一条线，不同颜色区分。 */
const filterCols = ref<string[]>([])
const selectedFilterValues = ref<string[]>([])
const displayCols = ref<string[]>([])
const rangePreset = ref<'1h' | '6h' | 'all' | 'custom'>('1h')
const customRange = ref<[string, string] | null>(null)

const loadingTasks = ref(false)
const loadingHistory = ref(false)
const actioning = ref(false)
const showTaskDialog = ref(false)
const showConnDialog = ref(false)
const editingTask = ref<SamplingTask | null>(null)

const currentPage = ref(1)
const pageSize = 50

const currentTask = computed(
  () => tasks.value.find(t => t.id === currentTaskId.value) || null
)

/** 已采集行数：优先取后端瞬态 historyCount（全量准确），回退本地明细行数 */
const sampledCount = computed(() => currentTask.value?.historyCount ?? history.value.length)

/**
 * 任务定义锁定：运行中或已采集过数据即不可编辑（后端 saveTask 同步卡口防绕过）。
 * 出口只有两个：清理已采集记录后编辑，或另建新任务。
 */
const editLocked = computed(
  () => !currentTask.value || currentTask.value.status === 'RUNNING' || sampledCount.value > 0
)

/* ==================== 加载 ==================== */
async function loadTasks() {
  loadingTasks.value = true
  try {
    const all = await listTasks()
    // 后端 listTasks 返回全部类型，本页面仅展示通用采样统计任务
    tasks.value = all.filter(t => t.samplingType === SAMPLING_TYPE)
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
    currentPage.value = 1
  } catch {
    // 异常已由拦截器提示
  } finally {
    loadingHistory.value = false
  }
}

/** 拉取当前任务 SQL 的列结构；失败时置空 */
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
  applyChartConfig()
}

/**
 * 从任务定义载入绘图配置：与当前 SQL 解析出的列对齐（失效列剔除），
 * 无配置/全失效时回退默认推断（首个维度列作筛选列、首个数值列作展示列）。
 */
function applyChartConfig() {
  const cfg = alignChartConfig(currentTask.value?.chartConfig, columns.value)
  filterCols.value = cfg.filterCols
  displayCols.value = cfg.displayCols
  selectedFilterValues.value = []
}

/** 解析查询窗口（ISO 字符串） */
function resolveRange(): [string | undefined, string | undefined] {
  if (rangePreset.value === 'all') return [undefined, undefined]
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

/* ==================== 列候选 ==================== */
/** 全部列名（可选作筛选列） */
const allColumnNames = computed(() => columns.value.map(c => c.column))
/** 展示列候选：全部非筛选列 */
const displayColCandidates = computed(() =>
  columns.value.filter(c => !filterCols.value.includes(c.column)).map(c => c.column)
)

/* ==================== 筛选列/筛选项工具 ==================== */
function numOf(v: unknown): number {
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : 0
}

/** 行 → 筛选项键：筛选列取值拼接；无筛选列时 '合计' */
function filterItemKey(row: Record<string, unknown>): string {
  if (filterCols.value.length === 0) return '合计'
  const parts = filterCols.value.map(c => String(row[c] ?? '')).filter(v => v !== '')
  return parts.length ? parts.join(' · ') : '合计'
}

/** 行是否纳入绘制（未选筛选项=全部） */
function inScope(row: Record<string, unknown>): boolean {
  return selectedFilterValues.value.length === 0
    || selectedFilterValues.value.includes(filterItemKey(row))
}

/** 切换筛选列：清空依赖旧维度的筛选项选择，并从展示列剔除新挪入筛选列的字段 */
function onFilterColsChange() {
  selectedFilterValues.value = []
  displayCols.value = displayCols.value.filter(c => !filterCols.value.includes(c))
}

/* ==================== 波动图（每个展示列一张图，图内每个筛选项一条线） ==================== */
const PALETTE = ['#f56c6c', '#409eff', '#67c23a', '#e6a23c', '#909399', '#9254de', '#13c2c2', '#fa541c', '#2f54eb', '#eb2f96', '#389e0d', '#d4380d']

/** 数据中出现的全部筛选项（去重排序），供“筛选项”多选框 */
const filterItemNames = computed(() => {
  const set = new Set<string>()
  for (const h of history.value) set.add(filterItemKey(h.row))
  return [...set].sort()
})

/** 纳入绘制的筛选项（去重排序），用于稳定配色——同一筛选项在所有图中颜色一致 */
const scopedFilterItems = computed(() => {
  const set = new Set<string>()
  for (const h of history.value) {
    if (inScope(h.row)) set.add(filterItemKey(h.row))
  }
  return [...set].sort()
})

function itemColor(item: string): string {
  const idx = scopedFilterItems.value.indexOf(item)
  return PALETTE[(idx < 0 ? 0 : idx) % PALETTE.length]
}

/** 单张波动图：一个展示列 + 时间轴标签 + 每个筛选项一条线 */
interface MetricChart {
  metric: string
  labels: string[]
  series: TrendSeries[]
}

/**
 * 每个展示列单独一张图（多个展示列→多张图）：
 * 横轴=采样时间（同一筛选项同一时间点多行求和），图内每条线=一个筛选项，不同颜色区分。
 */
const trendModel = computed<MetricChart[]>(() => {
  const metrics = displayCols.value
  const times = [...new Set(history.value.map(h => h.samplingTime))].sort()
  if (times.length === 0 || metrics.length === 0) return []

  // 抽稀：单条线最多约 400 点，高频长时间段仍流畅
  const step = Math.max(1, Math.ceil(times.length / 400))
  const keptTimes = times.filter((_, i) => i % step === 0 || i === times.length - 1)
  const labels = keptTimes.map(t => t.slice(11, 19) || t)
  const latest = times[times.length - 1]

  const charts: MetricChart[] = []
  for (const m of metrics) {
    // 筛选项 -> (samplingTime -> 求和)
    const perItem = new Map<string, Map<string, number>>()
    for (const h of history.value) {
      if (!inScope(h.row)) continue
      const item = filterItemKey(h.row)
      let bucket = perItem.get(item)
      if (!bucket) {
        bucket = new Map()
        perItem.set(item, bucket)
      }
      bucket.set(h.samplingTime, (bucket.get(h.samplingTime) || 0) + numOf(h.row[m]))
    }

    // 线数卡口：单图最多 12 条线；超出按最新时间点取值取前 12 个筛选项
    let items = [...perItem.keys()]
    if (items.length > 12) {
      items.sort((a, b) => (perItem.get(b)!.get(latest) ?? 0) - (perItem.get(a)!.get(latest) ?? 0))
      items = items.slice(0, 12)
    } else {
      items.sort()
    }

    const series: TrendSeries[] = items.map(item => ({
      label: item,
      color: itemColor(item),
      dash: '',
      points: keptTimes.map(t => perItem.get(item)!.get(t) ?? 0),
    }))
    charts.push({ metric: m, labels, series })
  }
  return charts
})

/* ==================== 概览卡片派生 ==================== */
const scopeItemCount = computed(() => scopedFilterItems.value.length)
const timePointCount = computed(() => new Set(history.value.map(h => h.samplingTime)).size)

/* ==================== 明细表数据 ==================== */
const detailRows = computed(() => {
  // 展开每行：samplingTime + 各列取值
  return history.value
    .map(h => {
      const flat: Record<string, unknown> = { samplingTime: h.samplingTime }
      for (const c of columns.value) flat[c.column] = h.row[c.column]
      return flat
    })
    .sort((a, b) => String(b.samplingTime).localeCompare(String(a.samplingTime)))
})

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return detailRows.value.slice(start, start + pageSize)
})

/* ==================== 任务操作 ==================== */
function selectTask(id: string) {
  if (currentTaskId.value === id) return
  currentTaskId.value = id
  filterCols.value = []
  displayCols.value = []
  selectedFilterValues.value = []
  loadColumns()
  loadHistory()
}

function openCreate() {
  editingTask.value = null
  showTaskDialog.value = true
}

function openEdit() {
  if (!currentTask.value || editLocked.value) return
  editingTask.value = { ...currentTask.value }
  showTaskDialog.value = true
}

async function onTaskSaved() {
  await loadTasks()
  if (!currentTaskId.value && tasks.value.length) {
    selectTask(tasks.value[0].id!)
  } else {
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
    loadHistory()
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

async function handleClearHistory() {
  const task = currentTask.value
  if (!task?.id) return
  if (task.status === 'RUNNING') {
    ElMessage.warning('任务运行中，请先停止采样再清理已采集记录')
    return
  }
  const rows = sampledCount.value
  try {
    await ElMessageBox.confirm(
      `将删除任务【${task.taskName}】的全部 ${rows} 行采样记录，不可恢复；清理后任务定义方可编辑（状态归位已停止）。确定继续？`,
      '清理已采集记录',
      { type: 'warning', confirmButtonText: '确定清理', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actioning.value = true
  try {
    const deleted = await clearHistory(task.id)
    ElMessage.success(`已清理 ${typeof deleted === 'number' ? deleted : rows} 行采样记录，现在可编辑任务定义`)
    history.value = []
    currentPage.value = 1
    await loadTasks()
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

/* 刷新仅由手动点击“刷新数据”按钮触发（绘制不自动轮询） */

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
})
</script>

<style scoped>
.db-sampler {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 12px 16px;
  box-sizing: border-box;
}

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

.toolbar-note {
  font-size: 12px;
  color: #a8abb2;
}

.dim-select {
  width: 200px;
}

.metric-select {
  width: 200px;
}

.agg-select {
  width: 96px;
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

.metric-value.accent {
  color: #409eff;
}

.metric-sub {
  font-size: 12px;
  color: #a8abb2;
}

.charts-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 4px;
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

.table-block {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  margin-top: 12px;
}

.table-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.btn-icon {
  margin-right: 4px;
}
</style>
