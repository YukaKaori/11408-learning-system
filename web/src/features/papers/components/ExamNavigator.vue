<script setup lang="ts">
/**
 * The answer card (答题卡): every question of the paper by number, grouped by
 * printed section — answered, flagged, current at a glance, one tap to go.
 *
 * State is said in words to assistive technology and with a mark as well as a
 * colour on screen (a flag glyph, a filled cell), so it never rests on colour
 * alone. It shows nothing about right or wrong: there is nothing to show
 * before the hand-in.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'
import type { AnswerEntryDto } from '@/api/modules/paper'
import { isAnswered, type ExamSlot } from '../paperFormat'

const props = defineProps<{
  slots: ExamSlot[]
  current: number
  sheet: Record<string, AnswerEntryDto>
  sectionName: (code: string) => string
}>()

defineEmits<{ go: [index: number] }>()

const { t } = useI18n()

const sections = computed(() => {
  const grouped: { code: string; slots: ExamSlot[] }[] = []
  for (const slot of props.slots) {
    const last = grouped[grouped.length - 1]
    if (last && last.code === slot.section) last.slots.push(slot)
    else grouped.push({ code: slot.section, slots: [slot] })
  }
  return grouped
})

function stateOf(slot: ExamSlot): string {
  const entry = props.sheet[slot.item.question.id]
  const parts = [isAnswered(entry) ? t('papers.exam.cell.answered') : t('papers.exam.cell.open')]
  if (entry?.flagged) parts.push(t('papers.exam.cell.flagged'))
  return parts.join(' · ')
}
</script>

<template>
  <nav class="navigator" :aria-label="t('papers.exam.navigator')">
    <section v-for="section in sections" :key="section.code" class="block">
      <h2 class="block-title">{{ sectionName(section.code) }}</h2>
      <ol class="cells">
        <li v-for="slot in section.slots" :key="slot.item.question.id">
          <button
            type="button"
            class="cell"
            :class="{
              answered: isAnswered(sheet[slot.item.question.id]),
              flagged: sheet[slot.item.question.id]?.flagged,
              current: slot.index === current,
            }"
            :aria-current="slot.index === current ? 'step' : undefined"
            :aria-label="t('papers.exam.cell.label', { n: slot.item.number, state: stateOf(slot) })"
            @click="$emit('go', slot.index)"
          >
            {{ slot.item.number }}
            <AppIcon v-if="sheet[slot.item.question.id]?.flagged" name="flag" size="sm" class="cell-flag" aria-hidden="true" />
          </button>
        </li>
      </ol>
    </section>

    <p class="legend" aria-hidden="true">
      <span class="legend-item"><span class="swatch answered"></span>{{ t('papers.exam.cell.answered') }}</span>
      <span class="legend-item"><span class="swatch"></span>{{ t('papers.exam.cell.open') }}</span>
      <span class="legend-item"><AppIcon name="flag" size="sm" class="legend-flag" />{{ t('papers.exam.cell.flagged') }}</span>
    </p>
  </nav>
</template>

<style scoped>
.navigator {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.block {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.block-title {
  margin: 0;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-secondary);
}

.cells {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(36px, 1fr));
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}

.cell {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 36px;
  padding: 0;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

.cell:hover {
  border-color: var(--color-border-strong);
}

.cell:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.cell.answered {
  border-color: var(--paper-accent, var(--color-primary));
  background-color: var(--color-primary-soft);
  color: var(--color-text);
  font-weight: 600;
}

.cell.current {
  border-width: 2px;
  border-color: var(--color-text);
}

.cell-flag {
  position: absolute;
  top: -6px;
  right: -6px;
  padding: 1px;
  border-radius: var(--radius-full);
  background-color: var(--color-warning-soft);
  color: var(--color-warning);
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-3);
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.swatch {
  width: 12px;
  height: 12px;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-sm);
}

.swatch.answered {
  border-color: var(--paper-accent, var(--color-primary));
  background-color: var(--color-primary-soft);
}

.legend-flag {
  color: var(--color-warning);
}
</style>
