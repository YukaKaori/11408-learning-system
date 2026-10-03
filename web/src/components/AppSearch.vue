<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AppIcon from './AppIcon.vue'
import type { Size } from './types'

withDefaults(
  defineProps<{
    size?: Size
    placeholder?: string
    /** Accessible label for the search field. */
    label?: string
  }>(),
  {
    size: 'md',
    placeholder: undefined,
    label: undefined,
  },
)

const model = defineModel<string>({ default: '' })
const emit = defineEmits<{ submit: [string] }>()
const { t } = useI18n()

function clear() {
  model.value = ''
}
</script>

<template>
  <form
    class="app-search"
    :class="`size-${size}`"
    role="search"
    @submit.prevent="emit('submit', model)"
  >
    <AppIcon name="search" size="sm" class="icon" aria-hidden="true" />
    <input
      v-model="model"
      type="search"
      :placeholder="placeholder ?? t('ds.search.placeholder')"
      :aria-label="label ?? t('ds.search.placeholder')"
    />
    <button
      v-if="model.length > 0"
      type="button"
      class="clear"
      :aria-label="t('ds.search.clear')"
      @click="clear"
    >
      <AppIcon name="close" size="sm" />
    </button>
  </form>
</template>

<style scoped>
.app-search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-full);
  background-color: var(--color-surface);
  padding-inline: var(--space-3);
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.app-search:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.icon {
  flex-shrink: 0;
  color: var(--color-text-tertiary);
}

.app-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-sans);
  color: var(--color-text);
}

.app-search input::placeholder {
  color: var(--color-text-tertiary);
}

.app-search input::-webkit-search-cancel-button {
  display: none;
}

.clear {
  flex-shrink: 0;
  display: flex;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: var(--color-text-tertiary);
  transition: color var(--duration-fast) var(--ease-out);
}
.clear:hover {
  color: var(--color-text);
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
</style>
