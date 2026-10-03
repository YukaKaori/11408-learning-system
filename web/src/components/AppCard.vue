<script setup lang="ts">
import type { CardVariant } from './types'

withDefaults(
  defineProps<{
    variant?: CardVariant
    padded?: boolean
    interactive?: boolean
  }>(),
  {
    variant: 'flat',
    padded: true,
    interactive: false,
  },
)
</script>

<template>
  <div class="app-card" :class="[`variant-${variant}`, { padded, interactive }]">
    <header v-if="$slots.header" class="card-header">
      <slot name="header" />
    </header>
    <div class="card-body">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="card-footer">
      <slot name="footer" />
    </footer>
  </div>
</template>

<style scoped>
.app-card {
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  transition:
    box-shadow var(--duration-base) var(--ease-out),
    transform var(--duration-base) var(--ease-out),
    border-color var(--duration-base) var(--ease-out);
}

.variant-flat {
  border: var(--border-width-sm) solid var(--color-border);
}

.variant-elevated {
  border: var(--border-width-sm) solid transparent;
  box-shadow: var(--shadow-md);
}

/*
 * There is no glass variant. AppCard is a content container — cards hold the
 * work, and the work is never glass (see docs/liquid-material-system.md §1).
 * The `glass` variant shipped through Phase 16 was the legacy glassmorphism
 * material: white fill, uniform 1px border, blur-as-the-whole-material. It was
 * removed in Phase 17.2; its call sites are `elevated` and `flat`.
 */

.interactive {
  cursor: pointer;
}
.interactive:hover,
.interactive:focus-visible {
  box-shadow: var(--shadow-lg), var(--shadow-glow-primary);
  transform: translateY(-1px);
}

.padded .card-body {
  padding: var(--space-5);
}
.padded .card-header {
  padding: var(--space-5) var(--space-5) 0;
}
.padded .card-footer {
  padding: 0 var(--space-5) var(--space-5);
}

.card-header {
  font-family: var(--font-title-family);
  font-size: var(--font-title-size);
  font-weight: var(--font-title-weight);
  padding-bottom: var(--space-4);
}

.card-footer {
  padding-top: var(--space-4);
  border-top: var(--border-width-sm) solid var(--color-border);
}
</style>
