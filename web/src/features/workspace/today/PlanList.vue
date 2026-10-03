<script setup lang="ts">
/**
 * The Plan — the primary surface of Today.
 *
 * One ordered list, rendered in the order the server sent. No grouping, no
 * headers, no widget grid: rank *is* the product, and inserting client-side
 * sections would re-order the server's answer by another name.
 *
 * The server caps the plan at 8 and reports what it suppressed in
 * `remainingCount`; overflow collapses into a single link into Calendar so a
 * plan can never grow into a backlog.
 *
 * Solid surface — a list of commitments is the work (phase17-material-audit
 * §2.3). One flat container, not eight floating cards.
 */
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'
import type { PlanItemDto } from '@/api/modules/workspace'
import PlanRow from './PlanRow.vue'

defineProps<{
  items: PlanItemDto[]
  remainingCount: number
  /**
   * The id of the row whose action is in flight. One at a time, deliberately:
   * a plan being acted on in three places at once is a queue, not a plan.
   */
  pendingId?: string | null
}>()

defineEmits<{ activate: [item: PlanItemDto] }>()

const { t } = useI18n()
</script>

<template>
  <div class="plan">
    <!--
      The plan has no visible heading — the greeting above it is the page's
      only h1, and a second title would make the band compete with itself. A
      screen reader still needs to know what this list is, so it is named
      directly: "Today's plan, N items" is what a sighted user reads from
      position and context.
    -->
    <ul class="plan-list" :aria-label="t('today.plan.label', { n: items.length })">
      <PlanRow
        v-for="item in items"
        :key="item.id"
        :item="item"
        :busy="item.id === pendingId"
        @activate="$emit('activate', $event)"
      />
    </ul>

    <RouterLink v-if="remainingCount > 0" :to="{ name: 'calendar' }" class="plan-overflow">
      {{ t('today.plan.more', { n: remainingCount }) }}
      <AppIcon name="arrow-right" size="sm" aria-hidden="true" />
    </RouterLink>
  </div>
</template>

<style scoped>
.plan {
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  overflow: hidden;
}

.plan-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.plan-overflow {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-5);
  border-top: var(--border-width-sm) solid var(--color-border);
  background-color: var(--color-muted-soft);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  text-decoration: none;
}

.plan-overflow:hover {
  color: var(--color-text);
}

/*
 * Width and colour come from the global ring (base.css). Only the offset is
 * re-declared: this strip is flush inside the plan's rounded border, so an
 * outward ring would be clipped by the container's `overflow: hidden`.
 */
.plan-overflow:focus-visible {
  outline-offset: -2px;
}
</style>
