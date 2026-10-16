<script setup lang="ts">
/**
 * The exam stage — a paper sat in the app, the way it is sat in the hall.
 *
 * Full-bleed and quiet on purpose: the paper, the clock and the answer sheet,
 * nothing to wander off to. The clock is the server's (`usePaperClock` only
 * shows it); answers are saved as they are written (`useAnswerSheet`), so a
 * closed tab or a lost connection costs nothing but the seconds since the last
 * save. Nothing here can show a verdict, a reference answer or a 解析 — the
 * server does not send them before the hand-in, and this view never asks.
 *
 * At zero the paper is handed in with the sheet as it stands; had the tab been
 * closed, the server would have done the same at its next read. Either way the
 * candidate lands on the result page, where scoring and the report live.
 *
 * Solid throughout: question text is content (material constitution §1).
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'
import { AppButton, AppDialog, AppEmpty, AppIcon, AppSkeleton } from '@/components'
import RichText from '@/components/RichText.vue'
import {
  abandonPaper,
  getPaperSession,
  savePaperAnswers,
  submitPaper,
  type PaperSessionDto,
} from '@/api/modules/paper'
import { toApiError, type ApiError } from '@/api/types'
import QuestionContent from '@/features/questions/components/QuestionContent.vue'
import { useSyllabusStore } from '@/stores/syllabus'
import { PAPER_ICON, paperAccentColor } from '@/features/syllabus/papers'
import {
  ANNOUNCED_MINUTES,
  clockLevel,
  flatten,
  formatClock,
  formatPoints,
  isAnswered,
  markerOf,
  sheetOf,
  wordCount,
} from './paperFormat'
import { usePaperClock } from './usePaperClock'
import { useAnswerSheet } from './useAnswerSheet'
import ExamNavigator from './components/ExamNavigator.vue'

/** Already handed in (by the clock, or another tab) — go to the result. */
const HANDED_IN = new Set([260007, 260008])

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const syllabusStore = useSyllabusStore()

const sessionId = computed(() => String(route.params.id))
const paper = ref<PaperSessionDto | null>(null)
const loading = ref(true)
const loadError = ref<ApiError | null>(null)
const index = ref(0)
/** True once the paper has left this stage (handed in, abandoned) — the leave guard stands down. */
const done = ref(false)

const slots = computed(() => (paper.value ? flatten(paper.value.sections) : []))
const current = computed(() => slots.value[index.value] ?? null)
const question = computed(() => current.value?.item.question ?? null)
const isChoice = computed(
  () => question.value?.type === 'single_choice' || question.value?.type === 'multi_choice',
)

const clock = usePaperClock({ onTick })
const sheet = useAnswerSheet({
  save: (body) => savePaperAnswers(sessionId.value, body),
  onSaved: (result) => clock.sync(result.deadlineAt, result.serverNow),
  onClosed: () => void toResult(),
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const loaded = await getPaperSession(sessionId.value)
    if (loaded.status !== 'in_progress') {
      await toResult()
      return
    }
    paper.value = loaded
    sheet.load(sheetOf(loaded.sections))
    clock.sync(loaded.deadlineAt, loaded.serverNow)
    clock.start()
    // Resume where the work stopped: the first unanswered question.
    const firstOpen = flatten(loaded.sections).findIndex((slot) => !isAnswered(sheet.sheet[slot.item.question.id]))
    index.value = firstOpen >= 0 ? firstOpen : 0
  } catch (caught) {
    loadError.value = toApiError(caught)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void syllabusStore.load()
  void load()
})

// --- The clock -------------------------------------------------------------------------

const level = computed(() => clockLevel(clock.remaining.value ?? Number.POSITIVE_INFINITY))
const announced = new Set<number>()
const announcement = ref('')

/** Once a second: time on the open question, the spoken thresholds, the hand-in at zero. */
function onTick(): void {
  if (!paper.value || done.value) return
  const open = question.value
  if (open && document.visibilityState === 'visible') sheet.addSecond(open.id)
  const remaining = clock.remaining.value
  if (remaining === null) return
  for (const minutes of ANNOUNCED_MINUTES) {
    if (remaining <= minutes * 60_000 && remaining > (minutes - 1) * 60_000 && !announced.has(minutes)) {
      announced.add(minutes)
      announcement.value = t('papers.exam.announce', { n: minutes })
    }
  }
  if (clock.expired.value) void handIn(true)
}

// --- Answering -------------------------------------------------------------------------

const entry = computed(() => (question.value ? sheet.sheet[question.value.id] : undefined))
const response = computed(() => entry.value?.response ?? '')

/** The question as shown: the shared passage moves to the side panel, so it is not printed twice. */
const shown = computed(() => {
  const asked = question.value
  if (!asked) return null
  return asked.groupId ? { ...asked, passage: null } : asked
})

function choose(letters: string): void {
  if (question.value) sheet.setResponse(question.value.id, letters || null)
}

function write(value: string): void {
  if (question.value) sheet.setResponse(question.value.id, value)
}

function toggleFlag(): void {
  if (question.value) sheet.toggleFlag(question.value.id)
}

function go(target: number): void {
  if (target >= 0 && target < slots.value.length) index.value = target
}

const passage = computed(() => {
  const slot = current.value
  if (!slot || !slot.item.question.groupId) return null
  return slot.item.question.passage
})

/** A new text scrolls its passage back to the top; moving within one text keeps the reading place. */
const passageRef = ref<HTMLElement | null>(null)
watch(
  () => current.value?.runStart,
  () => passageRef.value?.scrollTo({ top: 0 }),
)

/**
 * A text that numbers its questions in place (a cloze's blanks, a 七选五 gap, a
 * translation sentence) shows where the open question is: its marker in the
 * passage is lit and kept in view, so "Blank [7]" never means hunting for [7].
 */
watch(
  [() => question.value?.id, passage],
  () => {
    const host = passageRef.value
    if (!host) return
    host.querySelectorAll('.marker-current').forEach((node) => node.classList.remove('marker-current'))
    const label = markerOf(question.value?.stem)
    if (!label) return
    const target = [...host.querySelectorAll('strong')].find((node) => node.textContent?.trim() === label)
    if (!target) return
    target.classList.add('marker-current')
    // Scroll the passage pane only — never the page, which would carry the
    // question out of view where the passage sits above it (narrow screens).
    const pane = host.getBoundingClientRect()
    const mark = target.getBoundingClientRect()
    if (mark.top < pane.top || mark.bottom > pane.bottom) host.scrollTop += mark.top - pane.top - pane.height / 3
  },
  { flush: 'post' },
)

const sectionName = (code: string) =>
  syllabusStore.subjectDoc(paper.value?.subject ?? 'cs408')?.sections.find((s) => s.code === code)?.name ?? code

/** Writing is judged against a length; a translation is written in Chinese, so it gets no English word count. */
const words = computed(() =>
  question.value?.type === 'open' && question.value.section?.startsWith('english1.writing')
    ? wordCount(response.value)
    : null,
)

// --- Handing in ------------------------------------------------------------------------

const confirmOpen = ref(false)
const handingIn = ref(false)
const handInError = ref<string | null>(null)
const unanswered = computed(() => slots.value.length - sheet.answered.value)

async function handIn(byClock = false): Promise<void> {
  if (handingIn.value || done.value || !paper.value) return
  handingIn.value = true
  handInError.value = null
  confirmOpen.value = false
  clock.stop()
  try {
    await submitPaper(sessionId.value, sheet.snapshot())
    await toResult(byClock)
  } catch (caught) {
    const error = toApiError(caught)
    if (HANDED_IN.has(error.code)) {
      await toResult(byClock)
      return
    }
    // Still on the stage: the sheet is safe; let the candidate try again.
    handInError.value = error.messageKey
    if (!byClock) clock.start()
  } finally {
    handingIn.value = false
  }
}

async function toResult(byClock = false): Promise<void> {
  done.value = true
  sheet.dispose()
  clock.stop()
  await router.replace({ name: 'paper-result', params: { id: sessionId.value }, query: byClock ? { timeUp: '1' } : {} })
}

// --- Leaving, abandoning ----------------------------------------------------------------

const leaveTarget = ref<RouteLocationRaw | null>(null)

onBeforeRouteLeave((to) => {
  if (done.value || !paper.value) return true
  leaveTarget.value = to.fullPath
  return false
})

async function leave(): Promise<void> {
  const target = leaveTarget.value ?? { name: 'sittings' }
  leaveTarget.value = null
  await sheet.flush()
  done.value = true
  await router.push(target)
}

const abandonOpen = ref(false)
const abandoning = ref(false)

async function abandon(): Promise<void> {
  if (abandoning.value) return
  abandoning.value = true
  try {
    await abandonPaper(sessionId.value)
    done.value = true
    sheet.dispose()
    clock.stop()
    await router.replace({ name: 'sittings' })
  } catch (caught) {
    handInError.value = toApiError(caught).messageKey
    abandonOpen.value = false
  } finally {
    abandoning.value = false
  }
}

/** The tab itself: the clock keeps running server-side, but unsaved words would be lost. */
function onBeforeUnload(event: BeforeUnloadEvent): void {
  if (done.value || !sheet.unsaved.value) return
  event.preventDefault()
}

function onVisibility(): void {
  if (document.visibilityState === 'hidden' && !done.value) void sheet.flush()
}

// --- Keyboard ------------------------------------------------------------------------------
// Letters pick options, ←/→ move, F flags — only outside text fields and dialogs,
// so writing an answer never triggers a shortcut.

function onKey(event: KeyboardEvent): void {
  if (!question.value || done.value || event.metaKey || event.ctrlKey || event.altKey) return
  const target = event.target as HTMLElement | null
  if (target && (target.closest('input, textarea, select, [contenteditable="true"]') || target.closest('.el-dialog'))) {
    return
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    go(index.value + 1)
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    go(index.value - 1)
    return
  }
  if (event.key === 'f' || event.key === 'F') {
    event.preventDefault()
    toggleFlag()
    return
  }
  if (isChoice.value && /^[a-hA-H]$/.test(event.key)) {
    const letter = event.key.toUpperCase()
    if (letter.charCodeAt(0) - 65 >= question.value.options.length) return
    event.preventDefault()
    if (question.value.type === 'single_choice') {
      choose(response.value === letter ? '' : letter)
    } else {
      const chosen = new Set(response.value.split('').filter(Boolean))
      if (chosen.has(letter)) chosen.delete(letter)
      else chosen.add(letter)
      choose([...chosen].sort().join(''))
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  window.addEventListener('beforeunload', onBeforeUnload)
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('beforeunload', onBeforeUnload)
  document.removeEventListener('visibilitychange', onVisibility)
})

const saveLine = computed(() => {
  switch (sheet.state.value) {
    case 'saving':
      return t('papers.exam.save.saving')
    case 'dirty':
      return t('papers.exam.save.dirty')
    case 'error':
      return t('papers.exam.save.error')
    default:
      return t('papers.exam.save.saved')
  }
})

const navigatorOpen = ref(false)
</script>

<template>
  <div class="exam" :style="paper ? { '--paper-accent': paperAccentColor(paper.subject) } : undefined">
    <div v-if="loading && !paper" class="exam-loading">
      <AppSkeleton :lines="12" />
    </div>

    <AppEmpty v-else-if="loadError" class="exam-error" icon="alert-circle" :title="t(loadError.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="load">{{ t('common.retry') }}</AppButton>
        <AppButton size="sm" variant="ghost" @click="router.push({ name: 'sittings' })">
          {{ t('papers.exam.backToSittings') }}
        </AppButton>
      </template>
    </AppEmpty>

    <template v-else-if="paper && current && question && shown">
      <header class="exam-head">
        <div class="head-left">
          <button type="button" class="leave" @click="router.push({ name: 'sittings' })">
            <AppIcon name="arrow-left" size="sm" aria-hidden="true" />
            <span class="leave-text">{{ t('papers.exam.leave') }}</span>
          </button>
          <span class="paper-name">
            <AppIcon :name="PAPER_ICON[paper.subject]" size="sm" class="paper-icon" aria-hidden="true" />
            <span class="paper-subject">{{ t(`exam.papers.${paper.subject}`) }}</span>
            <span class="paper-title">{{ paper.title }}</span>
          </span>
        </div>

        <div class="head-right">
          <span class="save" :class="`save-${sheet.state.value}`" role="status">{{ saveLine }}</span>
          <span
            class="clock"
            :class="`clock-${level}`"
            role="timer"
            :aria-label="t('papers.exam.clockLabel', { time: formatClock(clock.remaining.value ?? 0) })"
          >
            <AppIcon name="timer" size="sm" aria-hidden="true" />
            <span class="clock-time">{{ formatClock(clock.remaining.value ?? 0) }}</span>
          </span>
          <AppButton size="sm" icon-left="send" :loading="handingIn" @click="confirmOpen = true">
            {{ t('papers.exam.submit') }}
          </AppButton>
        </div>
      </header>
      <p class="sr-only" aria-live="assertive">{{ announcement }}</p>

      <div class="exam-body">
        <aside class="rail" :class="{ open: navigatorOpen }">
          <button type="button" class="rail-toggle" :aria-expanded="navigatorOpen" @click="navigatorOpen = !navigatorOpen">
            <AppIcon name="list-checks" size="sm" aria-hidden="true" />
            {{ t('papers.exam.progress', { answered: sheet.answered.value, total: slots.length }) }}
            <AppIcon :name="navigatorOpen ? 'chevron-up' : 'chevron-down'" size="sm" aria-hidden="true" />
          </button>
          <ExamNavigator
            class="rail-grid"
            :slots="slots"
            :current="index"
            :sheet="sheet.sheet"
            :section-name="sectionName"
            @go="
              (target) => {
                go(target)
                navigatorOpen = false
              }
            "
          />
          <p class="rail-hint">{{ t('papers.exam.keyboardHint') }}</p>
          <button type="button" class="abandon" @click="abandonOpen = true">{{ t('papers.exam.abandon') }}</button>
        </aside>

        <main class="paper" :class="{ 'with-passage': passage }">
          <section v-if="passage" ref="passageRef" class="passage" :aria-label="t('papers.exam.passageLabel')">
            <p class="passage-label">
              {{ sectionName(current.section) }}
              <template v-if="current.text"> · {{ t('papers.exam.text', { n: current.text }) }}</template>
            </p>
            <RichText :source="passage" />
          </section>

          <article class="sheet" :aria-labelledby="`q-${question.id}`">
            <div class="sheet-head">
              <p :id="`q-${question.id}`" class="sheet-number">
                <span class="section-name">{{ sectionName(current.section) }}</span>
                <span>{{ t('papers.exam.number', { n: current.item.number }) }}</span>
                <span class="worth">{{ t('papers.exam.worth', { n: formatPoints(current.item.worth) }) }}</span>
                <span v-if="current.inText" class="in-text">
                  {{ t('papers.exam.inText', { n: current.inText, total: current.runLength }) }}
                </span>
              </p>
              <button
                type="button"
                class="flag"
                :class="{ on: entry?.flagged }"
                :aria-pressed="entry?.flagged ?? false"
                @click="toggleFlag"
              >
                <AppIcon name="flag" size="sm" aria-hidden="true" />
                {{ entry?.flagged ? t('papers.exam.unflag') : t('papers.exam.flag') }}
              </button>
            </div>

            <QuestionContent
              :question="shown"
              :model-value="isChoice ? response : ''"
              :selectable="isChoice"
              @update:model-value="choose"
            />

            <div v-if="!isChoice" class="response">
              <label class="response-label" :for="`answer-${question.id}`">
                {{ t(question.type === 'fill_blank' ? 'papers.exam.fillLabel' : 'papers.exam.openLabel') }}
              </label>
              <input
                v-if="question.type === 'fill_blank'"
                :id="`answer-${question.id}`"
                :value="response"
                class="response-input"
                type="text"
                autocomplete="off"
                :placeholder="t('papers.exam.fillPlaceholder')"
                @input="write(($event.target as HTMLInputElement).value)"
              />
              <textarea
                v-else
                :id="`answer-${question.id}`"
                :value="response"
                class="response-input response-area"
                :rows="question.section?.startsWith('english1.writing') ? 14 : 9"
                :placeholder="t('papers.exam.openPlaceholder')"
                @input="write(($event.target as HTMLTextAreaElement).value)"
              ></textarea>
              <p v-if="words !== null" class="words">{{ t('papers.exam.words', { n: words }) }}</p>
            </div>

            <p v-if="handInError" class="notice" role="alert">{{ t(handInError) }}</p>

            <footer class="sheet-foot">
              <AppButton variant="soft" tone="secondary" icon-left="arrow-left" :disabled="index === 0" @click="go(index - 1)">
                {{ t('papers.exam.prev') }}
              </AppButton>
              <AppButton
                v-if="index < slots.length - 1"
                variant="soft"
                icon-right="arrow-right"
                @click="go(index + 1)"
              >
                {{ t('papers.exam.next') }}
              </AppButton>
              <AppButton v-else icon-left="send" @click="confirmOpen = true">{{ t('papers.exam.submit') }}</AppButton>
            </footer>
          </article>
        </main>
      </div>
    </template>

    <AppDialog v-model="confirmOpen" :title="t('papers.exam.confirm.title')" width="440px">
      <p class="dialog-text">{{ t('papers.exam.confirm.body') }}</p>
      <ul class="dialog-facts">
        <li v-if="unanswered > 0">{{ t('papers.exam.confirm.unanswered', { n: unanswered }) }}</li>
        <li v-else>{{ t('papers.exam.confirm.allAnswered') }}</li>
        <li v-if="sheet.flagged.value > 0">{{ t('papers.exam.confirm.flagged', { n: sheet.flagged.value }) }}</li>
        <li>{{ t('papers.exam.confirm.remaining', { time: formatClock(clock.remaining.value ?? 0) }) }}</li>
      </ul>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="confirmOpen = false">{{ t('papers.exam.confirm.cancel') }}</AppButton>
        <AppButton :loading="handingIn" @click="handIn(false)">{{ t('papers.exam.confirm.submit') }}</AppButton>
      </template>
    </AppDialog>

    <AppDialog
      :model-value="leaveTarget !== null"
      :title="t('papers.exam.leaveConfirm.title')"
      width="440px"
      @update:model-value="(open) => !open && (leaveTarget = null)"
    >
      <p class="dialog-text">{{ t('papers.exam.leaveConfirm.body') }}</p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="leaveTarget = null">{{ t('papers.exam.leaveConfirm.stay') }}</AppButton>
        <AppButton tone="secondary" @click="leave">{{ t('papers.exam.leaveConfirm.leave') }}</AppButton>
      </template>
    </AppDialog>

    <AppDialog v-model="abandonOpen" :title="t('papers.exam.abandonConfirm.title')" width="440px">
      <p class="dialog-text">{{ t('papers.exam.abandonConfirm.body') }}</p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="abandonOpen = false">{{ t('common.cancel') }}</AppButton>
        <AppButton tone="danger" :loading="abandoning" @click="abandon">{{ t('papers.exam.abandonConfirm.confirm') }}</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
.exam {
  --exam-head-height: 60px;

  min-height: 100vh;
  background-color: var(--color-bg);
  color: var(--color-text);
}

.exam-loading,
.exam-error {
  max-width: 880px;
  margin: 0 auto;
  padding: var(--space-12) var(--space-6);
}

/* --- The head: the paper, the clock, the hand-in -------------------------------------- */

.exam-head {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  min-height: var(--exam-head-height);
  padding: var(--space-2) var(--space-6);
  border-bottom: var(--border-width-sm) solid var(--color-border);
  background-color: var(--color-surface);
}

.head-left,
.head-right {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  min-width: 0;
}

.leave {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  border: none;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
}

.leave:hover {
  color: var(--color-text);
  background-color: var(--color-surface-hover);
}

.leave:focus-visible,
.flag:focus-visible,
.rail-toggle:focus-visible,
.abandon:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.paper-name {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-2);
  min-width: 0;
}

.paper-icon {
  align-self: center;
  color: var(--paper-accent);
}

.paper-subject {
  font-weight: 600;
  white-space: nowrap;
}

.paper-title {
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.save {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

.save-error {
  color: var(--color-warning);
}

.clock {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-full);
  background-color: var(--color-bg);
  font-variant-numeric: tabular-nums;
}

.clock-time {
  min-width: 4.5em;
  font-size: var(--text-lg);
  font-weight: 600;
  text-align: center;
}

.clock-soon {
  border-color: var(--color-warning);
  background-color: var(--color-warning-soft);
  color: var(--color-warning);
}

.clock-urgent {
  border-color: var(--color-danger);
  background-color: var(--color-danger-soft);
  color: var(--color-danger);
}

/* --- The body: the navigator rail and the paper -------------------------------------- */

.exam-body {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  align-items: start;
}

.rail {
  position: sticky;
  top: var(--exam-head-height);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-height: calc(100vh - var(--exam-head-height));
  padding: var(--space-5) var(--space-4);
  overflow-y: auto;
  border-right: var(--border-width-sm) solid var(--color-border);
}

.rail-toggle {
  display: none;
}

.rail-hint {
  margin: 0;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--color-text-tertiary);
}

.abandon {
  align-self: flex-start;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-text-tertiary);
  font: inherit;
  font-size: var(--text-xs);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.abandon:hover {
  color: var(--color-danger);
}

.paper {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-6);
  max-width: 920px;
  width: 100%;
  margin: 0 auto;
  padding: var(--space-6) var(--space-8) var(--space-16);
}

.paper.with-passage {
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  max-width: 1360px;
}

.passage {
  position: sticky;
  top: calc(var(--exam-head-height) + var(--space-6));
  max-height: calc(100vh - var(--exam-head-height) - var(--space-12));
  padding: var(--space-5) var(--space-6);
  overflow-y: auto;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  line-height: 1.8;
}

.passage-label {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-secondary);
}

/* The open question's blank or sentence number, lit in the text. */
.passage :deep(strong.marker-current) {
  padding: 0 var(--space-1);
  border-radius: var(--radius-sm);
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
}

.sheet {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  min-width: 0;
  padding: var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.sheet-number {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-3);
  margin: 0;
  font-weight: 600;
}

.section-name {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--paper-accent);
}

.worth,
.in-text {
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-text-secondary);
}

.flag {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-full);
  background: none;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-xs);
  cursor: pointer;
}

.flag.on {
  border-color: var(--color-warning);
  background-color: var(--color-warning-soft);
  color: var(--color-warning);
}

.response {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.response-label {
  font-size: var(--text-sm);
  font-weight: 500;
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

.response-area {
  resize: vertical;
  line-height: 1.8;
}

.words {
  margin: 0;
  align-self: flex-end;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

.notice {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background-color: var(--color-warning-soft);
  font-size: var(--text-sm);
}

.sheet-foot {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
  padding-top: var(--space-2);
}

.dialog-text {
  margin: 0 0 var(--space-3);
  line-height: 1.7;
}

.dialog-facts {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin: 0;
  padding-left: var(--space-5);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
}

/* --- Narrower screens ----------------------------------------------------------------- */

@media (max-width: 1100px) {
  .paper.with-passage {
    grid-template-columns: minmax(0, 1fr);
  }

  .passage {
    position: static;
    max-height: 46vh;
  }
}

@media (max-width: 860px) {
  .exam-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .rail {
    position: static;
    max-height: none;
    padding: var(--space-3) var(--space-4);
    border-right: none;
    border-bottom: var(--border-width-sm) solid var(--color-border);
  }

  .rail-toggle {
    display: inline-flex;
    align-items: center;
    gap: var(--space-2);
    padding: var(--space-2) 0;
    border: none;
    background: none;
    color: var(--color-text);
    font: inherit;
    font-size: var(--text-sm);
    cursor: pointer;
  }

  .rail-grid,
  .rail-hint,
  .abandon {
    display: none;
  }

  .rail.open .rail-grid,
  .rail.open .abandon {
    display: flex;
  }

  .paper {
    padding: var(--space-4) var(--space-4) var(--space-12);
  }

  .sheet {
    padding: var(--space-4);
  }
}

@media (max-width: 640px) {
  .exam-head {
    padding: var(--space-2) var(--space-4);
  }

  .leave-text,
  .paper-title,
  .save {
    display: none;
  }

  .clock-time {
    min-width: 4em;
    font-size: var(--text-base);
  }
}
</style>
