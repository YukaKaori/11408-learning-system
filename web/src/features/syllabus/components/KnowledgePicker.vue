<script setup lang="ts">
/**
 * The one way to anchor something in the syllabus — the successor of the
 * retired subject picker, shared by notes, decks, tasks, sessions, AI
 * conversations, materials and captured questions.
 *
 * A cascade paper → module → chapter → 考点. Most artifacts may anchor at any
 * depth (a textbook to all of 408, a note to one 考点); questions must be
 * tagged with 考点, which `leafOnly` enforces — the same rule the server's
 * `QuestionRules` applies, so the picker cannot offer an answer the server
 * would reject. `null` means "not anchored"; clearing emits `null` (callers
 * translate that to the wire's `''` clear sentinel where needed).
 */
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ExamSubjectCode } from '@/api/modules/exam'
import { useSyllabusStore } from '@/stores/syllabus'
import { cascaderOptions } from '../syllabusIndex'

const props = withDefaults(
  defineProps<{
    modelValue: string | null
    /** Only 考点 are selectable (question tagging). */
    leafOnly?: boolean
    /** Restrict the cascade to one paper. */
    paper?: ExamSubjectCode | null
    size?: 'small' | 'default'
    placeholder?: string
  }>(),
  {
    leafOnly: false,
    paper: null,
    size: 'default',
    placeholder: undefined,
  },
)

const emit = defineEmits<{ 'update:modelValue': [code: string | null] }>()

const { t } = useI18n()
const syllabusStore = useSyllabusStore()

onMounted(() => {
  void syllabusStore.load()
})

const options = computed(() => {
  const index = syllabusStore.index
  if (!index) return []
  const all = cascaderOptions(index, (code) => t(`exam.papers.${code}`))
  return props.paper ? all.filter((option) => option.value === props.paper) : all
})

const cascaderProps = computed(() => ({
  checkStrictly: !props.leafOnly,
  emitPath: false,
  expandTrigger: 'hover' as const,
}))

function onChange(value: unknown) {
  emit('update:modelValue', typeof value === 'string' && value ? value : null)
}
</script>

<template>
  <el-cascader
    :model-value="modelValue ?? undefined"
    :options="options"
    :props="cascaderProps"
    :size="size"
    :placeholder="placeholder ?? t(leafOnly ? 'syllabus.picker.pointPlaceholder' : 'syllabus.picker.placeholder')"
    :disabled="syllabusStore.loading && !syllabusStore.loaded"
    separator=" › "
    filterable
    clearable
    class="knowledge-picker"
    @update:model-value="onChange"
  />
</template>

<style scoped>
.knowledge-picker {
  width: 100%;
}
</style>
