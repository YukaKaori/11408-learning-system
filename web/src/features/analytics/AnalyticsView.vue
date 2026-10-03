<script setup lang="ts">
/**
 * The long view of a preparation year. It leads with the one question a
 * candidate brings to this page — how ready am I, paper by paper — and then
 * the evidence under it: what has been practised, where the mistakes come
 * from, which 考点 are weakest, where the study time goes.
 *
 * Every figure is derived on the server from the answer log, the mistake book
 * and the study sessions; nothing is estimated here except the conversion of
 * readiness into points, which is labelled as a conversion ("按掌握度折算"),
 * never presented as a predicted score.
 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  AppButton,
  AppCard,
  AppEmpty,
  AppIcon,
  AppLoading,
  AppPageHeader,
  AppSkeleton,
  AppTooltip,
  StatTile,
} from '@/components'
import { useAsync } from '@/composables/useAsync'
import { useDuration } from '@/composables/useDuration'
import { generateWeakPoints, generateWeeklySummary } from '@/api/modules/ai'
import {
  getActivity,
  getAnalyticsSummary,
  getExamReadiness,
  getSubjectShares,
  type ActivityDayDto,
} from '@/api/modules/analytics'
import { getExamProfile } from '@/api/modules/exam'
import { useSyllabusStore } from '@/stores/syllabus'
import { usePracticeLauncher } from '@/features/practice/usePracticeLauncher'
import MasteryMeter from '@/features/syllabus/components/MasteryMeter.vue'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'
import { paperAccentColor } from '@/features/syllabus/papers'
import { parseIsoDate } from '@/utils/date'

/** 12 weeks × 7 — the heatmap's zero-filled series; the last 7 days feed the AI snapshot. */
const HEATMAP_DAYS = 84
const SHARES_WINDOW_DAYS = 30

const { t, d } = useI18n()
const router = useRouter()
const { formatMinutes } = useDuration()
const syllabusStore = useSyllabusStore()
const { launch, launching, errorKey: launchErrorKey } = usePracticeLauncher()

onMounted(() => {
  void syllabusStore.load()
})

const { data, loading, error, reload } = useAsync(async () => {
  const [summary, activity, shares, readiness, profile] = await Promise.all([
    getAnalyticsSummary(),
    getActivity(HEATMAP_DAYS),
    getSubjectShares(SHARES_WINDOW_DAYS),
    getExamReadiness(),
    getExamProfile(),
  ])
  return { summary, activity, shares, readiness, profile }
})

const showSkeleton = computed(() => loading.value && data.value === null)

function percentText(ratio: number | null | undefined): string {
  return ratio === null || ratio === undefined ? '—' : `${Math.round(ratio * 100)}%`
}

// --- Stat tiles -------------------------------------------------------------

/** Signed delta text; a null metric renders "—", never a fabricated 0. */
function deltaText(value: number | null): string {
  if (value === null) return '—'
  return value > 0 ? `+${value}%` : `${value}%`
}

/** Retention is null with no mature reviews yet → "—", never a fabricated 0%. */
const retentionText = computed(() => {
  const percent = data.value?.summary.retentionPercent
  return percent === null || percent === undefined ? '—' : `${percent}%`
})

/** The last 7 days of the practice series. */
const practiceWeek = computed(() => {
  const days = data.value?.readiness.practice.slice(-7) ?? []
  const answered = days.reduce((sum, day) => sum + day.answered, 0)
  const correct = days.reduce((sum, day) => sum + day.correct, 0)
  return { answered, accuracy: answered > 0 ? correct / answered : null }
})

// --- Readiness ----------------------------------------------------------------

const paperRows = computed(() =>
  (data.value?.readiness.subjects ?? []).map((row) => {
    const fullScore = syllabusStore.subjectDoc(row.subject)?.fullScore ?? null
    return {
      ...row,
      fullScore,
      held: fullScore === null ? null : Math.round(row.readiness * fullScore),
      target: data.value?.profile.targets[row.subject] ?? null,
    }
  }),
)

/** Σ readiness × full score — only when every paper's full score is known. */
const heldTotal = computed(() => {
  const rows = paperRows.value
  if (rows.length === 0 || rows.some((row) => row.held === null)) return null
  return rows.reduce((sum, row) => sum + (row.held ?? 0), 0)
})
const fullTotal = computed(() => paperRows.value.reduce((sum, row) => sum + (row.fullScore ?? 0), 0))
const targetTotal = computed(() => data.value?.profile.targetTotal ?? null)

// --- Practice trend (14 days, one series) ---------------------------------------

const practiceBars = computed(() => {
  const days = data.value?.readiness.practice ?? []
  const max = days.reduce((m, day) => Math.max(m, day.answered), 0)
  // Direct-label selectively: only the (most recent) busiest day.
  const lastMaxIndex = days.reduce((index, day, i) => (max > 0 && day.answered === max ? i : index), -1)
  return days.map((day, i) => ({
    date: day.date,
    answered: day.answered,
    correct: day.correct,
    heightPercent: max > 0 ? Math.max(4, Math.round((day.answered / max) * 100)) : 0,
    showLabel: i === lastMaxIndex,
  }))
})
const practiceTotal = computed(() => practiceBars.value.reduce((sum, bar) => sum + bar.answered, 0))

function practiceTooltip(bar: { date: string; answered: number; correct: number }): string {
  const day = d(parseIsoDate(bar.date), 'short')
  return bar.answered > 0
    ? t('analytics.practice.tooltip', { day, n: bar.answered, p: Math.round((bar.correct / bar.answered) * 100) })
    : t('analytics.practice.tooltipNone', { day })
}

// --- Mistake causes (one series, one hue) -----------------------------------------

const causeRows = computed(() => {
  const causes = data.value?.readiness.mistakeCauses ?? {}
  const rows = Object.entries(causes)
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1])
  const max = Math.max(1, ...rows.map(([, count]) => count))
  return rows.map(([code, count]) => ({ code, count, widthPercent: (count / max) * 100 }))
})

// --- Time shares (30-day window; null bucket = unanchored time) -------------------

const shareRows = computed(() => {
  const shares = data.value?.shares ?? []
  const total = shares.reduce((sum, share) => sum + share.minutes, 0)
  return shares.map((share) => ({
    key: share.subject ?? 'unlinked',
    name: share.subject !== null ? t(`exam.papers.${share.subject}`) : t('analytics.shares.unlinked'),
    color: share.subject !== null ? paperAccentColor(share.subject) : 'var(--color-muted)',
    minutes: share.minutes,
    widthPercent: total > 0 ? (share.minutes / total) * 100 : 0,
    percent: total > 0 ? Math.round((share.minutes / total) * 100) : 0,
  }))
})

// --- Study heatmap (12 week-columns × 7 days, oldest first) -------------------

const heatWeeks = computed<ActivityDayDto[][]>(() => {
  const days = data.value?.activity ?? []
  const weeks: ActivityDayDto[][] = []
  for (let i = 0; i < days.length; i += 7) {
    weeks.push(days.slice(i, i + 7))
  }
  return weeks
})

/** Intensity is relative to the candidate's own busiest day in the window. */
const heatMax = computed(() => data.value?.activity.reduce((m, day) => Math.max(m, day.minutes), 0) ?? 0)

function heatCellColor(minutes: number): string {
  if (minutes === 0 || heatMax.value === 0) return 'var(--color-muted-soft)'
  const intensity = Math.min(1, minutes / heatMax.value)
  const pct = Math.round(15 + intensity * 70)
  return `color-mix(in srgb, var(--color-primary) ${pct}%, var(--color-surface))`
}

const heatLegendSteps = [0, 0.25, 0.5, 0.75, 1]

// --- Practice from here -----------------------------------------------------------

function practise(nodeCode: string) {
  void launch({ mode: 'topic', nodeCode }, nodeCode)
}

// --- AI insights ------------------------------------------------------------
// The /v1/ai/analytics/* endpoints take a client-built snapshot of the real
// figures on this page (see docs/ai-engine.md for the scoping note). The
// snapshot leads with readiness and weaknesses, so the advice is about the
// exam rather than about minutes.

const insightsLoading = ref(false)
const insightsError = ref(false)
const weeklySummaryText = ref<string | null>(null)
const weakPointsText = ref<string | null>(null)
const hasInsights = computed(() => weeklySummaryText.value !== null && weakPointsText.value !== null)

function statsSnapshotText(): string {
  const snapshot = data.value
  if (!snapshot) return ''
  const { summary, readiness } = snapshot
  const lines: string[] = []
  for (const row of paperRows.value) {
    lines.push(
      t('analytics.ai.snapshotPaper', {
        paper: t(`exam.papers.${row.subject}`),
        readiness: percentText(row.readiness),
        coverage: percentText(row.coverage),
        accuracy: percentText(row.accuracy30d),
        answered: row.answered30d,
        mistakes: row.mistakes,
      }),
    )
  }
  if (readiness.weakest.length > 0) {
    lines.push(
      `${t('analytics.ai.snapshotWeakest')}: ${readiness.weakest
        .map((point) => `${syllabusStore.label(point.nodeCode)}（${percentText(point.mastery)}）`)
        .join('；')}`,
    )
  }
  if (causeRows.value.length > 0) {
    lines.push(
      `${t('analytics.ai.snapshotCauses')}: ${causeRows.value
        .map((row) => `${t(`mistakes.cause.${row.code}`)} ${row.count}`)
        .join('，')}`,
    )
  }
  lines.push(
    t('analytics.ai.snapshotPractice', {
      n: practiceWeek.value.answered,
      p: percentText(practiceWeek.value.accuracy),
    }),
  )
  lines.push(
    summary.weekDeltaPercent !== null
      ? t('analytics.ai.snapshotStudyTime', {
          time: formatMinutes(summary.weekMinutes),
          delta: deltaText(summary.weekDeltaPercent),
        })
      : t('analytics.ai.snapshotStudyTimeNoDelta', { time: formatMinutes(summary.weekMinutes) }),
  )
  lines.push(t('analytics.ai.snapshotStreak', { n: summary.streakDays }))
  lines.push(t('analytics.ai.snapshotDays', { n: snapshot.profile.daysRemaining }))
  if (shareRows.value.length > 0) {
    lines.push(
      `${t('analytics.ai.snapshotShares')}: ${shareRows.value
        .map((row) => `${row.name} ${formatMinutes(row.minutes)}`)
        .join(', ')}`,
    )
  }
  return lines.join('\n')
}

async function generateInsights() {
  if (insightsLoading.value || !data.value) return
  insightsLoading.value = true
  insightsError.value = false
  try {
    const snapshot = statsSnapshotText()
    const [weekly, weak] = await Promise.all([generateWeeklySummary(snapshot), generateWeakPoints(snapshot)])
    weeklySummaryText.value = weekly.content
    weakPointsText.value = weak.content
  } catch (caught) {
    console.error(caught)
    insightsError.value = true
  } finally {
    insightsLoading.value = false
  }
}
</script>

<template>
  <div class="page">
    <AppPageHeader :title="t('analytics.title')" :subtitle="t('analytics.subtitle')" />

    <!-- Loading -->
    <div v-if="showSkeleton" aria-hidden="true">
      <div class="stat-row">
        <AppSkeleton v-for="n in 6" :key="n" variant="block" height="104px" />
      </div>
      <AppSkeleton variant="block" height="260px" />
    </div>

    <!-- Error -->
    <AppEmpty v-else-if="error" icon="alert-circle" :title="t(error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>

    <template v-else-if="data">
      <div class="stat-row">
        <StatTile icon="clock" :label="t('analytics.stats.studyTime')">
          {{ formatMinutes(data.summary.weekMinutes) }}
          <span class="tile-delta">
            {{ t('analytics.stats.weekDelta', { delta: deltaText(data.summary.weekDeltaPercent) }) }}
          </span>
        </StatTile>
        <StatTile
          icon="flame"
          :label="t('analytics.stats.streak')"
          :value="t('analytics.stats.streakUnit', { n: data.summary.streakDays })"
        />
        <StatTile icon="pencil-line" :label="t('analytics.stats.practice')">
          {{ t('analytics.stats.practiceUnit', { n: practiceWeek.answered }) }}
          <span class="tile-delta">
            {{ t('analytics.stats.practiceAccuracy', { p: percentText(practiceWeek.accuracy) }) }}
          </span>
        </StatTile>
        <StatTile icon="book-x" :label="t('analytics.stats.mistakes')">
          {{ data.readiness.activeMistakes }}
          <span class="tile-delta">{{ t('analytics.stats.mistakesDue', { n: data.readiness.dueMistakes }) }}</span>
        </StatTile>
        <StatTile
          icon="layers"
          :label="t('analytics.stats.reviews')"
          :value="t('analytics.stats.reviewsUnit', { n: data.summary.reviewsThisWeek })"
        />
        <StatTile icon="target" :label="t('analytics.stats.retention')" :value="retentionText" />
      </div>

      <p v-if="launchErrorKey" class="notice" role="alert">{{ t(launchErrorKey) }}</p>

      <!-- Readiness: the page's one headline, then each paper as a labelled meter. -->
      <AppCard variant="flat" class="chart-card readiness-card">
        <div class="readiness-head">
          <div>
            <h2 class="chart-title">{{ t('analytics.readiness.title') }}</h2>
            <p class="chart-desc">{{ t('analytics.readiness.desc') }}</p>
          </div>
          <div v-if="heldTotal !== null" class="hero">
            <p class="hero-figure">
              <span class="hero-value">{{ heldTotal }}</span>
              <span class="hero-of">/ {{ fullTotal }}</span>
            </p>
            <p class="hero-caption">
              {{ t('analytics.readiness.heldCaption') }}
              <template v-if="targetTotal !== null">· {{ t('analytics.readiness.target', { n: targetTotal }) }}</template>
            </p>
          </div>
        </div>

        <table class="paper-table">
          <thead>
            <tr>
              <th scope="col">{{ t('analytics.readiness.paper') }}</th>
              <th scope="col">{{ t('analytics.readiness.readiness') }}</th>
              <th scope="col" class="num">{{ t('analytics.readiness.held') }}</th>
              <th scope="col" class="num">{{ t('analytics.readiness.coverage') }}</th>
              <th scope="col" class="num">{{ t('analytics.readiness.accuracy') }}</th>
              <th scope="col" class="num">{{ t('analytics.readiness.mistakes') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in paperRows" :key="row.subject">
              <th scope="row">
                <RouterLink :to="{ name: 'syllabus', query: { paper: row.subject } }" class="paper-link">
                  <span class="paper-dot" :style="{ backgroundColor: paperAccentColor(row.subject) }"></span>
                  {{ t(`exam.papers.${row.subject}`) }}
                </RouterLink>
              </th>
              <td class="meter-cell">
                <MasteryMeter
                  :value="row.coverage > 0 ? row.readiness : null"
                  :color="paperAccentColor(row.subject)"
                  :label="t('analytics.readiness.readinessOf', { paper: t(`exam.papers.${row.subject}`) })"
                />
              </td>
              <td class="num">
                <template v-if="row.held !== null">{{ row.held }} / {{ row.fullScore }}</template>
                <template v-else>—</template>
                <span v-if="row.target !== null" class="cell-sub">{{ t('analytics.readiness.target', { n: row.target }) }}</span>
              </td>
              <td class="num">{{ percentText(row.coverage) }}</td>
              <td class="num">
                {{ percentText(row.accuracy30d) }}
                <span class="cell-sub">{{ t('analytics.readiness.answered', { n: row.answered30d }) }}</span>
              </td>
              <td class="num">{{ row.mistakes }}</td>
            </tr>
          </tbody>
        </table>
      </AppCard>

      <div class="two-col">
        <AppCard variant="flat" class="chart-card">
          <h2 class="chart-title">{{ t('analytics.practice.title') }}</h2>
          <p class="chart-desc">{{ t('analytics.practice.desc') }}</p>
          <div v-if="practiceTotal > 0" class="column-chart">
            <div class="chart-bars">
              <AppTooltip v-for="bar in practiceBars" :key="bar.date" :content="practiceTooltip(bar)">
                <div class="bar-slot" tabindex="0" :aria-label="practiceTooltip(bar)">
                  <span v-if="bar.showLabel" class="bar-label">{{ bar.answered }}</span>
                  <span
                    class="bar"
                    :class="{ zero: bar.answered === 0 }"
                    :style="bar.answered > 0 ? { height: `${bar.heightPercent}%` } : undefined"
                  ></span>
                </div>
              </AppTooltip>
            </div>
            <div class="chart-days">
              <span v-for="(bar, i) in practiceBars" :key="bar.date" class="chart-day">
                {{ i % 2 === practiceBars.length % 2 ? '' : d(parseIsoDate(bar.date), 'short') }}
              </span>
            </div>
          </div>
          <div v-else class="chart-empty">
            <AppIcon name="pencil-line" class="chart-empty-icon" aria-hidden="true" />
            <p class="chart-empty-text">{{ t('analytics.practice.empty') }}</p>
            <AppButton size="sm" variant="soft" @click="router.push({ name: 'practice' })">
              {{ t('analytics.practice.emptyCta') }}
            </AppButton>
          </div>
        </AppCard>

        <AppCard variant="flat" class="chart-card">
          <h2 class="chart-title">{{ t('analytics.causes.title') }}</h2>
          <p class="chart-desc">{{ t('analytics.causes.desc') }}</p>
          <ul v-if="causeRows.length > 0" class="share-list">
            <li v-for="row in causeRows" :key="row.code" class="share-row">
              <span class="share-name">{{ t(`mistakes.cause.${row.code}`) }}</span>
              <div class="share-track">
                <div class="share-fill cause-fill" :style="{ width: `${row.widthPercent}%` }"></div>
              </div>
              <span class="share-value">{{ row.count }}</span>
            </li>
          </ul>
          <div v-else class="chart-empty">
            <AppIcon name="book-x" class="chart-empty-icon" aria-hidden="true" />
            <p class="chart-empty-text">{{ t('analytics.causes.empty') }}</p>
          </div>
        </AppCard>
      </div>

      <div class="two-col">
        <AppCard variant="flat" class="chart-card">
          <h2 class="chart-title">{{ t('analytics.weakest.title') }}</h2>
          <p class="chart-desc">{{ t('analytics.weakest.desc') }}</p>
          <ul v-if="data.readiness.weakest.length > 0" class="weak-list">
            <li v-for="point in data.readiness.weakest" :key="point.nodeCode" class="weak-row">
              <RouterLink :to="{ name: 'syllabus-node', params: { code: point.nodeCode } }" class="weak-link">
                <NodeChip :code="point.nodeCode" />
              </RouterLink>
              <MasteryMeter
                compact
                :value="point.mastery"
                :level="point.mastery < 0.6 ? 'weak' : 'developing'"
                :label="t('syllabus.mastery')"
              />
              <AppButton
                size="sm"
                variant="ghost"
                :loading="launching === point.nodeCode"
                :disabled="launching !== null"
                :aria-label="t('syllabus.practiceNode', { name: syllabusStore.node(point.nodeCode)?.name ?? point.nodeCode })"
                @click="practise(point.nodeCode)"
              >
                {{ t('syllabus.practice') }}
              </AppButton>
            </li>
          </ul>
          <div v-else class="chart-empty">
            <AppIcon name="target" class="chart-empty-icon" aria-hidden="true" />
            <p class="chart-empty-text">{{ t('analytics.weakest.empty') }}</p>
          </div>
        </AppCard>

        <AppCard variant="flat" class="chart-card">
          <h2 class="chart-title">{{ t('analytics.shares.title') }}</h2>
          <p class="chart-desc">{{ t('analytics.shares.desc') }}</p>
          <ul v-if="shareRows.length > 0" class="share-list">
            <li v-for="row in shareRows" :key="row.key" class="share-row">
              <span class="share-dot" :style="{ backgroundColor: row.color }"></span>
              <span class="share-name">{{ row.name }}</span>
              <div class="share-track">
                <AppTooltip :content="formatMinutes(row.minutes)" placement="top">
                  <div class="share-fill" :style="{ width: `${row.widthPercent}%`, backgroundColor: row.color }"></div>
                </AppTooltip>
              </div>
              <span class="share-value">{{ row.percent }}%</span>
            </li>
          </ul>
          <div v-else class="chart-empty">
            <AppIcon name="clock" class="chart-empty-icon" aria-hidden="true" />
            <p class="chart-empty-text">{{ t('analytics.shares.empty') }}</p>
            <AppButton size="sm" variant="soft" @click="router.push({ name: 'calendar' })">
              {{ t('analytics.shares.emptyCta') }}
            </AppButton>
          </div>
        </AppCard>
      </div>

      <AppCard variant="flat" class="chart-card heatmap-card">
        <div class="heatmap-head">
          <div>
            <h2 class="chart-title">{{ t('analytics.heatmap.title') }}</h2>
            <p class="chart-desc">{{ t('analytics.heatmap.desc') }}</p>
          </div>
          <div class="heatmap-legend">
            <span>{{ t('analytics.heatmap.less') }}</span>
            <span
              v-for="step in heatLegendSteps"
              :key="step"
              class="legend-swatch"
              :style="{ backgroundColor: heatCellColor(step * heatMax) }"
            ></span>
            <span>{{ t('analytics.heatmap.more') }}</span>
          </div>
        </div>
        <div class="heatmap-grid" role="img" :aria-label="t('analytics.heatmap.title')">
          <div v-for="(week, wi) in heatWeeks" :key="wi" class="heatmap-week">
            <AppTooltip
              v-for="cell in week"
              :key="cell.date"
              :content="`${d(parseIsoDate(cell.date), 'short')} · ${formatMinutes(cell.minutes)}`"
              placement="top"
            >
              <span class="heat-cell" :style="{ backgroundColor: heatCellColor(cell.minutes) }"></span>
            </AppTooltip>
          </div>
        </div>
      </AppCard>

      <AppCard variant="flat" class="chart-card insights-card">
        <div class="insights-head">
          <div>
            <h2 class="chart-title">{{ t('analytics.ai.title') }}</h2>
            <p class="chart-desc">{{ t('analytics.ai.desc') }}</p>
          </div>
          <AppButton size="sm" variant="soft" icon-left="sparkles" :loading="insightsLoading" @click="generateInsights">
            {{ hasInsights ? t('analytics.ai.regenerate') : t('analytics.ai.generate') }}
          </AppButton>
        </div>

        <AppLoading v-if="insightsLoading" :label="t('analytics.ai.generating')" />
        <p v-else-if="insightsError" class="insights-error">{{ t('analytics.ai.error') }}</p>
        <AppEmpty v-else-if="!hasInsights" :title="t('analytics.ai.empty')" />
        <div v-else class="insights-grid">
          <div class="insight-block">
            <h3 class="insight-title">
              <AppIcon name="trending-up" size="sm" />
              {{ t('analytics.ai.weeklySummary') }}
            </h3>
            <p class="insight-text">{{ weeklySummaryText }}</p>
          </div>
          <div class="insight-block">
            <h3 class="insight-title">
              <AppIcon name="target" size="sm" />
              {{ t('analytics.ai.weakPoints') }}
            </h3>
            <p class="insight-text">{{ weakPointsText }}</p>
          </div>
        </div>
      </AppCard>
    </template>
  </div>
</template>

<style scoped>
.page {
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-8);
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}

.tile-delta {
  display: block;
  margin-top: 2px;
  font-size: var(--text-xs);
  font-weight: 400;
  letter-spacing: normal;
  color: var(--color-text-tertiary);
}

.notice {
  margin: 0 0 var(--space-5);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background-color: var(--color-warning-soft);
  font-size: var(--text-sm);
  color: var(--color-text);
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-5);
  margin-bottom: var(--space-5);
}

.chart-title {
  margin: 0 0 var(--space-1);
  font-size: var(--text-base);
  font-weight: 600;
}

.chart-desc {
  margin: 0 0 var(--space-6);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

/* Designed chart empty state */
.chart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 160px;
  text-align: center;
}

.chart-empty-icon {
  color: var(--color-text-tertiary);
}

.chart-empty-text {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

/* Readiness — one hero figure, then a real table (the chart's table view is itself) */
.readiness-card {
  margin-bottom: var(--space-5);
}

.readiness-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-6);
  margin-bottom: var(--space-4);
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-1);
  flex-shrink: 0;
}

.hero-figure {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  margin: 0;
}

.hero-value {
  font-size: 48px;
  font-weight: 650;
  line-height: 1;
  color: var(--color-text);
}

.hero-of {
  font-size: var(--text-lg);
  color: var(--color-text-secondary);
}

.hero-caption {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.paper-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.paper-table th,
.paper-table td {
  padding: var(--space-3) var(--space-3);
  border-bottom: 1px solid var(--color-border);
  text-align: left;
  vertical-align: middle;
}

.paper-table thead th {
  padding-top: 0;
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--color-text-tertiary);
}

.paper-table tbody tr:last-child th,
.paper-table tbody tr:last-child td {
  border-bottom: none;
}

.paper-table .num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.meter-cell {
  width: 32%;
  min-width: 140px;
}

.paper-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 500;
  color: var(--color-text);
  text-decoration: none;
  white-space: nowrap;
}

.paper-link:hover {
  color: var(--color-primary);
}

.paper-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
}

.cell-sub {
  display: block;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

/* Column chart — thin marks, one hue, honest zero stubs */
.column-chart {
  display: flex;
  flex-direction: column;
}

.chart-bars {
  display: flex;
  align-items: stretch;
  height: 160px;
  border-bottom: 1px solid var(--color-border);
}

.bar-slot {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-1);
  border-radius: var(--radius-sm);
  cursor: default;
}

.bar-slot:focus-visible {
  outline: 2px solid var(--color-focus-ring);
  outline-offset: 2px;
}

.bar {
  width: min(20px, 70%);
  border-radius: 4px 4px 0 0;
  background-color: var(--color-primary);
  transition:
    height var(--duration-slow) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.bar-slot:hover .bar:not(.zero) {
  background-color: var(--color-primary-hover);
}

.bar.zero {
  height: 3px;
  background-color: var(--color-muted-soft);
}

.bar-label {
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.chart-days {
  display: flex;
  padding-top: var(--space-2);
}

.chart-day {
  flex: 1;
  min-width: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  text-align: center;
  overflow: hidden;
  white-space: nowrap;
}

/* Row bars (causes, time shares) */
.share-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.share-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.share-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
}

.share-name {
  width: 96px;
  flex-shrink: 0;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-track {
  flex: 1;
  height: 8px;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
  overflow: hidden;
}

.share-fill {
  height: 100%;
  min-width: 2px;
  border-radius: var(--radius-full);
}

.cause-fill {
  background-color: var(--color-primary);
}

.share-value {
  width: 36px;
  flex-shrink: 0;
  text-align: right;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

/* Weakest 考点 */
.weak-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.weak-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(80px, 120px) auto;
  align-items: center;
  gap: var(--space-3);
}

.weak-link {
  display: inline-flex;
  min-width: 0;
  text-decoration: none;
}

.weak-link:hover :deep(.path) {
  color: var(--color-primary);
  text-decoration: underline;
}

/* Heatmap */
.heatmap-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.heatmap-legend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

.legend-swatch {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.heatmap-grid {
  display: flex;
  gap: 3px;
  overflow-x: auto;
  padding-bottom: var(--space-1);
}

.heatmap-week {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.heat-cell {
  display: block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

/* AI insights */
.insights-card {
  margin-top: var(--space-5);
}

.insights-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.insights-error {
  margin: 0;
  padding: var(--space-4) 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.insights-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-5);
}

.insight-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-primary);
}

.insight-text {
  margin: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

@media (max-width: 900px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .two-col {
    grid-template-columns: 1fr;
  }

  .insights-grid {
    grid-template-columns: 1fr;
  }

  .readiness-head {
    flex-direction: column;
    gap: var(--space-2);
  }

  .hero {
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .page {
    padding: var(--space-5);
  }

  /* The table keeps paper, meter and held score; the rest lives on the paper's map. */
  .paper-table th:nth-child(n + 4),
  .paper-table td:nth-child(n + 4) {
    display: none;
  }
}
</style>
