<script setup lang="ts">
/**
 * The syllabus map — the whole 11408 exam as one tree, weighted by what it is
 * worth and coloured by how well it is held. Every other surface anchors into
 * this tree (notes, decks, questions, mistakes, materials); here the candidate
 * sees it whole and practises any part of it.
 *
 * One paper at a time (`?paper=`), because the papers are sat separately and
 * weighted differently; modules open to chapters, chapters to 考点. Readiness
 * is score-weighted, so a paper's number answers "how much of this paper's
 * score do I already hold" — not "how many boxes have I ticked".
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { AppButton, AppEmpty, AppIcon, AppPageHeader, AppSkeleton } from '@/components'
import { useAsync } from '@/composables/useAsync'
import { getExamProfile, type ExamSubjectCode } from '@/api/modules/exam'
import { getFocus, getMasteryMap, type NodeProgressDto } from '@/api/modules/mastery'
import { useSyllabusStore } from '@/stores/syllabus'
import { usePracticeLauncher } from '@/features/practice/usePracticeLauncher'
import MasteryMeter from './components/MasteryMeter.vue'
import NodeChip from './components/NodeChip.vue'
import NodeRow from './components/NodeRow.vue'
import { PAPER_ICON, PAPERS, paperAccentColor } from './papers'
import type { SyllabusNode } from './syllabusIndex'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const syllabusStore = useSyllabusStore()
const { launch, launching, errorKey: launchErrorKey } = usePracticeLauncher()

onMounted(() => {
  void syllabusStore.load()
})

// --- Which paper -------------------------------------------------------------

const paper = computed<ExamSubjectCode>(() => {
  const requested = route.query.paper
  return typeof requested === 'string' && (PAPERS as string[]).includes(requested)
    ? (requested as ExamSubjectCode)
    : PAPERS[0]!
})

function selectPaper(code: ExamSubjectCode) {
  void router.replace({ query: { ...route.query, paper: code } })
}

const accent = computed(() => paperAccentColor(paper.value))
const doc = computed(() => syllabusStore.subjectDoc(paper.value))

// --- Progress ----------------------------------------------------------------

const mastery = useAsync(getMasteryMap)
const progress = computed(() => {
  const byCode = new Map<string, NodeProgressDto>()
  for (const entry of mastery.data.value ?? []) byCode.set(entry.code, entry)
  return byCode
})

const profile = useAsync(getExamProfile)
const target = computed(() => profile.data.value?.targets[paper.value] ?? null)

const paperProgress = computed(() => progress.value.get(paper.value))
/** Readiness is a score-weighted share, so it converts to points held. */
const heldScore = computed(() => {
  const readiness = paperProgress.value?.readiness
  return readiness === null || readiness === undefined || !doc.value
    ? null
    : Math.round(readiness * doc.value.fullScore)
})

/** Share of the paper's score that has been tested at all, as a whole percent. */
const coverage = computed(() => {
  const value = paperProgress.value?.coverage
  return value === null || value === undefined ? null : Math.round(value * 100)
})

function percentText(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${Math.round(value * 100)}%`
}

const focus = useAsync(() => getFocus(paper.value, 3))
watch(paper, () => void focus.reload())

// --- The tree ----------------------------------------------------------------

const modules = computed<SyllabusNode[]>(() => {
  const root = syllabusStore.node(paper.value)
  return root ? root.children.map((code) => syllabusStore.node(code)).filter((n): n is SyllabusNode => !!n) : []
})

function childrenOf(node: SyllabusNode): SyllabusNode[] {
  return node.children.map((code) => syllabusStore.node(code)).filter((n): n is SyllabusNode => !!n)
}

/** Open nodes. Modules start open (chapters visible); chapters start closed. */
const open = ref(new Set<string>())
const closedModules = ref(new Set<string>())

function isOpen(node: SyllabusNode): boolean {
  return node.kind === 'module' ? !closedModules.value.has(node.code) : open.value.has(node.code)
}

function toggle(node: SyllabusNode) {
  const set = node.kind === 'module' ? closedModules.value : open.value
  if (set.has(node.code)) set.delete(node.code)
  else set.add(node.code)
}

const allOpen = computed(() =>
  modules.value.every((module) => isOpen(module) && childrenOf(module).every((chapter) => isOpen(chapter))),
)

function toggleAll() {
  if (allOpen.value) {
    open.value.clear()
    for (const module of modules.value) closedModules.value.add(module.code)
  } else {
    closedModules.value.clear()
    for (const module of modules.value) for (const chapter of childrenOf(module)) open.value.add(chapter.code)
  }
}

// --- Practice ------------------------------------------------------------------

function practiseNode(code: string) {
  void launch({ mode: 'topic', nodeCode: code }, code)
}

function practisePaper(mode: 'weakness' | 'random') {
  void launch({ mode, subject: paper.value }, `${mode}:${paper.value}`)
}

const loadingTree = computed(() => !syllabusStore.loaded && !syllabusStore.error)
</script>

<template>
  <div class="syllabus">
    <AppPageHeader
      :title="t('syllabus.title')"
      :subtitle="t('syllabus.subtitle', { year: syllabusStore.syllabus?.syllabusYear ?? '' })"
    />

    <div class="papers" role="tablist" :aria-label="t('syllabus.papers')">
      <button
        v-for="code in PAPERS"
        :key="code"
        type="button"
        role="tab"
        class="paper-tab"
        :class="{ active: code === paper }"
        :aria-selected="code === paper"
        :style="{ '--paper-accent': paperAccentColor(code) }"
        @click="selectPaper(code)"
      >
        <AppIcon :name="PAPER_ICON[code]" size="sm" aria-hidden="true" />
        <span class="paper-name">{{ t(`exam.papers.${code}`) }}</span>
        <span class="paper-value">{{ percentText(progress.get(code)?.readiness) }}</span>
      </button>
    </div>

    <p v-if="launchErrorKey" class="launch-error" role="alert">{{ t(launchErrorKey) }}</p>

    <AppEmpty v-if="syllabusStore.error" icon="alert-circle" :title="t(syllabusStore.error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="syllabusStore.load(true)">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>

    <AppSkeleton v-else-if="loadingTree" :lines="8" />

    <template v-else-if="doc">
      <section class="summary" :style="{ '--paper-accent': accent }" :aria-label="t(`exam.papers.${paper}`)">
        <div class="summary-main">
          <p class="summary-kicker">{{ t('syllabus.paperCode', { code: doc.paperCode }) }}</p>
          <h2 class="summary-title">{{ doc.name }}</h2>
          <p class="summary-meta">
            {{ t('syllabus.paperMeta', { score: doc.fullScore, minutes: doc.durationMinutes }) }}
          </p>
          <ul class="sections">
            <li v-for="section in doc.sections" :key="section.code" class="section-chip">
              {{ section.name }}
              <span class="section-count">
                {{ t('syllabus.sectionCount', { count: section.count, total: section.total }) }}
              </span>
            </li>
          </ul>
        </div>
        <div class="summary-side">
          <MasteryMeter
            :value="paperProgress?.readiness"
            :color="accent"
            :label="t('syllabus.readiness')"
          />
          <p class="held">
            <template v-if="heldScore !== null">
              {{ t('syllabus.held', { held: heldScore, full: doc.fullScore }) }}
            </template>
            <template v-else>{{ t('syllabus.heldUnknown') }}</template>
            <span v-if="target !== null" class="target">{{ t('syllabus.target', { n: target }) }}</span>
          </p>
          <p v-if="coverage !== null" class="coverage">{{ t('syllabus.coverage', { p: coverage }) }}</p>
          <div class="summary-actions">
            <AppButton
              size="sm"
              icon-left="target"
              :loading="launching === `weakness:${paper}`"
              :disabled="launching !== null"
              @click="practisePaper('weakness')"
            >
              {{ t('syllabus.practiceWeak') }}
            </AppButton>
            <AppButton
              size="sm"
              variant="soft"
              tone="secondary"
              icon-left="shuffle"
              :loading="launching === `random:${paper}`"
              :disabled="launching !== null"
              @click="practisePaper('random')"
            >
              {{ t('syllabus.practiceRandom') }}
            </AppButton>
          </div>
          <p v-if="doc.scoreApproximate" class="approx">{{ t('syllabus.scoreApproximate') }}</p>
        </div>
      </section>

      <section v-if="(focus.data.value?.length ?? 0) > 0" class="focus">
        <h2 class="block-title">{{ t('syllabus.focusTitle') }}</h2>
        <ul class="focus-list">
          <li v-for="item in focus.data.value" :key="item.nodeCode" class="focus-item">
            <div class="focus-main">
              <RouterLink :to="{ name: 'syllabus-node', params: { code: item.nodeCode } }" class="focus-name">
                {{ syllabusStore.node(item.nodeCode)?.name ?? item.nodeCode }}
              </RouterLink>
              <NodeChip :code="item.nodeCode" :show-paper="false" />
              <span class="focus-reason">
                {{
                  t(`syllabus.reason.${item.reason}`, {
                    n: item.mistakes,
                    p: Math.round((item.mastery ?? 0) * 100),
                  })
                }}
              </span>
            </div>
            <AppButton
              size="sm"
              variant="soft"
              :loading="launching === item.nodeCode"
              :disabled="launching !== null || item.available === 0"
              @click="practiseNode(item.nodeCode)"
            >
              {{ t('syllabus.practice') }}
            </AppButton>
          </li>
        </ul>
      </section>

      <section class="tree" :style="{ '--paper-accent': accent }">
        <header class="tree-head">
          <h2 class="block-title">{{ t('syllabus.treeTitle') }}</h2>
          <AppButton size="sm" variant="ghost" tone="secondary" @click="toggleAll">
            {{ allOpen ? t('syllabus.collapseAll') : t('syllabus.expandAll') }}
          </AppButton>
        </header>

        <p v-if="mastery.error.value" class="tree-note" role="status">
          {{ t(mastery.error.value.messageKey) }}
          <AppButton size="sm" variant="ghost" @click="mastery.reload">{{ t('common.retry') }}</AppButton>
        </p>

        <div class="rows">
          <template v-for="module in modules" :key="module.code">
            <NodeRow
              :node="module"
              :progress="progress.get(module.code)"
              :accent="accent"
              :busy="launching === module.code"
              :locked="launching !== null"
              @practice="practiseNode"
            >
              <template #lead>
                <button
                  type="button"
                  class="disclosure"
                  :aria-expanded="isOpen(module)"
                  :aria-label="t(isOpen(module) ? 'syllabus.collapse' : 'syllabus.expand', { name: module.name })"
                  @click="toggle(module)"
                >
                  <AppIcon :name="isOpen(module) ? 'chevron-down' : 'chevron-right'" size="sm" />
                </button>
              </template>
            </NodeRow>

            <template v-if="isOpen(module)">
              <template v-for="chapter in childrenOf(module)" :key="chapter.code">
                <NodeRow
                  class="depth-1"
                  :node="chapter"
                  :progress="progress.get(chapter.code)"
                  :accent="accent"
                  :busy="launching === chapter.code"
                  :locked="launching !== null"
                  @practice="practiseNode"
                >
                  <template #lead>
                    <button
                      type="button"
                      class="disclosure"
                      :aria-expanded="isOpen(chapter)"
                      :aria-label="t(isOpen(chapter) ? 'syllabus.collapse' : 'syllabus.expand', { name: chapter.name })"
                      @click="toggle(chapter)"
                    >
                      <AppIcon :name="isOpen(chapter) ? 'chevron-down' : 'chevron-right'" size="sm" />
                    </button>
                  </template>
                </NodeRow>

                <template v-if="isOpen(chapter)">
                  <NodeRow
                    v-for="point in childrenOf(chapter)"
                    :key="point.code"
                    class="depth-2"
                    :node="point"
                    :progress="progress.get(point.code)"
                    :accent="accent"
                    :busy="launching === point.code"
                    :locked="launching !== null"
                    @practice="practiseNode"
                  />
                </template>
              </template>
            </template>
          </template>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.syllabus {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-8);
}

.papers {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
}

.paper-tab {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.paper-tab:hover {
  color: var(--color-text);
}

.paper-tab:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.paper-tab.active {
  border-color: var(--paper-accent);
  box-shadow: inset 0 -3px 0 var(--paper-accent);
  color: var(--color-text);
}

.paper-tab.active:focus-visible {
  box-shadow:
    inset 0 -3px 0 var(--paper-accent),
    0 0 0 3px var(--color-focus-ring);
}

.paper-tab :deep(.app-icon) {
  flex-shrink: 0;
  color: var(--paper-accent);
}

.paper-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.paper-value {
  margin-left: auto;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

.launch-error {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background-color: var(--color-warning-soft);
  color: var(--color-text);
  font-size: var(--text-sm);
}

.summary {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: var(--space-6);
  padding: var(--space-5) var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-left: 4px solid var(--paper-accent);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.summary-main,
.summary-side {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

.summary-kicker {
  margin: 0;
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-tertiary);
}

.summary-title {
  margin: 0;
  font-family: var(--font-title-family);
  font-size: var(--font-title-size);
  font-weight: var(--font-title-weight);
  color: var(--color-text);
}

.summary-meta,
.coverage,
.approx {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.approx {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.sections {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: var(--space-1) 0 0;
  padding: 0;
  list-style: none;
}

.section-chip {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-1);
  padding: 2px var(--space-2);
  border-radius: var(--radius-sm);
  background-color: var(--color-muted-soft);
  font-size: var(--text-xs);
  color: var(--color-text);
}

.section-count {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.held {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.target {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.summary-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.block-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.focus {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.focus-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.focus-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
}

.focus-main {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.focus-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text);
  text-decoration: none;
}

.focus-name:hover {
  color: var(--color-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.focus-reason {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.tree {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.tree-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tree-note {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.rows {
  display: flex;
  flex-direction: column;
  padding: var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.depth-1 {
  padding-left: var(--space-8);
}

.depth-2 {
  padding-left: calc(var(--space-8) * 2);
}

.disclosure {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.disclosure:hover {
  background-color: var(--color-muted-soft);
  color: var(--color-text);
}

.disclosure:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

@media (max-width: 900px) {
  .summary {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .syllabus {
    padding: var(--space-5) var(--space-4);
  }

  .papers {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary {
    padding: var(--space-4);
  }

  .depth-1 {
    padding-left: var(--space-4);
  }

  .depth-2 {
    padding-left: var(--space-8);
  }
}
</style>
