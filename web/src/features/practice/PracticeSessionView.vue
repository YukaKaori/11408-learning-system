<script setup lang="ts">
/**
 * The practice stage: one question at a time, answered for real and graded
 * honestly. Choice questions are graded at once; a fill-blank that does not
 * match an accepted form, and every open question, is compared by the
 * candidate against the reference answer (the server's two-step protocol) —
 * the system never marks an answer wrong that it could not actually judge.
 *
 * Each graded answer lands in the mistake book, the mastery model and the
 * day's ledger on the server; the stage reports what happened to the mistake
 * book in words ("已记入错题本，10月2日重做") so the loop is visible.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { AppButton, AppDialog, AppEmpty, AppIcon, AppSkeleton } from '@/components'
import {
  finishPractice,
  getPractice,
  getPracticeReport,
  submitAnswer,
  type AnswerResultDto,
  type PracticeReportDto,
  type PracticeSessionDto,
} from '@/api/modules/practice'
import type { AttemptResult, QuestionSolutionDto } from '@/api/modules/question'
import { streamQuestionExplain } from '@/api/modules/ai'
import { toApiError, type ApiError } from '@/api/types'
import AiExplainPanel from '@/features/ai-tutor/AiExplainPanel.vue'
import QuestionContent from '@/features/questions/components/QuestionContent.vue'
import SolutionBlock from '@/features/questions/components/SolutionBlock.vue'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'
import PracticeReport from './components/PracticeReport.vue'
import { usePracticeLauncher } from './usePracticeLauncher'

/** `PracticeErrorCode` SESSION_CLOSED / ALREADY_ANSWERED — the server moved on; reload. */
const STALE = new Set([230002, 230004])

const { t, d } = useI18n()
const route = useRoute()
const router = useRouter()
const { launch, launching, errorKey: launchErrorKey } = usePracticeLauncher()

const sessionId = computed(() => String(route.params.id))
const state = ref<PracticeSessionDto | null>(null)
const report = ref<PracticeReportDto | null>(null)
const loading = ref(true)
const loadError = ref<ApiError | null>(null)
const index = ref(0)

async function load() {
  const id = sessionId.value
  loading.value = true
  loadError.value = null
  report.value = null
  try {
    const loaded = await getPractice(id)
    if (id !== sessionId.value) return
    state.value = loaded
    if (loaded.session.status === 'completed') {
      report.value = await getPracticeReport(id)
    } else {
      const next = loaded.items.findIndex((item) => item.answer === null)
      index.value = next >= 0 ? next : 0
    }
  } catch (caught) {
    loadError.value = toApiError(caught)
  } finally {
    loading.value = false
  }
}

watch(sessionId, () => void load(), { immediate: true })

const session = computed(() => state.value?.session ?? null)
const items = computed(() => state.value?.items ?? [])
const item = computed(() => items.value[index.value] ?? null)
const question = computed(() => item.value?.question ?? null)
const answered = computed(() => item.value?.answer ?? null)
const isChoice = computed(() => question.value?.type === 'single_choice' || question.value?.type === 'multi_choice')
const allAnswered = computed(() => items.value.length > 0 && items.value.every((entry) => entry.answer !== null))

const heading = computed(() => {
  const current = session.value
  if (!current) return ''
  const mode = t(`practice.mode.${current.mode}`)
  return current.title ? `${mode} · ${current.title}` : mode
})

// --- The current question ----------------------------------------------------------

const draft = ref('')
const selfGrading = ref<{ solution: QuestionSolutionDto; response: string | null } | null>(null)
const outcome = ref<AnswerResultDto | null>(null)
const submitting = ref(false)
const submitErrorKey = ref<string | null>(null)
const explaining = ref(false)
let shownAt = Date.now()
let answeredAfter: number | null = null

watch(index, () => {
  draft.value = ''
  selfGrading.value = null
  outcome.value = null
  submitErrorKey.value = null
  explaining.value = false
  shownAt = Date.now()
  answeredAfter = null
})

/** The letters shown on the options: the draft, or — once answered — what was given. */
const selection = computed(() => (answered.value ? (answered.value.response ?? '') : draft.value))

const canSubmit = computed(() => {
  if (!question.value || answered.value || selfGrading.value) return false
  return question.value.type === 'open' || draft.value.trim().length > 0
})

async function submit(selfGrade?: AttemptResult, blank = false) {
  const current = item.value
  const id = sessionId.value
  if (!current || current.answer || submitting.value) return
  const response = selfGrade ? (selfGrading.value?.response ?? null) : blank ? null : draft.value.trim() || null
  if (answeredAfter === null) answeredAfter = Math.round((Date.now() - shownAt) / 1000)
  submitting.value = true
  submitErrorKey.value = null
  try {
    const result = await submitAnswer(id, {
      questionId: current.question.id,
      response: response ?? undefined,
      selfGrade,
      durationSeconds: Math.min(answeredAfter, 36000),
    })
    if (result.outcome === 'needs_self_grade') {
      selfGrading.value = { solution: result.solution, response }
      return
    }
    current.answer = {
      result: result.result ?? 'wrong',
      response,
      selfGraded: selfGrade !== undefined,
      solution: result.solution,
    }
    if (state.value) {
      state.value.session.answered = result.progress.answered
      state.value.session.correct = result.progress.correct
      if (result.progress.completed) state.value.session.status = 'completed'
    }
    outcome.value = result
    selfGrading.value = null
  } catch (caught) {
    const error = toApiError(caught)
    if (STALE.has(error.code)) {
      await load()
      return
    }
    submitErrorKey.value = error.messageKey
  } finally {
    submitting.value = false
  }
}

function next() {
  const after = items.value.findIndex((entry, i) => i > index.value && entry.answer === null)
  const before = items.value.findIndex((entry) => entry.answer === null)
  const target = after >= 0 ? after : before
  if (target >= 0) index.value = target
}

/** What this answer did to the mistake book, in words. */
const mistakeLine = computed(() => {
  const change = outcome.value?.mistake
  if (!change || change.change === 'none') return null
  const date = change.nextDueAt ? d(change.nextDueAt, 'short') : ''
  return t(`practice.mistake.${change.change}`, {
    date,
    streak: change.correctStreak,
    goal: change.resolveStreak,
  })
})

function runExplain(signal: AbortSignal) {
  return streamQuestionExplain(question.value?.id ?? '', answered.value?.response ?? null, signal)
}

// --- Finishing ---------------------------------------------------------------------

const finishing = ref(false)
const confirmFinish = ref(false)

async function finish() {
  if (finishing.value) return
  finishing.value = true
  confirmFinish.value = false
  try {
    report.value = await finishPractice(sessionId.value)
    if (state.value) state.value.session.status = 'completed'
  } catch (caught) {
    submitErrorKey.value = toApiError(caught).messageKey
  } finally {
    finishing.value = false
  }
}

function requestFinish() {
  if (allAnswered.value) void finish()
  else confirmFinish.value = true
}

function again() {
  const current = session.value
  if (!current) return
  void launch(
    { mode: current.mode, nodeCode: current.nodeCode ?? undefined, subject: current.subject ?? undefined },
    'again',
  )
}

function drill(nodeCode: string) {
  void launch({ mode: 'topic', nodeCode }, nodeCode)
}

// --- Keyboard ------------------------------------------------------------------------
// Letters pick options, Enter submits or moves on — only outside text fields,
// so typing an answer never triggers a shortcut.

function onKey(event: KeyboardEvent) {
  if (report.value || !question.value || event.metaKey || event.ctrlKey || event.altKey) return
  const target = event.target as HTMLElement | null
  if (target && (target.closest('input, textarea, select, [contenteditable="true"]') || target.closest('.el-dialog'))) {
    return
  }
  if (event.key === 'Enter') {
    if (answered.value) {
      if (!allAnswered.value) {
        event.preventDefault()
        next()
      }
    } else if (canSubmit.value) {
      event.preventDefault()
      void submit()
    }
    return
  }
  if (isChoice.value && !answered.value && /^[a-zA-Z]$/.test(event.key)) {
    const letter = event.key.toUpperCase()
    const position = letter.charCodeAt(0) - 65
    if (position >= question.value.options.length) return
    event.preventDefault()
    if (question.value.type === 'single_choice') {
      draft.value = draft.value === letter ? '' : letter
    } else {
      const chosen = new Set(draft.value.split('').filter(Boolean))
      if (chosen.has(letter)) chosen.delete(letter)
      else chosen.add(letter)
      draft.value = [...chosen].sort().join('')
    }
  }
}

onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

function resultTone(result: AttemptResult | undefined): string {
  return result === 'correct' ? 'success' : result === 'partial' ? 'warning' : 'danger'
}
</script>

<template>
  <div class="practice-session">
    <AppSkeleton v-if="loading && !state" :lines="10" />

    <AppEmpty v-else-if="loadError" icon="alert-circle" :title="t(loadError.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="load">{{ t('common.retry') }}</AppButton>
        <AppButton size="sm" variant="ghost" @click="router.push({ name: 'practice' })">
          {{ t('practice.backToPractice') }}
        </AppButton>
      </template>
    </AppEmpty>

    <template v-else-if="state && session">
      <header class="stage-head">
        <RouterLink :to="{ name: 'practice' }" class="back">
          <AppIcon name="arrow-left" size="sm" aria-hidden="true" />
          {{ t('practice.backToPractice') }}
        </RouterLink>
        <h1 class="title">{{ heading }}</h1>
        <p class="progress" aria-live="polite">
          {{ t('practice.progress', { answered: session.answered, total: session.total, correct: session.correct }) }}
        </p>
      </header>

      <p v-if="launchErrorKey" class="notice" role="alert">{{ t(launchErrorKey) }}</p>

      <PracticeReport v-if="report" :report="report" :launching="launching" @again="again" @drill="drill" />

      <template v-else>
        <nav class="navigator" :aria-label="t('practice.navigator')">
          <button
            v-for="(entry, i) in items"
            :key="entry.question.id"
            type="button"
            class="dot"
            :class="[entry.answer ? `dot-${entry.answer.result}` : 'dot-open', { current: i === index }]"
            :aria-current="i === index ? 'step' : undefined"
            :aria-label="
              t('practice.goTo', {
                n: i + 1,
                state: entry.answer ? t(`practice.result.${entry.answer.result}`) : t('practice.unanswered'),
              })
            "
            @click="index = i"
          >
            {{ i + 1 }}
          </button>
        </nav>

        <article v-if="item && question" class="stage">
          <div class="points">
            <NodeChip v-for="code in question.points" :key="code" :code="code" />
          </div>

          <QuestionContent
            :model-value="selection"
            :question="question"
            :number="index + 1"
            :selectable="isChoice && !answered"
            :correct="answered && isChoice ? answered.solution.answer : null"
            @update:model-value="(letters) => (draft = letters)"
          />

          <div v-if="!isChoice && !answered" class="response">
            <label class="response-label" :for="`response-${question.id}`">
              {{ t(question.type === 'fill_blank' ? 'practice.fillLabel' : 'practice.openLabel') }}
            </label>
            <input
              v-if="question.type === 'fill_blank'"
              :id="`response-${question.id}`"
              v-model="draft"
              class="response-input"
              type="text"
              autocomplete="off"
              :disabled="selfGrading !== null"
              :placeholder="t('practice.fillPlaceholder')"
              @keydown.enter.prevent="canSubmit && submit()"
            />
            <textarea
              v-else
              :id="`response-${question.id}`"
              v-model="draft"
              class="response-input response-area"
              rows="6"
              :disabled="selfGrading !== null"
              :placeholder="t('practice.openPlaceholder')"
            ></textarea>
          </div>

          <div v-if="answered && !isChoice && answered.response" class="given">
            <span class="given-label">{{ t('practice.yourAnswer') }}</span>
            <p class="given-text">{{ answered.response }}</p>
          </div>

          <p v-if="submitErrorKey" class="notice" role="alert">{{ t(submitErrorKey) }}</p>

          <!-- Unanswered: submit (or admit not knowing — an honest wrong). -->
          <div v-if="!answered && !selfGrading" class="actions">
            <AppButton :loading="submitting" :disabled="!canSubmit" @click="submit()">
              {{ t(question.type === 'open' ? 'practice.compare' : 'practice.submit') }}
            </AppButton>
            <AppButton
              v-if="question.type !== 'open'"
              variant="ghost"
              tone="secondary"
              :disabled="submitting"
              @click="submit(undefined, true)"
            >
              {{ t('practice.dontKnow') }}
            </AppButton>
            <span v-if="isChoice" class="hint">{{ t('practice.keyboardHint') }}</span>
          </div>

          <!-- Step two of two: the candidate judges against the reference. -->
          <div v-if="selfGrading" class="self-grade">
            <SolutionBlock :solution="selfGrading.solution" :type="question.type" />
            <p class="self-grade-prompt">{{ t('practice.selfGradePrompt') }}</p>
            <div class="actions">
              <AppButton tone="success" :loading="submitting" @click="submit('correct')">
                {{ t('practice.result.correct') }}
              </AppButton>
              <AppButton tone="warning" variant="soft" :disabled="submitting" @click="submit('partial')">
                {{ t('practice.result.partial') }}
              </AppButton>
              <AppButton tone="danger" variant="soft" :disabled="submitting" @click="submit('wrong')">
                {{ t('practice.result.wrong') }}
              </AppButton>
            </div>
          </div>

          <!-- Answered: the verdict, the reference, and what comes next. -->
          <template v-if="answered">
            <p class="verdict" :class="`verdict-${resultTone(answered.result)}`" role="status">
              <AppIcon :name="answered.result === 'correct' ? 'check-circle' : 'alert-circle'" aria-hidden="true" />
              {{ t(`practice.verdict.${answered.result}`) }}
              <span v-if="answered.selfGraded" class="verdict-note">{{ t('practice.selfGraded') }}</span>
            </p>
            <p v-if="mistakeLine" class="mistake-line">
              <AppIcon name="book-x" size="sm" aria-hidden="true" />
              {{ mistakeLine }}
            </p>
            <SolutionBlock :solution="answered.solution" :type="question.type" />
            <AiExplainPanel
              v-if="explaining"
              :title="t('practice.explainTitle')"
              :run="runExplain"
              @close="explaining = false"
            />
            <div class="actions">
              <AppButton v-if="!allAnswered" icon-right="arrow-right" @click="next">{{ t('practice.next') }}</AppButton>
              <AppButton v-else icon-right="arrow-right" :loading="finishing" @click="finish">
                {{ t('practice.seeReport') }}
              </AppButton>
              <AppButton v-if="!explaining" variant="soft" tone="secondary" icon-left="sparkles" @click="explaining = true">
                {{ answered.result === 'correct' ? t('practice.explain') : t('practice.explainWrong') }}
              </AppButton>
            </div>
          </template>
        </article>

        <footer class="stage-foot">
          <AppButton variant="ghost" tone="secondary" size="sm" :loading="finishing" @click="requestFinish">
            {{ t('practice.finish') }}
          </AppButton>
        </footer>
      </template>
    </template>

    <AppDialog v-model="confirmFinish" :title="t('practice.finishConfirm.title')" width="420px">
      <p>
        {{
          t('practice.finishConfirm.body', {
            n: items.filter((entry) => entry.answer === null).length,
          })
        }}
      </p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="confirmFinish = false">{{ t('common.cancel') }}</AppButton>
        <AppButton :loading="finishing" @click="finish">{{ t('practice.finishConfirm.confirm') }}</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
.practice-session {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  max-width: 880px;
  margin: 0 auto;
  padding: var(--space-8);
}

.stage-head {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  width: fit-content;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  text-decoration: none;
}

.back:hover {
  color: var(--color-primary);
}

.title {
  margin: var(--space-2) 0 0;
  font-family: var(--font-headline-family);
  font-size: var(--font-title-size);
  font-weight: var(--font-headline-weight);
  color: var(--color-text);
}

.progress {
  margin: 0;
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.notice {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background-color: var(--color-warning-soft);
  font-size: var(--text-sm);
  color: var(--color-text);
}

.navigator {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 var(--space-1);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

.dot:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.dot.current {
  border-color: var(--color-primary);
  border-width: 2px;
  color: var(--color-text);
  font-weight: 600;
}

.dot-correct {
  background-color: var(--color-success-soft);
  color: var(--color-success);
}

.dot-partial {
  background-color: var(--color-warning-soft);
  color: var(--color-warning);
}

.dot-wrong {
  background-color: var(--color-danger-soft);
  color: var(--color-danger);
}

.stage {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.points {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-4);
}

.response {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.response-label,
.given-label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.response-input {
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
}

.response-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.response-input:disabled {
  background-color: var(--color-surface-hover);
  color: var(--color-text-secondary);
}

.response-area {
  resize: vertical;
  line-height: 1.7;
}

.given {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.given-text {
  margin: 0;
  white-space: pre-wrap;
  color: var(--color-text-secondary);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.hint {
  margin-left: auto;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.self-grade {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.self-grade-prompt {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.verdict {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-weight: 600;
}

.verdict-success {
  color: var(--color-success);
}

.verdict-warning {
  color: var(--color-warning);
}

.verdict-danger {
  color: var(--color-danger);
}

.verdict-note {
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-text-tertiary);
}

.mistake-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.stage-foot {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .practice-session {
    padding: var(--space-5) var(--space-4);
  }

  .stage {
    padding: var(--space-4);
  }

  .hint {
    display: none;
  }
}
</style>
