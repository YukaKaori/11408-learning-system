<script setup lang="ts">
import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppSkeleton } from '@/components'
import { listBacklinks } from '@/api/modules/note'
import { useAsync } from '@/composables/useAsync'

/**
 * The context rail's Backlinks section (Phase 16 Step 4): every note that links
 * to the open one, read from `GET /api/v1/notes/{id}/backlinks`.
 *
 * The panel is a pure reader of the server's derived `note_links` index — it
 * never computes the graph client-side, so a link made from a note that isn't
 * currently loaded still shows up, and dangling links resolved by title (the
 * backend's fallback) appear without any extra round trip.
 */
const props = defineProps<{
  noteId: string | null
  /** Renaming the note changes which dangling links resolve to it. */
  noteTitle: string | null
}>()

const emit = defineEmits<{ select: [id: string] }>()

const { t, d } = useI18n()

const { data, loading, error, reload } = useAsync(() =>
  props.noteId ? listBacklinks(props.noteId) : Promise.resolve([]),
)

watch(
  () => [props.noteId, props.noteTitle],
  () => void reload(),
)
</script>

<template>
  <section class="backlinks">
    <h3 class="rail-title">{{ t('notes.backlinks') }}</h3>
    <AppSkeleton v-if="loading" :lines="2" />
    <div v-else-if="error" class="rail-error">
      <p class="rail-empty">{{ t(error.messageKey) }}</p>
      <AppButton size="sm" variant="ghost" @click="reload">{{ t('common.retry') }}</AppButton>
    </div>
    <p v-else-if="!data || data.length === 0" class="rail-empty">{{ t('notes.backlinksEmpty') }}</p>
    <ul v-else class="backlink-list">
      <li v-for="note in data" :key="note.id">
        <button type="button" class="backlink-item" @click="emit('select', note.id)">
          <span class="backlink-title">{{ note.title }}</span>
          <span class="backlink-date">{{ d(note.updatedAt, 'short') }}</span>
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.rail-title {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.rail-empty {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.rail-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}

.backlink-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.backlink-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: var(--space-2);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.backlink-item:hover {
  background-color: var(--color-surface-hover);
}

.backlink-title {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.backlink-item:hover .backlink-title {
  color: var(--color-primary);
}

.backlink-date {
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}
</style>
