<script setup lang="ts">
/**
 * The plan's two views — the long view (规划) and its concrete schedule (日历)
 * — as one section with a segmented switch. Plain links: each view is its own
 * route, so either can be bookmarked and the browser's back button works.
 */
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <nav class="plan-tabs" :aria-label="t('plan.tabs.label')">
    <RouterLink :to="{ name: 'plan' }" class="tab" exact-active-class="active">{{
      t('plan.tabs.plan')
    }}</RouterLink>
    <RouterLink :to="{ name: 'calendar' }" class="tab" exact-active-class="active">
      {{ t('plan.tabs.calendar') }}
    </RouterLink>
  </nav>
</template>

<style scoped>
.plan-tabs {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border-radius: var(--radius-md);
  background-color: var(--color-surface-hover);
}

.tab {
  padding: var(--space-1) var(--space-4);
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  text-decoration: none;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.tab:hover {
  color: var(--color-text);
}

.tab:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.tab.active {
  background-color: var(--color-surface);
  color: var(--color-text);
  font-weight: 500;
  box-shadow: var(--shadow-sm);
}
</style>
