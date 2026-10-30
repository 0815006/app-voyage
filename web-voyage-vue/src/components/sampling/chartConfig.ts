import type { SamplingColumn } from '@/api/sampling'

/**
 * 采样绘图配置契约（两页共用：死元组=固化预设，通用=用户自选）。
 * <p>
 * 存于 sampling_task.chart_config（JSON 文本），后端按黑盒原样存取、不解析语义。
 * 只固化「怎么画」的结构性设定：筛选列（绘制时的筛选/分组条件，图内每条线=一个筛选项）
 * 与展示列（每个展示列单独一张图）。具体筛选项（数据值）随采样数据变化，
 * 属查看态临时筛选，不落库。
 * </p>
 */
export interface SamplingChartConfig {
  /** 筛选列列名清单（可为空 = 全部数据合一条线） */
  filterCols: string[]
  /** 展示列列名清单（与筛选列互斥） */
  displayCols: string[]
}

/** 维度列候选：非数值且非时间的列 */
export function dimensionCandidates(columns: SamplingColumn[]): string[] {
  return columns.filter(c => !c.numeric && !c.temporal).map(c => c.column)
}

/** 数值列候选：可作展示列（纵轴）的列 */
export function numericCandidates(columns: SamplingColumn[]): string[] {
  return columns.filter(c => c.numeric).map(c => c.column)
}

/** 默认推断：首个维度列作筛选列、首个数值列作展示列（无配置/解析失败时的回退） */
export function defaultChartConfig(columns: SamplingColumn[]): SamplingChartConfig {
  return {
    filterCols: dimensionCandidates(columns).slice(0, 1),
    displayCols: numericCandidates(columns).slice(0, 1),
  }
}

/** 解析任务上的绘图配置原文；非法 JSON 或结构不符返回 null（由调用方回退默认推断） */
export function parseChartConfig(raw?: string | null): SamplingChartConfig | null {
  if (!raw || !raw.trim()) return null
  try {
    const obj = JSON.parse(raw) as unknown
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return null
    const source = obj as Record<string, unknown>
    const toStrList = (v: unknown): string[] =>
      Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string' && x !== '') : []
    return { filterCols: toStrList(source.filterCols), displayCols: toStrList(source.displayCols) }
  } catch {
    return null
  }
}

/** 序列化为存库原文；两组皆空时返回 null（表示未配置，读取端走默认推断） */
export function serializeChartConfig(cfg: SamplingChartConfig): string | null {
  if (cfg.filterCols.length === 0 && cfg.displayCols.length === 0) return null
  return JSON.stringify({
    filterCols: cfg.filterCols,
    displayCols: cfg.displayCols,
  })
}

/**
 * 加载对齐：任务存的列名引用必须与当前 SQL 解析出的列求交集。
 * SQL 变更后失效列自动剔除；展示列全失效时回退首个数值列（否则打开就是空图）。
 * 筛选列为合法空集（用户选了「无筛选列=合计」）时不强加默认。
 */
export function alignChartConfig(
  raw: string | null | undefined,
  columns: SamplingColumn[]
): SamplingChartConfig {
  const parsed = parseChartConfig(raw)
  if (!parsed || columns.length === 0) return defaultChartConfig(columns)

  const valid = new Set(columns.map(c => c.column))
  const filterCols = parsed.filterCols.filter(c => valid.has(c))
  // 原本配了筛选列却全部失效（SQL 改了列名）→ 回退默认维度列
  if (parsed.filterCols.length > 0 && filterCols.length === 0) {
    filterCols.push(...defaultChartConfig(columns).filterCols)
  }

  const displayCols = parsed.displayCols.filter(c => valid.has(c) && !filterCols.includes(c))
  // 展示列是画图必需项：缺失或全部失效时回退首个数值列
  if (displayCols.length === 0) {
    displayCols.push(...numericCandidates(columns).slice(0, 1).filter(c => !filterCols.includes(c)))
  }
  return { filterCols, displayCols }
}
