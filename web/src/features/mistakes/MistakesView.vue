<script setup lang="ts">
/**
 * The mistake book (错题本). Every wrong answer lands here on its own — from
 * practice, or captured from paper — and leaves only by being answered right
 * on three separate due days, spaced by the same FSRS scheduler that runs the
 * flashcards. "Resolved" therefore means *retained*, not "looked at once".
 *
 * The page answers three questions in order: what is due today (the redo
 * button), where the mistakes cluster (by paper and by cause — the diagnosis
 * view), and what exactly each one is (the list and its drawer).
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { AppButton, AppEmpty, AppPageHeader, AppPagination, AppSkeleton } from '@/components'
import RichText from '@/components/RichText.vue'
import { useAsync } from '@/composables/useAsync'
import type { ExamSubjectCode } from '@/api/modules/exam'
import {
  getMistakeStats,
  listMistakes,
  MISTAKE_CAUSES,
  type MistakeDto,
  type MistakeListParams,
} from '@/api/modules/mistake'
import { usePracticeLauncher } from '@/features/practice/usePracticeLauncher'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'
import { PAPERS, paperAccentColor } from '@/features/syllabus/papers'
import CaptureMistakeDialog from './components/CaptureMistakeDialog.vue'
import MistakeDrawer from './components/MistakeDrawer.vue'

const { t, d } = useI18n()
const route = useRoute()
const router = useRouter()
const { launch, launching, errorKey: launchErrorKey } = usePracticeLauncher()

const PAGE_SIZE = 20

// --- Filters (in the URL, so a 考点 page can link to its mistakes) ---------------

type StatusFilter = 'active' | 'resolved' | 'all'

function queryString(key: string): string {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

const status = computed<StatusFilter>(() => {
  const value = queryString('status')
  return value === 'resolved' || value === 'all' ? value : 'active'
})
const paper = computed(() => {
  const value = queryString('paper')
  return (PAPERS as string[]).includes(value) ? (value as ExamSubjectCode) : ''
})
const cause = computed(() => queryString('cause'))
const node = computed(() => queryString('node'))
const dueOnly = computed(() => queryString('due') === '1')
const page = ref(1)

function setFilter(key: string, value: string) {
  const query = { ...route.query }
  if (value) query[key] = value
  else delete query[key]
  void router.replace({ query })
}

function onStatus(value: string | number | boolean | undefined) {
  setFilter('status', value === 'active' ? '' : String(value ?? ''))
}

function onDueOnly(value: string | number | boolean) {
  setFilter('due', value ? '1' : '')
}

const params = computed<MistakeListParams>(() => ({
  status: status.value,
  subject: paper.value || undefined,
  cause: cause.value || undefined,
  nodeCode: node.value || undefined,
  due: dueOnly.value || undefined,
  page: page.value,
  size: PAGE_SIZE,
}))

const list = useAsync(() => listMistakes(params.value))
const stats = useAsync(getMistakeStats)

watch(
  () => route.query,
  () => {
    page.value = 1
    void list.reload()
  },
)
watch(page, () => void list.reload())

const hasFilters = computed(() => !!(paper.value || cause.value || node.value || dueOnly.value))

// --- Distributions -------------------------------------------------------------

const causeRows = computed(() => {
  const byCause = stats.data.value?.byCause ?? {}
  const rows = [...MISTAKE_CAUSES, 'undiagnosed']
    .map((code) => ({ code, count: byCause[code] ?? 0 }))
    .filter((row) => row.count > 0)
  const max = Math.max(1, ...rows.map((row) => row.count))
  return rows.sort((a, b) => b.count - a.count).map((row) => ({ ...row, share: row.count / max }))
})

const paperRows = computed(() => {
  const bySubject = stats.data.value?.bySubject ?? {}
  const max = Math.max(1, ...PAPERS.map((code) => bySubject[code] ?? 0))
  return PAPERS.map((code) => ({ code, count: bySubject[code] ?? 0, share: (bySubject[code] ?? 0) / max }))
})

// --- Actions ---------------------------------------------------------------------

function redoDue() {
  void launch({ mode: 'mistakes', subject: paper.value || undefined }, 'redo')
}

const captureOpen = ref(false)
const openId = ref<string | null>(null)

function refresh() {
  void list.reload()
  void stats.reload()
}

function onCaptured(mistake: MistakeDto) {
  refresh()
  openId.value = mistake.id
}

function onDeleted() {
  openId.value = null
  refresh()
}

function dueText(mistake: MistakeDto): string {
  if (mistake.status === 'resolved') {
    return mistake.resolvedAt ? t('mistakes.resolvedOn', { date: d(mistake.resolvedAt, 'short') }) : ''
  }
  return mistake.due ? t('mistakes.dueToday') : t('mistakes.dueOn', { date: d(mistake.dueAt, 'short') })
}
</script>

<template>
  <div class="mistakes">
    <AppPageHeader :title="t('mistakes.title')" :subtitle="t('mistakes.subtitle')">
      <template #actions>
        <div class="header-actions">
          <AppButton variant="soft" icon-left="plus" @click="captureOpen = true">{{ t('mistakes.capture.open') }}</AppButton>
          <AppButton
            icon-left="rotate-ccw"
            :loading="launching === 'redo'"
            :disabled="(stats.data.value?.dueToday ?? 0) === 0 || launching !== null"
            @click="redoDue"
          >
            {{ t('mistakes.redo', { n: stats.data.value?.dueToday ?? 0 }) }}
          </AppButton>
        </div>
      </template>
    </AppPageHeader>

    <p v-if="launchErrorKey" class="notice" role="alert">{{ t(launchErrorKey) }}</p>

    <section class="overview" :aria-label="t('mistakes.overview')">
      <dl class="figures">
        <div class="figure">
          <dt>{{ t('mistakes.figures.active') }}</dt>
          <dd>{{ stats.data.value?.active ?? '—' }}</dd>
        </div>
        <div class="figure">
          <dt>{{ t('mistakes.figures.due') }}</dt>
          <dd :class="{ hot: (stats.data.value?.dueToday ?? 0) > 0 }">{{ stats.data.value?.dueToday ?? '—' }}</dd>
        </div>
        <div class="figure">
          <dt>{{ t('mistakes.figures.resolvedWeek') }}</dt>
          <dd>{{ stats.data.value?.resolvedThisWeek ?? '—' }}</dd>
        </div>
        <div class="figure">
          <dt>{{ t('mistakes.figures.resolved') }}</dt>
          <dd>{{ stats.data.value?.resolved ?? '—' }}</dd>
        </div>
      </dl>

      <div v-if="(stats.data.value?.active ?? 0) > 0" class="distributions">
        <div class="distribution">
          <h2 class="dist-title">{{ t('mistakes.byPaper') }}</h2>
          <ul class="bars">
            <li v-for="row in paperRows" :key="row.code">
              <button
                type="button"
                class="bar-row"
                :class="{ active: paper === row.code }"
                :aria-pressed="paper === row.code"
                @click="setFilter('paper', paper === row.code ? '' : row.code)"
              >
                <span class="bar-label">{{ t(`exam.papers.${row.code}`) }}</span>
                <span class="bar-track">
                  <span
                    class="bar-fill"
                    :style="{ width: `${row.share * 100}%`, backgroundColor: paperAccentColor(row.code) }"
                  ></span>
                </span>
                <span class="bar-count">{{ row.count }}</span>
              </button>
            </li>
          </ul>
        </div>
        <div class="distribution">
          <h2 class="dist-title">{{ t('mistakes.byCause') }}</h2>
          <ul class="bars">
            <li v-for="row in causeRows" :key="row.code">
              <button
                type="button"
                class="bar-row"
                :class="{ active: cause === row.code }"
                :aria-pressed="cause === row.code"
                @click="setFilter('cause', cause === row.code ? '' : row.code)"
              >
                <span class="bar-label">{{ t(`mistakes.cause.${row.code}`) }}</span>
                <span class="bar-track">
                  <span class="bar-fill cause-fill" :style="{ width: `${row.share * 100}%` }"></span>
                </span>
                <span class="bar-count">{{ row.count }}</span>
              </button>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <section class="list-block">
      <div class="filters">
        <el-radio-group :model-value="status" size="small" @update:model-value="onStatus">
          <el-radio-button value="active">{{ t('mistakes.status.active') }}</el-radio-button>
          <el-radio-button value="resolved">{{ t('mistakes.status.resolved') }}</el-radio-button>
          <el-radio-button value="all">{{ t('mistakes.status.all') }}</el-radio-button>
        </el-radio-group>
        <el-checkbox
          v-if="status === 'active'"
          :model-value="dueOnly"
          size="small"
          @update:model-value="onDueOnly"
        >
          {{ t('mistakes.dueOnly') }}
        </el-checkbox>
        <span v-if="node" class="node-filter">
          <NodeChip :code="node" />
          <AppButton size="sm" variant="ghost" icon-left="close" :aria-label="t('mistakes.clearNode')" @click="setFilter('node', '')" />
        </span>
        <AppButton v-if="hasFilters" size="sm" variant="ghost" tone="secondary" @click="router.replace({ query: {} })">
          {{ t('mistakes.clearFilters') }}
        </AppButton>
      </div>

      <AppSkeleton v-if="list.loading.value && !list.data.value" :lines="6" />
      <AppEmpty v-else-if="list.error.value" icon="alert-circle" :title="t(list.error.value.messageKey)">
        <template #action>
          <AppButton size="sm" variant="soft" @click="list.reload">{{ t('common.retry') }}</AppButton>
        </template>
      </AppEmpty>
      <AppEmpty
        v-else-if="(list.data.value?.total ?? 0) === 0"
        icon="book-x"
        :title="hasFilters ? t('mistakes.emptyFiltered') : t(`mistakes.empty.${status}`)"
        :description="hasFilters ? undefined : t('mistakes.emptyHint')"
      />
      <template v-else-if="list.data.value">
        <ul class="list">
          <li v-for="mistake in list.data.value.items" :key="mistake.id">
            <button
              type="button"
              class="row"
              :style="{ '--paper-accent': paperAccentColor(mistake.question.subject) }"
              @click="openId = mistake.id"
            >
              <span class="row-main">
                <RichText :source="mistake.question.stem" class="row-stem" />
                <span class="row-meta">
                  <NodeChip v-for="code in mistake.question.points.slice(0, 2)" :key="code" :code="code" />
                </span>
              </span>
              <span class="row-side">
                <span class="row-cause" :class="{ undiagnosed: !mistake.cause }">
                  {{ mistake.cause ? t(`mistakes.cause.${mistake.cause}`) : t('mistakes.cause.undiagnosed') }}
                </span>
                <span v-if="mistake.status === 'active'" class="row-streak">
                  {{ t('mistakes.streakShort', { streak: mistake.correctStreak, goal: mistake.resolveStreak }) }}
                </span>
                <span class="row-due" :class="{ due: mistake.due && mistake.status === 'active' }">
                  {{ dueText(mistake) }}
                </span>
              </span>
            </button>
          </li>
        </ul>
        <AppPagination
          v-if="list.data.value.total > PAGE_SIZE"
          v-model:current-page="page"
          :total="list.data.value.total"
          :page-size="PAGE_SIZE"
        />
      </template>
    </section>

    <MistakeDrawer :mistake-id="openId" @close="openId = null" @changed="refresh" @deleted="onDeleted" />
    <CaptureMistakeDialog v-model="captureOpen" @captured="onCaptured" />
  </div>
</template>

<style scoped>
.mistakes {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-8);
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
  font-size: var(--text-sm);
  color: var(--color-text);
}

.overview {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: var(--space-5) var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.figures {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
  margin: 0;
}

.figure {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.figure dt {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.figure dd {
  margin: 0;
  font-size: var(--text-2xl);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.figure dd.hot {
  color: var(--color-danger);
}

.distributions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-6);
}

.distribution {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.dist-title {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text);
}

.bars {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.bar-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr) 32px;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-1) var(--space-2);
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
}

.bar-row:hover {
  background-color: var(--color-surface-hover);
}

.bar-row:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.bar-row.active {
  background-color: var(--color-primary-soft);
}

.bar-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-track {
  position: relative;
  height: 8px;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
  overflow: hidden;
}

.bar-fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: var(--radius-full);
}

/* One series, one hue — status red is reserved for state, not for a count. */
.cause-fill {
  background-color: var(--color-primary);
}

.bar-count {
  font-variant-numeric: tabular-nums;
  text-align: right;
  color: var(--color-text-secondary);
}

.list-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.node-filter {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding-left: var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-full);
}

.list {
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

.list li + li {
  border-top: var(--border-width-sm) solid var(--color-border);
}

.row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-4);
  width: 100%;
  padding: var(--space-3) var(--space-4) var(--space-3) var(--space-5);
  border: none;
  box-shadow: inset 3px 0 0 var(--paper-accent);
  background: none;
  color: var(--color-text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.row:hover {
  background-color: var(--color-surface-hover);
}

.row:focus-visible {
  outline: none;
  box-shadow:
    inset 3px 0 0 var(--paper-accent),
    inset 0 0 0 3px var(--color-focus-ring);
}

.row-main {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.row-stem {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  font-size: var(--text-sm);
  line-height: 1.6;
}

.row-stem :deep(p) {
  display: inline;
}

.row-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-3);
}

.row-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-1);
  font-size: var(--text-xs);
  white-space: nowrap;
}

.row-cause {
  padding: 1px var(--space-2);
  border-radius: var(--radius-sm);
  background-color: var(--color-muted-soft);
  color: var(--color-text);
}

.row-cause.undiagnosed {
  background: none;
  color: var(--color-text-tertiary);
  font-style: italic;
}

.row-streak {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.row-due {
  color: var(--color-text-tertiary);
}

.row-due.due {
  color: var(--color-danger);
  font-weight: 600;
}

@media (max-width: 900px) {
  .distributions {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .mistakes {
    padding: var(--space-5) var(--space-4);
  }

  .overview {
    padding: var(--space-4);
  }

  .figures {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
