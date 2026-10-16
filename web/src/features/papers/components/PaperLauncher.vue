<script setup lang="ts">
/**
 * 在线模考 — where a paper is chosen and its clock started, for the paper
 * subject selected on the 模考 page.
 *
 * Three sources, one verb: library papers (content packs), a new paper
 * assembled from the bank (组卷 — the whole paper, or one printed section), and
 * the candidate's own earlier papers (to sit again, 二刷). What the bank
 * cannot assemble yet is said plainly — which slot is short, by how much —
 * rather than hidden or padded. A paper in progress or awaiting scoring is
 * always offered first: one paper at a time.
 *
 * Starting asks once, because a started clock does not stop.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { AppButton, AppDialog, AppIcon, AppSkeleton } from '@/components'
import type { ExamSubjectCode } from '@/api/modules/exam'
import {
  assemblePaper,
  getPaperCatalog,
  listOpenPaperSessions,
  startPaperSession,
  type PaperCatalogDto,
  type PaperSessionSummaryDto,
  type PaperShortageDto,
  type PaperSummaryDto,
} from '@/api/modules/paper'
import { toApiError } from '@/api/types'
import { useSyllabusStore } from '@/stores/syllabus'
import { formatClock, formatPoints } from '../paperFormat'

const props = defineProps<{ subject: ExamSubjectCode }>()

const { t, d } = useI18n()
const router = useRouter()
const syllabusStore = useSyllabusStore()

const catalog = ref<PaperCatalogDto | null>(null)
const open = ref<PaperSessionSummaryDto[]>([])
const loading = ref(false)
const loadErrorKey = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  loadErrorKey.value = null
  try {
    const [loaded, sessions] = await Promise.all([getPaperCatalog(props.subject), listOpenPaperSessions()])
    catalog.value = loaded
    open.value = sessions
  } catch (caught) {
    loadErrorKey.value = toApiError(caught).messageKey
  } finally {
    loading.value = false
  }
}

watch(() => props.subject, () => void load(), { immediate: true })

const doc = computed(() => syllabusStore.subjectDoc(props.subject))
const sectionName = (code: string) => doc.value?.sections.find((section) => section.code === code)?.name ?? code
const sectionTotal = (code: string) => doc.value?.sections.find((section) => section.code === code)?.total ?? 0

/** "数据结构还差 3 道", "阅读理解还差 2 篇" — what the bank lacks for one slot. */
function shortage(entry: PaperShortageDto): string {
  const name = syllabusStore.node(entry.scope)?.name ?? entry.scope
  const missing = Math.max(1, entry.needed - entry.available)
  return entry.available >= entry.needed
    ? t('papers.launcher.noCombination', { name })
    : t(entry.texts ? 'papers.launcher.shortTexts' : 'papers.launcher.shortQuestions', { name, n: missing })
}

const inProgress = computed(() => open.value.find((session) => session.status === 'in_progress') ?? null)

function remaining(session: PaperSessionSummaryDto): string {
  return formatClock(session.deadlineAt - Date.now())
}

function history(paper: PaperSummaryDto): string {
  if (paper.history.sat === 0) return t('papers.launcher.notSat')
  return t('papers.launcher.sat', {
    n: paper.history.sat,
    best: formatPoints(paper.history.best ?? 0),
  })
}

// --- Starting ----------------------------------------------------------------------------

/** What the confirm dialog is about to start. */
interface Pending {
  title: string
  minutes: number
  fullScore: number
  /** An existing paper, or the sections to assemble ([] = whole). */
  paperId?: string
  sections?: string[]
}

const pending = ref<Pending | null>(null)
const starting = ref(false)
const startErrorKey = ref<string | null>(null)

function confirmPaper(paper: PaperSummaryDto): void {
  startErrorKey.value = null
  pending.value = { title: paper.title, minutes: paper.durationMinutes, fullScore: paper.fullScore, paperId: paper.id }
}

function confirmAssembly(sections: string[]): void {
  startErrorKey.value = null
  const assembly = catalog.value?.assembly
  if (!assembly) return
  const minutes =
    sections.length === 0
      ? assembly.wholeMinutes
      : sections.reduce((sum, code) => sum + (assembly.sections.find((s) => s.code === code)?.minutes ?? 0), 0)
  const fullScore =
    sections.length === 0 ? (doc.value?.fullScore ?? 0) : sections.reduce((sum, code) => sum + sectionTotal(code), 0)
  pending.value = {
    title: sections.length === 0 ? t('papers.launcher.wholeTitle') : sections.map(sectionName).join('、'),
    minutes,
    fullScore,
    sections,
  }
}

async function start(): Promise<void> {
  const target = pending.value
  if (!target || starting.value) return
  starting.value = true
  startErrorKey.value = null
  try {
    const paperId = target.paperId ?? (await assemblePaper(props.subject, target.sections)).id
    const session = await startPaperSession(paperId)
    pending.value = null
    await router.push({ name: 'paper-exam', params: { id: session.id } })
  } catch (caught) {
    startErrorKey.value = toApiError(caught).messageKey
    void load()
  } finally {
    starting.value = false
  }
}

function resume(session: PaperSessionSummaryDto): void {
  void router.push(
    session.status === 'in_progress'
      ? { name: 'paper-exam', params: { id: session.id } }
      : { name: 'paper-result', params: { id: session.id } },
  )
}

function openPaper(paper: PaperSummaryDto): void {
  if (paper.history.openSessionId) {
    const session = open.value.find((entry) => entry.id === paper.history.openSessionId)
    if (session) {
      resume(session)
      return
    }
  }
  confirmPaper(paper)
}
</script>

<template>
  <section class="launcher" :aria-label="t('papers.launcher.title')">
    <header class="launcher-head">
      <div>
        <h2 class="launcher-title">{{ t('papers.launcher.title') }}</h2>
        <p class="launcher-text">{{ t('papers.launcher.subtitle') }}</p>
      </div>
    </header>

    <!-- Unfinished papers first: one paper at a time, and a hand-in awaiting scores is unfinished work. -->
    <ul v-if="open.length > 0" class="open">
      <li v-for="session in open" :key="session.id" class="open-row" :class="`open-${session.status}`">
        <AppIcon :name="session.status === 'in_progress' ? 'timer' : 'clipboard-check'" size="sm" aria-hidden="true" />
        <span class="open-text">
          <span class="open-title">{{ t(`exam.papers.${session.subject}`) }} · {{ session.title }}</span>
          <span class="open-meta">
            <template v-if="session.status === 'in_progress'">
              {{ t('papers.launcher.openInProgress', { time: remaining(session), answered: session.answered, total: session.total }) }}
            </template>
            <template v-else>{{ t('papers.launcher.openScoring', { n: session.pending }) }}</template>
          </span>
        </span>
        <AppButton size="sm" @click="resume(session)">
          {{ session.status === 'in_progress' ? t('papers.launcher.resume') : t('papers.launcher.score') }}
        </AppButton>
      </li>
    </ul>

    <div v-if="loading && !catalog" class="skeleton" aria-busy="true">
      <AppSkeleton :lines="4" />
    </div>
    <p v-else-if="loadErrorKey" class="error" role="alert">
      {{ t(loadErrorKey) }}
      <AppButton size="sm" variant="ghost" @click="load">{{ t('common.retry') }}</AppButton>
    </p>

    <template v-else-if="catalog">
      <!-- 组卷: the whole paper, or one printed section, from the bank. -->
      <div class="block">
        <h3 class="block-title">{{ t('papers.launcher.assemble') }}</h3>
        <ul class="rows">
          <li class="row">
            <span class="row-main">
              <span class="row-title">{{ t('papers.launcher.wholeTitle') }}</span>
              <span class="row-meta">
                {{ t('papers.launcher.shape', { minutes: catalog.assembly.wholeMinutes, score: doc?.fullScore ?? 0 }) }}
                · {{ t('papers.launcher.countsTowardEstimate') }}
              </span>
              <span v-if="!catalog.assembly.whole" class="row-short">{{ t('papers.launcher.wholeShort') }}</span>
            </span>
            <AppButton
              size="sm"
              :disabled="!catalog.assembly.whole || inProgress !== null"
              @click="confirmAssembly([])"
            >
              {{ t('papers.launcher.assembleStart') }}
            </AppButton>
          </li>
          <li v-for="section in catalog.assembly.sections" :key="section.code" class="row">
            <span class="row-main">
              <span class="row-title">{{ sectionName(section.code) }}</span>
              <span class="row-meta">
                {{ t('papers.launcher.shape', { minutes: section.minutes, score: formatPoints(sectionTotal(section.code)) }) }}
              </span>
              <span v-if="!section.available" class="row-short">
                {{ section.shortages.map(shortage).join('；') }}
              </span>
            </span>
            <AppButton
              size="sm"
              variant="soft"
              :disabled="!section.available || inProgress !== null"
              @click="confirmAssembly([section.code])"
            >
              {{ t('papers.launcher.assembleStart') }}
            </AppButton>
          </li>
        </ul>
      </div>

      <div v-if="catalog.library.length > 0" class="block">
        <h3 class="block-title">{{ t('papers.launcher.library') }}</h3>
        <ul class="rows">
          <li v-for="paper in catalog.library" :key="paper.id" class="row">
            <span class="row-main">
              <span class="row-title">{{ paper.title }}</span>
              <span class="row-meta">
                {{ t('papers.launcher.shape', { minutes: paper.durationMinutes, score: formatPoints(paper.fullScore) }) }}
                · {{ history(paper) }}
              </span>
            </span>
            <AppButton size="sm" variant="soft" :disabled="inProgress !== null && !paper.history.openSessionId" @click="openPaper(paper)">
              {{ paper.history.openSessionId ? t('papers.launcher.continue') : t('papers.launcher.start') }}
            </AppButton>
          </li>
        </ul>
      </div>

      <div v-if="catalog.mine.length > 0" class="block">
        <h3 class="block-title">{{ t('papers.launcher.mine') }}</h3>
        <ul class="rows">
          <li v-for="paper in catalog.mine" :key="paper.id" class="row">
            <span class="row-main">
              <span class="row-title">{{ paper.title }}</span>
              <span class="row-meta">
                {{ d(paper.createdAt, 'short') }} ·
                {{ t('papers.launcher.shape', { minutes: paper.durationMinutes, score: formatPoints(paper.fullScore) }) }}
                · {{ history(paper) }}
              </span>
            </span>
            <AppButton size="sm" variant="ghost" :disabled="inProgress !== null && !paper.history.openSessionId" @click="openPaper(paper)">
              {{ paper.history.openSessionId ? t('papers.launcher.continue') : t('papers.launcher.again') }}
            </AppButton>
          </li>
        </ul>
      </div>
      <p v-if="inProgress" class="hint">{{ t('papers.launcher.oneAtATime') }}</p>
    </template>

    <AppDialog
      :model-value="pending !== null"
      :title="pending ? t('papers.launcher.confirm.title', { title: pending.title }) : ''"
      width="460px"
      @update:model-value="(value) => !value && (pending = null)"
    >
      <template v-if="pending">
        <p class="dialog-lead">
          {{ t('papers.launcher.confirm.shape', { minutes: pending.minutes, score: formatPoints(pending.fullScore) }) }}
        </p>
        <ul class="dialog-rules">
          <li>{{ t('papers.launcher.confirm.clock') }}</li>
          <li>{{ t('papers.launcher.confirm.nothingRevealed') }}</li>
          <li>{{ t('papers.launcher.confirm.timer') }}</li>
        </ul>
        <p v-if="startErrorKey" class="error" role="alert">{{ t(startErrorKey) }}</p>
      </template>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="pending = null">{{ t('common.cancel') }}</AppButton>
        <AppButton icon-left="play" :loading="starting" @click="start">{{ t('papers.launcher.confirm.start') }}</AppButton>
      </template>
    </AppDialog>
  </section>
</template>

<style scoped>
.launcher {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.launcher-head {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
}

.launcher-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
}

.launcher-text {
  max-width: 68ch;
  margin: var(--space-1) 0 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.open {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.open-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
}

.open-in_progress {
  border-color: var(--color-primary);
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
}

.open-scoring {
  border-color: var(--color-warning);
  background-color: var(--color-warning-soft);
  color: var(--color-warning);
}

.open-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.open-title {
  font-weight: 600;
  color: var(--color-text);
}

.open-meta {
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.block {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.block-title {
  margin: 0;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-secondary);
}

.rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: var(--border-width-sm) solid var(--color-border);
}

.row:last-child {
  border-bottom: none;
}

.row-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.row-title {
  font-weight: 500;
}

.row-meta {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.row-short {
  font-size: var(--text-xs);
  color: var(--color-warning);
}

.hint {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.error {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.dialog-lead {
  margin: 0 0 var(--space-3);
  font-weight: 600;
}

.dialog-rules {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0 0 var(--space-3);
  padding-left: var(--space-5);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

@media (max-width: 640px) {
  .launcher {
    padding: var(--space-4);
  }
}
</style>
