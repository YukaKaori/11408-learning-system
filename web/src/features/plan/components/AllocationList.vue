<script setup lang="ts">
/**
 * How the study day divides among the four papers — and why. Each row is a
 * share of the day in the paper's accent with a tick at the phase's base
 * share, the time it comes to, and one sentence of reasoning built from the
 * same numbers the server used: the base, and the gap between the whole-paper
 * estimate and the target that tilted it. Nothing here is a black box; a
 * candidate who disagrees can see which number to change.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'
import type { ExamPhase } from '@/api/modules/exam'
import type { PlanPaperDto } from '@/api/modules/plan'
import { PAPER_ICON, paperAccentColor } from '@/features/syllabus/papers'
import { hoursOf } from '@/features/focus/clock'
import { formatScore } from '@/features/sittings/sittingFormat'

const props = defineProps<{
  papers: PlanPaperDto[]
  phase: ExamPhase
}>()

const { t } = useI18n()

/** Bars are scaled to the largest share so the comparison uses the width. */
const scale = computed(() => Math.max(...props.papers.map((paper) => paper.share), 0.01))

const TILT = 0.005

function reason(paper: PlanPaperDto): string {
  const base = Math.round(paper.baseShare * 100)
  const phase = t(`exam.phase.${props.phase}`)
  if (paper.estimate === null) return t('plan.daily.reason.noEvidence', { phase, base })
  if (paper.target === null) return t('plan.daily.reason.noTarget', { phase, base })
  const gapPoints = formatScore(Math.max(0, paper.target - paper.estimate.score))
  const estimate = formatScore(paper.estimate.score)
  if (paper.share > paper.baseShare + TILT) {
    return t('plan.daily.reason.up', { estimate, target: paper.target, gap: gapPoints, base })
  }
  if (paper.share < paper.baseShare - TILT) {
    return paper.target <= paper.estimate.score
      ? t('plan.daily.reason.atTarget', { estimate, target: paper.target, base })
      : t('plan.daily.reason.down', { estimate, gap: gapPoints, base })
  }
  return t('plan.daily.reason.even', { phase, base })
}
</script>

<template>
  <ul class="allocation">
    <li v-for="paper in papers" :key="paper.subject" class="row">
      <span class="paper">
        <AppIcon
          :name="PAPER_ICON[paper.subject]"
          size="sm"
          :style="{ color: paperAccentColor(paper.subject) }"
        />
        {{ t(`exam.papers.${paper.subject}`) }}
      </span>
      <div
        class="track"
        role="meter"
        :aria-label="t(`exam.papers.${paper.subject}`)"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuenow="Math.round(paper.share * 100)"
        :aria-valuetext="`${Math.round(paper.share * 100)}%`"
      >
        <span
          class="fill"
          :style="{
            width: `${(paper.share / scale) * 100}%`,
            backgroundColor: paperAccentColor(paper.subject),
          }"
        ></span>
        <span
          class="base"
          :style="{ left: `${(paper.baseShare / scale) * 100}%` }"
          :title="t('plan.daily.baseMark', { n: Math.round(paper.baseShare * 100) })"
          aria-hidden="true"
        ></span>
      </div>
      <span class="time">
        <strong>{{ t('plan.daily.perDay', { n: hoursOf(paper.dailyMinutes) }) }}</strong>
        <span class="share">{{ Math.round(paper.share * 100) }}%</span>
      </span>
      <p class="reason">{{ reason(paper) }}</p>
    </li>
  </ul>
</template>

<style scoped>
.allocation {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 132px;
  grid-template-areas:
    'paper track time'
    '. reason reason';
  align-items: center;
  column-gap: var(--space-4);
  row-gap: var(--space-1);
}

.paper {
  grid-area: paper;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.track {
  grid-area: track;
  position: relative;
  height: 10px;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
}

.fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: var(--radius-full);
}

/* The phase's base share: a hairline tick the tilt is read against. */
.base {
  position: absolute;
  top: -4px;
  bottom: -4px;
  width: 2px;
  border-radius: var(--radius-full);
  background-color: var(--color-text);
  opacity: 0.55;
  transform: translateX(-1px);
}

.time {
  grid-area: time;
  display: inline-flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.share {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.reason {
  grid-area: reason;
  margin: 0;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

@media (max-width: 640px) {
  .row {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas:
      'paper time'
      'track track'
      'reason reason';
  }
}
</style>
