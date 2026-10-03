<script setup lang="ts">
/**
 * Starting the study timer: which paper (required — it is what the plan
 * counts the time against), optionally how deep (a chapter or a 考点), and an
 * optional label carried onto the recorded session ("2019 真题", "线代第三章").
 *
 * Starting while a timer runs is a switch: the running one is saved first.
 * The dialog says so before the candidate presses the button, with the time
 * that is about to be saved, so the switch is never a surprise.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppDialog, AppIcon } from '@/components'
import type { ExamSubjectCode } from '@/api/modules/exam'
import { useFocusStore } from '@/stores/focus'
import KnowledgePicker from '@/features/syllabus/components/KnowledgePicker.vue'
import { PAPER_ICON, PAPERS, paperAccentColor } from '@/features/syllabus/papers'
import { subjectOfCode } from '@/features/syllabus/syllabusIndex'
import { useDuration } from '@/composables/useDuration'

const open = defineModel<boolean>({ default: false })

const props = withDefaults(
  defineProps<{
    /** Preselect a node (a 考点 page starts the timer on itself). */
    initialNode?: string | null
    /** Preselect a paper (Today's band starts it on a paper). */
    initialPaper?: ExamSubjectCode | null
  }>(),
  { initialNode: null, initialPaper: null },
)

const { t } = useI18n()
const focusStore = useFocusStore()
const { formatMinutes } = useDuration()

const paper = ref<ExamSubjectCode | null>(null)
const node = ref<string | null>(null)
const title = ref('')

watch(open, (isOpen) => {
  if (!isOpen) return
  const fromNode = subjectOfCode(props.initialNode)
  paper.value = fromNode ?? props.initialPaper ?? null
  // A paper code itself is not "deeper" — only chapters and 考点 go in the picker.
  node.value = props.initialNode && props.initialNode !== fromNode ? props.initialNode : null
  title.value = ''
  focusStore.error = null
})

// A node from another paper cannot stay selected when the paper changes.
watch(paper, (code) => {
  if (node.value && subjectOfCode(node.value) !== code) node.value = null
})

const runningPaper = computed(() => subjectOfCode(focusStore.timer?.nodeCode))
const runningMinutes = computed(() => Math.floor(focusStore.elapsedSeconds / 60))

async function start() {
  if (!paper.value) return
  const ok = await focusStore.start({
    nodeCode: node.value ?? paper.value,
    title: title.value.trim() || null,
  })
  if (ok) open.value = false
}
</script>

<template>
  <AppDialog v-model="open" :title="t('focus.start.title')" width="min(520px, 96vw)">
    <div class="form">
      <div class="field">
        <span id="focus-paper-label" class="label">{{ t('focus.start.paper') }}</span>
        <div class="papers" role="radiogroup" aria-labelledby="focus-paper-label">
          <button
            v-for="code in PAPERS"
            :key="code"
            type="button"
            role="radio"
            class="paper"
            :class="{ active: paper === code }"
            :aria-checked="paper === code"
            :style="{ '--paper-accent': paperAccentColor(code) }"
            @click="paper = code"
          >
            <AppIcon :name="PAPER_ICON[code]" size="sm" class="paper-icon" />
            <span>{{ t(`exam.papers.${code}`) }}</span>
          </button>
        </div>
      </div>

      <div class="field">
        <span class="label">{{ t('focus.start.node') }}</span>
        <KnowledgePicker
          v-model="node"
          :paper="paper"
          size="default"
          :placeholder="t('focus.start.nodePlaceholder')"
        />
      </div>

      <div class="field">
        <label class="label" for="focus-title">{{ t('focus.start.label') }}</label>
        <input
          id="focus-title"
          v-model="title"
          class="input"
          type="text"
          maxlength="255"
          :placeholder="t('focus.start.labelPlaceholder')"
          @keydown.enter.prevent="start"
        />
      </div>

      <p v-if="focusStore.running" class="switch-note">
        <AppIcon name="info" size="sm" aria-hidden="true" />
        <span>
          {{
            t('focus.start.switchNote', {
              paper: runningPaper ? t(`exam.papers.${runningPaper}`) : t('focus.unclassified'),
              time: formatMinutes(runningMinutes),
            })
          }}
        </span>
      </p>
      <p v-if="focusStore.error" class="error" role="alert">{{ t(focusStore.error.messageKey) }}</p>
    </div>
    <template #footer>
      <AppButton variant="soft" tone="secondary" @click="open = false">{{
        t('common.cancel')
      }}</AppButton>
      <AppButton icon-left="play" :loading="focusStore.pending" :disabled="!paper" @click="start">
        {{ t(focusStore.running ? 'focus.start.switch' : 'focus.start.submit') }}
      </AppButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

.label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.papers {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
}

.paper {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 44px;
  padding: var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.paper:hover {
  color: var(--color-text);
}

.paper:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.paper-icon {
  color: var(--paper-accent);
}

.paper.active {
  border-color: var(--paper-accent);
  box-shadow: inset 0 0 0 1px var(--paper-accent);
  color: var(--color-text);
}

.input {
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

.input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.switch-note {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.switch-note :deep(svg) {
  flex-shrink: 0;
  margin-top: 3px;
}

.error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

@media (max-width: 480px) {
  .papers {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
