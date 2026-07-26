<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { listBacklinks } from '@/api/modules/note'
import { useAsync } from '@/composables/useAsync'
import RailSection from './RailSection.vue'

/**
 * The context rail's Backlinks section (Phase 16 Step 4): every note that links
 * to the open one, read from `GET /api/v1/notes/{id}/backlinks`.
 *
 * The panel is a pure reader of the server's derived `note_links` index — it
 * never computes the graph client-side, so a link made from a note that isn't
 * currently loaded still shows up, and dangling links resolved by title (the
 * backend's fallback) appear without any extra round trip.
 *
 * Step 6 moved its view-state rendering onto {@link RailSection} so it matches
 * every other section in the rail.
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

const backlinks = computed(() => data.value ?? [])
</script>

<template>
  <RailSection
    :title="t('notes.backlinks')"
    :loading="loading"
    :error="error ? t(error.messageKey) : null"
    :empty="t('notes.backlinksEmpty')"
    :is-empty="backlinks.length === 0"
    @retry="reload"
  >
    <ul class="backlink-list">
      <li v-for="note in backlinks" :key="note.id">
        <button type="button" class="backlink-item" @click="emit('select', note.id)">
          <span class="backlink-title">{{ note.title }}</span>
          <span class="backlink-date">{{ d(note.updatedAt, 'short') }}</span>
        </button>
      </li>
    </ul>
  </RailSection>
</template>

<style scoped>
.backlink-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-0-5);
}

.backlink-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-0-5);
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
