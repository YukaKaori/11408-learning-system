<script setup lang="ts">
/**
 * Today — one prioritized, actionable, completable answer to "what should I do
 * right now?". This is not a dashboard: a dashboard reports state, Today
 * issues a plan, and rank is the product.
 *
 * Three bands in descending priority — The Line (who/where/how far), The Plan
 * (the view), The Ledger (inherited context, demoted). The first renders
 * before any data; the second is the only thing that matters; the third never
 * competes.
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
 * Step 3 scope: composition and all six view states. Acting in place (mounting
 * the review session, completing a task inline, reloading so the plan shrinks)
 * is Step 4, and the day-complete settle is Step 5.
 */
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { AppButton, AppEmpty, AppIcon, AppSkeleton } from '@/components'
import { getToday, getWorkspaceSummary, type PlanItemDto } from '@/api/modules/workspace'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'
import { useSubjectsStore } from '@/stores/subjects'
import PlanList from './today/PlanList.vue'
import LedgerBand from './today/LedgerBand.vue'

const { t, d } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const subjectsStore = useSubjectsStore()

onMounted(() => {
  void subjectsStore.load()
})

/*
 * Two independent round trips on purpose. The plan band renders the moment
 * `/today` resolves and never waits on the ledger; a slow summary can delay
 * context but never the day's answer. The small duplicated computation (goal,
 * streak) is the accepted cost of keeping the two contracts independent.
 */
const { data: today, loading, error, reload } = useAsync(getToday)
const { data: summary } = useAsync(getWorkspaceSummary)

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

const goalPercent = computed(() => {
  const progress = today.value?.progress
  if (!progress || progress.goalMinutes <= 0) return 0
  return Math.min(100, Math.round((progress.studiedMinutes / progress.goalMinutes) * 100))
})

// --- Actions ---------------------------------------------------------------

/**
 * Step 3 sends every verb to the module that owns the commitment. Step 4
 * replaces this with acting in place — grading the review session here,
 * completing a task inline — each ending in a reload so the plan visibly
 * shrinks. Navigation now keeps every row honest in the meantime; a rendered
 * verb that does nothing would be worse than no verb at all.
 */
function activate(item: PlanItemDto): void {
  if (item.kind === 'review') {
    void router.push({ name: 'flashcards' })
    return
  }
  void router.push({ name: 'calendar' })
}
</script>

<template>
  <div class="today">
    <!-- THE LINE — renders before data; the greeting needs none. -->
    <header class="line">
      <div class="line-text">
        <h1 class="line-greeting">{{ t(greetingKey, { name: userName }) }}</h1>
        <p v-if="dayLine" class="line-day">{{ dayLine }}</p>
      </div>

      <div v-if="today" class="line-meters">
        <div class="meter" :aria-label="t('today.line.goalLabel')">
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
            {{
              t('today.line.goal', {
                done: today.progress.studiedMinutes,
                goal: today.progress.goalMinutes,
              })
            }}
          </span>
        </div>

        <div class="meter" :aria-label="t('today.line.streakLabel')">
          <AppIcon name="flame" size="sm" class="meter-icon" aria-hidden="true" />
          <span class="meter-value">
            {{ t('today.line.streak', { n: today.progress.streakDays }) }}
          </span>
        </div>
      </div>
    </header>

    <!-- Loading — the skeleton takes the plan's shape, not a widget grid's. -->
    <div v-if="showSkeleton" class="plan-skeleton" aria-hidden="true">
      <AppSkeleton v-for="n in 4" :key="n" variant="block" height="56px" />
    </div>

    <!-- Error + retry -->
    <AppEmpty v-else-if="error" icon="alert-circle" :title="t(error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>

    <template v-else-if="today">
      <!-- THE PLAN -->
      <PlanList
        v-if="today.state === 'planned'"
        :items="today.plan"
        :remaining-count="today.remainingCount"
        @activate="activate"
      />

      <!--
        The three terminal states are kept distinct by the server and must stay
        distinct here. `complete` earns the congratulation; `clear` explicitly
        does not, because the user did nothing; `empty` is a new account and
        gets one honest next action instead of a finished day.
      -->
      <section v-else-if="today.state === 'complete'" class="terminal terminal-complete">
        <AppIcon name="check-circle" class="terminal-icon" aria-hidden="true" />
        <h2 class="terminal-title">{{ t('today.complete.title') }}</h2>
        <p class="terminal-text">
          {{
            t('today.complete.text', {
              reviews: today.progress.reviewsCompleted,
              tasks: today.progress.tasksCompleted,
              minutes: today.progress.studiedMinutes,
            })
          }}
        </p>
      </section>

      <section v-else-if="today.state === 'clear'" class="terminal">
        <AppIcon name="sun" class="terminal-icon" aria-hidden="true" />
        <h2 class="terminal-title">{{ t('today.clear.title') }}</h2>
        <p class="terminal-text">{{ t('today.clear.text') }}</p>
      </section>

      <section v-else class="terminal">
        <AppIcon name="book-open" class="terminal-icon" aria-hidden="true" />
        <h2 class="terminal-title">{{ t('today.empty.title') }}</h2>
        <p class="terminal-text">{{ t('today.empty.text') }}</p>
        <AppButton size="sm" @click="router.push({ name: 'subjects' })">
          {{ t('today.empty.cta') }}
        </AppButton>
      </section>

      <!--
        THE LEDGER — never shown to a brand-new account: four empty-state cards
        are noise to someone who has nothing yet, and the `empty` state already
        gave them the one action that matters.
      -->
      <LedgerBand v-if="summary && today.state !== 'empty'" :summary="summary" />
    </template>
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

/* --- Terminal states ---------------------------------------------------- */

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

/* `complete` is the only state that earns colour — it is the one the user
   worked for. `clear` and `empty` stay neutral by design. */
.terminal-complete .terminal-icon {
  color: var(--color-success);
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

@media (max-width: 768px) {
  .today {
    padding: var(--space-4);
  }

  .line-meters {
    gap: var(--space-4);
  }
}
</style>
