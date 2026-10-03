<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { OutlineHeading } from '../editor/useNoteOutline'
import BacklinksPanel from './BacklinksPanel.vue'
import RailPlaceholder from './RailPlaceholder.vue'
import RailSection from './RailSection.vue'

/**
 * The Knowledge Workspace's context rail (Phase 16 Step 6).
 *
 * Four sections, ordered inward-to-outward — how this note is built, then what
 * points at it, then what it resembles, then what it produced:
 *
 * 1. **Outline** — structure of the open note (from the ProseMirror document).
 * 2. **Backlinks** — inbound links, from the server's `note_links` index.
 * 3. **Related notes** — reserved for Phase 18's retrieval layer.
 * 4. **Cards from this note** — reserved for note→card provenance.
 *
 * The rail is stateless chrome: it owns no note data and mutates nothing. Every
 * action leaves as an event, so the view stays the single navigator. Sections
 * 3–4 are honest placeholders (see {@link RailPlaceholder}) — they exist to fix
 * the rail's shape before those phases land, and show no invented data.
 */
defineProps<{
  outline: OutlineHeading[]
  /** Null while no note is open — the rail then shows only its empty states. */
  noteId: string | null
  noteTitle: string | null
}>()

const emit = defineEmits<{
  /** Jump the caret to a heading's document position. */
  heading: [pos: number]
  select: [id: string]
}>()

const { t } = useI18n()

/** Depth indent, capped at the v1 schema's three heading levels. */
function indentOf(level: number): string {
  return `${(Math.min(level, 3) - 1) * 12}px`
}
</script>

<template>
  <aside class="context-rail" :aria-label="t('notes.rail.label')">
    <RailSection
      :title="t('notes.outline')"
      :empty="t('notes.outlineEmpty')"
      :is-empty="outline.length === 0"
    >
      <ul class="outline-list">
        <li v-for="item in outline" :key="item.pos">
          <button
            type="button"
            class="outline-item"
            :class="`level-${item.level}`"
            :style="{ paddingInlineStart: indentOf(item.level) }"
            @click="emit('heading', item.pos)"
          >
            {{ item.text }}
          </button>
        </li>
      </ul>
    </RailSection>

    <BacklinksPanel :note-id="noteId" :note-title="noteTitle" @select="emit('select', $event)" />

    <RailPlaceholder
      :title="t('notes.rail.related')"
      icon="network"
      :note="t('notes.rail.relatedNote')"
    />

    <RailPlaceholder
      :title="t('notes.rail.cards')"
      icon="layers"
      :note="t('notes.rail.cardsNote')"
    />
  </aside>
</template>

<style scoped>
.context-rail {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  min-width: 0;
  padding: var(--space-8) var(--space-5);
  border-left: var(--border-width-sm) solid var(--color-border);
  overflow-y: auto;
}

.outline-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-0-5);
}

.outline-item {
  display: block;
  width: 100%;
  padding: var(--space-1) var(--space-2);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-family: inherit;
  font-size: var(--text-sm);
  line-height: var(--leading-tight);
  color: var(--color-text-secondary);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background-color var(--duration-fast) var(--ease-out);
}

/* Depth reads as weight as well as indent, so the outline is scannable at a
   glance rather than requiring the eye to measure left edges. */
.outline-item.level-1 {
  font-weight: 600;
  color: var(--color-text);
}

.outline-item:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-text);
}

/* Below the three-column breakpoint the rail becomes a row under the canvas:
   its sections flow into columns instead of stacking into a long tail. */
@media (max-width: 1024px) {
  .context-rail {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: var(--space-5) var(--space-6);
    align-content: start;
    padding: var(--space-5) var(--space-6);
    border-left: none;
    border-top: var(--border-width-sm) solid var(--color-border);
  }
}
</style>
