<script setup lang="ts">
/**
 * One place in the syllabus — a paper, a module, a chapter or a single 考点 —
 * and everything the candidate has there: how well it is held, the questions
 * the bank offers, the mistakes made on it, their notes and reference
 * materials, and the AI tutor explaining it against their own standing.
 *
 * This is where "learn" and "practise" meet: every artifact anchored in the
 * subtree is gathered here, so a 考点 is never studied from one screen and
 * drilled from another with nothing connecting them.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { AppButton, AppEmpty, AppIcon, AppPageHeader, AppPagination, AppSkeleton } from '@/components'
import RichText from '@/components/RichText.vue'
import { useAsync } from '@/composables/useAsync'
import { getMasteryMap, type NodeProgressDto } from '@/api/modules/mastery'
import { listQuestions } from '@/api/modules/question'
import { createNote, listNotes } from '@/api/modules/note'
import { streamPointExplain } from '@/api/modules/ai'
import { toApiError } from '@/api/types'
import { useSyllabusStore } from '@/stores/syllabus'
import AiExplainPanel from '@/features/ai-tutor/AiExplainPanel.vue'
import MaterialsPanel from '@/features/materials/MaterialsPanel.vue'
import { usePracticeLauncher } from '@/features/practice/usePracticeLauncher'
import FocusStartDialog from '@/features/focus/FocusStartDialog.vue'
import QuestionDrawer from '@/features/questions/components/QuestionDrawer.vue'
import MasteryMeter from './components/MasteryMeter.vue'
import NodeRow from './components/NodeRow.vue'
import { paperAccentColor } from './papers'
import { within, type SyllabusNode } from './syllabusIndex'

const { t, d } = useI18n()
const route = useRoute()
const router = useRouter()
const syllabusStore = useSyllabusStore()
const { launch, launching, errorKey: launchErrorKey } = usePracticeLauncher()

onMounted(() => {
  void syllabusStore.load()
})

const code = computed(() => String(route.params.code))
const node = computed(() => syllabusStore.node(code.value))
const path = computed(() => syllabusStore.path(code.value))
const accent = computed(() => paperAccentColor(node.value?.subject))
const isPoint = computed(() => node.value?.kind === 'point')
const children = computed<SyllabusNode[]>(() =>
  (node.value?.children ?? []).map((child) => syllabusStore.node(child)).filter((n): n is SyllabusNode => !!n),
)

// --- Standing ------------------------------------------------------------------

const mastery = useAsync(getMasteryMap)
const progressByCode = computed(() => {
  const byCode = new Map<string, NodeProgressDto>()
  for (const entry of mastery.data.value ?? []) byCode.set(entry.code, entry)
  return byCode
})
const progress = computed(() => progressByCode.value.get(code.value))
const accuracy = computed(() => {
  const p = progress.value
  return p && p.attempts > 0 ? Math.round((p.correct / p.attempts) * 100) : null
})

const kindLabel = computed(() => {
  const current = node.value
  if (!current) return ''
  const kind = t(`syllabus.kind.${current.kind}`)
  return current.kind === 'point' ? `${kind} · ${t(`syllabus.weight.${current.weight}`)}` : kind
})

// --- Explain -------------------------------------------------------------------

const explaining = ref(false)

/** Timing study of this node: the start dialog opens preselected on it. */
const focusOpen = ref(false)
function runExplain(signal: AbortSignal) {
  return streamPointExplain(code.value, signal)
}

// --- Questions -----------------------------------------------------------------

const QUESTION_PAGE_SIZE = 10
const questionPage = ref(1)
const questions = useAsync(() => listQuestions({ nodeCode: code.value, page: questionPage.value, size: QUESTION_PAGE_SIZE }))
watch(questionPage, () => void questions.reload())
const openQuestionId = ref<string | null>(null)

// --- Notes ---------------------------------------------------------------------

const notes = useAsync(listNotes)
const notesHere = computed(() => (notes.data.value ?? []).filter((note) => within(note.nodeCode, code.value)).slice(0, 6))
const creatingNote = ref(false)
const noteErrorKey = ref<string | null>(null)

async function newNote() {
  if (creatingNote.value || !node.value) return
  creatingNote.value = true
  noteErrorKey.value = null
  try {
    const created = await createNote({ title: node.value.name, nodeCode: code.value })
    await router.push({ name: 'notes', query: { note: created.id } })
  } catch (caught) {
    noteErrorKey.value = toApiError(caught).messageKey
  } finally {
    creatingNote.value = false
  }
}

// --- Navigation between nodes ----------------------------------------------------

watch(code, () => {
  explaining.value = false
  openQuestionId.value = null
  questionPage.value = 1
  void questions.reload()
})

function practise(target: string) {
  void launch({ mode: 'topic', nodeCode: target }, target)
}
</script>

<template>
  <div class="node-view" :style="{ '--paper-accent': accent }">
    <AppSkeleton v-if="!syllabusStore.loaded && !syllabusStore.error" :lines="8" />

    <AppEmpty v-else-if="syllabusStore.error" icon="alert-circle" :title="t(syllabusStore.error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="syllabusStore.load(true)">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>

    <AppEmpty v-else-if="!node" icon="compass" :title="t('syllabus.notFound')">
      <template #action>
        <AppButton size="sm" variant="soft" @click="router.push({ name: 'syllabus' })">
          {{ t('syllabus.backToMap') }}
        </AppButton>
      </template>
    </AppEmpty>

    <template v-else>
      <AppPageHeader :title="node.name" :subtitle="kindLabel">
        <template #breadcrumb>
          <nav class="crumbs" :aria-label="t('syllabus.breadcrumb')">
            <RouterLink :to="{ name: 'syllabus', query: { paper: node.subject } }">{{ t('syllabus.title') }}</RouterLink>
            <template v-for="(ancestor, index) in path.slice(0, -1)" :key="ancestor.code">
              <span aria-hidden="true">›</span>
              <RouterLink
                v-if="index > 0"
                :to="{ name: 'syllabus-node', params: { code: ancestor.code } }"
              >
                {{ ancestor.name }}
              </RouterLink>
              <RouterLink v-else :to="{ name: 'syllabus', query: { paper: node.subject } }">
                {{ t(`exam.papers.${node.subject}`) }}
              </RouterLink>
            </template>
          </nav>
        </template>
        <template #actions>
          <div class="header-actions">
            <AppButton
              icon-left="play"
              :loading="launching === code"
              :disabled="launching !== null || (progress?.available ?? 0) === 0"
              @click="practise(code)"
            >
              {{ t('syllabus.practiceHere') }}
            </AppButton>
            <AppButton variant="soft" tone="secondary" icon-left="timer" @click="focusOpen = true">
              {{ t('syllabus.focusHere') }}
            </AppButton>
            <AppButton v-if="!explaining" variant="soft" tone="secondary" icon-left="sparkles" @click="explaining = true">
              {{ t('syllabus.explain') }}
            </AppButton>
          </div>
        </template>
      </AppPageHeader>

      <p v-if="launchErrorKey" class="notice" role="alert">{{ t(launchErrorKey) }}</p>

      <section class="standing" :aria-label="t('syllabus.standing')">
        <div class="standing-meter">
          <span class="standing-label">{{ t(isPoint ? 'syllabus.mastery' : 'syllabus.readiness') }}</span>
          <MasteryMeter
            :value="isPoint ? progress?.mastery : progress?.readiness"
            :level="isPoint ? (progress?.level ?? 'untested') : null"
            :color="isPoint ? undefined : accent"
            :label="t(isPoint ? 'syllabus.mastery' : 'syllabus.readiness')"
          />
          <span v-if="isPoint" class="level">{{ t(`exam.levels.${progress?.level ?? 'untested'}`) }}</span>
        </div>
        <dl class="facts">
          <div class="fact">
            <dt>{{ t('syllabus.facts.attempts') }}</dt>
            <dd>{{ progress?.attempts ?? 0 }}</dd>
          </div>
          <div class="fact">
            <dt>{{ t('syllabus.facts.accuracy') }}</dt>
            <dd>{{ accuracy === null ? '—' : `${accuracy}%` }}</dd>
          </div>
          <div class="fact">
            <dt>{{ t('syllabus.facts.available') }}</dt>
            <dd>{{ progress?.available ?? 0 }}</dd>
          </div>
          <div class="fact">
            <dt>{{ t('syllabus.facts.mistakes') }}</dt>
            <dd>
              <RouterLink
                v-if="(progress?.mistakes ?? 0) > 0"
                :to="{ name: 'mistakes', query: { node: code } }"
                class="fact-link"
              >
                {{ progress?.mistakes }}
              </RouterLink>
              <template v-else>0</template>
            </dd>
          </div>
          <div class="fact">
            <dt>{{ t('syllabus.facts.last') }}</dt>
            <dd>{{ progress?.lastAttemptAt ? d(progress.lastAttemptAt, 'short') : '—' }}</dd>
          </div>
        </dl>
      </section>

      <AiExplainPanel v-if="explaining" :title="t('syllabus.explainTitle', { name: node.name })" :run="runExplain" @close="explaining = false" />

      <section v-if="children.length > 0" class="block">
        <h2 class="block-title">{{ t(`syllabus.childrenTitle.${node.kind}`) }}</h2>
        <div class="rows">
          <NodeRow
            v-for="child in children"
            :key="child.code"
            :node="child"
            :progress="progressByCode.get(child.code)"
            :accent="accent"
            :busy="launching === child.code"
            :locked="launching !== null"
            @practice="practise"
          />
        </div>
      </section>

      <section class="block">
        <header class="block-head">
          <h2 class="block-title">{{ t('syllabus.questionsTitle') }}</h2>
          <span v-if="questions.data.value" class="block-count">
            {{ t('syllabus.questionsTotal', { n: questions.data.value.total }) }}
          </span>
        </header>
        <AppSkeleton v-if="questions.loading.value && !questions.data.value" :lines="4" />
        <AppEmpty v-else-if="questions.error.value" icon="alert-circle" :title="t(questions.error.value.messageKey)">
          <template #action>
            <AppButton size="sm" variant="soft" @click="questions.reload">{{ t('common.retry') }}</AppButton>
          </template>
        </AppEmpty>
        <p v-else-if="(questions.data.value?.total ?? 0) === 0" class="empty">{{ t('syllabus.noQuestions') }}</p>
        <template v-else-if="questions.data.value">
          <ol class="question-list">
            <li v-for="question in questions.data.value.items" :key="question.id">
              <button type="button" class="question-row" @click="openQuestionId = question.id">
                <span class="question-type">{{ t(`question.type.${question.type}`) }}</span>
                <RichText :source="question.stem" class="question-stem" />
                <span v-if="question.sourceYear" class="question-year">{{ question.sourceYear }}</span>
                <AppIcon name="chevron-right" size="sm" class="question-go" aria-hidden="true" />
              </button>
            </li>
          </ol>
          <AppPagination
            v-if="questions.data.value.total > QUESTION_PAGE_SIZE"
            v-model:current-page="questionPage"
            :total="questions.data.value.total"
            :page-size="QUESTION_PAGE_SIZE"
            :show-total="false"
          />
        </template>
      </section>

      <div class="side-by-side">
        <section class="block">
          <header class="block-head">
            <h2 class="block-title">{{ t('syllabus.notesTitle') }}</h2>
            <AppButton size="sm" variant="soft" icon-left="plus" :loading="creatingNote" @click="newNote">
              {{ t('syllabus.newNote') }}
            </AppButton>
          </header>
          <p v-if="noteErrorKey" class="notice" role="alert">{{ t(noteErrorKey) }}</p>
          <p v-if="notesHere.length === 0 && !notes.loading.value" class="empty">{{ t('syllabus.noNotes') }}</p>
          <ul v-else class="note-list">
            <li v-for="note in notesHere" :key="note.id">
              <RouterLink :to="{ name: 'notes', query: { note: note.id } }" class="note-link">
                <AppIcon name="notebook-pen" size="sm" aria-hidden="true" />
                <span class="note-title">{{ note.title || t('notes.untitled') }}</span>
                <span class="note-date">{{ d(note.updatedAt, 'short') }}</span>
              </RouterLink>
            </li>
          </ul>
        </section>

        <MaterialsPanel class="block" :node-code="code" />
      </div>
    </template>

    <QuestionDrawer :question-id="openQuestionId" @close="openQuestionId = null" />
    <FocusStartDialog v-model="focusOpen" :initial-node="code" />
  </div>
</template>

<style scoped>
.node-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-8);
}

.crumbs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-2);
}

.crumbs a {
  color: var(--color-text-tertiary);
  text-decoration: none;
}

.crumbs a:hover {
  color: var(--color-primary);
  text-decoration: underline;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.notice {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background-color: var(--color-warning-soft);
  color: var(--color-text);
  font-size: var(--text-sm);
}

.standing {
  display: grid;
  grid-template-columns: minmax(200px, 1fr) minmax(0, 2fr);
  gap: var(--space-6);
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border: var(--border-width-sm) solid var(--color-border);
  border-left: 4px solid var(--paper-accent);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.standing-meter {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.standing-label {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-secondary);
}

.level {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.facts {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.fact {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.fact dt {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.fact dd {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.fact-link {
  color: var(--color-danger);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.block {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.block-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.block-count {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.rows {
  display: flex;
  flex-direction: column;
  padding: var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.empty {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.question-list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  overflow: hidden;
}

.question-list li + li {
  border-top: var(--border-width-sm) solid var(--color-border);
}

.question-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: start;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: none;
  background: none;
  color: var(--color-text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.question-row:hover {
  background-color: var(--color-surface-hover);
}

.question-row:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 3px var(--color-focus-ring);
}

.question-type {
  padding: 1px var(--space-2);
  border-radius: var(--radius-sm);
  background-color: var(--color-muted-soft);
  font-size: var(--text-xs);
  white-space: nowrap;
  color: var(--color-text-secondary);
}

.question-stem {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  font-size: var(--text-sm);
  line-height: 1.6;
}

.question-stem :deep(p) {
  display: inline;
}

.question-year {
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

.question-go {
  color: var(--color-text-tertiary);
}

.side-by-side {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-6);
  align-items: start;
}

.note-list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.note-link {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2);
  border-radius: var(--radius-md);
  color: var(--color-text);
  text-decoration: none;
}

.note-link:hover {
  background-color: var(--color-surface-hover);
}

.note-link :deep(.app-icon) {
  color: var(--color-text-secondary);
}

.note-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
}

.note-date {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

@media (max-width: 900px) {
  .standing {
    grid-template-columns: minmax(0, 1fr);
  }

  .side-by-side {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .node-view {
    padding: var(--space-5) var(--space-4);
  }

  .facts {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
