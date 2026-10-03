<script setup lang="ts">
/**
 * The Ledger — the third band, below the fold and deliberately quiet.
 *
 * This is the former Workspace dashboard's context: practice sets left
 * half-done, recent conversations, recent notes, and the week chart. Nothing here is a
 * commitment — none of these sources carries a time contract, so none of them
 * may enter The Plan. They are demoted, not deleted: the day's answer is
 * above, and this is what the day sits on.
 *
 * It is read-only on purpose. Every row navigates to the module that owns it;
 * the Ledger never mutates, because a second place to act would rebuild the
 * dashboard Today replaced.
 *
 * Step 5 refined it against one rule: **it must never compete with the Plan.**
 * Two things follow. Its links are links rather than buttons — a button is an
 * action affordance, and actions belong to the plan above; the Ledger only ever
 * takes you somewhere. And it renders nothing at all when it has nothing to
 * show, instead of four empty-state cards, which on a `clear` day would make
 * the loudest thing on the page a report that there is nothing to report.
 *
 * Solid surfaces throughout — content and data-viz are never glass
 * (docs/liquid-material-system.md §1).
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppCard, AppIcon, AppTooltip } from '@/components'
import type { IconName } from '@/components'
import type { PracticeMode, PracticeSummaryDto } from '@/api/modules/practice'
import type { WorkspaceSummaryDto } from '@/api/modules/workspace'
import { useDuration } from '@/composables/useDuration'
import { paperAccentColor } from '@/features/syllabus/papers'
import { subjectOfCode } from '@/features/syllabus/syllabusIndex'
import { parseIsoDate } from '@/utils/date'

const props = defineProps<{ summary: WorkspaceSummaryDto }>()

const { t, d, locale } = useI18n()
const { formatMinutes } = useDuration()

/** A note wears its paper's accent; an unanchored one stays neutral. */
function paperAccent(nodeCode: string | null): string {
  return nodeCode ? paperAccentColor(subjectOfCode(nodeCode)) : 'var(--color-muted)'
}

const MODE_ICON: Record<PracticeMode, IconName> = {
  topic: 'crosshair',
  weakness: 'target',
  mistakes: 'book-x',
  random: 'shuffle',
}

function practiceHeading(session: PracticeSummaryDto): string {
  const mode = t(`practice.mode.${session.mode}`)
  return session.title ? `${mode} · ${session.title}` : mode
}

/** A set's accent: its paper when it has one, the brand colour when it spans papers. */
function practiceAccent(session: PracticeSummaryDto): string {
  const paper = session.subject ?? subjectOfCode(session.nodeCode)
  return paper ? paperAccentColor(paper) : 'var(--color-primary)'
}

// --- Week chart — 7 real days, one hue, tooltip per mark -------------------

const weekBars = computed(() => {
  const days = props.summary.weekActivity
  const max = days.reduce((m, day) => Math.max(m, day.minutes), 0)
  // Direct-label selectively: only the (most recent) busiest day.
  const lastMaxIndex = days.reduce(
    (index, day, i) => (max > 0 && day.minutes === max ? i : index),
    -1,
  )
  return days.map((day, i) => ({
    date: day.date,
    minutes: day.minutes,
    heightPercent: max > 0 ? Math.max(4, Math.round((day.minutes / max) * 100)) : 0,
    showLabel: i === lastMaxIndex,
  }))
})

const weekTotalMinutes = computed(() =>
  props.summary.weekActivity.reduce((sum, day) => sum + day.minutes, 0),
)

const weekdayFormat = computed(() => new Intl.DateTimeFormat(locale.value, { weekday: 'short' }))

function barTooltip(bar: { date: string; minutes: number }): string {
  return `${d(parseIsoDate(bar.date), 'short')} · ${formatMinutes(bar.minutes)}`
}

/**
 * The chart as one sentence: the week's total, then every day and its minutes.
 * This is the whole of the chart's data, so a screen-reader user gets what the
 * bars show rather than a shape they cannot see.
 */
const chartLabel = computed(() => {
  const days = weekBars.value
    .map((bar) => `${weekdayFormat.value.format(parseIsoDate(bar.date))} ${formatMinutes(bar.minutes)}`)
    .join(t('today.line.separator'))
  return t('workspace.growth.chartLabel', {
    total: formatMinutes(weekTotalMinutes.value),
    days,
  })
})

/**
 * Whether the Ledger has any context to offer. An account with nothing in it
 * yet gets no band — the `empty` state already gave that user the one action
 * that matters, and a wall of "nothing here" cards is noise dressed as content.
 *
 * Note this is *not* a state derivation: it asks what the summary contains, and
 * never what kind of day it is. `state` stays the server's word alone.
 */
const hasContext = computed(
  () =>
    props.summary.continuePractice.length > 0 ||
    props.summary.recentConversations.length > 0 ||
    props.summary.recentNotes.length > 0 ||
    weekTotalMinutes.value > 0,
)
</script>

<template>
  <section v-if="hasContext" class="ledger" :aria-label="t('today.ledger.title')">
    <h2 class="ledger-heading">{{ t('today.ledger.title') }}</h2>

    <!-- Practice left half-done — resumable exactly where it stopped -->
    <section class="section">
      <div class="section-head">
        <h3 class="section-title">{{ t('workspace.continuePractice.title') }}</h3>
        <RouterLink :to="{ name: 'practice' }" class="section-link">
          {{ t('common.viewAll') }}
          <AppIcon name="arrow-right" size="sm" />
        </RouterLink>
      </div>
      <!--
        A card that navigates is a link, so it is one: tab order, Enter,
        middle-click and open-in-new-tab come with the element.
      -->
      <div v-if="summary.continuePractice.length > 0" class="continue-grid">
        <RouterLink
          v-for="item in summary.continuePractice"
          :key="item.id"
          :to="{ name: 'practice-session', params: { id: item.id } }"
          class="continue-link"
        >
          <AppCard variant="flat" interactive>
            <div class="continue-head">
              <span class="continue-icon" :style="{ color: practiceAccent(item) }" aria-hidden="true">
                <AppIcon :name="MODE_ICON[item.mode]" />
              </span>
              <span class="continue-progress">{{ item.answered }} / {{ item.total }}</span>
            </div>
            <h4 class="continue-name">{{ practiceHeading(item) }}</h4>
            <span class="continue-meta">
              {{ t('workspace.continuePractice.started', { time: d(item.startedAt, 'short') }) }}
            </span>
            <!-- Repeats the count printed above it, so it is decorative. -->
            <div class="progress-track" aria-hidden="true">
              <div
                class="progress-fill"
                :style="{
                  width: `${item.total > 0 ? (item.answered / item.total) * 100 : 0}%`,
                  backgroundColor: practiceAccent(item),
                }"
              ></div>
            </div>
          </AppCard>
        </RouterLink>
      </div>
      <AppCard v-else variant="flat">
        <div class="section-empty">
          <AppIcon name="pencil-line" class="section-empty-icon" aria-hidden="true" />
          <p class="section-empty-text">{{ t('workspace.continuePractice.empty') }}</p>
          <RouterLink :to="{ name: 'practice' }" class="section-empty-link">
            {{ t('workspace.continuePractice.emptyCta') }}
          </RouterLink>
        </div>
      </AppCard>
    </section>

    <div class="two-col">
      <!-- Recent AI conversations -->
      <section class="section">
        <div class="section-head">
          <h3 class="section-title">{{ t('workspace.recentChats.title') }}</h3>
          <RouterLink :to="{ name: 'ai-tutor' }" class="section-link">
            {{ t('common.viewAll') }}
            <AppIcon name="arrow-right" size="sm" />
          </RouterLink>
        </div>
        <AppCard variant="flat" :padded="false">
          <div v-if="summary.recentConversations.length === 0" class="section-empty">
            <AppIcon name="message-square" class="section-empty-icon" aria-hidden="true" />
            <p class="section-empty-text">{{ t('workspace.recentChats.empty') }}</p>
            <RouterLink :to="{ name: 'ai-tutor' }" class="section-empty-link">
              {{ t('workspace.recentChats.emptyCta') }}
            </RouterLink>
          </div>
          <!--
            A list of links, which is what this always was. The rows were
            `<li @click>`: no tab stop, no role, no Enter key. The fix is the
            element that already means "go here", not a tabindex bolted onto a
            list item.
          -->
          <ul v-else class="row-list">
            <li v-for="conv in summary.recentConversations" :key="conv.id" class="row">
              <RouterLink
                :to="{ name: 'ai-tutor', params: { conversationId: conv.id } }"
                class="row-link"
              >
                <span class="row-icon" aria-hidden="true">
                  <AppIcon name="message-square" size="sm" />
                </span>
                <span class="row-text">{{ conv.title }}</span>
                <span class="row-meta">{{ d(conv.updatedAt, 'short') }}</span>
              </RouterLink>
            </li>
          </ul>
        </AppCard>
      </section>

      <!-- Recent notes -->
      <section class="section">
        <div class="section-head">
          <h3 class="section-title">{{ t('workspace.recentNotes.title') }}</h3>
          <RouterLink :to="{ name: 'notes' }" class="section-link">
            {{ t('common.viewAll') }}
            <AppIcon name="arrow-right" size="sm" />
          </RouterLink>
        </div>
        <AppCard variant="flat" :padded="false">
          <div v-if="summary.recentNotes.length === 0" class="section-empty">
            <AppIcon name="notebook-pen" class="section-empty-icon" aria-hidden="true" />
            <p class="section-empty-text">{{ t('workspace.recentNotes.empty') }}</p>
            <RouterLink :to="{ name: 'notes' }" class="section-empty-link">
              {{ t('workspace.recentNotes.emptyCta') }}
            </RouterLink>
          </div>
          <ul v-else class="row-list">
            <li v-for="note in summary.recentNotes" :key="note.id" class="row">
              <RouterLink :to="{ name: 'notes', query: { note: note.id } }" class="row-link">
                <span
                  class="row-dot"
                  :style="{ backgroundColor: paperAccent(note.nodeCode) }"
                  aria-hidden="true"
                ></span>
                <span class="row-text">{{ note.title }}</span>
                <span class="row-meta">{{ d(note.updatedAt, 'short') }}</span>
              </RouterLink>
            </li>
          </ul>
        </AppCard>
      </section>
    </div>

    <!-- Week chart -->
    <section class="section">
      <div class="section-head">
        <h3 class="section-title">{{ t('workspace.growth.title') }}</h3>
        <span v-if="weekTotalMinutes > 0" class="section-link">
          {{ t('workspace.growth.weekTotal', { time: formatMinutes(weekTotalMinutes) }) }}
        </span>
      </div>
      <AppCard variant="flat">
        <!--
          The chart is one image with one accessible name, plus the seven
          readings in text. It previously gave each bar `tabindex="0"` so the
          pointer tooltip could be reached by keyboard — seven tab stops on
          non-interactive divs, which is the tabindex abuse this pass removes.
          Nothing here is actionable, so nothing here takes focus; the data
          arrives as prose instead, which is also faster to hear than tabbing
          through a week.
        -->
        <div v-if="weekTotalMinutes > 0" class="growth-chart">
          <div class="chart-figure" role="img" :aria-label="chartLabel">
            <div class="chart-bars">
              <AppTooltip v-for="bar in weekBars" :key="bar.date" :content="barTooltip(bar)">
                <div class="bar-slot">
                  <span v-if="bar.showLabel" class="bar-label">
                    {{ formatMinutes(bar.minutes) }}
                  </span>
                  <span
                    class="bar"
                    :class="{ zero: bar.minutes === 0 }"
                    :style="bar.minutes > 0 ? { height: `${bar.heightPercent}%` } : undefined"
                  ></span>
                </div>
              </AppTooltip>
            </div>
            <div class="chart-days">
              <span v-for="bar in weekBars" :key="bar.date" class="chart-day">
                {{ weekdayFormat.format(parseIsoDate(bar.date)) }}
              </span>
            </div>
          </div>
        </div>
        <div v-else class="section-empty">
          <AppIcon name="trending-up" class="section-empty-icon" aria-hidden="true" />
          <p class="section-empty-text">{{ t('workspace.growth.empty') }}</p>
          <RouterLink :to="{ name: 'calendar' }" class="section-empty-link">
            {{ t('workspace.growth.emptyCta') }}
          </RouterLink>
        </div>
      </AppCard>
    </section>
  </section>
</template>

<style scoped>
/*
 * "Visually quiet" is spent here: a hairline separates the band from the plan,
 * its heading is a label rather than a title, and everything inside is one
 * step down the type scale from the bands above. The Ledger must never read
 * as a second headline — that is how Today becomes a dashboard again.
 */
.ledger {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  margin-top: var(--space-12);
  padding-top: var(--space-8);
  border-top: var(--border-width-sm) solid var(--color-border);
}

/*
 * Quiet is carried by the type — label scale, uppercase, tracked out, against
 * the plan's body-size titles above. It is *not* carried by low contrast: the
 * tertiary ramp measures 2.6:1 on light and 3.6:1 on dark, and a heading below
 * AA is not restraint, it is a heading some people cannot read.
 */
.ledger-heading {
  margin: 0;
  font-family: var(--font-label-family);
  font-size: var(--font-label-size);
  font-weight: var(--font-label-weight);
  letter-spacing: var(--font-label-tracking);
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.section {
  min-width: 0;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.section-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  letter-spacing: var(--tracking-tight);
  color: var(--color-text-secondary);
}

.section-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  transition: color var(--duration-fast) var(--ease-out);
}

a.section-link:hover {
  color: var(--color-primary);
}

.section-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-6) var(--space-4);
  text-align: center;
}

.section-empty-icon {
  color: var(--color-text-tertiary);
}

.section-empty-text {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

/* A link, not a button. The Ledger points; the Plan acts. The focus ring comes
   from base.css — a text link has no geometry of its own to preserve. */
.section-empty-link {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  transition: color var(--duration-fast) var(--ease-out);
}

.section-empty-link:hover {
  color: var(--color-primary);
}

/* Continue learning */
.continue-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-4);
}

/* The link is the card's shell: it carries no colour of its own, or the global
   anchor colour would repaint every label inside it. */
.continue-link {
  display: block;
  min-width: 0;
  color: inherit;
  border-radius: var(--radius-card);
}

.continue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.continue-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  background-color: color-mix(in srgb, currentColor 12%, transparent);
}

.continue-progress {
  font-size: var(--text-xs);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.continue-name {
  margin: 0 0 var(--space-1);
  font-size: var(--text-base);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.continue-meta {
  display: block;
  margin-bottom: var(--space-3);
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.progress-track {
  height: 4px;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: var(--radius-full);
  transition: width var(--duration-slow) var(--ease-out);
}

/* Row lists (conversations, notes) */
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-6);
}

.row-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.row + .row {
  border-top: var(--border-width-sm) solid var(--color-border);
}

/* The link fills the row, so the whole strip is the target — the click area
   the old `<li @click>` had, now with a tab stop and a role attached to it. */
.row-link {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  color: inherit;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.row-link:hover {
  background-color: var(--color-surface-hover);
}

/* Inset: the list sits flush inside the card's rounded, clipped border, so an
   outward ring on the first or last row would be cut off. */
.row-link:focus-visible {
  outline-offset: -2px;
}

@media (pointer: coarse) {
  .row-link {
    min-height: 44px;
  }
}

.row-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
}

.row-icon {
  display: flex;
  color: var(--color-text-tertiary);
}

.row-text {
  flex: 1;
  min-width: 0;
  font-size: var(--text-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-meta {
  flex-shrink: 0;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

/* Week chart — thin marks, one hue, recessive baseline */
.growth-chart {
  display: flex;
  flex-direction: column;
}

.chart-bars {
  display: flex;
  align-items: stretch;
  height: 120px;
  border-bottom: var(--border-width-sm) solid var(--color-border);
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

.bar {
  width: min(24px, 60%);
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
  color: var(--color-text-secondary);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}

/*
 * At the narrowest tier the cards go one-up rather than squeezing two 200px
 * columns into ~343px of content width, and the section link drops under its
 * title instead of forcing the heading to ellipsis.
 */
@media (max-width: 420px) {
  .continue-grid {
    grid-template-columns: 1fr;
  }

  .section-head {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-1);
  }
}
</style>
