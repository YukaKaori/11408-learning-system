<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'
import type { IconName } from '@/components/icons/registry'
import RailSection from './RailSection.vue'

/**
 * A reserved-but-empty context-rail slot (Phase 16 Step 6).
 *
 * The Knowledge Workspace's rail is architected for four kinds of context;
 * two of them — Related notes (needs the retrieval layer, Phase 18) and Cards
 * from this note (needs note→card provenance) — have no data source yet. They
 * are rendered now so the rail's shape is settled before those phases land, and
 * so the user can see where their corpus is heading.
 *
 * **Honesty rule:** a placeholder shows no rows, no counts and no sample data.
 * It states what will appear here and is explicitly marked as not-yet-built.
 * Anything that could be mistaken for real content belongs in the phase that
 * actually produces it.
 */
defineProps<{
  title: string
  icon: IconName
  /** One already-translated sentence: what will appear here, and from where. */
  note: string
}>()

const { t } = useI18n()
</script>

<template>
  <RailSection :title="title">
    <div class="placeholder">
      <div class="placeholder-head">
        <AppIcon :name="icon" size="sm" class="placeholder-icon" aria-hidden="true" />
        <span class="placeholder-tag">{{ t('notes.rail.planned') }}</span>
      </div>
      <p class="placeholder-note">{{ note }}</p>
    </div>
  </RailSection>
</template>

<style scoped>
/* Dashed, unfilled, non-interactive: it reads as reserved space, never as a
   list that happens to be empty. */
.placeholder {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-3);
  border: var(--border-width-sm) dashed var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
}

.placeholder-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.placeholder-icon {
  color: var(--color-text-tertiary);
}

/* Text stays on the secondary ramp for contrast (see RailSection); it is the
   dashed frame and the icon, not faint type, that mark the slot as reserved. */
.placeholder-tag {
  font-family: var(--font-label-family);
  font-size: var(--text-xs);
  font-weight: var(--font-label-weight);
  letter-spacing: var(--font-label-tracking);
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.placeholder-note {
  margin: 0;
  font-size: var(--text-xs);
  line-height: var(--leading-normal);
  color: var(--color-text-secondary);
}
</style>
