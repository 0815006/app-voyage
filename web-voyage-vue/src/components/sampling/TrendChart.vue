<template>
  <div class="trend-chart">
    <div v-if="isEmpty" class="chart-empty">
      <el-empty description="暂无采样数据，请启动任务并稍候刷新" :image-size="72" />
    </div>
    <template v-else>
      <!-- 图例 -->
      <div class="chart-legend">
        <span v-for="s in series" :key="s.label" class="legend-item">
          <span class="legend-dot" :style="{ background: s.color }"></span>
          {{ s.label }}
        </span>
      </div>

      <svg
        :viewBox="`0 0 ${W} ${H}`"
        class="chart-svg"
        preserveAspectRatio="none"
        @mousemove="onMove"
        @mouseleave="hoverIndex = null"
      >
        <!-- 网格线与 Y 轴刻度 -->
        <g v-for="(t, i) in yTicks" :key="'y' + i">
          <line :x1="PAD_L" :y1="t.y" :x2="W - PAD_R" :y2="t.y" class="grid-line" />
          <text :x="PAD_L - 6" :y="t.y + 4" class="tick-text tick-y">{{ t.label }}</text>
        </g>

        <!-- X 轴时间刻度（约 6 个） -->
        <text
          v-for="(x, i) in xTicks"
          :key="'x' + i"
          :x="x.x"
          :y="H - 8"
          class="tick-text tick-x"
        >{{ x.label }}</text>

        <!-- 折线 -->
        <polyline
          v-for="s in series"
          :key="'p' + s.label"
          :points="s.points.map((p, i) => `${px(i)},${py(p)}`).join(' ')"
          fill="none"
          :stroke="s.color"
          stroke-width="1.8"
          stroke-linejoin="round"
          :stroke-dasharray="s.dash || undefined"
        />

        <!-- 数据点（点数少时显示） -->
        <template v-if="maxPointCount <= 80">
          <g v-for="(s, si) in series" :key="'cg' + si">
            <circle
              v-for="(p, i) in s.points"
              :key="'cp' + si + '-' + i"
              :cx="px(i)"
              :cy="py(p)"
              r="2.4"
              :fill="s.color"
            />
          </g>
        </template>

        <!-- 悬停参考线 -->
        <line
          v-if="hoverIndex !== null"
          :x1="px(hoverIndex)"
          :y1="PAD_T"
          :x2="px(hoverIndex)"
          :y2="H - PAD_B"
          class="hover-line"
        />
      </svg>

      <!-- 悬停信息条 -->
      <div v-if="hoverIndex !== null" class="hover-tip">
        <span class="hover-time">{{ labels[hoverIndex] }}</span>
        <span v-for="s in series" :key="'t' + s.label" class="hover-item">
          <span class="legend-dot" :style="{ background: s.color }"></span>
          {{ s.label }}: <b>{{ formatNum(s.points[hoverIndex]) }}</b>
        </span>
      </div>
      <div v-else class="hover-tip hover-hint">悬停曲线查看各时间点数值</div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

/** 单条折线：标签 + 数值序列（与共享时间轴 labels 按索引对齐） */
export interface TrendSeries {
  label: string
  color: string
  points: number[]
  /** 虚线样式（如 '6 4'），空=实线；用于同一表不同展示列的区分 */
  dash?: string
}

const props = defineProps<{
  /** 共享时间轴标签（如 14:23:02） */
  labels: string[]
  /** 多条折线（死元组数量等指标） */
  series: TrendSeries[]
}>()

const W = 900
const H = 320
const PAD_L = 64
const PAD_R = 16
const PAD_T = 14
const PAD_B = 30

const hoverIndex = ref<number | null>(null)

const isEmpty = computed(
  () => props.labels.length === 0 || props.series.every(s => s.points.length === 0)
)

const maxPointCount = computed(() =>
  Math.max(0, ...props.series.map(s => s.points.length))
)

const bounds = computed(() => {
  const all = props.series.flatMap(s => s.points)
  if (all.length === 0) return { min: 0, max: 1 }
  let min = Math.min(...all)
  let max = Math.max(...all)
  if (min === max) {
    // 平坦曲线：撑开上下界避免除零且便于阅读
    min = Math.max(0, min - 1)
    max = max + 1
  } else {
    const pad = (max - min) * 0.08
    min = Math.max(0, min - pad)
    max = max + pad
  }
  return { min, max }
})

const yTicks = computed(() => {
  const ticks: { y: number; label: string }[] = []
  const count = 4
  for (let i = 0; i <= count; i++) {
    const ratio = i / count
    const y = PAD_T + (H - PAD_T - PAD_B) * (1 - ratio)
    const v = bounds.value.min + (bounds.value.max - bounds.value.min) * ratio
    ticks.push({ y, label: compactNum(v) })
  }
  return ticks
})

const xTicks = computed(() => {
  const n = props.labels.length
  if (n === 0) return []
  const count = Math.min(6, n)
  const ticks: { x: number; label: string }[] = []
  for (let i = 0; i < count; i++) {
    const idx = Math.round((i * (n - 1)) / Math.max(1, count - 1))
    ticks.push({ x: px(idx), label: props.labels[idx] ?? '' })
  }
  return ticks
})

function px(pointIndex: number): number {
  const n = Math.max(1, maxPointCount.value - 1)
  return PAD_L + ((W - PAD_L - PAD_R) * pointIndex) / n
}

function py(value: number): number {
  const { min, max } = bounds.value
  const ratio = (value - min) / (max - min)
  return PAD_T + (H - PAD_T - PAD_B) * (1 - ratio)
}

function onMove(e: MouseEvent) {
  const svg = e.currentTarget as SVGSVGElement
  const rect = svg.getBoundingClientRect()
  if (rect.width <= 0 || props.labels.length === 0) return
  const xIn = ((e.clientX - rect.left) / rect.width) * W
  const n = Math.max(1, maxPointCount.value - 1)
  const idx = Math.round(((xIn - PAD_L) / (W - PAD_L - PAD_R)) * n)
  hoverIndex.value = Math.min(Math.max(0, idx), props.labels.length - 1)
}

function compactNum(v: number): string {
  if (Math.abs(v) >= 1_000_000) return (v / 1_000_000).toFixed(1) + 'M'
  if (Math.abs(v) >= 1_000) return (v / 1_000).toFixed(1) + 'k'
  return String(Math.round(v))
}

function formatNum(v: number | undefined): string {
  return v === undefined ? '-' : v.toLocaleString()
}
</script>

<style scoped>
.trend-chart {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.chart-empty {
  padding: 24px 0;
}

.chart-svg {
  width: 100%;
  height: 300px;
  display: block;
}

.grid-line {
  stroke: #ebeef5;
  stroke-width: 1;
}

.hover-line {
  stroke: #c0c4cc;
  stroke-width: 1;
  stroke-dasharray: 4 3;
}

.tick-text {
  fill: #909399;
  font-size: 11px;
}

.tick-y {
  text-anchor: end;
}

.tick-x {
  text-anchor: middle;
}

.chart-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #606266;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.hover-tip {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12px;
  color: #303133;
  min-height: 20px;
}

.hover-hint {
  color: #a8abb2;
}

.hover-time {
  color: #909399;
}

.hover-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
