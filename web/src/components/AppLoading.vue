<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AppIcon from './AppIcon.vue'
import type { Size } from './types'

withDefaults(
  defineProps<{
    size?: Size
    label?: string
    /** Render inline with surrounding content instead of centered in a block. */
    inline?: boolean
  }>(),
  {
    size: 'md',
    label: undefined,
    inline: false,
  },
)

const { t } = useI18n()
</script>

<template>
  <span class="app-loading" :class="{ inline }" role="status" :aria-label="label ?? t('common.loading')">
    <AppIcon name="loader" :size="size" class="spin" aria-hidden="true" />
    <span v-if="label" class="label">{{ label }}</span>
  </span>
</template>

<style scoped>
.app-loading {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-tertiary);
}

.app-loading:not(.inline) {
  justify-content: center;
  padding: var(--space-8);
}

.label {
  font-size: var(--text-sm);
}

.spin {
  animation: app-spin 0.8s linear infinite;
}

@keyframes app-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
