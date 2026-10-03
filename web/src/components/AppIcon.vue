<script setup lang="ts">
import { computed } from 'vue'
import { iconRegistry, type IconName } from './icons/registry'
import type { Size } from './types'

const props = withDefaults(
  defineProps<{
    name: IconName
    size?: Size | number
    strokeWidth?: number
    /** Defaults to `currentColor` so icons inherit surrounding text color. */
    color?: string
    /** Set when the icon is purely decorative (default). Provide a label to expose it to AT. */
    label?: string
  }>(),
  {
    // eslint-disable-next-line vue/require-valid-default-prop -- `Size | number` union isn't resolved across the type-only import
    size: 'md',
    strokeWidth: 1.75,
    color: 'currentColor',
    label: undefined,
  },
)

const sizeMap: Record<Size, number> = { sm: 14, md: 18, lg: 22 }

const pixelSize = computed(() => (typeof props.size === 'number' ? props.size : sizeMap[props.size]))
const icon = computed(() => iconRegistry[props.name])
</script>

<template>
  <component
    :is="icon"
    :size="pixelSize"
    :stroke-width="strokeWidth"
    :color="color"
    :aria-hidden="label ? undefined : 'true'"
    :aria-label="label"
    :role="label ? 'img' : undefined"
    focusable="false"
  />
</template>
