<script setup lang="ts">
/**
 * The 真题 shelf: one cell per year, newest first — the inventory every
 * candidate keeps in a notebook or a spreadsheet, kept here from the records
 * themselves. A cell shows the best whole-paper score (or the latest part, out
 * of what it covered) and how many times the year was sat; an untouched year
 * is a quiet "—". Choosing a year starts a record for it — the first time, or
 * 二刷.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PastPaperYearDto } from '@/api/modules/sitting'
import { formatScore } from '../sittingFormat'

const props = defineProps<{
  years: PastPaperYearDto[]
  fullScore: number
}>()

const emit = defineEmits<{ pick: [year: number] }>()

const { t } = useI18n()

const done = computed(() => props.years.filter((year) => year.times > 0).length)

function cellLabel(year: PastPaperYearDto): string {
  if (year.times === 0) return t('sittings.shelf.cellNotDone', { year: year.year })
  const score =
    year.best !== null
      ? `${formatScore(year.best)} / ${props.fullScore}`
      : `${formatScore(year.latest ?? 0)} / ${formatScore(year.latestFull ?? 0)}`
  return t('sittings.shelf.cellDone', { year: year.year, score, n: year.times })
}
</script>

<template>
  <div class="shelf">
    <p class="summary">{{ t('sittings.shelf.summary', { done, total: years.length }) }}</p>
    <ul class="grid">
      <li v-for="year in years" :key="year.year">
        <button
          type="button"
          class="cell"
          :class="{ done: year.times > 0 }"
          :aria-label="cellLabel(year)"
          :title="cellLabel(year)"
          @click="emit('pick', year.year)"
        >
          <span class="year">{{ year.year }}</span>
          <span v-if="year.times === 0" class="score empty">—</span>
          <span v-else-if="year.best !== null" class="score">{{ formatScore(year.best) }}</span>
          <span v-else class="score partial">{{ t('sittings.shelf.partial') }}</span>
          <span v-if="year.times > 1" class="times">×{{ year.times }}</span>
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.shelf {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.summary {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(58px, 1fr));
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.cell {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  min-height: 52px;
  padding: var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out);
}

.cell:hover {
  border-color: var(--color-border-strong);
}

.cell:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.cell.done {
  background-color: var(--color-primary-soft);
  border-color: transparent;
}

.year {
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.score {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text);
}

.score.empty {
  font-weight: 400;
  color: var(--color-text-tertiary);
}

.score.partial {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--color-text-secondary);
}

.times {
  position: absolute;
  top: var(--space-1);
  right: var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}
</style>
