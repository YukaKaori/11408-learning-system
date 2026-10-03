<script setup lang="ts">
/**
 * The two exam days as printed on the admission ticket — each paper, its date
 * and its times. Worth seeing all year: the sprint rehearses on exactly this
 * timetable (数学 in the morning, 专业课 in the afternoon), and the body clock
 * is part of the preparation.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'
import type { ExamSlotDto } from '@/api/modules/plan'
import { PAPER_ICON, paperAccentColor } from '@/features/syllabus/papers'
import { parseIsoDate } from '@/utils/date'

const props = defineProps<{
  slots: ExamSlotDto[]
  estimated: boolean
}>()

const { t, d, locale } = useI18n()

const days = computed(() => {
  const byDay = new Map<number, ExamSlotDto[]>()
  for (const slot of props.slots) byDay.set(slot.day, [...(byDay.get(slot.day) ?? []), slot])
  return [...byDay.entries()].map(([day, slots]) => ({ day, date: slots[0]!.date, slots }))
})

/** The weekday in the app's language, not the browser's. */
function weekday(iso: string): string {
  return parseIsoDate(iso).toLocaleDateString(locale.value, { weekday: 'short' })
}
</script>

<template>
  <div class="timetable">
    <div v-for="day in days" :key="day.day" class="day">
      <div class="day-head">
        <span class="day-name">{{ t('plan.exam.day', { n: day.day }) }}</span>
        <span class="day-date">
          {{ d(parseIsoDate(day.date), 'short') }} · {{ weekday(day.date) }}
          <template v-if="estimated">{{ t('plan.exam.estimated') }}</template>
        </span>
      </div>
      <ul class="slots">
        <li v-for="slot in day.slots" :key="slot.subject" class="slot">
          <AppIcon
            :name="PAPER_ICON[slot.subject]"
            size="sm"
            :style="{ color: paperAccentColor(slot.subject) }"
          />
          <span class="slot-paper">{{ t(`exam.papers.${slot.subject}`) }}</span>
          <span class="slot-time">{{ slot.startTime }}–{{ slot.endTime }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.timetable {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.day {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.day-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.day-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text);
}

.day-date {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.slots {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.slot {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
}

.slot-paper {
  flex: 1;
  font-size: var(--text-sm);
  color: var(--color-text);
}

.slot-time {
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

@media (max-width: 640px) {
  .timetable {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
