<script setup lang="ts">
import type { Tone } from './types'

withDefaults(
  defineProps<{
    tone?: Tone
    variant?: 'solid' | 'soft' | 'dot'
  }>(),
  {
    tone: 'primary',
    variant: 'soft',
  },
)
</script>

<template>
  <span v-if="variant === 'dot'" class="app-badge dot" :class="`tone-${tone}`" aria-hidden="true" />
  <span v-else class="app-badge" :class="[`variant-${variant}`, `tone-${tone}`]">
    <slot />
  </span>
</template>

<style scoped>
.app-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 var(--space-1);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 600;
  line-height: 1;
}

.app-badge.dot {
  min-width: 8px;
  width: 8px;
  height: 8px;
  padding: 0;
}

.variant-solid.tone-primary,
.dot.tone-primary {
  background-color: var(--color-primary);
  color: white;
}
.variant-solid.tone-secondary,
.dot.tone-secondary {
  background-color: var(--color-secondary);
  color: white;
}
.variant-solid.tone-success,
.dot.tone-success {
  background-color: var(--color-success);
  color: white;
}
.variant-solid.tone-warning,
.dot.tone-warning {
  background-color: var(--color-warning);
  color: white;
}
.variant-solid.tone-danger,
.dot.tone-danger {
  background-color: var(--color-danger);
  color: white;
}
.variant-solid.tone-info,
.dot.tone-info {
  background-color: var(--color-info);
  color: white;
}

.variant-soft.tone-primary {
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
}
.variant-soft.tone-secondary {
  background-color: var(--color-secondary-soft);
  color: var(--color-secondary);
}
.variant-soft.tone-success {
  background-color: var(--color-success-soft);
  color: var(--color-success);
}
.variant-soft.tone-warning {
  background-color: var(--color-warning-soft);
  color: var(--color-warning);
}
.variant-soft.tone-danger {
  background-color: var(--color-danger-soft);
  color: var(--color-danger);
}
.variant-soft.tone-info {
  background-color: var(--color-info-soft);
  color: var(--color-info);
}
</style>
