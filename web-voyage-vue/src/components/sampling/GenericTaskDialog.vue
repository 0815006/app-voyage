<template>
  <el-dialog
    :model-value="visible"
    width="680px"
    :title="form.id ? '编辑采样统计任务' : '新建采样统计任务'"
    @update:model-value="emit('update:visible', $event)"
    @open="openHandler"
  >
    <el-form :model="form" label-width="110px" @submit.prevent>
      <el-form-item label="任务名称" required>
        <el-input v-model="form.taskName" placeholder="如：订单量按小时统计" maxlength="100" />
      </el-form-item>

      <el-form-item label="数据库连接" required>
        <el-select v-model="form.connectionId" placeholder="复用「数据库性能分析」已维护的连接" style="width: 100%" @change="onConnectionChange">
          <el-option
            v-for="conn in connections"
            :key="conn.id"
            :label="`${conn.alias}（${conn.dialect} · ${conn.host}:${conn.port}/${conn.dbName}）`"
            :value="conn.id || ''"
          />
        </el-select>
        <div v-if="connections.length === 0" class="form-tip warn">
          尚未维护数据库连接，请先点击任务列表右上角齿轮按钮配置连接
        </div>
      </el-form-item>

      <el-form-item label="自定义 SQL" required>
        <el-input
          v-model="form.customSql"
          type="textarea"
          :rows="8"
          placeholder="任意只读统计 SQL（仅允许 SELECT / WITH 开头）。结果集列将被自动解析：文本列可作分组维度，数值列可作统计指标"
        />
        <div class="sql-actions">
          <el-button link type="primary" size="small" @click="applySampleTemplate">载入示例 SQL</el-button>
          <el-button link type="warning" size="small" :loading="parsing" @click="handleParseColumns">解析列结构</el-button>
          <span class="form-tip">不执行查询，通过数据库元数据获取列名与类型</span>
        </div>
        <div v-if="parsedColumns.length" class="column-preview">
          <el-tag
            v-for="c in parsedColumns"
            :key="c.column"
            size="small"
            :type="c.numeric ? 'success' : c.temporal ? 'warning' : 'info'"
            effect="plain"
          >{{ c.column }} · {{ c.label }}{{ c.numeric ? ' → 指标列' : c.temporal ? ' → 时间列' : ' → 维度列' }}</el-tag>
        </div>
      </el-form-item>

      <el-form-item label="绘图配置">
        <div class="chart-config">
          <div class="cfg-item">
            <span class="cfg-label">筛选列</span>
            <el-select v-model="chartFilterCols" multiple filterable collapse-tags collapse-tags-tooltip
                       :max-collapse-tags="2" size="small" class="cfg-select" :disabled="!columnsReady"
                       placeholder="绘制时的筛选/分组条件列（空=合一条线）" @change="onFilterColsChange">
              <el-option v-for="c in columnNames" :key="c" :label="c" :value="c" />
            </el-select>
          </div>
          <div class="cfg-item">
            <span class="cfg-label">展示列</span>
            <el-select v-model="chartDisplayCols" multiple collapse-tags collapse-tags-tooltip
                       :max-collapse-tags="2" size="small" class="cfg-select" :disabled="!columnsReady"
                       placeholder="每个展示列单独一张图">
              <el-option v-for="c in displayCandidates" :key="c" :label="c" :value="c" />
            </el-select>
          </div>
          <div class="form-tip">
            <template v-if="columnsReady">
              固化「怎么画」：筛选列 = 图内每条线的分组条件，展示列 = 每张图一个纵轴指标；
              具体筛选项（数据值）在绘图页临时选择，不随任务保存
            </template>
            <template v-else>请先点「解析列结构」拿到列候选后设定；不选则绘图页按「首个维度列 + 首个数值列」推断</template>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="采样计划" required>
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="采样频率" required>
        <el-input-number v-model="form.frequency" :min="1" :max="86400" :step="1" />
        <span class="form-tip" style="margin-left: 10px">秒/次（每次执行 SQL 并将结果行落库为一条采样数据）</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { SamplingColumn, SamplingTask } from '@/api/sampling'
import { saveTask, previewColumns } from '@/api/sampling'
import { alignChartConfig, defaultChartConfig, serializeChartConfig } from '@/components/sampling/chartConfig'
import type { DbConnectionConfig } from '@/api/db-analysis'

/** 采样类型标识：通用数据库采样统计（与后端 sampling_type 字段对应，复用通用采样底座） */
const SAMPLING_TYPE = 'DB_SAMPLER'

/** 示例 SQL：PostgreSQL  catalogs 通用统计，纯演示结果集结构（维度列 + 指标列），用户可自行替换 */
const SAMPLE_SQL = `SELECT schemaname,
       relname,
       n_live_tup,
       n_dead_tup
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC
LIMIT 50`

const props = defineProps<{
  visible: boolean
  /** 编辑时传入的任务对象；新建传 null */
  task: SamplingTask | null
  /** 可选连接清单（来自数据库性能分析模块的连接配置） */
  connections: DbConnectionConfig[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
}>()

const saving = ref(false)
const parsing = ref(false)
const timeRange = ref<[string, string] | null>(null)
/** 「解析列结构」预览结果（每次改 SQL/换连接后需重新解析，作为筛选列/展示列候选） */
const parsedColumns = ref<SamplingColumn[]>([])
/** 随任务保存的绘图配置（写入 chart_config，后端黑盒存取） */
const chartFilterCols = ref<string[]>([])
const chartDisplayCols = ref<string[]>([])

const emptyForm = (): SamplingTask => ({
  id: undefined,
  taskName: '',
  connectionId: '',
  samplingType: SAMPLING_TYPE,
  customSql: SAMPLE_SQL,
  startTime: '',
  endTime: '',
  frequency: 60,
})

const form = reactive<SamplingTask>(emptyForm())

/** 列候选已就绪（已解析过 SQL 列结构）才可设定绘图配置 */
const columnsReady = computed(() => parsedColumns.value.length > 0)
const columnNames = computed(() => parsedColumns.value.map(c => c.column))
/** 展示列候选：非筛选列的全部列 */
const displayCandidates = computed(() =>
  parsedColumns.value.filter(c => !chartFilterCols.value.includes(c.column)).map(c => c.column)
)

function onFilterColsChange() {
  chartDisplayCols.value = chartDisplayCols.value.filter(c => !chartFilterCols.value.includes(c))
}

/** 换连接后列结构需重新解析，已选绘图列随之失效 */
function onConnectionChange() {
  parsedColumns.value = []
  chartFilterCols.value = []
  chartDisplayCols.value = []
}

function openHandler() {
  Object.assign(form, emptyForm())
  parsedColumns.value = []
  chartFilterCols.value = []
  chartDisplayCols.value = []
  if (props.task) {
    Object.assign(form, props.task)
    timeRange.value = [props.task.startTime, props.task.endTime]
    // 编辑态打开即解析列结构（不执行查询），并把已存配置与当前列对齐后回填
    void refreshColumnsAndAlignConfig()
  } else {
    // 新建默认：从现在开始 1 小时的采样窗口
    const now = new Date()
    const later = new Date(now.getTime() + 60 * 60 * 1000)
    timeRange.value = [fmtLocal(now), fmtLocal(later)]
  }
}

/** 解析列结构并将任务已存 chart_config 对齐后回填（SQL 变更后的失效列自动剔除） */
async function refreshColumnsAndAlignConfig() {
  if (!form.connectionId || !form.customSql.trim()) return
  parsing.value = true
  try {
    parsedColumns.value = await previewColumns(form.connectionId, form.customSql)
    const cfg = alignChartConfig(props.task?.chartConfig, parsedColumns.value)
    chartFilterCols.value = cfg.filterCols
    chartDisplayCols.value = cfg.displayCols
  } catch {
    parsedColumns.value = []
    // 异常已由拦截器提示
  } finally {
    parsing.value = false
  }
}

function fmtLocal(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function applySampleTemplate() {
  form.customSql = SAMPLE_SQL
  parsedColumns.value = []
  chartFilterCols.value = []
  chartDisplayCols.value = []
}

/** 调后端列预览接口：不执行查询，验证 SQL 可解析并展示列清单 */
async function handleParseColumns() {
  if (!form.connectionId) {
    ElMessage.warning('请先选择数据库连接')
    return
  }
  if (!form.customSql.trim()) {
    ElMessage.warning('请先填写自定义 SQL')
    return
  }
  parsing.value = true
  try {
    parsedColumns.value = await previewColumns(form.connectionId, form.customSql)
    ElMessage.success(`解析成功，共 ${parsedColumns.value.length} 列`)
    // 尚未设定过绘图配置时给一份默认可用推断
    if (chartFilterCols.value.length === 0 && chartDisplayCols.value.length === 0) {
      const cfg = defaultChartConfig(parsedColumns.value)
      chartFilterCols.value = cfg.filterCols
      chartDisplayCols.value = cfg.displayCols
    } else {
      onFilterColsChange()
    }
  } catch {
    parsedColumns.value = []
    // 异常已由拦截器提示（SQL 语法/表不存在等原因在后端消息中）
  } finally {
    parsing.value = false
  }
}

function validate(): boolean {
  if (!form.taskName.trim()) {
    ElMessage.warning('请填写任务名称')
    return false
  }
  if (!form.connectionId) {
    ElMessage.warning('请选择数据库连接')
    return false
  }
  const sql = form.customSql.trim()
  if (!sql) {
    ElMessage.warning('请填写自定义 SQL')
    return false
  }
  if (!/^SELECT\b/i.test(sql) && !/^WITH\b/i.test(sql)) {
    ElMessage.warning('采样 SQL 仅允许 SELECT / WITH 开头的只读查询')
    return false
  }
  if (!timeRange.value || !timeRange.value[0] || !timeRange.value[1]) {
    ElMessage.warning('请设定采样起止时间窗口')
    return false
  }
  return true
}

async function handleSave() {
  if (!validate()) return
  saving.value = true
  try {
    await saveTask({
      ...form,
      samplingType: SAMPLING_TYPE,
      startTime: timeRange.value![0],
      endTime: timeRange.value![1],
      // 未配置时写空串（而非 undefined）：避免后端动态更新跳过该列、残留旧配置
      chartConfig:
        serializeChartConfig({
          filterCols: chartFilterCols.value,
          displayCols: chartDisplayCols.value,
        }) ?? '',
    })
    ElMessage.success(form.id ? '任务已更新' : '任务已创建')
    emit('saved')
    emit('update:visible', false)
  } catch {
    // 异常已由拦截器提示
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  width: 100%;
}

.form-tip.warn {
  color: #e6a23c;
}

.sql-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.column-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  width: 100%;
}

.chart-config {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.cfg-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cfg-label {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
  width: 48px;
}

.cfg-select {
  width: 100%;
}
</style>
