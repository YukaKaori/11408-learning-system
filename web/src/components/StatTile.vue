<script setup lang="ts">
import AppCard from './AppCard.vue'
import AppIcon from './AppIcon.vue'
import type { IconName } from './icons/registry'

/**
 * Dashboard KPI tile — icon (optional), headline value, caption label.
 * `value` accepts pre-formatted strings only; formatting (durations, units,
 * honest "—" for null metrics) stays in the view. The default slot replaces
 * the plain value for richer content (e.g. a progress ring).
 */
withDefaults(
  defineProps<{
    label: string
    value?: string
    icon?: IconName
  }>(),
  {
    value: undefined,
    icon: undefined,
  },
)
</script>

<template>
  <AppCard variant="flat" class="stat-tile">
    <span v-if="icon" class="stat-icon" aria-hidden="true"><AppIcon :name="icon" /></span>
    <span class="stat-value"><slot>{{ value }}</slot></span>
    <span class="stat-label">{{ label }}</span>
  </AppCard>
</template>

<style scoped>
.stat-tile :deep(.card-body) {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-bottom: var(--space-2);
  border-radius: var(--radius-sm);
  color: var(--color-primary);
  background-color: var(--color-primary-soft);
}

.stat-value {
  font-size: var(--text-xl);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  letter-spacing: var(--tracking-tight);
}

.stat-label {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}
</style>
