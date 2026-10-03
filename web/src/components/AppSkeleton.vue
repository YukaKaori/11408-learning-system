<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'text' | 'circle' | 'block'
    width?: string
    height?: string
    /** Number of stacked lines, only relevant to variant="text". */
    lines?: number
  }>(),
  {
    variant: 'text',
    width: undefined,
    height: undefined,
    lines: 1,
  },
)
</script>

<template>
  <div v-if="variant === 'text'" class="app-skeleton-group" role="presentation" aria-hidden="true">
    <div
      v-for="line in lines"
      :key="line"
      class="app-skeleton text"
      :style="{ width: line === lines && lines > 1 ? '70%' : (width ?? '100%') }"
    />
  </div>
  <div
    v-else
    class="app-skeleton"
    :class="variant"
    role="presentation"
    aria-hidden="true"
    :style="{ width: width ?? (variant === 'circle' ? '40px' : '100%'), height: height ?? (variant === 'circle' ? '40px' : '80px') }"
  />
</template>

<style scoped>
.app-skeleton-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.app-skeleton {
  background: linear-gradient(
    90deg,
    var(--color-surface-hover) 25%,
    var(--color-border) 37%,
    var(--color-surface-hover) 63%
  );
  background-size: 400% 100%;
  animation: app-skeleton-shimmer 1.4s ease infinite;
}

.app-skeleton.text {
  height: 12px;
  border-radius: var(--radius-sm);
}

.app-skeleton.block {
  border-radius: var(--radius-card);
}

.app-skeleton.circle {
  border-radius: var(--radius-full);
}

@keyframes app-skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-skeleton {
    animation: none;
  }
}
</style>
