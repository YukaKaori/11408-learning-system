<script setup lang="ts">
/**
 * Themed wrapper over ElDialog — positioning, focus-trap and Esc handling
 * are Element Plus's; visuals come from the .el-dialog rules in
 * styles/element-theme.css (glass surface, token-driven radius/shadow).
 */
withDefaults(
  defineProps<{
    title?: string
    width?: string | number
    closeOnClickModal?: boolean
    showClose?: boolean
  }>(),
  {
    title: undefined,
    width: 480,
    closeOnClickModal: true,
    showClose: true,
  },
)

const open = defineModel<boolean>({ default: false })
</script>

<template>
  <el-dialog
    v-model="open"
    :title="title"
    :width="width"
    :close-on-click-modal="closeOnClickModal"
    :show-close="showClose"
    align-center
    class="app-dialog"
  >
    <template v-if="$slots.header" #header>
      <slot name="header" />
    </template>
    <slot />
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </el-dialog>
</template>
