<template>
  <el-dialog
    :model-value="visible"
    width="680px"
    :title="form.id ? '编辑死元组采样任务' : '新建死元组采样任务'"
    @update:model-value="emit('update:visible', $event)"
    @open="openHandler"
  >
    <el-form :model="form" label-width="110px" @submit.prevent>
      <el-form-item label="任务名称" required>
        <el-input v-model="form.taskName" placeholder="如：订单库核心表死元组监测" maxlength="100" />
      </el-form-item>

      <el-form-item label="数据库连接" required>
        <el-select v-model="form.connectionId" placeholder="复用「数据库性能分析」已维护的连接" style="width: 100%" @change="parsedColumns = []">
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

      <el-form-item label="采样类型">
        <el-input model-value="DEAD_TUPLE（死元组监测采样）" disabled />
        <div class="form-tip">通用采样架构的类型标识，当前版本固定为死元组监测</div>
      </el-form-item>

      <el-form-item label="自定义 SQL" required>
        <el-input
          v-model="form.customSql"
          type="textarea"
          :rows="7"
          placeholder="仅允许 SELECT / WITH 开头的只读查询"
        />
        <div class="sql-actions">
          <el-button link type="primary" size="small" @click="applyGaussTemplate">载入高斯死元组查询模板</el-button>
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
          >{{ c.column }} · {{ c.label }}{{ c.numeric ? ' → 可作展示列' : '' }}</el-tag>
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
        <el-input-number v-model="form.frequency" :min="1" :max="3600" :step="1" />
        <span class="form-tip" style="margin-left: 10px">秒/次（高频采样建议 2 秒）</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { SamplingColumn, SamplingTask } from '@/api/sampling'
import { saveTask, previewColumns } from '@/api/sampling'
import type { DbConnectionConfig } from '@/api/db-analysis'

/** 内置高斯死元组查询模板（pg_stat_user_tables，含模式名便于同名表跨模式区分） */
const GAUSS_DEAD_TUPLE_SQL = `SELECT schemaname,
       relname,
       n_dead_tup,
       n_live_tup,
       last_autovacuum,
       last_autoanalyze
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC`

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
/** 「解析列结构」预览结果（每次改 SQL/换连接后需重新解析，仅辅助确认，不落库） */
const parsedColumns = ref<SamplingColumn[]>([])

const emptyForm = (): SamplingTask => ({
  id: undefined,
  taskName: '',
  connectionId: '',
  samplingType: 'DEAD_TUPLE',
  customSql: GAUSS_DEAD_TUPLE_SQL,
  startTime: '',
  endTime: '',
  frequency: 2,
})

const form = reactive<SamplingTask>(emptyForm())

function openHandler() {
  Object.assign(form, emptyForm())
  parsedColumns.value = []
  if (props.task) {
    Object.assign(form, props.task)
    timeRange.value = [props.task.startTime, props.task.endTime]
  } else {
    // 新建默认：从现在开始 1 小时的采样窗口
    const now = new Date()
    const later = new Date(now.getTime() + 60 * 60 * 1000)
    timeRange.value = [fmtLocal(now), fmtLocal(later)]
  }
}

function fmtLocal(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function applyGaussTemplate() {
  form.customSql = GAUSS_DEAD_TUPLE_SQL
  parsedColumns.value = []
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
      samplingType: 'DEAD_TUPLE',
      startTime: timeRange.value![0],
      endTime: timeRange.value![1],
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
</style>
