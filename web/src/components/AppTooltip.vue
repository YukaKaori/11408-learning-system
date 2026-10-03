<script setup lang="ts">
/** Themed wrapper over ElTooltip — see AppDialog.vue for the rationale. */
withDefaults(
  defineProps<{
    content?: string
    placement?:
      | 'top'
      | 'top-start'
      | 'top-end'
      | 'bottom'
      | 'bottom-start'
      | 'bottom-end'
      | 'left'
      | 'right'
    disabled?: boolean
    showAfter?: number
  }>(),
  {
    content: undefined,
    placement: 'top',
    disabled: false,
    showAfter: 200,
  },
)
</script>

<template>
  <el-tooltip
    :content="content"
    :placement="placement"
    :disabled="disabled || !content"
    :show-after="showAfter"
    effect="dark"
  >
    <template v-if="$slots.content" #content>
      <slot name="content" />
    </template>
    <slot />
  </el-tooltip>
</template>
