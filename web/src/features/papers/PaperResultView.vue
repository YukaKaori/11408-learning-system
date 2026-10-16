<script setup lang="ts">
/**
 * A paper after its hand-in — first the candidate's scoring, then the report.
 *
 * Scoring: the grader has already marked every answer it can judge (and those
 * marks are already in the answer log, the mistake book and the 模考 record).
 * What is left — open answers, fill-blanks it could not match — is scored here
 * against the reference and its scoring points, saved as it is given, and
 * completed in one step that writes the rest of the evidence.
 *
 * Report: what the paper yielded, section by section and 考点 by 考点, with every
 * question reviewable and every weak 考点 one tap from a practice set. The
 * 复盘 note is the record's own note, the one part of an app-scored record the
 * candidate writes by hand.
 *
 * Solid throughout: scores and answers are content (material constitution §1).
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { AppButton, AppDrawer, AppEmpty, AppIcon, AppSkeleton } from '@/components'
import {
  completePaper,
  getPaperSession,
  savePaperScores,
  startPaperSession,
  type PaperItemDto,
  type PaperSessionDto,
} from '@/api/modules/paper'
import { listSittings, updateSittingNote } from '@/api/modules/sitting'
import { toApiError, type ApiError } from '@/api/types'
import { useSyllabusStore } from '@/stores/syllabus'
import { PAPER_ICON, paperAccentColor } from '@/features/syllabus/papers'
import { usePracticeLauncher } from '@/features/practice/usePracticeLauncher'
import { formatPoints } from './paperFormat'
import SelfScoreCard from './components/SelfScoreCard.vue'
import PaperReportPanel from './components/PaperReportPanel.vue'
import QuestionReview from './components/QuestionReview.vue'

const { t, d } = useI18n()
const route = useRoute()
const router = useRouter()
const syllabusStore = useSyllabusStore()
const { launch, launching, errorKey: launchErrorKey } = usePracticeLauncher()

const sessionId = computed(() => String(route.params.id))
const paper = ref<PaperSessionDto | null>(null)
const loading = ref(true)
const loadError = ref<ApiError | null>(null)
/**
 * The clock ran out — said once, at the top. Either the stage handed the paper
 * in at zero (`?timeUp=1`), or the server did, at the deadline, while the tab
 * was closed: then the hand-in is stamped with the deadline itself.
 */
const timeUp = computed(() => {
  if (route.query.timeUp === '1') return true
  const current = paper.value
  return current?.submittedAt != null && current.submittedAt >= current.deadlineAt
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const loaded = await getPaperSession(sessionId.value)
    if (loaded.status === 'in_progress') {
      await router.replace({ name: 'paper-exam', params: { id: loaded.id } })
      return
    }
    paper.value = loaded
    seedScores(loaded)
    if (loaded.status === 'completed') void loadNote(loaded)
  } catch (caught) {
    loadError.value = toApiError(caught)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void syllabusStore.load()
})
watch(sessionId, () => void load(), { immediate: true })

const sectionNames = computed<Record<string, string>>(() =>
  Object.fromEntries(
    (paper.value ? (syllabusStore.subjectDoc(paper.value.subject)?.sections ?? []) : []).map((section) => [
      section.code,
      section.name,
    ]),
  ),
)

const subtitle = computed(() => {
  const current = paper.value
  if (!current?.submittedAt) return ''
  return `${t(`sittings.kind.${current.kind}`)} · ${d(current.submittedAt, 'short')} ${d(current.submittedAt, 'time')}`
})

// --- Scoring --------------------------------------------------------------------------------

/** Every answer the candidate scores, in paper order. */
const toScore = computed<{ item: PaperItemDto; section: string }[]>(() =>
  (paper.value?.sections ?? []).flatMap((section) =>
    section.items.filter((item) => item.mark?.verdict === 'self').map((item) => ({ item, section: section.code })),
  ),
)

/** The marks as given here — the server's, then the candidate's edits. */
const given = ref<Record<string, number | null>>({})
const scoreError = ref<string | null>(null)
const savingScores = ref(false)
let pendingSave: Record<string, number | null> = {}
let saveTimer: ReturnType<typeof setTimeout> | null = null

function seedScores(loaded: PaperSessionDto): void {
  const marks: Record<string, number | null> = {}
  for (const section of loaded.sections) {
    for (const item of section.items) {
      if (item.mark?.verdict === 'self') marks[item.question.id] = item.mark.points
    }
  }
  given.value = marks
}

const scoredCount = computed(() => toScore.value.filter(({ item }) => given.value[item.question.id] != null).length)
const allScored = computed(() => scoredCount.value === toScore.value.length)

/** Points decided: the grader's marks plus the candidate's scores. */
const runningScore = computed(() => {
  let total = 0
  for (const section of paper.value?.sections ?? []) {
    for (const item of section.items) {
      if (item.mark?.verdict === 'self') total += given.value[item.question.id] ?? 0
      else total += item.mark?.points ?? 0
    }
  }
  return total
})

const autoScore = computed(() =>
  (paper.value?.sections ?? []).reduce(
    (sum, section) =>
      sum + section.items.reduce((inner, item) => inner + (item.mark?.verdict !== 'self' ? (item.mark?.points ?? 0) : 0), 0),
    0,
  ),
)

function onScore(questionId: string, points: number | null): void {
  given.value = { ...given.value, [questionId]: points }
  pendingSave[questionId] = points
  if (saveTimer !== null) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => void flushScores(), 600)
}

async function flushScores(): Promise<boolean> {
  if (saveTimer !== null) {
    clearTimeout(saveTimer)
    saveTimer = null
  }
  const batch = pendingSave
  if (Object.keys(batch).length === 0) return true
  pendingSave = {}
  savingScores.value = true
  scoreError.value = null
  try {
    await savePaperScores(sessionId.value, batch)
    return true
  } catch (caught) {
    pendingSave = { ...batch, ...pendingSave }
    scoreError.value = toApiError(caught).messageKey
    return false
  } finally {
    savingScores.value = false
  }
}

const completing = ref(false)

async function complete(): Promise<void> {
  if (completing.value || !allScored.value) return
  completing.value = true
  scoreError.value = null
  try {
    if (!(await flushScores())) return
    const done = await completePaper(sessionId.value)
    paper.value = done
    void loadNote(done)
    window.scrollTo({ top: 0 })
  } catch (caught) {
    scoreError.value = toApiError(caught).messageKey
  } finally {
    completing.value = false
  }
}

// --- The report --------------------------------------------------------------------------

const reviewing = ref<PaperItemDto | null>(null)
const reviewOpen = computed({
  get: () => reviewing.value !== null,
  set: (open: boolean) => {
    if (!open) reviewing.value = null
  },
})

function drill(nodeCode: string): void {
  void launch({ mode: 'topic', nodeCode }, nodeCode)
}

const restarting = ref(false)
const restartError = ref<string | null>(null)

/** Sits the same paper again — 二刷, comparable with this sitting. */
async function again(): Promise<void> {
  if (!paper.value || restarting.value) return
  restarting.value = true
  restartError.value = null
  try {
    const started = await startPaperSession(paper.value.paperId)
    await router.push({ name: 'paper-exam', params: { id: started.id } })
  } catch (caught) {
    restartError.value = toApiError(caught).messageKey
  } finally {
    restarting.value = false
  }
}

// --- 复盘: the record's note -------------------------------------------------------------

const note = ref('')
const noteSaved = ref('')
const noteState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const recordExists = ref(false)

async function loadNote(current: PaperSessionDto): Promise<void> {
  recordExists.value = false
  if (!current.sittingId) return
  try {
    const records = await listSittings(current.subject, 200)
    const record = records.find((entry) => entry.id === current.sittingId)
    recordExists.value = record !== undefined
    note.value = record?.note ?? ''
    noteSaved.value = note.value
  } catch {
    recordExists.value = false
  }
}

async function saveNote(): Promise<void> {
  if (!paper.value?.sittingId || note.value === noteSaved.value) return
  noteState.value = 'saving'
  try {
    const saved = await updateSittingNote(paper.value.sittingId, note.value.trim() || null)
    noteSaved.value = saved.note ?? ''
    noteState.value = 'saved'
  } catch {
    noteState.value = 'error'
  }
}
</script>

<template>
  <div class="result" :style="paper ? { '--paper-accent': paperAccentColor(paper.subject) } : undefined">
    <AppSkeleton v-if="loading && !paper" :lines="10" />

    <AppEmpty v-else-if="loadError" icon="alert-circle" :title="t(loadError.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="load">{{ t('common.retry') }}</AppButton>
        <AppButton size="sm" variant="ghost" @click="router.push({ name: 'sittings' })">
          {{ t('papers.result.back') }}
        </AppButton>
      </template>
    </AppEmpty>

    <template v-else-if="paper">
      <header class="head">
        <RouterLink :to="{ name: 'sittings' }" class="back">
          <AppIcon name="arrow-left" size="sm" aria-hidden="true" />
          {{ t('papers.result.back') }}
        </RouterLink>
        <h1 class="title">
          <AppIcon :name="PAPER_ICON[paper.subject]" class="title-icon" aria-hidden="true" />
          {{ t(`exam.papers.${paper.subject}`) }} · {{ paper.title }}
        </h1>
        <p class="subtitle">{{ subtitle }}</p>
      </header>

      <p v-if="timeUp" class="notice" role="status">
        <AppIcon name="timer" size="sm" aria-hidden="true" />
        {{ t('papers.result.timeUp') }}
      </p>

      <!-- Scoring: what the grader could not judge, judged by the candidate. -->
      <template v-if="paper.status === 'scoring'">
        <section class="scoring-head">
          <div>
            <h2 class="scoring-title">{{ t('papers.scoring.title') }}</h2>
            <p class="scoring-text">
              {{ t('papers.scoring.intro', { auto: formatPoints(autoScore), n: toScore.length }) }}
            </p>
          </div>
          <div class="scoring-progress" aria-live="polite">
            <span class="progress-score">{{ formatPoints(runningScore) }}<span class="progress-full"> / {{ formatPoints(paper.fullScore) }}</span></span>
            <span class="progress-count">{{ t('papers.scoring.progress', { done: scoredCount, total: toScore.length }) }}</span>
          </div>
        </section>

        <div class="cards">
          <SelfScoreCard
            v-for="{ item, section } in toScore"
            :key="item.question.id"
            :item="item"
            :section-name="sectionNames[section] ?? section"
            :value="given[item.question.id] ?? null"
            @change="(points) => onScore(item.question.id, points)"
          />
        </div>

        <footer class="scoring-foot">
          <p v-if="scoreError" class="error" role="alert">{{ t(scoreError) }}</p>
          <span class="save-state">{{ savingScores ? t('papers.scoring.saving') : t('papers.scoring.autosaved') }}</span>
          <AppButton :disabled="!allScored" :loading="completing" icon-right="arrow-right" @click="complete">
            {{ allScored ? t('papers.scoring.complete') : t('papers.scoring.remaining', { n: toScore.length - scoredCount }) }}
          </AppButton>
        </footer>
      </template>

      <!-- The report. -->
      <template v-else>
        <p v-if="launchErrorKey" class="notice" role="alert">{{ t(launchErrorKey) }}</p>
        <PaperReportPanel :paper="paper" :launching="launching" @review="(item) => (reviewing = item)" @drill="drill" />

        <article class="panel">
          <div class="panel-head">
            <h2 class="panel-title">{{ t('papers.result.note.title') }}</h2>
            <span class="note-state" aria-live="polite">
              <template v-if="noteState === 'saving'">{{ t('papers.result.note.saving') }}</template>
              <template v-else-if="noteState === 'saved'">{{ t('papers.result.note.saved') }}</template>
              <template v-else-if="noteState === 'error'">{{ t('papers.result.note.error') }}</template>
            </span>
          </div>
          <template v-if="recordExists">
            <textarea
              v-model="note"
              class="note"
              rows="4"
              maxlength="2000"
              :placeholder="t('papers.result.note.placeholder')"
              :aria-label="t('papers.result.note.title')"
              @blur="saveNote"
            ></textarea>
          </template>
          <p v-else class="panel-empty">{{ t('papers.result.note.noRecord') }}</p>
        </article>

        <footer class="actions">
          <p v-if="restartError" class="error" role="alert">{{ t(restartError) }}</p>
          <AppButton icon-left="rotate-ccw" :loading="restarting" @click="again">{{ t('papers.result.again') }}</AppButton>
          <AppButton variant="soft" tone="secondary" @click="router.push({ name: 'sittings', query: { subject: paper.subject } })">
            {{ t('papers.result.toSittings') }}
          </AppButton>
        </footer>
      </template>
    </template>

    <AppDrawer
      v-model="reviewOpen"
      :title="reviewing ? t('papers.exam.number', { n: reviewing.number }) : ''"
      size="min(720px, 100vw)"
    >
      <QuestionReview v-if="reviewing" :item="reviewing" />
    </AppDrawer>
  </div>
</template>

<style scoped>
.result {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-8);
}

.head {
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
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-2) 0 0;
  font-family: var(--font-headline-family);
  font-size: var(--font-title-size);
  font-weight: var(--font-headline-weight);
}

.title-icon {
  color: var(--paper-accent);
}

.subtitle {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.notice {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background-color: var(--color-warning-soft);
  font-size: var(--text-sm);
}

.scoring-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.scoring-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
}

.scoring-text {
  max-width: 62ch;
  margin: var(--space-1) 0 0;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.scoring-progress {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-1);
}

.progress-score {
  font-size: var(--text-2xl);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.progress-full {
  font-size: var(--text-sm);
  font-weight: 400;
  color: var(--color-text-tertiary);
}

.progress-count {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.cards {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.scoring-foot,
.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-3);
}

.scoring-foot {
  position: sticky;
  bottom: 0;
  padding: var(--space-3) 0;
  border-top: var(--border-width-sm) solid var(--color-border);
  background-color: var(--color-bg);
}

.save-state {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.error {
  margin: 0;
  margin-right: auto;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.panel-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
}

.panel-empty {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.note-state {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.note {
  width: 100%;
  padding: var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  line-height: 1.7;
  resize: vertical;
}

.note:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

@media (max-width: 640px) {
  .result {
    padding: var(--space-5) var(--space-4);
  }

  .scoring-progress {
    align-items: flex-start;
  }
}
</style>
