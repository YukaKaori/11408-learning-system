<script setup lang="ts">
/**
 * Themed wrapper over ElDrawer — see AppDialog.vue for the rationale
 * (EP owns positioning/focus-trap; visuals come from element-theme.css).
 */
withDefaults(
  defineProps<{
    title?: string
    size?: string | number
    direction?: 'rtl' | 'ltr' | 'ttb' | 'btt'
    closeOnClickModal?: boolean
  }>(),
  {
    title: undefined,
    size: 360,
    direction: 'rtl',
    closeOnClickModal: true,
  },
)

const open = defineModel<boolean>({ default: false })
</script>

<template>
  <el-drawer
    v-model="open"
    :title="title"
    :size="size"
    :direction="direction"
    :close-on-click-modal="closeOnClickModal"
    class="app-drawer"
  >
    <template v-if="$slots.header" #header>
      <slot name="header" />
    </template>
    <slot />
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </el-drawer>
</template>
