<script setup lang="ts">
/**
 * One paper's whole-paper scores, oldest to newest, against the target.
 *
 * One series, so no legend: the section title names it. Marks follow the
 * product's chart specs — a 2px line in the paper's accent, 8px markers with
 * a 2px surface ring, hairline solid gridlines — and only the latest score is
 * labelled; every other value is in the tooltip and in the records list
 * below, which is this chart's table view. The target is the one dashed line:
 * it is a threshold, which is exactly what dashing says.
 *
 * Sittings are events, not a time series sampled at intervals, so they are
 * spaced one step apart; the dates are in the axis ends and the tooltip.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SittingPointDto } from '@/api/modules/sitting'
import { parseIsoDate } from '@/utils/date'
import { formatScore, sittingTitle } from '../sittingFormat'

const props = defineProps<{
  points: SittingPointDto[]
  fullScore: number
  target: number | null
  /** The paper's accent as a CSS value (a token). */
  accent: string
}>()

const { t, d } = useI18n()

const HEIGHT = 168
const PAD = { top: 16, right: 56, bottom: 28, left: 36 }

const root = ref<HTMLElement | null>(null)
const width = ref(560)
let observer: ResizeObserver | null = null

onMounted(() => {
  if (!root.value || typeof ResizeObserver === 'undefined') return
  observer = new ResizeObserver(([entry]) => {
    if (entry) width.value = Math.max(240, Math.round(entry.contentRect.width))
  })
  observer.observe(root.value)
})

onBeforeUnmount(() => observer?.disconnect())

/** A clean domain around the data and the target, inside [0, full]. */
const domain = computed(() => {
  const values = props.points.map((point) => (point.score / point.fullScore) * props.fullScore)
  if (props.target !== null) values.push(props.target)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const step = props.fullScore >= 150 ? 10 : 5
  const lo = Math.max(0, Math.floor((min - step) / step) * step)
  const hi = Math.min(props.fullScore, Math.ceil((max + step) / step) * step)
  return { lo, hi: hi > lo ? hi : lo + step, step }
})

const ticks = computed(() => {
  const { lo, hi } = domain.value
  const span = hi - lo
  const raw = span / 3
  const nice = [5, 10, 20, 25, 50].find((candidate) => candidate >= raw) ?? 50
  const first = Math.ceil(lo / nice) * nice
  const list: number[] = []
  for (let value = first; value <= hi; value += nice) list.push(value)
  return list
})

const plotWidth = computed(() => width.value - PAD.left - PAD.right)
const plotHeight = HEIGHT - PAD.top - PAD.bottom

function x(index: number): number {
  const n = props.points.length
  return PAD.left + (n <= 1 ? plotWidth.value / 2 : (index / (n - 1)) * plotWidth.value)
}

function y(value: number): number {
  const { lo, hi } = domain.value
  return PAD.top + (1 - (value - lo) / (hi - lo)) * plotHeight
}

const marks = computed(() =>
  props.points.map((point, index) => {
    const value = (point.score / point.fullScore) * props.fullScore
    return {
      point,
      value,
      cx: x(index),
      cy: y(value),
      label: `${sittingTitle(t, point)} · ${d(parseIsoDate(point.satOn), 'short')} · ${formatScore(point.score)}`,
    }
  }),
)

const linePath = computed(() =>
  marks.value
    .map((mark, index) => `${index === 0 ? 'M' : 'L'}${mark.cx.toFixed(1)},${mark.cy.toFixed(1)}`)
    .join(' '),
)

const last = computed(() => marks.value[marks.value.length - 1])

/** The hovered or focused mark — one tooltip at a time. */
const active = ref<number | null>(null)
const activeMark = computed(() => (active.value === null ? null : marks.value[active.value]))
</script>

<template>
  <div ref="root" class="score-trend">
    <svg
      class="chart"
      :viewBox="`0 0 ${width} ${HEIGHT}`"
      :width="width"
      :height="HEIGHT"
      role="img"
      :aria-label="t('sittings.trend.ariaLabel', { n: points.length })"
    >
      <!-- Gridlines and ticks: hairline, recessive, solid. -->
      <g class="grid" aria-hidden="true">
        <g v-for="tick in ticks" :key="tick">
          <line :x1="PAD.left" :x2="width - PAD.right" :y1="y(tick)" :y2="y(tick)" />
          <text
            :x="PAD.left - 8"
            :y="y(tick)"
            class="tick"
            text-anchor="end"
            dominant-baseline="middle"
          >
            {{ tick }}
          </text>
        </g>
      </g>

      <!-- The target: the one dashed line, because it is a threshold. -->
      <g v-if="target !== null" class="target" aria-hidden="true">
        <line :x1="PAD.left" :x2="width - PAD.right" :y1="y(target)" :y2="y(target)" />
        <text
          :x="width - PAD.right + 6"
          :y="y(target)"
          class="target-label"
          dominant-baseline="middle"
        >
          {{ t('sittings.trend.target', { n: target }) }}
        </text>
      </g>

      <path :d="linePath" class="line" :style="{ stroke: accent }" aria-hidden="true" />

      <g v-for="(mark, index) in marks" :key="mark.point.id">
        <circle
          :cx="mark.cx"
          :cy="mark.cy"
          r="4"
          class="marker"
          :style="{ fill: accent }"
          aria-hidden="true"
        />
        <!-- The hit target is bigger than the mark: 24px, focusable, labelled. -->
        <circle
          :cx="mark.cx"
          :cy="mark.cy"
          r="12"
          class="hit"
          tabindex="0"
          role="img"
          :aria-label="mark.label"
          @pointerenter="active = index"
          @pointerleave="active = null"
          @focus="active = index"
          @blur="active = null"
        />
      </g>

      <!-- Label selectively: the latest score only. -->
      <text
        v-if="last && (target === null || Math.abs(last.cy - y(target)) > 14)"
        :x="last.cx + 10"
        :y="last.cy"
        class="end-label"
        dominant-baseline="middle"
      >
        {{ formatScore(last.point.score) }}
      </text>

      <text :x="PAD.left" :y="HEIGHT - 6" class="axis-date" aria-hidden="true">
        {{ points[0] ? d(parseIsoDate(points[0].satOn), 'short') : '' }}
      </text>
      <text
        :x="width - PAD.right"
        :y="HEIGHT - 6"
        class="axis-date"
        text-anchor="end"
        aria-hidden="true"
      >
        {{ last ? d(parseIsoDate(last.point.satOn), 'short') : '' }}
      </text>
    </svg>

    <div
      v-if="activeMark"
      class="tooltip"
      role="presentation"
      :style="{
        left: `${Math.min(Math.max(activeMark.cx, 80), width - 80)}px`,
        top: `${activeMark.cy}px`,
      }"
    >
      <strong class="tooltip-value">
        {{ formatScore(activeMark.point.score) }}
        <span class="tooltip-of">/ {{ formatScore(activeMark.point.fullScore) }}</span>
      </strong>
      <span class="tooltip-line">
        <span class="tooltip-key" :style="{ backgroundColor: accent }" aria-hidden="true"></span>
        {{ sittingTitle(t, activeMark.point) }}
      </span>
      <span class="tooltip-date">{{ d(parseIsoDate(activeMark.point.satOn), 'short') }}</span>
    </div>
  </div>
</template>

<style scoped>
.score-trend {
  position: relative;
  width: 100%;
}

.chart {
  display: block;
  overflow: visible;
}

.grid line {
  stroke: var(--color-border);
  stroke-width: 1;
}

.tick,
.axis-date {
  fill: var(--color-text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.target line {
  stroke: var(--color-text-tertiary);
  stroke-width: 1;
  stroke-dasharray: 4 4;
}

.target-label {
  fill: var(--color-text-secondary);
  font-size: 11px;
}

.line {
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.marker {
  stroke: var(--color-surface);
  stroke-width: 2;
}

.hit {
  fill: transparent;
  cursor: default;
  outline: none;
}

.hit:focus-visible {
  stroke: var(--color-focus-ring);
  stroke-width: 3;
}

.end-label {
  fill: var(--color-text);
  font-size: 12px;
  font-weight: 600;
}

.tooltip {
  position: absolute;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 140px;
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  box-shadow: var(--shadow-md);
  pointer-events: none;
  transform: translate(-50%, calc(-100% - 12px));
}

.tooltip-value {
  font-size: var(--text-base);
  color: var(--color-text);
}

.tooltip-of {
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-text-tertiary);
}

.tooltip-line {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.tooltip-key {
  width: 12px;
  height: 2px;
  border-radius: var(--radius-full);
}

.tooltip-date {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}
</style>
