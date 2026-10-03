<script setup lang="ts">
/**
 * 模考与真题 — the paper level of the loop.
 *
 * The 考点 pages say how much of a paper is secured point by point; this page
 * says what a whole paper, sat in 180 minutes, actually yields — the number a
 * target is written in. Four paper cards across the top (estimate against
 * target, at a glance) select the paper below: its trajectory, where its
 * points are lost section by section, its 真题 shelf, and its records.
 *
 * Every figure comes from the overview read model; nothing is computed here
 * but layout. The estimate is labelled as what it is — a recency-weighted
 * average of recent whole papers — never a prediction.
 *
 * Solid throughout: scores and records are content (material constitution §1).
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { AppButton, AppEmpty, AppIcon, AppPageHeader, AppSkeleton } from '@/components'
import { useAsync } from '@/composables/useAsync'
import type { ExamSubjectCode } from '@/api/modules/exam'
import {
  getSittingOverview,
  listSittings,
  type PaperSittingOverviewDto,
  type SittingDto,
} from '@/api/modules/sitting'
import { useSyllabusStore } from '@/stores/syllabus'
import { PAPER_ICON, PAPERS, paperAccentColor } from '@/features/syllabus/papers'
import { parseIsoDate } from '@/utils/date'
import { formatScore, sittingTitle } from './sittingFormat'
import ScoreTrend from './components/ScoreTrend.vue'
import SectionProfile from './components/SectionProfile.vue'
import PastPaperShelf from './components/PastPaperShelf.vue'
import SittingDialog from './components/SittingDialog.vue'

const { t, d } = useI18n()
const route = useRoute()
const router = useRouter()
const syllabusStore = useSyllabusStore()

const { data: overview, loading, error, reload } = useAsync(getSittingOverview)
const { data: records, reload: reloadRecords } = useAsync(() => listSittings(undefined, 200))

onMounted(() => {
  void syllabusStore.load()
})

const selected = ref<ExamSubjectCode | null>(null)

/** Until the candidate chooses: the paper of the latest record, else the first. */
watch(records, (list) => {
  if (selected.value === null && list) selected.value = list[0]?.subject ?? PAPERS[0] ?? 'politics'
})

const paper = computed<PaperSittingOverviewDto | undefined>(() =>
  overview.value?.papers.find((entry) => entry.subject === selected.value),
)
const paperDoc = computed(() =>
  selected.value ? syllabusStore.subjectDoc(selected.value) : undefined,
)
const sectionNames = computed<Record<string, string>>(() =>
  Object.fromEntries(
    (paperDoc.value?.sections ?? []).map((section) => [section.code, section.name]),
  ),
)
const paperRecords = computed(() =>
  (records.value ?? []).filter((record) => record.subject === selected.value),
)

function gapText(entry: PaperSittingOverviewDto): string {
  if (!entry.estimate || entry.target === null) return ''
  const gap = entry.target - entry.estimate.score
  return gap > 0
    ? t('sittings.estimate.below', { n: formatScore(gap) })
    : t('sittings.estimate.above', { n: formatScore(-gap) })
}

// --- recording ---------------------------------------------------------------

const dialogOpen = ref(false)
const editing = ref<SittingDto | null>(null)
const prefill = ref<{
  subject?: ExamSubjectCode | null
  minutes?: number | null
  date?: string | null
} | null>(null)
const prefillYear = ref<number | null>(null)

function logNew(subject: ExamSubjectCode | null = selected.value, year: number | null = null) {
  editing.value = null
  prefill.value = { subject }
  prefillYear.value = year
  dialogOpen.value = true
}

function edit(record: SittingDto) {
  editing.value = record
  prefill.value = null
  prefillYear.value = null
  dialogOpen.value = true
}

/**
 * Arriving from a stopped timer (`?log=1&subject=…&minutes=…&date=…`): the
 * dialog opens prefilled, and the query is consumed so a reload does not
 * open it again.
 */
onMounted(() => {
  if (route.query.log !== '1') return
  const subject =
    typeof route.query.subject === 'string' ? (route.query.subject as ExamSubjectCode) : null
  const minutes = Number(route.query.minutes)
  editing.value = null
  prefill.value = {
    subject: subject && PAPERS.includes(subject) ? subject : selected.value,
    minutes: Number.isFinite(minutes) && minutes > 0 ? minutes : null,
    date: typeof route.query.date === 'string' ? route.query.date : null,
  }
  if (prefill.value.subject) selected.value = prefill.value.subject
  dialogOpen.value = true
  void router.replace({ name: 'sittings' })
})

async function onSaved(saved: SittingDto) {
  selected.value = saved.subject
  await Promise.all([reload(), reloadRecords()])
}

async function onDeleted() {
  await Promise.all([reload(), reloadRecords()])
}
</script>

<template>
  <div class="sittings">
    <AppPageHeader :title="t('sittings.title')" :subtitle="t('sittings.subtitle')">
      <template #actions>
        <AppButton icon-left="plus" @click="logNew()">{{ t('sittings.log') }}</AppButton>
      </template>
    </AppPageHeader>

    <div v-if="loading && !overview" class="skeleton" aria-busy="true" aria-hidden="true">
      <AppSkeleton v-for="n in 4" :key="n" variant="block" height="112px" />
    </div>

    <AppEmpty v-else-if="error" role="alert" icon="alert-circle" :title="t(error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>

    <template v-else-if="overview">
      <!-- The four papers at a glance; each card selects its paper below. -->
      <div class="papers" role="tablist" :aria-label="t('sittings.papersLabel')">
        <button
          v-for="entry in overview.papers"
          :key="entry.subject"
          type="button"
          role="tab"
          class="paper-card"
          :class="{ active: entry.subject === selected }"
          :aria-selected="entry.subject === selected"
          :style="{ '--paper-accent': paperAccentColor(entry.subject) }"
          @click="selected = entry.subject"
        >
          <span class="paper-name">
            <AppIcon :name="PAPER_ICON[entry.subject]" size="sm" class="paper-icon" />
            {{ t(`exam.papers.${entry.subject}`) }}
          </span>
          <span v-if="entry.estimate" class="paper-score">
            {{ formatScore(entry.estimate.score)
            }}<span class="paper-full"> / {{ entry.fullScore }}</span>
          </span>
          <span v-else class="paper-score none">—</span>
          <span class="paper-meta">
            <template v-if="entry.target !== null">{{
              t('sittings.estimate.target', { n: entry.target })
            }}</template>
            <template v-else>{{ t('sittings.estimate.noTarget') }}</template>
            <template v-if="entry.estimate"> · {{ gapText(entry) }}</template>
          </span>
        </button>
      </div>

      <section
        v-if="paper"
        class="detail"
        role="tabpanel"
        :aria-label="t(`exam.papers.${paper.subject}`)"
      >
        <!-- The estimate, said honestly. -->
        <div class="estimate">
          <div class="estimate-figure">
            <span class="estimate-label">{{ t('sittings.estimate.label') }}</span>
            <span v-if="paper.estimate" class="estimate-value">
              {{ formatScore(paper.estimate.score)
              }}<span class="estimate-full"> / {{ paper.fullScore }}</span>
            </span>
            <span v-else class="estimate-value none">—</span>
          </div>
          <div class="estimate-text">
            <template v-if="paper.estimate">
              <p class="estimate-basis">
                {{ t('sittings.estimate.basis', { n: paper.estimate.sittings }) }}
                <template v-if="paper.estimate.sittings > 1">
                  ·
                  {{
                    t('sittings.estimate.range', {
                      low: formatScore(paper.estimate.low),
                      high: formatScore(paper.estimate.high),
                    })
                  }}
                </template>
                <template v-if="paper.target !== null"> · {{ gapText(paper) }}</template>
              </p>
              <p class="estimate-honest">{{ t('sittings.estimate.honest') }}</p>
            </template>
            <template v-else>
              <p class="estimate-basis">{{ t('sittings.estimate.none') }}</p>
              <p class="estimate-honest">{{ t('sittings.estimate.noneHint') }}</p>
            </template>
          </div>
        </div>

        <div class="grid">
          <article class="panel trend">
            <h2 class="panel-title">{{ t('sittings.trend.title') }}</h2>
            <ScoreTrend
              v-if="paper.trend.length >= 2"
              :points="paper.trend"
              :full-score="paper.fullScore"
              :target="paper.target"
              :accent="paperAccentColor(paper.subject)"
            />
            <p v-else class="panel-empty">{{ t('sittings.trend.empty') }}</p>
          </article>

          <article class="panel">
            <h2 class="panel-title">{{ t('sittings.sections.title') }}</h2>
            <SectionProfile
              :sections="paper.sections"
              :names="sectionNames"
              :accent="paperAccentColor(paper.subject)"
            />
          </article>
        </div>

        <article class="panel">
          <div class="panel-head">
            <h2 class="panel-title">{{ t('sittings.shelf.title') }}</h2>
            <span class="panel-hint">{{ t('sittings.shelf.hint') }}</span>
          </div>
          <PastPaperShelf
            :years="paper.pastPapers.years"
            :full-score="paper.fullScore"
            @pick="(year) => logNew(paper!.subject, year)"
          />
        </article>

        <article class="panel">
          <h2 class="panel-title">{{ t('sittings.list.title') }}</h2>
          <ul v-if="paperRecords.length > 0" class="records">
            <li v-for="record in paperRecords" :key="record.id">
              <button type="button" class="record" @click="edit(record)">
                <span class="record-main">
                  <span class="record-title">{{ sittingTitle(t, record) }}</span>
                  <span class="record-meta">
                    {{ t(`sittings.kind.${record.kind}`) }} ·
                    {{ d(parseIsoDate(record.satOn), 'short') }}
                    <template v-if="record.durationMinutes">
                      · {{ t('sittings.list.duration', { n: record.durationMinutes }) }}</template
                    >
                    <template v-if="!record.complete"> · {{ t('sittings.list.partial') }}</template>
                  </span>
                  <span v-if="record.note" class="record-note">{{ record.note }}</span>
                </span>
                <span class="record-score">
                  {{ formatScore(record.score)
                  }}<span class="record-full"> / {{ formatScore(record.fullScore) }}</span>
                </span>
              </button>
            </li>
          </ul>
          <p v-else class="panel-empty">{{ t('sittings.list.empty') }}</p>
        </article>
      </section>
    </template>

    <SittingDialog
      v-if="overview"
      v-model="dialogOpen"
      :sitting="editing"
      :initial="prefill ? { ...prefill } : null"
      :initial-year="prefillYear"
      :latest-paper-year="overview.latestPaperYear"
      @saved="onSaved"
      @deleted="onDeleted"
    />
  </div>
</template>

<style scoped>
.sittings {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-8);
}

.skeleton {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.papers {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.paper-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-1);
  min-width: 0;
  padding: var(--space-4);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.paper-card:hover {
  border-color: var(--color-border-strong);
}

.paper-card:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.paper-card.active {
  border-color: var(--paper-accent);
  box-shadow: inset 0 0 0 1px var(--paper-accent);
}

.paper-name {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.paper-icon {
  color: var(--paper-accent);
}

.paper-score {
  font-size: var(--text-2xl);
  font-weight: 600;
  line-height: 1.2;
  color: var(--color-text);
}

.paper-score.none {
  color: var(--color-text-tertiary);
}

.paper-full {
  font-size: var(--text-sm);
  font-weight: 400;
  color: var(--color-text-tertiary);
}

.paper-meta {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.estimate {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  padding: var(--space-5) var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.estimate-figure {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  gap: var(--space-1);
}

.estimate-label {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.estimate-value {
  font-size: 48px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--color-text);
}

.estimate-value.none {
  color: var(--color-text-tertiary);
}

.estimate-full {
  font-size: var(--text-base);
  font-weight: 400;
  letter-spacing: 0;
  color: var(--color-text-tertiary);
}

.estimate-text {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.estimate-basis {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.estimate-honest {
  margin: 0;
  max-width: 60ch;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--color-text-tertiary);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: var(--space-4);
}

.panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-5);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.panel-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.panel-hint {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.panel-empty {
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-text-tertiary);
}

.records {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.records li + li {
  border-top: var(--border-width-sm) solid var(--color-border);
}

.record {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  width: 100%;
  padding: var(--space-3) var(--space-1);
  border: none;
  background: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.record:hover .record-title {
  color: var(--color-primary);
}

.record:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
  border-radius: var(--radius-sm);
}

.record-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.record-title {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.record-meta {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.record-note {
  overflow: hidden;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-score {
  flex-shrink: 0;
  font-size: var(--text-lg);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.record-full {
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-text-tertiary);
}

@media (max-width: 960px) {
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .sittings {
    padding: var(--space-4);
  }

  .papers,
  .skeleton {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .estimate {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
    padding: var(--space-4);
  }

  .estimate-value {
    font-size: 40px;
  }
}
</style>
