<script setup lang="ts">
/**
 * The report of a completed paper — what it yielded, where the points went,
 * where the time went, and what to do about it.
 *
 * Every figure is the server's (`report`, `sections`, marks); this component
 * only lays them out. Meters are single-measure bars in the paper's own
 * accent with the value written beside them; the question map uses the
 * reserved status tokens plus a glyph, so a result is never told by colour
 * alone. The estimate is said for what it is — a recency-weighted average of
 * whole papers — and a section paper says plainly that it does not count
 * towards it.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppIcon, StatTile, type IconName } from '@/components'
import type { PaperItemDto, PaperSessionDto } from '@/api/modules/paper'
import { useSyllabusStore } from '@/stores/syllabus'
import { formatClock, formatPoints, toMinutes } from '../paperFormat'

const props = defineProps<{
  paper: PaperSessionDto
  /** A drill is being launched for this 考点. */
  launching: string | null
}>()

defineEmits<{ review: [item: PaperItemDto]; drill: [nodeCode: string] }>()

const { t } = useI18n()
const syllabusStore = useSyllabusStore()

const report = computed(() => props.paper.report!)
const score = computed(() => props.paper.score ?? 0)

const sectionNames = computed<Record<string, string>>(() =>
  Object.fromEntries(
    (syllabusStore.subjectDoc(props.paper.subject)?.sections ?? []).map((section) => [section.code, section.name]),
  ),
)

/** The paper's standing after this sitting, in one honest sentence. */
const standing = computed(() => {
  const s = report.value.standing
  if (!props.paper.whole) return t('papers.report.standing.section')
  if (!s.counted) return t('papers.report.standing.notCounted')
  if (s.estimate === null) return t('papers.report.standing.none')
  const base = t('papers.report.standing.estimate', { score: formatPoints(s.estimate), n: s.sittings })
  if (s.target === null) return base
  const gap = s.target - s.estimate
  return `${base} · ${
    gap > 0
      ? t('papers.report.standing.below', { target: s.target, n: formatPoints(gap) })
      : t('papers.report.standing.above', { target: s.target, n: formatPoints(-gap) })
  }`
})

/** Time on the clock: what was used of what was allowed. */
const used = computed(() => formatClock(report.value.secondsUsed * 1000))
const allowed = computed(() => formatClock(props.paper.timeLimitMinutes * 60_000))

/** The longest of spent and suggested per section sets that section's time scale. */
function timeScale(seconds: number, minutes: number): number {
  return Math.max(seconds, minutes * 60, 1)
}

type Result = 'correct' | 'partial' | 'wrong' | 'blank'

function resultOf(item: PaperItemDto): Result {
  if (!item.mark || item.mark.verdict === 'blank') return 'blank'
  return item.mark.result ?? 'blank'
}

const GLYPH: Record<Result, IconName> = {
  correct: 'check',
  partial: 'circle-dot',
  wrong: 'close',
  blank: 'minus',
}

const weakest = computed(() => report.value.points.slice(0, 8))

function pointName(code: string): string {
  return syllabusStore.node(code)?.name ?? code
}
</script>

<template>
  <div class="report">
    <section class="hero" :aria-label="t('papers.report.scoreLabel')">
      <div class="hero-score">
        <span class="hero-value">{{ formatPoints(score) }}</span>
        <span class="hero-full">/ {{ formatPoints(paper.fullScore) }}</span>
      </div>
      <div class="hero-text">
        <p class="standing">{{ standing }}</p>
        <p class="time">
          <AppIcon name="timer" size="sm" aria-hidden="true" />
          {{ t('papers.report.timeUsed', { used, allowed }) }}
        </p>
      </div>
      <ul class="meters">
        <li v-for="section in report.sections" :key="section.code" class="meter">
          <span class="meter-name">{{ sectionNames[section.code] ?? section.code }}</span>
          <span class="meter-track" aria-hidden="true">
            <span class="meter-fill" :style="{ width: `${section.full > 0 ? (section.score / section.full) * 100 : 0}%` }"></span>
          </span>
          <span class="meter-value">{{ formatPoints(section.score) }} / {{ formatPoints(section.full) }}</span>
        </li>
      </ul>
    </section>

    <div class="tiles">
      <StatTile :label="t('papers.report.counts.correct')" :value="String(report.correct)" icon="check-circle" />
      <StatTile :label="t('papers.report.counts.partial')" :value="String(report.partial)" icon="circle-dot" />
      <StatTile :label="t('papers.report.counts.wrong')" :value="String(report.wrong)" icon="alert-circle" />
      <StatTile :label="t('papers.report.counts.blank')" :value="String(report.blank)" icon="minus" />
      <RouterLink :to="{ name: 'mistakes' }" class="tile-link">
        <StatTile :label="t('papers.report.counts.filed')" :value="String(report.mistakesFiled)" icon="book-x" />
      </RouterLink>
    </div>

    <div class="grid">
      <article class="panel">
        <h2 class="panel-title">{{ t('papers.report.map.title') }}</h2>
        <p class="panel-hint">{{ t('papers.report.map.hint') }}</p>
        <div v-for="section in paper.sections" :key="section.code" class="map-block">
          <h3 class="map-title">{{ sectionNames[section.code] ?? section.code }}</h3>
          <ol class="map">
            <li v-for="item in section.items" :key="item.question.id">
              <button
                type="button"
                class="map-cell"
                :class="`r-${resultOf(item)}`"
                :aria-label="
                  t('papers.report.map.cell', {
                    n: item.number,
                    result: t(`papers.report.result.${resultOf(item)}`),
                    points: formatPoints(item.mark?.points ?? 0),
                    worth: formatPoints(item.worth),
                  })
                "
                :title="`${formatPoints(item.mark?.points ?? 0)} / ${formatPoints(item.worth)}`"
                @click="$emit('review', item)"
              >
                <span class="map-number">{{ item.number }}</span>
                <AppIcon :name="GLYPH[resultOf(item)]" size="sm" class="map-glyph" aria-hidden="true" />
              </button>
            </li>
          </ol>
        </div>
        <p class="legend" aria-hidden="true">
          <span v-for="result in (['correct', 'partial', 'wrong', 'blank'] as const)" :key="result" class="legend-item">
            <span class="legend-cell" :class="`r-${result}`"><AppIcon :name="GLYPH[result]" size="sm" /></span>
            {{ t(`papers.report.result.${result}`) }}
          </span>
        </p>
      </article>

      <article class="panel">
        <h2 class="panel-title">{{ t('papers.report.timing.title') }}</h2>
        <ul class="times">
          <li v-for="section in report.sections" :key="section.code" class="time-row">
            <span class="time-name">{{ sectionNames[section.code] ?? section.code }}</span>
            <span class="time-track" aria-hidden="true">
              <span
                class="time-fill"
                :class="{ over: section.seconds > section.minutes * 60 }"
                :style="{ width: `${(section.seconds / timeScale(section.seconds, section.minutes)) * 100}%` }"
              ></span>
              <span
                class="time-mark"
                :style="{ left: `${((section.minutes * 60) / timeScale(section.seconds, section.minutes)) * 100}%` }"
              ></span>
            </span>
            <span class="time-value">
              {{ t('papers.report.timing.spent', { spent: toMinutes(section.seconds), suggested: section.minutes }) }}
            </span>
          </li>
        </ul>
        <p class="panel-hint">{{ t('papers.report.timing.hint') }}</p>
      </article>
    </div>

    <article class="panel">
      <div class="panel-head">
        <h2 class="panel-title">{{ t('papers.report.points.title') }}</h2>
        <span class="panel-hint">{{ t('papers.report.points.hint') }}</span>
      </div>
      <ul v-if="weakest.length > 0" class="points">
        <li v-for="point in weakest" :key="point.nodeCode" class="point">
          <span class="point-name">
            <span class="point-title">{{ pointName(point.nodeCode) }}</span>
            <span class="point-path">{{ syllabusStore.label(point.nodeCode) }}</span>
          </span>
          <span class="point-meter" aria-hidden="true">
            <span class="meter-fill" :style="{ width: `${point.worth > 0 ? (point.earned / point.worth) * 100 : 0}%` }"></span>
          </span>
          <span class="point-value">
            {{ t('papers.report.points.value', { earned: formatPoints(point.earned), worth: formatPoints(point.worth), correct: point.correct, attempted: point.attempted }) }}
          </span>
          <button
            type="button"
            class="drill"
            :disabled="launching !== null"
            @click="$emit('drill', point.nodeCode)"
          >
            <AppIcon :name="launching === point.nodeCode ? 'loader' : 'crosshair'" size="sm" :class="{ spin: launching === point.nodeCode }" aria-hidden="true" />
            {{ t('papers.report.points.drill') }}
          </button>
        </li>
      </ul>
      <p v-else class="panel-empty">{{ t('papers.report.points.empty') }}</p>
    </article>
  </div>
</template>

<style scoped>
.report {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-4) var(--space-8);
  align-items: center;
  padding: var(--space-6) var(--space-8);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.hero-score {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.hero-value {
  font-family: var(--font-display-family);
  font-size: var(--font-display-size);
  font-weight: var(--font-display-weight);
  line-height: 1;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.hero-full {
  font-size: var(--text-lg);
  color: var(--color-text-tertiary);
}

.hero-text {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.standing {
  margin: 0;
  font-weight: 500;
  line-height: 1.6;
}

.time {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.meters {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.meter,
.time-row {
  display: grid;
  grid-template-columns: 9em minmax(0, 1fr) 7em;
  align-items: center;
  gap: var(--space-3);
}

.meter-name,
.time-name {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.meter-track,
.point-meter {
  position: relative;
  height: 8px;
  border-radius: var(--radius-full);
  background-color: var(--color-surface-hover);
  overflow: hidden;
}

.meter-fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: var(--radius-full);
  background-color: var(--paper-accent, var(--color-primary));
}

.meter-value,
.time-value,
.point-value {
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
  text-align: right;
}

.tiles {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-3);
}

.tile-link {
  color: inherit;
  text-decoration: none;
  border-radius: var(--radius-card);
}

.tile-link:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
  gap: var(--space-4);
}

.panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.panel-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
}

.panel-hint,
.panel-empty {
  margin: 0;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--color-text-tertiary);
}

.map-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.map-title {
  margin: 0;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-secondary);
}

.map {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(42px, 1fr));
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}

.map-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  width: 100%;
  min-height: 44px;
  padding: 0;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

.map-cell:focus-visible,
.drill:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.r-correct {
  border-color: var(--color-success);
  background-color: var(--color-success-soft);
}

.r-partial {
  border-color: var(--color-warning);
  background-color: var(--color-warning-soft);
}

.r-wrong {
  border-color: var(--color-danger);
  background-color: var(--color-danger-soft);
}

.r-blank {
  border-style: dashed;
  color: var(--color-text-tertiary);
}

.r-correct .map-glyph,
.legend-cell.r-correct {
  color: var(--color-success);
}

.r-partial .map-glyph,
.legend-cell.r-partial {
  color: var(--color-warning);
}

.r-wrong .map-glyph,
.legend-cell.r-wrong {
  color: var(--color-danger);
}

.map-number {
  line-height: 1;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.legend-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-sm);
}

.times {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.time-row {
  grid-template-columns: 7em minmax(0, 1fr) auto;
}

.time-track {
  position: relative;
  height: 8px;
  border-radius: var(--radius-full);
  background-color: var(--color-surface-hover);
}

.time-fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: var(--radius-full);
  background-color: var(--paper-accent, var(--color-primary));
}

.time-fill.over {
  background-color: var(--color-warning);
}

/* The suggested time: a thin mark across the track, not a second bar. */
.time-mark {
  position: absolute;
  top: -4px;
  bottom: -4px;
  width: 2px;
  margin-left: -1px;
  border-radius: 1px;
  background-color: var(--color-text);
}

.points {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.point {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(80px, 1fr) auto auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: var(--border-width-sm) solid var(--color-border);
}

.point:last-child {
  border-bottom: none;
}

.point-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.point-title {
  font-size: var(--text-sm);
  font-weight: 500;
}

.point-path {
  overflow: hidden;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drill {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: 32px;
  padding: 0 var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-button);
  background-color: var(--color-surface);
  color: var(--color-primary);
  font: inherit;
  font-size: var(--text-xs);
  white-space: nowrap;
  cursor: pointer;
}

.drill:hover:not(:disabled) {
  background-color: var(--color-primary-soft);
}

.drill:disabled {
  opacity: 0.6;
  cursor: default;
}

.spin {
  animation: report-spin 0.8s linear infinite;
}

@keyframes report-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spin {
    animation: none;
  }
}

@media (max-width: 960px) {
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .tiles {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .hero {
    grid-template-columns: minmax(0, 1fr);
    padding: var(--space-5);
  }

  .meter {
    grid-template-columns: 6em minmax(0, 1fr) auto;
  }

  .tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .point {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .point-meter {
    display: none;
  }
}
</style>
