<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'
import type { SuggestionRow } from './wikiLinkSuggestion'

/**
 * The `[[` autocomplete popup (Phase 16 Step 4).
 *
 * Deliberately **solid** — per the Phase 16 optical-glass boundary the editor
 * canvas and everything anchored inside it stays solid; the only new glass this
 * phase is the Step 5 selection toolbar. Purely presentational: the caller owns
 * the rows, the highlighted index and the keyboard.
 */
defineProps<{
  rows: SuggestionRow[]
  activeIndex: number
  /** Position within the editor wrapper, in px. */
  left: number
  top: number
}>()

const emit = defineEmits<{ select: [index: number] }>()

const { t } = useI18n()
</script>

<template>
  <div
    class="wiki-suggestions"
    role="listbox"
    :aria-label="t('notes.linkSuggest.label')"
    :style="{ left: `${left}px`, top: `${top}px` }"
  >
    <p v-if="rows.length === 0" class="suggestion-empty">{{ t('notes.linkSuggest.empty') }}</p>
    <ul v-else class="suggestion-list">
      <li v-for="(row, index) in rows" :key="`${row.kind}-${row.id ?? row.title}`">
        <button
          type="button"
          class="suggestion-row"
          :class="{ active: index === activeIndex }"
          role="option"
          :aria-selected="index === activeIndex"
          @mousedown.prevent="emit('select', index)"
        >
          <AppIcon :name="row.kind === 'create' ? 'plus' : 'file-text'" size="sm" />
          <span class="suggestion-title">
            {{ row.kind === 'create' ? t('notes.linkSuggest.create', { title: row.title }) : row.title }}
          </span>
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.wiki-suggestions {
  position: absolute;
  z-index: 20;
  width: 280px;
  max-height: 260px;
  overflow-y: auto;
  padding: var(--space-1);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  box-shadow: var(--shadow-float);
}

.suggestion-empty {
  margin: 0;
  padding: var(--space-3);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.suggestion-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.suggestion-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-2);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: inherit;
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.suggestion-row:hover,
.suggestion-row.active {
  background-color: var(--color-surface-hover);
  color: var(--color-text);
}

.suggestion-row.active {
  color: var(--color-primary);
}

.suggestion-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
