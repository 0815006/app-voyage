import request from '@/utils/request'

/**
 * 死元组监测采样 API（通用采样监测底座）。
 * 后端完全通用：采样明细以「时间戳 + 原始行 JSON」返回，
 * 表名筛选 / 指标计算 / 波动图解析全部在本模块前端完成。
 * 数据库连接复用「数据库性能分析」模块的 db_connection_config（本模块零密码概念）。
 */

/** 通用采样任务（sampling_type 区分预设：DEAD_TUPLE 固化 / DB_SAMPLER 用户自选） */
export interface SamplingTask {
  id?: string
  taskName: string
  connectionId: string
  samplingType: string
  customSql: string
  /** yyyy-MM-dd HH:mm:ss（后端实体 @JsonFormat 约定；任务 JSON 出入参统一此格式，非 ISO 带 T） */
  startTime: string
  /** yyyy-MM-dd HH:mm:ss */
  endTime: string
  /** 采样频率（秒/次） */
  frequency: number
  /** RUNNING / STOPPED / COMPLETED（后端驱动，只读） */
  status?: string
  /**
   * 绘图配置 JSON 文本（筛选列/展示列等「怎么画」的结构性设定）。
   * 后端按黑盒原样存取；具体筛选项（数据值）属查看态临时筛选，不落库。
   */
  chartConfig?: string
  /** 已采集明细行数（后端瞬态回传，只读）：>0 即锁定任务定义 */
  historyCount?: number
  createTime?: string
  updateTime?: string
}

/** 单行采样明细：samplingTime + row_data 原样 JSON 平铺 */
export interface SamplingHistoryRow {
  samplingTime: string
  row: Record<string, unknown>
}

/** 结果集列描述符（后端 JDBC 元数据权威解析，不执行查询） */
export interface SamplingColumn {
  /** 列标签（含别名） */
  column: string
  /** 类型名，如 BIGINT/VARCHAR/TIMESTAMP */
  label: string
  /** 是否数值列 → 波动图展示列（纵轴）候选 */
  numeric: boolean
  /** 是否时间列 */
  temporal: boolean
}

/** 任务列表 */
export function listTasks(): Promise<SamplingTask[]> {
  return request.get('/sampling/tasks')
}

/** 新增或更新任务（运行中或已有采样记录时禁止编辑） */
export function saveTask(task: SamplingTask): Promise<SamplingTask> {
  return request.post('/sampling/task', task)
}

/** 删除任务（运行中先停止，级联删除历史明细） */
export function deleteTask(id: string): Promise<void> {
  return request.delete(`/sampling/task/${id}`)
}

/** 启动任务 */
export function startTask(id: string): Promise<SamplingTask> {
  return request.post(`/sampling/task/${id}/start`)
}

/** 停止任务 */
export function stopTask(id: string): Promise<SamplingTask> {
  return request.post(`/sampling/task/${id}/stop`)
}

/**
 * 解析采样 SQL 的结果集列结构（供表范围/展示列候选，兼做语法校验）。
 * 未保存的任务也可预览（需已选连接）。
 */
export async function previewColumns(connectionId: string, customSql: string): Promise<SamplingColumn[]> {
  const data = await request.post('/sampling/preview-columns', { connectionId, customSql })
  // 空列清单时 axios 拦截器会回退返回整个 Result body，防 [0] 错误
  return Array.isArray(data) ? (data as SamplingColumn[]) : []
}

/**
 * 查询任务时间窗口内的采样明细。
 * 注意：这里的是 query 参数，由后端 @DateTimeFormat(ISO) 解析，与任务实体的
 * @JsonFormat 空格格式无关，必须传 ISO yyyy-MM-ddTHH:mm:ss（带 T）。
 * @param start ISO yyyy-MM-ddTHH:mm:ss（可选）
 * @param end   ISO yyyy-MM-ddTHH:mm:ss（可选）
 */
export function listHistory(id: string, start?: string, end?: string): Promise<SamplingHistoryRow[]> {
  return request.get(`/sampling/task/${id}/history`, { params: { start, end } })
}

/**
 * 清理该任务全部已采集记录（保留任务定义，不可恢复）。
 * 运行中需先停止；清理后状态归位 STOPPED，任务定义随之可编辑。
 * @returns 实际删除的行数
 */
export function clearHistory(id: string): Promise<number> {
  return request.delete(`/sampling/task/${id}/history`)
}
