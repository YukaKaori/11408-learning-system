<script setup lang="ts">
/**
 * Today — one prioritized, actionable, completable answer to "what should I do
 * right now?". This is not a dashboard: a dashboard reports state, Today
 * issues a plan, and rank is the product.
 *
 * Five bands in descending priority — The Line (who/where/how far, and how
 * many days are left before the exam), The Time (the study day in hours, per
 * paper, from the plan — one tap starts the timer), The Plan (the view), The
 * Focus (what the mastery model suggests practising next — an offer, never a
 * commitment), The Ledger (inherited context, demoted). The Line renders
 * before any data; the plan is still the only thing the server ranks; the
 * time band frames the day without entering it.
 *
 * **The server is the source of truth for rank, cap and state.** This view
 * renders `plan` in the order it arrived and renders `state` as given. It
 * never sorts, never re-tiers, never decides that a day is complete. Doing any
 * of that here would let two clients disagree about the same day, and would
 * risk congratulating a user who did nothing.
 *
 * **Material:** solid throughout. Today is the work, and the work is never
 * glass (docs/liquid-material-system.md §1, docs/phase17-material-audit.md).
 * No GlassSurface, no backdrop-filter, no material preset — the displacement
 * budget stays at 3 and `glassBudget.spec.ts` is a gate on this step.
 *
 * **Today dispatches; it never re-implements.** Every verb hands the work to
 * the module that owns the commitment — the Phase 15 review session, the
 * mistake book's redo set, the task API, the calendar — and Today's only
 * contribution afterwards is to reload
 * itself so the plan visibly shrinks. There is no second scheduler here, no
 * second grading path, no local copy of a task's state machine.
 *
 * Step 5 closes the day: `complete` moves into its own component so the one
 * settle lives with the one state that earns it, and the Ledger is refined to
 * stay context — quiet, read-only, and silent when it has nothing to say.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { AppButton, AppEmpty, AppIcon, AppSkeleton } from '@/components'
import { getToday, getWorkspaceSummary, type PlanItemDto, type TodayFocusDto } from '@/api/modules/workspace'
import { getPlan } from '@/api/modules/plan'
import { updateTask } from '@/api/modules/task'
import { toApiError } from '@/api/types'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'
import { useFocusStore } from '@/stores/focus'
import { useSyllabusStore } from '@/stores/syllabus'
import ReviewSessionView from '@/features/flashcards/ReviewSessionView.vue'
import { usePracticeLauncher } from '@/features/practice/usePracticeLauncher'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'
import { hoursOf } from '@/features/focus/clock'
import { parseIsoDate } from '@/utils/date'
import PlanList from './today/PlanList.vue'
import DayComplete from './today/DayComplete.vue'
import LedgerBand from './today/LedgerBand.vue'
import TimeBand from './today/TimeBand.vue'

const { t, d } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const syllabusStore = useSyllabusStore()
const focusStore = useFocusStore()
const { launch, launching, errorKey: launchErrorKey } = usePracticeLauncher()

onMounted(() => {
  void syllabusStore.load()
})

/*
 * Two independent round trips on purpose. The plan band renders the moment
 * `/today` resolves and never waits on the ledger; a slow summary can delay
 * context but never the day's answer. The small duplicated computation (goal,
 * streak) is the accepted cost of keeping the two contracts independent.
 */
const { data: today, loading, error, reload } = useAsync(getToday)
const { data: summary, reload: reloadSummary } = useAsync(getWorkspaceSummary)
// The third contract, equally independent: the plan's hours per paper.
const { data: plan, reload: reloadPlan } = useAsync(getPlan)

// A stopped (or switched) timer wrote a session: today's hours moved.
watch(
  () => focusStore.recorded,
  () => {
    void reload()
    void reloadPlan()
  },
)

const showSkeleton = computed(() => loading.value && today.value === null)

// --- The Line --------------------------------------------------------------

const userName = computed(() => authStore.user?.nickname || authStore.user?.username || '')

const greetingKey = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return 'today.greeting.morning'
  if (hour < 18) return 'today.greeting.afternoon'
  return 'today.greeting.evening'
})

/**
 * The day in one sentence, composed only from what the server actually put in
 * the plan. Each clause is a count of real commitments — nothing is inferred,
 * nothing is rounded up, and an empty plan produces no sentence at all rather
 * than a cheerful placeholder.
 */
const dayLine = computed<string>(() => {
  const plan = today.value?.plan ?? []
  if (plan.length === 0) return ''

  const parts: string[] = []
  const review = plan.find((item) => item.kind === 'review')?.review
  if (review) parts.push(t('today.line.cards', { n: review.total }))

  const mistake = plan.find((item) => item.kind === 'mistake')?.mistake
  if (mistake) parts.push(t('today.line.mistakes', { n: mistake.due }))

  const tasks = plan.filter((item) => item.kind === 'task').length
  if (tasks > 0) parts.push(t('today.line.tasks', { n: tasks }))

  const sessions = plan.filter((item) => item.kind === 'session')
  if (sessions.length === 1 && sessions[0]?.session) {
    parts.push(t('today.line.session', { time: d(sessions[0].session.startsAt, 'time') }))
  } else if (sessions.length > 1) {
    parts.push(t('today.line.sessions', { n: sessions.length }))
  }

  return parts.join(t('today.line.separator'))
})

/**
 * The countdown, said the way a candidate says it: "2027 考研 · 还有 87 天".
 * An estimated date is labelled as one — the official date is announced each
 * autumn, and a guess is never presented as a fact.
 */
const examLine = computed(() => {
  const exam = today.value?.exam
  if (!exam) return ''
  if (exam.phase === 'finished') return t('today.exam.finished', { year: exam.targetYear })
  const date = d(parseIsoDate(exam.examDate), 'short')
  return [
    t('today.exam.countdown', { year: exam.targetYear, n: exam.daysRemaining }),
    t(exam.estimated ? 'today.exam.estimatedDate' : 'today.exam.date', { date }),
    t(`exam.phase.${exam.phase}`),
  ].join(t('today.line.separator'))
})

/** Today's recorded minutes plus the running timer, as it ticks. */
const studiedMinutes = computed(
  () => (today.value?.progress.studiedMinutes ?? 0) + (focusStore.running ? focusStore.elapsedSeconds / 60 : 0),
)

const goalPercent = computed(() => {
  const progress = today.value?.progress
  if (!progress || progress.goalMinutes <= 0) return 0
  return Math.min(100, Math.round((studiedMinutes.value / progress.goalMinutes) * 100))
})

// --- Actions ---------------------------------------------------------------

/** The Phase 15 review stage, mounted here — Today is its second consumer. */
const reviewOpen = ref(false)

/** The plan item whose action is in flight; exactly one at a time. */
const pendingId = ref<string | null>(null)

/** The last failed action as an i18n key. Cleared when the next one starts. */
const actionError = ref<string | null>(null)

/** What just changed, for the `role="status"` region. */
const announcement = ref('')

/**
 * The day ending is the one change a sighted user gets for free — the plan is
 * replaced by a different surface — and a screen-reader user does not, because
 * the focused element is a button that simply stopped existing. So the flip is
 * announced.
 *
 * Only the *transition* is announced, never the initial state: arriving on an
 * already-finished day, the heading says so and an interruption on load would
 * be talking over the user's own reading. It runs after the action
 * announcements it may overwrite, which is the right order — "day complete" is
 * the larger piece of news than the row that caused it.
 */
watch(
  () => today.value?.state,
  (state, previous) => {
    if (previous === undefined || state === previous) return
    if (state === 'complete') announcement.value = t('today.complete.title')
  },
)

/**
 * The shrink loop. Every completed action ends here: the server recomputes the
 * plan, the rank, the cap and the state, and the view renders whatever comes
 * back. Nothing is spliced out of `plan` locally — a client that removed the
 * row itself would be guessing at a state only the server can decide, and
 * would get the `planned → complete` flip wrong the moment the last row goes.
 *
 * The ledger is refreshed alongside it so the page cannot show a task as both
 * done and upcoming.
 */
async function refresh(): Promise<void> {
  await Promise.all([reload(), reloadSummary()])
}

/**
 * One verb per row, dispatched to its owner:
 *
 * - **review** → mount the existing review session; grading, scheduling and
 *   the FSRS state all stay inside it.
 * - **task** → the task module's own update endpoint, the same path the
 *   calendar's checkbox uses. `completedAt` is stamped server-side.
 * - **session** → the calendar. A session has no completion — it is retired by
 *   time passing — so Today hands it over rather than inventing an action, and
 *   owns no timer (that is P21).
 */
function activate(item: PlanItemDto): void {
  actionError.value = null
  switch (item.kind) {
    case 'review':
      reviewOpen.value = true
      return
    case 'mistake':
      void redoMistakes(item)
      return
    case 'task':
      void completeTask(item)
      return
    default:
      void router.push({ name: 'calendar' })
  }
}

/**
 * Due mistakes are retired by answering them right again, so the verb draws
 * the redo set and opens the practice stage — the mistake book's own path.
 * Today reloads when the candidate comes back, like every other verb.
 */
async function redoMistakes(item: PlanItemDto): Promise<void> {
  if (pendingId.value !== null) return
  pendingId.value = item.id
  try {
    await launch({ mode: 'mistakes' }, item.id)
    if (launchErrorKey.value) actionError.value = launchErrorKey.value
  } finally {
    pendingId.value = null
  }
}

/** Why a 考点 is suggested, in one clause — the reason code comes from the server. */
function focusReason(item: TodayFocusDto): string {
  return t(`syllabus.reason.${item.reason}`, {
    n: item.mistakes,
    p: Math.round((item.mastery ?? 0) * 100),
  })
}

/** A suggested 考点 opens a short topic set; the plan is untouched by it. */
function practiseFocus(nodeCode: string): void {
  actionError.value = null
  void launch({ mode: 'topic', nodeCode }, nodeCode)
}

async function completeTask(item: PlanItemDto): Promise<void> {
  const task = item.task
  if (!task || pendingId.value !== null) return
  pendingId.value = item.id
  try {
    await updateTask(task.id, { status: 'done' })
    announcement.value = t('today.plan.announce.taskDone', { title: task.title })
    await refresh()
  } catch (caught) {
    // The row stays exactly where it was: nothing was removed optimistically,
    // so the failure needs no rollback and the verb can simply be pressed again.
    actionError.value = toApiError(caught).messageKey
  } finally {
    pendingId.value = null
  }
}

/**
 * The session ended *on Today*, so the plan shrinks in place — the whole point
 * of mounting the stage here rather than sending the user to Flashcards. It
 * reloads even when nothing was graded: the queue can have moved on its own.
 */
async function onReviewClose(reviewed: number): Promise<void> {
  reviewOpen.value = false
  announcement.value = reviewed > 0 ? t('today.plan.announce.reviewed', { n: reviewed }) : ''
  await refresh()
}
</script>

<template>
  <div class="today">
    <!-- THE LINE — renders before data; the greeting needs none. -->
    <header class="line">
      <div class="line-text">
        <p v-if="examLine" class="line-exam">{{ examLine }}</p>
        <h1 class="line-greeting">{{ t(greetingKey, { name: userName }) }}</h1>
        <p v-if="dayLine" class="line-day">{{ dayLine }}</p>
      </div>

      <!--
        Each meter names itself in text rather than through `aria-label` on a
        plain div, which has no role for the label to attach to and is dropped
        by most screen readers. The visible number stays the visible number;
        the word in front of it is simply not painted.
      -->
      <div v-if="today" class="line-meters">
        <div class="meter">
          <svg class="goal-ring" viewBox="0 0 36 36" aria-hidden="true">
            <circle class="goal-ring-track" cx="18" cy="18" r="15.5" pathLength="100" />
            <circle
              v-if="goalPercent > 0"
              class="goal-ring-fill"
              :class="{ reached: goalPercent >= 100 }"
              cx="18"
              cy="18"
              r="15.5"
              pathLength="100"
              :stroke-dasharray="`${goalPercent} ${100 - goalPercent}`"
            />
          </svg>
          <span class="meter-value">
            <span class="sr-only">{{ t('today.line.goalLabel') }}</span>
            {{
              t('today.line.goal', {
                done: hoursOf(studiedMinutes),
                goal: hoursOf(today.progress.goalMinutes),
              })
            }}
          </span>
        </div>

        <div class="meter">
          <AppIcon name="pencil-line" size="sm" class="meter-icon-practice" aria-hidden="true" />
          <span class="meter-value">
            <span class="sr-only">{{ t('today.line.questionsLabel') }}</span>
            {{ t('today.line.questions', { n: today.progress.questionsAnswered }) }}
          </span>
        </div>

        <div class="meter">
          <AppIcon name="flame" size="sm" class="meter-icon" aria-hidden="true" />
          <span class="meter-value">
            <span class="sr-only">{{ t('today.line.streakLabel') }}</span>
            {{ t('today.line.streak', { n: today.progress.streakDays }) }}
          </span>
        </div>
      </div>
    </header>

    <!--
      Loading — the skeleton takes the plan's shape, not a widget grid's. It is
      decorative and hidden from assistive tech; `aria-busy` on the region is
      what actually reports the wait, so nothing tries to read four grey bars.
    -->
    <div v-if="showSkeleton" class="plan-skeleton" aria-busy="true" aria-hidden="true">
      <AppSkeleton v-for="n in 4" :key="n" variant="block" height="56px" />
    </div>

    <!-- Error + retry. `alert` because a failed load is unrequested bad news. -->
    <AppEmpty v-else-if="error" role="alert" icon="alert-circle" :title="t(error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>

    <template v-else-if="today">
      <!--
        THE TIME — the study day in hours per paper (the plan's split), the
        running timer included. Withheld after the exam, when there is no day
        left to divide.
      -->
      <TimeBand v-if="plan && plan.exam.phase !== 'finished'" :plan="plan" />

      <!-- THE PLAN -->
      <template v-if="today.state === 'planned'">
        <PlanList
          :items="today.plan"
          :remaining-count="today.remainingCount"
          :pending-id="pendingId"
          @activate="activate"
        />
        <p v-if="actionError" class="plan-error" role="alert">{{ t(actionError) }}</p>
      </template>

      <!--
        The three terminal states are kept distinct by the server and must stay
        distinct here. `complete` earns the congratulation, the success token
        and the one settle — it has its own component for exactly that reason.
        `clear` explicitly earns none of them, because the user did nothing;
        `empty` is a new account and gets one honest next action instead of a
        finished day. All three share the `.terminal` geometry so the day ends
        in one visual family rather than three.
      -->
      <DayComplete
        v-else-if="today.state === 'complete'"
        :reviews="today.progress.reviewsCompleted"
        :questions="today.progress.questionsAnswered"
        :tasks="today.progress.tasksCompleted"
        :minutes="today.progress.studiedMinutes"
      />

      <section v-else-if="today.state === 'clear'" class="terminal">
        <AppIcon name="sun" class="terminal-icon" aria-hidden="true" />
        <h2 class="terminal-title">{{ t('today.clear.title') }}</h2>
        <p class="terminal-text">{{ t('today.clear.text') }}</p>
      </section>

      <!--
        Explicitly keyed rather than a bare `v-else`: an unrecognised state must
        render nothing, not inherit the new-account copy. Telling an existing
        user to "start from the syllabus" would be the same fabrication the four
        states exist to prevent, arriving through the back door.
      -->
      <section v-else-if="today.state === 'empty'" class="terminal">
        <AppIcon name="network" class="terminal-icon" aria-hidden="true" />
        <h2 class="terminal-title">{{ t('today.empty.title') }}</h2>
        <p class="terminal-text">{{ t('today.empty.text') }}</p>
        <div class="terminal-actions">
          <AppButton size="sm" @click="router.push({ name: 'syllabus' })">
            {{ t('today.empty.cta') }}
          </AppButton>
          <AppButton size="sm" variant="soft" tone="secondary" @click="router.push({ name: 'settings' })">
            {{ t('today.empty.ctaExam') }}
          </AppButton>
        </div>
      </section>

      <!--
        THE FOCUS — up to three 考点 the mastery model suggests, one per paper.
        An offer that travels beside the plan and never enters it, so it can
        never decide whether the day is done; it shows on every kind of day,
        including a new account's, where it is the most useful first step.
      -->
      <section v-if="today.focus.length > 0" class="focus" aria-labelledby="today-focus-title">
        <div class="focus-head">
          <h2 id="today-focus-title" class="focus-title">{{ t('today.focus.title') }}</h2>
          <RouterLink :to="{ name: 'syllabus' }" class="focus-link">
            {{ t('today.focus.map') }}
            <AppIcon name="arrow-right" size="sm" aria-hidden="true" />
          </RouterLink>
        </div>
        <ul class="focus-list">
          <li v-for="item in today.focus" :key="item.nodeCode" class="focus-item">
            <div class="focus-main">
              <RouterLink :to="{ name: 'syllabus-node', params: { code: item.nodeCode } }" class="focus-name">
                {{ syllabusStore.node(item.nodeCode)?.name ?? item.nodeCode }}
              </RouterLink>
              <NodeChip :code="item.nodeCode" />
              <span class="focus-reason">{{ focusReason(item) }}</span>
            </div>
            <AppButton
              size="sm"
              variant="soft"
              :loading="launching === item.nodeCode"
              :disabled="launching !== null || item.available === 0"
              @click="practiseFocus(item.nodeCode)"
            >
              {{ t('syllabus.practice') }}
            </AppButton>
          </li>
        </ul>
        <p v-if="launchErrorKey && pendingId === null" class="plan-error" role="alert">{{ t(launchErrorKey) }}</p>
      </section>

      <!--
        THE LEDGER — last, always, whatever the day turned out to be. It is
        context, so it follows the answer and never precedes it.

        Withheld from a brand-new account outright: the `empty` state already
        gave that user the one action that matters, and anything below it would
        compete with it. On every other day the band decides for itself whether
        it has anything worth saying (LedgerBand `hasContext`), so a quiet
        account gets a quiet page rather than a wall of empty cards.
      -->
      <LedgerBand v-if="summary && today.state !== 'empty'" :summary="summary" />
    </template>

    <!--
      The plan shrinks silently for anyone not watching it, so each completed
      action is announced. Outside the `v-if` chain on purpose: a live region
      only announces while it is already mounted.
    -->
    <p class="sr-only" role="status">{{ announcement }}</p>

    <!--
      The review session runs *on Today* (Phase 15's stage, unchanged, second
      consumer) so the day it belongs to is still underneath it when it ends.
      No deck id: the plan's review row is the whole due queue.
    -->
    <ReviewSessionView v-if="reviewOpen" @close="onReviewClose" />
  </div>
</template>

<style scoped>
.today {
  padding: var(--space-6);
  max-width: 1180px;
  margin: 0 auto;
}

/* --- The Line ----------------------------------------------------------- */

.line {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-6);
  flex-wrap: wrap;
  margin-bottom: var(--space-6);
}

.line-text {
  min-width: 0;
}

/* The countdown is a kicker above the greeting: always present, never loud. */
.line-exam {
  margin: 0 0 var(--space-1);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text-secondary);
}

.line-greeting {
  margin: 0;
  font-family: var(--font-headline-family);
  font-size: var(--font-headline-size);
  font-weight: var(--font-headline-weight);
  line-height: var(--font-headline-leading);
  letter-spacing: var(--font-headline-tracking);
}

.line-day {
  margin: var(--space-2) 0 0;
  font-size: var(--font-body-size);
  color: var(--color-text-secondary);
}

/* Two meters, not four stat tiles. The Line reports how far the day has come;
   anything more is a dashboard band competing with the plan below it. */
.line-meters {
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

.meter {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.meter-icon {
  color: var(--color-warning);
}

.meter-icon-practice {
  color: var(--color-primary);
}

.meter-value {
  font-size: var(--text-sm);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.goal-ring {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  transform: rotate(-90deg);
}

.goal-ring-track,
.goal-ring-fill {
  fill: none;
  stroke-width: 3.5;
}

.goal-ring-track {
  stroke: var(--color-muted-soft);
}

.goal-ring-fill {
  stroke: var(--color-primary);
  stroke-linecap: round;
  transition: stroke-dasharray var(--duration-slow) var(--ease-out);
}

.goal-ring-fill.reached {
  stroke: var(--color-success);
}

/* --- The Plan ----------------------------------------------------------- */

.plan-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

/* A failed verb speaks under the plan rather than replacing it: the other
   rows are still actionable, and the failed one is still there to retry. */
.plan-error {
  margin: var(--space-3) 0 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

/* `.sr-only` is the global utility in base.css — not restated here. */

/* --- The Focus -----------------------------------------------------------
 * An offer below the plan: a label-scale heading and a row of plain cards,
 * one per paper. Solid — suggestions are content.
 */

.focus {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-top: var(--space-8);
}

.focus-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.focus-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.focus-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  text-decoration: none;
}

.focus-link:hover {
  color: var(--color-primary);
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

/* --- Terminal states ----------------------------------------------------
 * `clear` and `empty` only. `complete` carries the same geometry in
 * `today/DayComplete.vue`, where it also owns the success token and the
 * settle — the two things the other two states must never acquire.
 */

.terminal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-16) var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  text-align: center;
}

.terminal-icon {
  color: var(--color-text-tertiary);
}

.terminal-title {
  margin: 0;
  font-family: var(--font-title-family);
  font-size: var(--font-title-size);
  font-weight: var(--font-title-weight);
}

.terminal-text {
  margin: 0;
  max-width: 42ch;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.terminal-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-2);
}

@media (max-width: 768px) {
  .today {
    padding: var(--space-4);
  }

  .line-meters {
    gap: var(--space-4);
  }

  /* 64px of vertical padding is a third of a phone screen spent on nothing. */
  .terminal {
    padding: var(--space-10) var(--space-4);
  }
}
</style>
