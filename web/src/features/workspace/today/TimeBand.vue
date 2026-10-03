<script setup lang="ts">
/**
 * The day in hours, per paper — the frame a 考研 day is actually lived in.
 * Each paper shows today's planned time (the plan's share of the study day)
 * against the time recorded so far, the running timer included as it ticks,
 * and one tap to start timing it. Starting a paper while another runs is a
 * switch: the running block is saved first, as everywhere else.
 *
 * Solid and quiet: four tiles, a hairline meter each in the paper's accent,
 * text in text tokens. Nothing here decides the day — the plan above is
 * still the server's, and this band never enters it.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'
import type { ExamSubjectCode } from '@/api/modules/exam'
import type { PlanDto } from '@/api/modules/plan'
import { useFocusStore } from '@/stores/focus'
import { PAPER_ICON, paperAccentColor } from '@/features/syllabus/papers'
import { subjectOfCode } from '@/features/syllabus/syllabusIndex'
import { formatClock, hoursOf } from '@/features/focus/clock'

const props = defineProps<{ plan: PlanDto }>()

const { t } = useI18n()
const focusStore = useFocusStore()

const runningPaper = computed<ExamSubjectCode | null>(() =>
  focusStore.running ? subjectOfCode(focusStore.timer?.nodeCode) : null,
)

const tiles = computed(() =>
  props.plan.papers.map((paper) => {
    const running = runningPaper.value === paper.subject
    const studied = paper.todayMinutes + (running ? focusStore.elapsedSeconds / 60 : 0)
    return {
      paper,
      running,
      studied,
      percent: paper.dailyMinutes > 0 ? Math.min(100, (studied / paper.dailyMinutes) * 100) : 0,
    }
  }),
)

const sittingsDue = computed(() =>
  props.plan.papers.reduce((sum, paper) => sum + paper.weeklySittings, 0),
)
const sittingsDone = computed(() =>
  props.plan.papers.reduce((sum, paper) => sum + paper.sittingsThisWeek, 0),
)

function start(subject: ExamSubjectCode) {
  void focusStore.start({ nodeCode: subject })
}

function stop() {
  void focusStore.stop()
}
</script>

<template>
  <section class="time-band" aria-labelledby="today-time-title">
    <div class="head">
      <h2 id="today-time-title" class="title">{{ t('today.time.title') }}</h2>
      <span v-if="sittingsDue > 0" class="week">
        {{ t('today.time.week', { done: sittingsDone, due: sittingsDue }) }}
      </span>
      <RouterLink :to="{ name: 'plan' }" class="link">
        {{ t('today.time.plan') }}
        <AppIcon name="arrow-right" size="sm" aria-hidden="true" />
      </RouterLink>
    </div>
    <ul class="tiles" :aria-label="t('today.time.label')">
      <li
        v-for="tile in tiles"
        :key="tile.paper.subject"
        class="tile"
        :class="{ running: tile.running }"
        :style="{ '--paper-accent': paperAccentColor(tile.paper.subject) }"
      >
        <div class="tile-head">
          <span class="paper">
            <AppIcon :name="PAPER_ICON[tile.paper.subject]" size="sm" class="paper-icon" />
            {{ t(`exam.papers.${tile.paper.subject}`) }}
          </span>
          <button
            v-if="tile.running"
            type="button"
            class="action stop"
            :disabled="focusStore.pending"
            :aria-label="
              t('today.time.stopLabel', { paper: t(`exam.papers.${tile.paper.subject}`) })
            "
            @click="stop"
          >
            <span class="clock">{{ formatClock(focusStore.elapsedSeconds) }}</span>
            <AppIcon name="stop" size="sm" />
          </button>
          <button
            v-else
            type="button"
            class="action"
            :disabled="focusStore.pending"
            :aria-label="
              t('today.time.startLabel', { paper: t(`exam.papers.${tile.paper.subject}`) })
            "
            @click="start(tile.paper.subject)"
          >
            <AppIcon name="play" size="sm" />
            <span>{{ t('today.time.start') }}</span>
          </button>
        </div>
        <div
          class="track"
          role="meter"
          :aria-label="t(`exam.papers.${tile.paper.subject}`)"
          aria-valuemin="0"
          :aria-valuemax="tile.paper.dailyMinutes"
          :aria-valuenow="Math.round(Math.min(tile.studied, tile.paper.dailyMinutes))"
          :aria-valuetext="
            t('today.time.paper', {
              done: hoursOf(tile.studied),
              planned: hoursOf(tile.paper.dailyMinutes),
            })
          "
        >
          <span
            v-if="tile.percent > 0"
            class="fill"
            :style="{ width: `${Math.max(3, tile.percent)}%` }"
          ></span>
        </div>
        <span class="hours">
          {{
            t('today.time.paper', {
              done: hoursOf(tile.studied),
              planned: hoursOf(tile.paper.dailyMinutes),
            })
          }}
        </span>
      </li>
    </ul>
    <p v-if="focusStore.error" class="error" role="alert">{{ t(focusStore.error.messageKey) }}</p>
  </section>
</template>

<style scoped>
.time-band {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}

.head {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
}

.title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.week {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-left: auto;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  text-decoration: none;
}

.link:hover {
  color: var(--color-primary);
}

.tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.tile {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
}

.tile.running {
  border-color: var(--paper-accent);
  box-shadow: inset 0 0 0 1px var(--paper-accent);
}

.tile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.paper {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
  white-space: nowrap;
}

.paper-icon {
  color: var(--paper-accent);
}

.action {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--space-1);
  min-height: 28px;
  padding: 0 var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-full);
  background-color: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-xs);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.action:hover:not(:disabled) {
  border-color: var(--paper-accent);
  color: var(--color-text);
}

.action.stop {
  border-color: var(--paper-accent);
  color: var(--color-text);
}

.clock {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.track {
  position: relative;
  height: 6px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
}

.fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: var(--radius-full);
  background-color: var(--paper-accent);
}

.hours {
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

@media (max-width: 960px) {
  .tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
