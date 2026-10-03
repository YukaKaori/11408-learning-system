<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import AppIcon from './AppIcon.vue'
import type { IconName } from './icons/registry'
import type { Size } from './types'

const props = withDefaults(
  defineProps<{
    type?: 'text' | 'password' | 'email' | 'search' | 'number'
    size?: Size
    placeholder?: string
    disabled?: boolean
    invalid?: boolean
    clearable?: boolean
    iconLeft?: IconName
    label?: string
    /**
     * Accessible name for the control itself, for fields whose meaning is
     * carried by their surroundings rather than a visible `label` (a document
     * title that *is* the heading, a bare search box). Bound to the `<input>`,
     * not the wrapper — a bare `aria-label` attribute would land on the root
     * element and name nothing.
     */
    ariaLabel?: string
    errorMessage?: string
    autocomplete?: string
  }>(),
  {
    type: 'text',
    size: 'md',
    placeholder: undefined,
    disabled: false,
    invalid: false,
    clearable: false,
    iconLeft: undefined,
    label: undefined,
    ariaLabel: undefined,
    errorMessage: undefined,
    autocomplete: undefined,
  },
)

/**
 * Focus events are **declared**, not left to attribute fallthrough.
 *
 * `blur` and `focus` do not bubble, so an undeclared `@blur` on this component
 * would be attached to the wrapper `<div>` and never fire — the listener looks
 * wired at the call site and silently does nothing. (Phase 16's note-title
 * save was lost exactly this way.) Declaring them re-emits from the `<input>`
 * itself, which is the element that actually focuses.
 */
const emit = defineEmits<{
  blur: [event: FocusEvent]
  focus: [event: FocusEvent]
}>()

const model = defineModel<string | number>({ default: '' })

const { t } = useI18n()
const inputId = useId()
const errorId = useId()
const showPassword = ref(false)

const resolvedType = computed(() => {
  if (props.type !== 'password') return props.type
  return showPassword.value ? 'text' : 'password'
})

function clear() {
  model.value = ''
}
</script>

<template>
  <div class="app-input-field">
    <label v-if="label" :for="inputId" class="field-label">{{ label }}</label>
    <div
      class="app-input"
      :class="[`size-${size}`, { disabled, invalid }]"
    >
      <AppIcon v-if="iconLeft" :name="iconLeft" size="sm" class="affix leading" />
      <input
        :id="inputId"
        v-model="model"
        :type="resolvedType"
        :placeholder="placeholder"
        :disabled="disabled"
        :autocomplete="autocomplete"
        :aria-label="ariaLabel"
        :aria-invalid="invalid || undefined"
        :aria-describedby="errorMessage ? errorId : undefined"
        @blur="emit('blur', $event)"
        @focus="emit('focus', $event)"
      />
      <button
        v-if="type === 'password'"
        type="button"
        class="affix trailing icon-button"
        :aria-label="showPassword ? t('ds.input.hidePassword') : t('ds.input.showPassword')"
        @click="showPassword = !showPassword"
      >
        <AppIcon :name="showPassword ? 'eye-off' : 'eye'" size="sm" />
      </button>
      <button
        v-else-if="clearable && String(model).length > 0"
        type="button"
        class="affix trailing icon-button"
        :aria-label="t('ds.input.clear')"
        @click="clear"
      >
        <AppIcon name="close" size="sm" />
      </button>
    </div>
    <p v-if="errorMessage" :id="errorId" class="field-error" role="alert">{{ errorMessage }}</p>
  </div>
</template>

<style scoped>
.app-input-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.field-label {
  font-size: var(--font-label-size);
  font-weight: var(--font-label-weight);
  color: var(--color-text-secondary);
}

.app-input {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  background-color: var(--color-surface);
  padding-inline: var(--space-3);
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.app-input:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.app-input.invalid {
  border-color: var(--color-danger);
}
.app-input.invalid:focus-within {
  box-shadow: 0 0 0 3px var(--color-danger-soft);
}

.app-input.disabled {
  opacity: var(--opacity-disabled);
  pointer-events: none;
}

.app-input input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-sans);
  color: var(--color-text);
}

.app-input input::placeholder {
  color: var(--color-text-tertiary);
}

.size-sm {
  height: 28px;
  font-size: var(--text-xs);
}
.size-md {
  height: 34px;
  font-size: var(--text-sm);
}
.size-lg {
  height: 40px;
  font-size: var(--text-base);
}

.affix {
  flex-shrink: 0;
  display: flex;
  color: var(--color-text-tertiary);
}

.icon-button {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: var(--color-text-tertiary);
  transition: color var(--duration-fast) var(--ease-out);
}
.icon-button:hover {
  color: var(--color-text);
}

.field-error {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-danger);
}
</style>
