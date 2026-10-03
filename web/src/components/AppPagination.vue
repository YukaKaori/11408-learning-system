<script setup lang="ts">
import { computed } from 'vue'

/** Themed wrapper over ElPagination — see AppDialog.vue for the rationale. */
const props = withDefaults(
  defineProps<{
    total: number
    pageSize?: number
    pageSizes?: number[]
    showSizeChanger?: boolean
    showTotal?: boolean
  }>(),
  {
    pageSize: 20,
    pageSizes: () => [10, 20, 50, 100],
    showSizeChanger: false,
    showTotal: true,
  },
)

const emit = defineEmits<{ 'update:pageSize': [number] }>()

const currentPage = defineModel<number>('currentPage', { default: 1 })

// EP's own locale (wired via ElConfigProvider in App.vue) formats the "total" segment.
const layout = computed(() => {
  const parts = ['prev', 'pager', 'next']
  if (props.showSizeChanger) parts.push('sizes')
  if (props.showTotal) parts.unshift('total')
  return parts.join(', ')
})
</script>

<template>
  <el-pagination
    v-model:current-page="currentPage"
    :page-size="pageSize"
    :page-sizes="pageSizes"
    :total="total"
    :layout="layout"
    background
    @update:page-size="(size: number) => emit('update:pageSize', size)"
  />
</template>
