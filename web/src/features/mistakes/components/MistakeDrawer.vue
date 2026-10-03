<script setup lang="ts">
/**
 * One mistake, opened: the question with the wrong answer marked, the
 * reference and 解析, every attempt so far, and the diagnosis — why it went
 * wrong, in the candidate's own words. The diagnosis is the point of a mistake
 * book: "粗心" and "概念不清" need different medicine, and the AI tutor reads
 * both the cause and the reflection when it diagnoses.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppDialog, AppDrawer, AppEmpty, AppSkeleton } from '@/components'
import {
  deleteMistake,
  getMistake,
  MISTAKE_CAUSES,
  reactivateMistake,
  resolveMistake,
  updateMistake,
  type MistakeCause,
  type MistakeDetailDto,
  type MistakeDto,
} from '@/api/modules/mistake'
import { streamMistakeDiagnosis } from '@/api/modules/ai'
import { toApiError, type ApiError } from '@/api/types'
import AiExplainPanel from '@/features/ai-tutor/AiExplainPanel.vue'
import QuestionContent from '@/features/questions/components/QuestionContent.vue'
import SolutionBlock from '@/features/questions/components/SolutionBlock.vue'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'
import { usePracticeLauncher } from '@/features/practice/usePracticeLauncher'

const props = defineProps<{ mistakeId: string | null }>()
const emit = defineEmits<{
  close: []
  changed: [mistake: MistakeDto]
  deleted: [id: string]
}>()

const { t, d } = useI18n()
const { launch, launching, errorKey: launchErrorKey } = usePracticeLauncher()

const detail = ref<MistakeDetailDto | null>(null)
const loading = ref(false)
const error = ref<ApiError | null>(null)

const cause = ref<MistakeCause | null>(null)
const note = ref('')
const saving = ref(false)
const actionErrorKey = ref<string | null>(null)
const diagnosing = ref(false)
const confirmDelete = ref(false)
const busy = ref<'resolve' | 'reactivate' | 'delete' | null>(null)

async function load(id: string) {
  loading.value = true
  error.value = null
  detail.value = null
  try {
    const loaded = await getMistake(id)
    if (props.mistakeId !== id) return
    detail.value = loaded
    cause.value = loaded.mistake.cause
    note.value = loaded.mistake.note ?? ''
  } catch (caught) {
    if (props.mistakeId === id) error.value = toApiError(caught)
  } finally {
    if (props.mistakeId === id) loading.value = false
  }
}

watch(
  () => props.mistakeId,
  (id) => {
    diagnosing.value = false
    actionErrorKey.value = null
    if (id) void load(id)
  },
  { immediate: true },
)

const mistake = computed(() => detail.value?.mistake ?? null)
const question = computed(() => mistake.value?.question ?? null)
const isChoice = computed(() => question.value?.type === 'single_choice' || question.value?.type === 'multi_choice')

/** Attempts arrive oldest first; the history reads newest first. */
const newestFirst = computed(() => [...(detail.value?.attempts ?? [])].reverse())

/** The most recent answer that was not correct — what the mistake is about. */
const lastWrong = computed(() => newestFirst.value.find((attempt) => attempt.result !== 'correct') ?? null)

const dirty = computed(
  () => !!mistake.value && (cause.value !== mistake.value.cause || note.value.trim() !== (mistake.value.note ?? '')),
)

function apply(updated: MistakeDto) {
  if (detail.value) detail.value = { ...detail.value, mistake: updated }
  emit('changed', updated)
}

async function saveDiagnosis() {
  const current = mistake.value
  if (!current || saving.value) return
  saving.value = true
  actionErrorKey.value = null
  try {
    apply(await updateMistake(current.id, { cause: cause.value ?? '', note: note.value.trim() }))
  } catch (caught) {
    actionErrorKey.value = toApiError(caught).messageKey
  } finally {
    saving.value = false
  }
}

async function run(kind: 'resolve' | 'reactivate') {
  const current = mistake.value
  if (!current || busy.value) return
  busy.value = kind
  actionErrorKey.value = null
  try {
    apply(kind === 'resolve' ? await resolveMistake(current.id) : await reactivateMistake(current.id))
  } catch (caught) {
    actionErrorKey.value = toApiError(caught).messageKey
  } finally {
    busy.value = null
  }
}

async function remove() {
  const current = mistake.value
  if (!current || busy.value) return
  busy.value = 'delete'
  try {
    await deleteMistake(current.id)
    confirmDelete.value = false
    emit('deleted', current.id)
  } catch (caught) {
    actionErrorKey.value = toApiError(caught).messageKey
  } finally {
    busy.value = null
  }
}

function runDiagnosis(signal: AbortSignal) {
  return streamMistakeDiagnosis(props.mistakeId ?? '', signal)
}

function practisePoint(code: string) {
  void launch({ mode: 'topic', nodeCode: code }, code)
}
</script>

<template>
  <AppDrawer
    :model-value="mistakeId !== null"
    :title="t('mistakes.detail.title')"
    size="min(680px, 100vw)"
    @update:model-value="(open) => { if (!open) emit('close') }"
  >
    <AppSkeleton v-if="loading" :lines="8" />
    <AppEmpty v-else-if="error" icon="alert-circle" :title="t(error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="mistakeId && load(mistakeId)">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>

    <div v-else-if="detail && mistake && question" class="body">
      <p class="status">
        <span class="state" :class="mistake.status">{{ t(`mistakes.status.${mistake.status}`) }}</span>
        <template v-if="mistake.status === 'active'">
          <span>{{ t('mistakes.streak', { streak: mistake.correctStreak, goal: mistake.resolveStreak }) }}</span>
          <span :class="{ due: mistake.due }">
            {{ mistake.due ? t('mistakes.dueToday') : t('mistakes.dueOn', { date: d(mistake.dueAt, 'short') }) }}
          </span>
        </template>
        <span v-else-if="mistake.resolvedAt">{{ t('mistakes.resolvedOn', { date: d(mistake.resolvedAt, 'short') }) }}</span>
        <span>{{ t('mistakes.wrongCount', { n: mistake.wrongCount }) }}</span>
      </p>

      <div class="points">
        <span v-for="code in question.points" :key="code" class="point">
          <RouterLink :to="{ name: 'syllabus-node', params: { code } }" class="point-link" @click="emit('close')">
            <NodeChip :code="code" />
          </RouterLink>
          <AppButton
            size="sm"
            variant="ghost"
            :loading="launching === code"
            :disabled="launching !== null"
            @click="practisePoint(code)"
          >
            {{ t('mistakes.detail.practisePoint') }}
          </AppButton>
        </span>
      </div>
      <p v-if="launchErrorKey" class="error" role="alert">{{ t(launchErrorKey) }}</p>

      <QuestionContent
        :question="question"
        :model-value="isChoice ? (lastWrong?.response ?? '') : ''"
        :correct="isChoice ? detail.solution.answer : null"
      />

      <div v-if="!isChoice && lastWrong?.response" class="given">
        <span class="label">{{ t('mistakes.detail.yourAnswer') }}</span>
        <p class="given-text">{{ lastWrong.response }}</p>
      </div>

      <SolutionBlock :solution="detail.solution" :type="question.type" />

      <section class="diagnosis" :aria-label="t('mistakes.detail.diagnosis')">
        <h3 class="section-title">{{ t('mistakes.detail.diagnosis') }}</h3>
        <div class="causes" role="radiogroup" :aria-label="t('mistakes.detail.cause')">
          <button
            v-for="code in MISTAKE_CAUSES"
            :key="code"
            type="button"
            role="radio"
            class="cause"
            :class="{ active: cause === code }"
            :aria-checked="cause === code"
            @click="cause = cause === code ? null : code"
          >
            {{ t(`mistakes.cause.${code}`) }}
          </button>
        </div>
        <label class="label" for="mistake-note">{{ t('mistakes.detail.note') }}</label>
        <textarea
          id="mistake-note"
          v-model="note"
          class="note"
          rows="3"
          :placeholder="t('mistakes.detail.notePlaceholder')"
        ></textarea>
        <div class="row">
          <AppButton size="sm" :disabled="!dirty" :loading="saving" @click="saveDiagnosis">
            {{ t('common.save') }}
          </AppButton>
          <AppButton
            v-if="!diagnosing"
            size="sm"
            variant="soft"
            tone="secondary"
            icon-left="sparkles"
            @click="diagnosing = true"
          >
            {{ t('mistakes.detail.aiDiagnose') }}
          </AppButton>
        </div>
      </section>

      <AiExplainPanel
        v-if="diagnosing"
        :title="t('mistakes.detail.aiDiagnoseTitle')"
        :run="runDiagnosis"
        @close="diagnosing = false"
      />

      <section class="history">
        <h3 class="section-title">{{ t('mistakes.detail.history') }}</h3>
        <ol class="attempts">
          <li v-for="(attempt, index) in newestFirst" :key="index" class="attempt">
            <span class="attempt-result" :class="attempt.result">{{ t(`practice.result.${attempt.result}`) }}</span>
            <span class="attempt-date">{{ d(attempt.attemptedAt, 'long') }}</span>
            <span v-if="attempt.captured" class="attempt-tag">{{ t('mistakes.detail.captured') }}</span>
            <span v-else-if="attempt.selfGraded" class="attempt-tag">{{ t('practice.selfGraded') }}</span>
            <span v-if="attempt.response" class="attempt-response">{{ attempt.response }}</span>
          </li>
        </ol>
      </section>

      <p v-if="actionErrorKey" class="error" role="alert">{{ t(actionErrorKey) }}</p>

      <footer class="footer">
        <AppButton
          v-if="mistake.status === 'active'"
          variant="soft"
          tone="success"
          icon-left="check"
          :loading="busy === 'resolve'"
          :disabled="busy !== null"
          @click="run('resolve')"
        >
          {{ t('mistakes.detail.resolve') }}
        </AppButton>
        <AppButton
          v-else
          variant="soft"
          icon-left="rotate-ccw"
          :loading="busy === 'reactivate'"
          :disabled="busy !== null"
          @click="run('reactivate')"
        >
          {{ t('mistakes.detail.reactivate') }}
        </AppButton>
        <AppButton variant="ghost" tone="danger" icon-left="trash" :disabled="busy !== null" @click="confirmDelete = true">
          {{ t('common.delete') }}
        </AppButton>
      </footer>
    </div>

    <AppDialog v-model="confirmDelete" :title="t('mistakes.deleteConfirm.title')" width="420px">
      <p>{{ t('mistakes.deleteConfirm.body') }}</p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="confirmDelete = false">{{ t('common.cancel') }}</AppButton>
        <AppButton tone="danger" :loading="busy === 'delete'" @click="remove">{{ t('common.delete') }}</AppButton>
      </template>
    </AppDialog>
  </AppDrawer>
</template>

<style scoped>
.body {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-4);
  margin: 0;
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.state {
  padding: 1px var(--space-2);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 600;
}

.state.active {
  background-color: var(--color-danger-soft);
  color: var(--color-danger);
}

.state.resolved {
  background-color: var(--color-success-soft);
  color: var(--color-success);
}

.due {
  color: var(--color-danger);
  font-weight: 600;
}

.points {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.point {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.point-link {
  display: inline-flex;
  min-width: 0;
  text-decoration: none;
}

.point-link:hover :deep(.path) {
  color: var(--color-primary);
  text-decoration: underline;
}

.given {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.given-text {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-left: 3px solid var(--color-danger);
  background-color: var(--color-surface-hover);
  white-space: pre-wrap;
  color: var(--color-text);
}

.section-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.diagnosis {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.causes {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.cause {
  padding: var(--space-1) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-full);
  background-color: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
}

.cause:hover {
  color: var(--color-text);
}

.cause:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.cause.active {
  border-color: var(--color-primary);
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 500;
}

.note {
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  resize: vertical;
}

.note:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.history {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.attempts {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.attempt {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-3);
  font-size: var(--text-sm);
}

.attempt-result {
  font-weight: 600;
}

.attempt-result.correct {
  color: var(--color-success);
}

.attempt-result.partial {
  color: var(--color-warning);
}

.attempt-result.wrong {
  color: var(--color-danger);
}

.attempt-date,
.attempt-tag {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.attempt-response {
  flex-basis: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-2);
  padding-top: var(--space-2);
  border-top: var(--border-width-sm) solid var(--color-border);
}
</style>
