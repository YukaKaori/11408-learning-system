<script setup lang="ts">
/**
 * One question from the bank, opened for study outside a practice set: the
 * question, the 考点 it tests, the candidate's history with it, and — on
 * request, never by default — the answer, the 解析 and the AI tutor's
 * walk-through. Browsing must not spoil a question the candidate may still
 * meet in practice, so the answer is one deliberate click away.
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppDrawer, AppEmpty, AppSkeleton } from '@/components'
import { getQuestion, type QuestionDetailDto } from '@/api/modules/question'
import { streamQuestionExplain } from '@/api/modules/ai'
import { toApiError, type ApiError } from '@/api/types'
import AiExplainPanel from '@/features/ai-tutor/AiExplainPanel.vue'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'
import QuestionContent from './QuestionContent.vue'
import SolutionBlock from './SolutionBlock.vue'

const props = defineProps<{ questionId: string | null }>()
const emit = defineEmits<{ close: [] }>()

const { t, d } = useI18n()

const detail = ref<QuestionDetailDto | null>(null)
const loading = ref(false)
const error = ref<ApiError | null>(null)
const revealed = ref(false)
const explaining = ref(false)

async function load(id: string) {
  loading.value = true
  error.value = null
  detail.value = null
  try {
    const loaded = await getQuestion(id)
    if (props.questionId === id) detail.value = loaded
  } catch (caught) {
    if (props.questionId === id) error.value = toApiError(caught)
  } finally {
    if (props.questionId === id) loading.value = false
  }
}

watch(
  () => props.questionId,
  (id) => {
    revealed.value = false
    explaining.value = false
    if (id) void load(id)
  },
  { immediate: true },
)

function runExplain(signal: AbortSignal) {
  return streamQuestionExplain(props.questionId ?? '', null, signal)
}

function openExplain() {
  explaining.value = true
  revealed.value = true
}

function isChoice(detail: QuestionDetailDto): boolean {
  return detail.question.type === 'single_choice' || detail.question.type === 'multi_choice'
}
</script>

<template>
  <AppDrawer
    :model-value="questionId !== null"
    :title="t('question.drawerTitle')"
    size="min(640px, 100vw)"
    @update:model-value="(open) => { if (!open) emit('close') }"
  >
    <AppSkeleton v-if="loading" :lines="6" />
    <AppEmpty v-else-if="error" icon="alert-circle" :title="t(error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="questionId && load(questionId)">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>
    <div v-else-if="detail" class="body">
      <div class="points">
        <RouterLink
          v-for="code in detail.question.points"
          :key="code"
          :to="{ name: 'syllabus-node', params: { code } }"
          class="point-link"
          @click="emit('close')"
        >
          <NodeChip :code="code" />
        </RouterLink>
      </div>

      <QuestionContent
        :question="detail.question"
        :correct="revealed && isChoice(detail) ? detail.solution.answer : null"
      />

      <p class="history">
        <template v-if="detail.history.attempts > 0">
          {{
            t('question.history', {
              attempts: detail.history.attempts,
              correct: detail.history.correct,
            })
          }}
          <span v-if="detail.history.lastAttemptedAt">
            · {{ t('question.lastAttempt', { date: d(detail.history.lastAttemptedAt, 'short') }) }}
          </span>
        </template>
        <template v-else>{{ t('question.neverAttempted') }}</template>
      </p>

      <div class="actions">
        <AppButton
          v-if="!revealed"
          variant="soft"
          icon-left="eye"
          @click="revealed = true"
        >
          {{ t('question.reveal') }}
        </AppButton>
        <AppButton
          v-if="!explaining"
          variant="soft"
          tone="secondary"
          icon-left="sparkles"
          @click="openExplain"
        >
          {{ t('question.explain') }}
        </AppButton>
      </div>

      <SolutionBlock v-if="revealed" :solution="detail.solution" :type="detail.question.type" />

      <AiExplainPanel
        v-if="explaining"
        :title="t('question.explainTitle')"
        :run="runExplain"
        @close="explaining = false"
      />
    </div>
  </AppDrawer>
</template>

<style scoped>
.body {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.points {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
}

.point-link {
  display: inline-flex;
  min-width: 0;
  max-width: 100%;
  text-decoration: none;
}

.point-link:hover :deep(.path) {
  color: var(--color-primary);
  text-decoration: underline;
}

.history {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
</style>
