<template>
  <div class="bar-chart">
    <div v-if="isEmpty" class="chart-empty">
      <el-empty description="暂无符合条件的采样数据" :image-size="72" />
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

        <!-- 分组柱：每个横轴标签下按序列并排 -->
        <rect
          v-for="(b, i) in bars"
          :key="'b' + i"
          :x="b.x"
          :y="b.y"
          :width="b.w"
          :height="b.h"
          :fill="b.color"
          :opacity="hoverIndex === null || hoverIndex === b.group ? 1 : 0.45"
          rx="1"
        />

        <!-- X 轴标签（点多时抽稀显示） -->
        <text
          v-for="(x, i) in xLabels"
          :key="'x' + i"
          :x="x.x"
          :y="H - 8"
          class="tick-text tick-x"
        >{{ x.label }}</text>
      </svg>

      <!-- 悬停信息条 -->
      <div v-if="hoverIndex !== null" class="hover-tip">
        <span class="hover-time">{{ labels[hoverIndex] }}</span>
        <span v-for="s in series" :key="'t' + s.label" class="hover-item">
          <span class="legend-dot" :style="{ background: s.color }"></span>
          {{ s.label }}: <b>{{ formatNum(s.values[hoverIndex]) }}</b>
        </span>
      </div>
      <div v-else class="hover-tip hover-hint">悬停柱子查看各序列数值</div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

/** 单条序列：标签 + 颜色 + 与横轴 labels 按索引对齐的数值 */
export interface BarSeries {
  label: string
  color: string
  values: number[]
}

const props = defineProps<{
  /** 横轴标签（时间桶 / 维度值） */
  labels: string[]
  /** 多条序列（维度 × 展示列） */
  series: BarSeries[]
}>()

const W = 900
const H = 320
const PAD_L = 64
const PAD_R = 16
const PAD_T = 14
const PAD_B = 30

const hoverIndex = ref<number | null>(null)

const isEmpty = computed(() => props.labels.length === 0 || props.series.length === 0)

const bounds = computed(() => {
  const all = props.series.flatMap(s => s.values)
  const max = Math.max(...all, 0)
  const min = Math.min(...all, 0)
  const span = max === min ? Math.max(1, Math.abs(max)) : max - min
  return { min, max: max === min ? max + 1 : max + span * 0.08 }
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

/** 组内并排柱：组宽扣除间隙后按序列数均分 */
const bars = computed(() => {
  const n = props.labels.length
  if (n === 0) return []
  const m = props.series.length
  const groupW = (W - PAD_L - PAD_R) / n
  const innerW = groupW * 0.72
  const barW = innerW / m
  const list: { x: number; y: number; w: number; h: number; color: string; group: number }[] = []
  props.series.forEach((s, si) => {
    s.values.forEach((v, gi) => {
      const x = PAD_L + gi * groupW + (groupW - innerW) / 2 + si * barW
      const y0 = py(Math.max(v, 0))
      const y1 = py(Math.min(v, 0))
      list.push({ x: x + 1, y: y0, w: Math.max(1, barW - 2), h: Math.max(1, y1 - y0), color: s.color, group: gi })
    })
  })
  return list
})

/** X 轴标签抽稀：最多约 8 个，避免拥挤 */
const xLabels = computed(() => {
  const n = props.labels.length
  if (n === 0) return []
  const step = Math.max(1, Math.ceil(n / 8))
  const out: { x: number; label: string }[] = []
  const groupW = (W - PAD_L - PAD_R) / n
  for (let i = 0; i < n; i += step) {
    out.push({ x: PAD_L + (i + 0.5) * groupW, label: props.labels[i] })
  }
  return out
})

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
  const groupW = (W - PAD_L - PAD_R) / props.labels.length
  const idx = Math.floor((xIn - PAD_L) / groupW)
  hoverIndex.value = Math.min(Math.max(0, idx), props.labels.length - 1)
}

function compactNum(v: number): string {
  if (Math.abs(v) >= 1_000_000) return (v / 1_000_000).toFixed(1) + 'M'
  if (Math.abs(v) >= 1_000) return (v / 1_000).toFixed(1) + 'k'
  return String(Math.round(v * 100) / 100)
}

function formatNum(v: number | undefined): string {
  return v === undefined ? '-' : v.toLocaleString()
}
</script>

<style scoped>
.bar-chart {
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
