<script setup lang="ts">
/**
 * A syllabus anchor, shown compactly: the paper's accent, its short name, and
 * the last one or two levels of the path — the full path is in the tooltip
 * text. Solid and quiet: it labels content, it is not content.
 */
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSyllabusStore } from '@/stores/syllabus'
import { paperAccentColor } from '../papers'
import { subjectOfCode } from '../syllabusIndex'

const props = withDefaults(
  defineProps<{
    code: string | null | undefined
    /** Show the paper's short name before the path. */
    showPaper?: boolean
  }>(),
  { showPaper: true },
)

const { t } = useI18n()
const syllabusStore = useSyllabusStore()

// Loads once per session and coalesces, so every chip may ask: a page opened
// directly (the mistake book, a practice set) must show names, not codes.
onMounted(() => {
  void syllabusStore.load()
})

const paper = computed(() => subjectOfCode(props.code))
const node = computed(() => syllabusStore.node(props.code))
const isPaper = computed(() => node.value?.kind === 'subject')
const short = computed(() => (isPaper.value ? '' : syllabusStore.shortLabel(props.code)))
const full = computed(() => {
  const paperName = paper.value ? t(`exam.papers.${paper.value}`) : ''
  const rest = isPaper.value ? '' : syllabusStore.label(props.code)
  return [paperName, rest].filter(Boolean).join(' › ')
})
</script>

<template>
  <span v-if="code" class="node-chip" :title="full">
    <span class="dot" :style="{ backgroundColor: paperAccentColor(paper) }" aria-hidden="true"></span>
    <span v-if="showPaper && paper" class="paper">{{ t(`exam.papers.${paper}`) }}</span>
    <span v-if="short" class="path">{{ short }}</span>
  </span>
</template>

<style scoped>
.node-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-width: 0;
  max-width: 100%;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
}

.paper {
  flex-shrink: 0;
  font-weight: 500;
  color: var(--color-text);
}

.path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
