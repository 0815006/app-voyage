<template>
  <div class="pie-chart">
    <div v-if="isEmpty" class="chart-empty">
      <el-empty description="暂无符合条件的采样数据" :image-size="72" />
    </div>
    <div v-else class="pie-body">
      <svg :viewBox="`0 0 ${SIZE} ${SIZE}`" class="pie-svg">
        <path
          v-for="(a, i) in arcs"
          :key="'a' + i"
          :d="a.d"
          :fill="a.color"
          :opacity="hoverIndex === null || hoverIndex === i ? 1 : 0.5"
          stroke="#fff"
          stroke-width="1"
          @mouseenter="hoverIndex = i"
          @mouseleave="hoverIndex = null"
        />
        <!-- 中心汇总 -->
        <circle :cx="CX" :cy="CY" :r="INNER" fill="#fff" />
        <text :x="CX" :y="CY - 4" class="center-total">{{ compactNum(total) }}</text>
        <text :x="CX" :y="CY + 14" class="center-label">合计</text>
      </svg>

      <div class="pie-legend">
        <div
          v-for="(a, i) in arcs"
          :key="'l' + i"
          class="legend-row"
          :class="{ active: hoverIndex === i }"
          @mouseenter="hoverIndex = i"
          @mouseleave="hoverIndex = null"
        >
          <span class="legend-dot" :style="{ background: a.color }"></span>
          <span class="legend-name">{{ a.label }}</span>
          <span class="legend-value">{{ formatNum(a.value) }}</span>
          <span class="legend-pct">{{ a.pct }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

/** 饼图切片：标签 + 数值 */
export interface PieSlice {
  label: string
  value: number
}

const props = defineProps<{
  slices: PieSlice[]
  /** 调色板（不传用内置） */
  palette?: string[]
}>()

const SIZE = 260
const CX = SIZE / 2
const CY = SIZE / 2
const R = SIZE / 2 - 6
const INNER = R * 0.55

const DEFAULT_PALETTE = [
  '#409eff', '#f56c6c', '#67c23a', '#e6a23c', '#909399',
  '#9254de', '#13c2c2', '#fa541c', '#2f54eb', '#eb2f96',
]

const hoverIndex = ref<number | null>(null)

/** 仅取正值参与占比（负值/零无面积意义） */
const positive = computed(() =>
  props.slices.filter(s => Number.isFinite(s.value) && s.value > 0)
)

const isEmpty = computed(() => positive.value.length === 0)

const total = computed(() => positive.value.reduce((a, s) => a + s.value, 0))

const arcs = computed(() => {
  const pal = props.palette && props.palette.length ? props.palette : DEFAULT_PALETTE
  const sum = total.value
  if (sum <= 0) return []
  let angle = -Math.PI / 2
  return positive.value.map((s, i) => {
    const ratio = s.value / sum
    const a0 = angle
    const a1 = angle + ratio * Math.PI * 2
    angle = a1
    return {
      label: s.label,
      value: s.value,
      color: pal[i % pal.length],
      pct: (ratio * 100).toFixed(1) + '%',
      d: donutPath(a0, a1),
    }
  })
})

/** 单块环形扇区路径（外弧 + 内弧闭合） */
function donutPath(a0: number, a1: number): string {
  // 单一整圆：用两段半圆拼接避免弧线退化
  if (a1 - a0 >= Math.PI * 2 - 1e-6) {
    return `M ${CX} ${CY - R} A ${R} ${R} 0 1 1 ${CX - 0.01} ${CY - R} Z` +
      ` M ${CX} ${CY - INNER} A ${INNER} ${INNER} 0 1 0 ${CX - 0.01} ${CY - INNER} Z`
  }
  const large = a1 - a0 > Math.PI ? 1 : 0
  const [x0, y0] = point(R, a0)
  const [x1, y1] = point(R, a1)
  const [ix1, iy1] = point(INNER, a1)
  const [ix0, iy0] = point(INNER, a0)
  return [
    `M ${x0} ${y0}`,
    `A ${R} ${R} 0 ${large} 1 ${x1} ${y1}`,
    `L ${ix1} ${iy1}`,
    `A ${INNER} ${INNER} 0 ${large} 0 ${ix0} ${iy0}`,
    'Z',
  ].join(' ')
}

function point(radius: number, angle: number): [number, number] {
  return [CX + radius * Math.cos(angle), CY + radius * Math.sin(angle)]
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
.pie-chart {
  width: 100%;
}

.chart-empty {
  padding: 24px 0;
}

.pie-body {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.pie-svg {
  width: 260px;
  height: 260px;
  flex-shrink: 0;
}

.center-total {
  fill: #303133;
  font-size: 20px;
  font-weight: 700;
  text-anchor: middle;
}

.center-label {
  fill: #909399;
  font-size: 12px;
  text-anchor: middle;
}

.pie-legend {
  flex: 1;
  min-width: 240px;
  max-height: 260px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #606266;
  padding: 3px 6px;
  border-radius: 4px;
  cursor: default;
}

.legend-row.active {
  background: #f5f7fa;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  flex-shrink: 0;
}

.legend-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.legend-value {
  font-weight: 600;
  color: #303133;
}

.legend-pct {
  width: 56px;
  text-align: right;
  color: #909399;
}
</style>
