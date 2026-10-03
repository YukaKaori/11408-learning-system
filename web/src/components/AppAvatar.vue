<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import type { Size } from './types'

const props = withDefaults(
  defineProps<{
    src?: string
    name?: string
    size?: Size | number
    /** Accessible name; falls back to `name` when omitted. */
    alt?: string
  }>(),
  {
    src: undefined,
    name: undefined,
    // eslint-disable-next-line vue/require-valid-default-prop -- `Size | number` union isn't resolved across the type-only import
    size: 'md',
    alt: undefined,
  },
)

const sizeMap: Record<Size, number> = { sm: 24, md: 32, lg: 44 }
const pixelSize = computed(() => (typeof props.size === 'number' ? props.size : sizeMap[props.size]))

const initials = computed(() => {
  if (!props.name) return ''
  const parts = props.name.trim().split(/\s+/)
  const chars = parts.length > 1 ? [parts[0]![0], parts[parts.length - 1]![0]] : [parts[0]![0]]
  return chars.join('').toUpperCase()
})
</script>

<template>
  <span
    class="app-avatar"
    :style="{ width: `${pixelSize}px`, height: `${pixelSize}px`, fontSize: `${pixelSize * 0.4}px` }"
    :role="src || name ? 'img' : undefined"
    :aria-label="alt ?? name"
  >
    <img v-if="src" :src="src" :alt="alt ?? name ?? ''" />
    <span v-else-if="initials">{{ initials }}</span>
    <AppIcon v-else name="user" :size="Math.round(pixelSize * 0.55)" />
  </span>
</template>

<style scoped>
.app-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: var(--radius-full);
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
  user-select: none;
}

.app-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
