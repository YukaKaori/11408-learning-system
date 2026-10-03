<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import type { IconName } from './icons/registry'
import type { ButtonVariant, Size, Tone } from './types'

withDefaults(
  defineProps<{
    variant?: ButtonVariant
    tone?: Tone
    size?: Size
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
    loading?: boolean
    block?: boolean
    iconLeft?: IconName
    iconRight?: IconName
    /** Required when the button has no visible text (icon-only). */
    ariaLabel?: string
  }>(),
  {
    variant: 'solid',
    tone: 'primary',
    size: 'md',
    type: 'button',
    disabled: false,
    loading: false,
    block: false,
    iconLeft: undefined,
    iconRight: undefined,
    ariaLabel: undefined,
  },
)

defineEmits<{ click: [MouseEvent] }>()
</script>

<template>
  <button
    class="app-button"
    :class="[`variant-${variant}`, `tone-${tone}`, `size-${size}`, { block, loading }]"
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
    :aria-label="ariaLabel"
    @click="(e) => $emit('click', e)"
  >
    <AppIcon v-if="loading" name="loader" class="spin" :size="size" />
    <AppIcon v-else-if="iconLeft" :name="iconLeft" :size="size" />
    <span v-if="$slots.default" class="label"><slot /></span>
    <AppIcon v-if="!loading && iconRight" :name="iconRight" :size="size" />
  </button>
</template>

<style scoped>
.app-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: var(--border-width-sm) solid transparent;
  border-radius: var(--radius-button);
  font-family: var(--font-sans);
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.app-button:active:not(:disabled) {
  transform: scale(var(--motion-scale-press));
}

.app-button:disabled {
  cursor: not-allowed;
  opacity: var(--opacity-disabled);
}

.app-button.block {
  display: flex;
  width: 100%;
}

/* Sizes */
.size-sm {
  height: 28px;
  padding: 0 var(--space-3);
  font-size: var(--text-xs);
}
.size-md {
  height: 34px;
  padding: 0 var(--space-4);
  font-size: var(--text-sm);
}
.size-lg {
  height: 40px;
  padding: 0 var(--space-5);
  font-size: var(--text-base);
}

/* Solid */
.variant-solid.tone-primary {
  background-color: var(--color-primary);
  color: white;
  /* Interactive-emphasis halo — real value in dark mode only (light's token is zero-alpha). */
  box-shadow: var(--shadow-glow-primary);
}
.variant-solid.tone-primary:hover:not(:disabled) {
  background-color: var(--color-primary-hover);
}
.variant-solid.tone-primary:active:not(:disabled) {
  background-color: var(--color-primary-active);
}

.variant-solid.tone-secondary {
  background-color: var(--color-secondary);
  color: white;
}
.variant-solid.tone-secondary:hover:not(:disabled) {
  background-color: var(--color-secondary-hover);
}

.variant-solid.tone-success {
  background-color: var(--color-success);
  color: white;
}
.variant-solid.tone-warning {
  background-color: var(--color-warning);
  color: white;
}
.variant-solid.tone-danger {
  background-color: var(--color-danger);
  color: white;
}
.variant-solid.tone-info {
  background-color: var(--color-info);
  color: white;
}

/* Soft */
.variant-soft {
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
.variant-soft:hover:not(:disabled) {
  filter: brightness(0.97);
}

/* Outline */
.variant-outline {
  background-color: transparent;
  border-color: var(--color-border-strong);
  color: var(--color-text);
}
.variant-outline:hover:not(:disabled) {
  background-color: var(--color-surface-hover);
  border-color: var(--color-primary);
}

/* Ghost */
.variant-ghost {
  background-color: transparent;
  color: var(--color-text-secondary);
}
.variant-ghost:hover:not(:disabled) {
  background-color: var(--color-surface-hover);
  color: var(--color-text);
}

/* Plain — text-only, no chrome */
.variant-plain {
  background-color: transparent;
  color: var(--color-primary);
  padding-inline: var(--space-1);
  height: auto;
}
.variant-plain:hover:not(:disabled) {
  color: var(--color-primary-hover);
  text-decoration: underline;
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
