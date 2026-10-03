<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AppIcon from './AppIcon.vue'
import type { Size, Tone } from './types'

withDefaults(
  defineProps<{
    tone?: Tone
    size?: Size
    removable?: boolean
    disabled?: boolean
  }>(),
  {
    tone: 'secondary',
    size: 'md',
    removable: false,
    disabled: false,
  },
)

const emit = defineEmits<{ remove: [] }>()
const { t } = useI18n()
</script>

<template>
  <span class="app-tag" :class="[`tone-${tone}`, `size-${size}`, { disabled }]">
    <slot />
    <button
      v-if="removable"
      type="button"
      class="remove"
      :disabled="disabled"
      :aria-label="t('ds.tag.remove')"
      @click="emit('remove')"
    >
      <AppIcon name="close" size="sm" />
    </button>
  </span>
</template>

<style scoped>
.app-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  border-radius: var(--radius-sm);
  font-weight: 500;
  line-height: 1;
}

.app-tag.disabled {
  opacity: var(--opacity-disabled);
}

.size-sm {
  padding: var(--space-0-5) var(--space-2);
  font-size: var(--text-xs);
}
.size-md {
  padding: var(--space-1) var(--space-3);
  font-size: var(--text-sm);
}

.tone-primary {
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
}
.tone-secondary {
  background-color: var(--color-secondary-soft);
  color: var(--color-secondary);
}
.tone-success {
  background-color: var(--color-success-soft);
  color: var(--color-success);
}
.tone-warning {
  background-color: var(--color-warning-soft);
  color: var(--color-warning);
}
.tone-danger {
  background-color: var(--color-danger-soft);
  color: var(--color-danger);
}
.tone-info {
  background-color: var(--color-info-soft);
  color: var(--color-info);
}

.remove {
  display: inline-flex;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: inherit;
  opacity: 0.7;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.remove:hover:not(:disabled) {
  opacity: 1;
}
.remove:disabled {
  cursor: not-allowed;
}
</style>
