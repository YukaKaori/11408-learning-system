<script setup lang="ts">
/**
 * This week against the plan: hours studied per paper against the week's
 * planned hours, and whole papers sat against the phase's cadence. The hours
 * come from timed and logged sessions only — the plan cannot see study that
 * was never recorded, and it does not pretend to.
 */
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'
import type { PlanPaperDto } from '@/api/modules/plan'
import { PAPER_ICON, paperAccentColor } from '@/features/syllabus/papers'
import { hoursOf } from '@/features/focus/clock'

defineProps<{
  papers: PlanPaperDto[]
  unclassifiedMinutes: number
}>()

const { t } = useI18n()
</script>

<template>
  <div class="week">
    <ul class="rows">
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
          :aria-valuemax="paper.weekPlannedMinutes"
          :aria-valuenow="Math.min(paper.weekMinutes, paper.weekPlannedMinutes)"
          :aria-valuetext="
            t('plan.week.hours', {
              done: hoursOf(paper.weekMinutes),
              planned: hoursOf(paper.weekPlannedMinutes),
            })
          "
        >
          <span
            v-if="paper.weekMinutes > 0 && paper.weekPlannedMinutes > 0"
            class="fill"
            :style="{
              width: `${Math.min(100, (paper.weekMinutes / paper.weekPlannedMinutes) * 100)}%`,
              backgroundColor: paperAccentColor(paper.subject),
            }"
          ></span>
        </div>
        <span class="hours">
          {{
            t('plan.week.hours', {
              done: hoursOf(paper.weekMinutes),
              planned: hoursOf(paper.weekPlannedMinutes),
            })
          }}
        </span>
        <span
          class="sittings"
          :class="{
            met: paper.weeklySittings > 0 && paper.sittingsThisWeek >= paper.weeklySittings,
          }"
        >
          <template v-if="paper.weeklySittings > 0">
            {{
              t('plan.week.sittings', { done: paper.sittingsThisWeek, due: paper.weeklySittings })
            }}
          </template>
          <template v-else-if="paper.sittingsThisWeek > 0">
            {{ t('plan.week.sittingsOnly', { done: paper.sittingsThisWeek }) }}
          </template>
          <template v-else>—</template>
        </span>
      </li>
    </ul>
    <p v-if="unclassifiedMinutes > 0" class="unclassified">
      {{ t('plan.week.unclassified', { n: hoursOf(unclassifiedMinutes) }) }}
    </p>
  </div>
</template>

<style scoped>
.week {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.rows {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 150px 96px;
  align-items: center;
  gap: var(--space-4);
}

.paper {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.track {
  position: relative;
  height: 8px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
}

.fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: var(--radius-full);
}

.hours {
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  text-align: right;
  color: var(--color-text);
}

.sittings {
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  text-align: right;
  color: var(--color-text-secondary);
}

.sittings.met {
  color: var(--color-success);
  font-weight: 500;
}

.unclassified {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

@media (max-width: 640px) {
  .row {
    grid-template-columns: minmax(0, 1fr) auto;
    row-gap: var(--space-1);
  }

  .track {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .hours {
    grid-column: 1;
    grid-row: 3;
    text-align: left;
  }

  .sittings {
    grid-column: 2;
    grid-row: 3;
  }
}
</style>
