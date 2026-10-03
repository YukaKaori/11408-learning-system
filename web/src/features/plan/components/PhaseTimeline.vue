<script setup lang="ts">
/**
 * The preparation year as four phases ending at the two exam days. Segments
 * are drawn at equal width — they are stages, not a scale — each with its
 * dates; today is a hairline inside the current one, placed by how far
 * through it the candidate is. The open-ended foundation is measured from a
 * year before the exam for that purpose only.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PlanPhaseDto } from '@/api/modules/plan'
import { parseIsoDate } from '@/utils/date'

const props = defineProps<{
  phases: PlanPhaseDto[]
  /** ISO, the caller's today. */
  today: string
  /** ISO, exam day one. */
  examDate: string
  estimated: boolean
}>()

const { t, d } = useI18n()

const DAY = 86_400_000

const todayTime = computed(() => parseIsoDate(props.today).getTime())
const examTime = computed(() => parseIsoDate(props.examDate).getTime())

function startOf(phase: PlanPhaseDto): number {
  return phase.start ? parseIsoDate(phase.start).getTime() : examTime.value - 365 * DAY
}

const segments = computed(() =>
  props.phases.map((phase) => {
    const start = startOf(phase)
    const end = parseIsoDate(phase.end).getTime()
    const state = todayTime.value > end ? 'past' : todayTime.value < start ? 'upcoming' : 'current'
    const progress =
      state === 'current'
        ? Math.min(1, Math.max(0, (todayTime.value - start) / (end - start + DAY)))
        : 0
    return {
      phase,
      state,
      progress,
      range: phase.start
        ? t('plan.timeline.range', {
            start: d(parseIsoDate(phase.start), 'short'),
            end: d(parseIsoDate(phase.end), 'short'),
          })
        : t('plan.timeline.until', { end: d(parseIsoDate(phase.end), 'short') }),
    }
  }),
)

const examLabel = computed(() => {
  const dayOne = parseIsoDate(props.examDate)
  const dayTwo = new Date(dayOne.getTime() + DAY)
  return t(props.estimated ? 'plan.timeline.examEstimated' : 'plan.timeline.exam', {
    start: d(dayOne, 'short'),
    end: d(dayTwo, 'short'),
  })
})
</script>

<template>
  <div class="timeline">
    <ol class="segments">
      <li
        v-for="segment in segments"
        :key="segment.phase.phase"
        class="segment"
        :class="segment.state"
      >
        <div class="bar">
          <span
            v-if="segment.state === 'current'"
            class="today"
            :style="{ left: `${segment.progress * 100}%` }"
            aria-hidden="true"
          >
            <span class="today-label">{{ t('plan.timeline.today') }}</span>
          </span>
        </div>
        <span class="name">
          {{ t(`exam.phase.${segment.phase.phase}`) }}
          <span v-if="segment.state === 'current'" class="sr-only">{{
            t('plan.timeline.current')
          }}</span>
        </span>
        <span class="range">{{ segment.range }}</span>
      </li>
    </ol>
    <div class="exam">
      <span class="exam-mark" aria-hidden="true"></span>
      <span class="name">{{ t('plan.timeline.examName') }}</span>
      <span class="range">{{ examLabel }}</span>
    </div>
  </div>
</template>

<style scoped>
.timeline {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
  align-items: start;
}

.segments {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}

.segment {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.bar {
  position: relative;
  height: 8px;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
}

.segment.past .bar {
  background-color: var(--color-border-strong);
}

.segment.current .bar {
  background-color: var(--color-primary-soft);
  box-shadow: inset 0 0 0 1px var(--color-primary);
}

.today {
  position: absolute;
  top: -6px;
  bottom: -6px;
  width: 2px;
  border-radius: var(--radius-full);
  background-color: var(--color-primary);
  transform: translateX(-1px);
}

.today-label {
  position: absolute;
  bottom: calc(100% + 2px);
  left: 50%;
  font-size: var(--text-xs);
  font-weight: 500;
  white-space: nowrap;
  color: var(--color-primary);
  transform: translateX(-50%);
}

.name {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text-secondary);
}

.segment.current .name {
  color: var(--color-text);
}

.range {
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

.exam {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 88px;
}

.exam-mark {
  width: 8px;
  height: 8px;
  margin-top: 0;
  border-radius: var(--radius-full);
  background-color: var(--color-text);
}

@media (max-width: 640px) {
  .timeline {
    grid-template-columns: minmax(0, 1fr);
  }

  .segments {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: var(--space-5);
  }
}
</style>
