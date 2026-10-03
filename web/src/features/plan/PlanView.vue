<script setup lang="ts">
/**
 * 备考规划 — where the candidate's time goes between today and the exam.
 *
 * A read model, rendered: the phases and today's place in them, how the study
 * day divides among the four papers and why, this week against the plan, and
 * the two exam days. The only input edited here is the study day itself (the
 * daily goal); everything else moves when its evidence does — a sitting
 * logged, a target changed, an hour timed.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { AppButton, AppEmpty, AppIcon, AppPageHeader, AppSkeleton } from '@/components'
import { useAsync } from '@/composables/useAsync'
import { getPlan } from '@/api/modules/plan'
import { toApiError } from '@/api/types'
import { useAppStore } from '@/stores/app'
import { useFocusStore } from '@/stores/focus'
import { PAPER_ICON, PAPERS, paperAccentColor } from '@/features/syllabus/papers'
import { hoursOf } from '@/features/focus/clock'
import { parseIsoDate } from '@/utils/date'
import PlanTabs from './components/PlanTabs.vue'
import PhaseTimeline from './components/PhaseTimeline.vue'
import AllocationList from './components/AllocationList.vue'
import WeekBoard from './components/WeekBoard.vue'
import ExamTimetable from './components/ExamTimetable.vue'

const { t, d } = useI18n()
const router = useRouter()
const appStore = useAppStore()
const focusStore = useFocusStore()

const { data: plan, loading, error, reload } = useAsync(getPlan)

// A stopped timer changes this week's hours.
watch(
  () => focusStore.recorded,
  () => void reload(),
)

const finished = computed(() => plan.value?.exam.phase === 'finished')
const phase = computed(() => plan.value?.exam.phase ?? 'foundation')

// --- the study day -------------------------------------------------------------

const editingHours = ref(false)
const hoursDraft = ref('')
const hoursError = ref<string | null>(null)
const savingHours = ref(false)

function editHours() {
  hoursDraft.value = hoursOf(plan.value?.dailyMinutes ?? 480)
  hoursError.value = null
  editingHours.value = true
}

async function saveHours() {
  const hours = Number(hoursDraft.value)
  if (!Number.isFinite(hours) || hours < 0.5 || hours > 16) {
    hoursError.value = 'plan.daily.hoursInvalid'
    return
  }
  savingHours.value = true
  try {
    await appStore.updatePreferences({ dailyGoalMinutes: Math.round(hours * 60) })
    editingHours.value = false
    await reload()
  } catch (caught) {
    hoursError.value = toApiError(caught).messageKey
  } finally {
    savingHours.value = false
  }
}
</script>

<template>
  <div class="plan">
    <AppPageHeader :title="t('plan.title')" :subtitle="t('plan.subtitle')">
      <template #breadcrumb>
        <PlanTabs />
      </template>
    </AppPageHeader>

    <div v-if="loading && !plan" class="skeleton" aria-busy="true" aria-hidden="true">
      <AppSkeleton v-for="n in 3" :key="n" variant="block" height="140px" />
    </div>

    <AppEmpty v-else-if="error" role="alert" icon="alert-circle" :title="t(error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>

    <AppEmpty
      v-else-if="plan && finished"
      icon="flag"
      :title="t('plan.finished.title', { year: plan.exam.targetYear })"
    >
      <template #action>
        <AppButton size="sm" @click="router.push({ name: 'settings' })">{{
          t('plan.finished.cta')
        }}</AppButton>
      </template>
    </AppEmpty>

    <template v-else-if="plan">
      <section class="panel">
        <h2 class="panel-title">{{ t('plan.timeline.title') }}</h2>
        <PhaseTimeline
          :phases="plan.phases"
          :today="plan.today"
          :exam-date="plan.exam.examDate"
          :estimated="plan.exam.estimated"
        />
        <div class="guide">
          <p class="guide-lead">{{ t(`plan.lead.${phase}`) }}</p>
          <ul class="guide-list">
            <li v-for="code in PAPERS" :key="code" class="guide-item">
              <AppIcon
                :name="PAPER_ICON[code]"
                size="sm"
                :style="{ color: paperAccentColor(code) }"
              />
              <span class="guide-paper">{{ t(`exam.papers.${code}`) }}</span>
              <span class="guide-text">{{ t(`plan.guide.${phase}.${code}`) }}</span>
            </li>
          </ul>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2 class="panel-title">{{ t('plan.daily.title') }}</h2>
          <div v-if="!editingHours" class="hours">
            <span>{{ t('plan.daily.hours', { n: hoursOf(plan.dailyMinutes) }) }}</span>
            <AppButton size="sm" variant="ghost" @click="editHours">{{
              t('plan.daily.edit')
            }}</AppButton>
          </div>
          <form v-else class="hours-form" @submit.prevent="saveHours">
            <input
              v-model="hoursDraft"
              class="hours-input"
              type="number"
              min="0.5"
              max="16"
              step="0.5"
              :aria-label="t('plan.daily.hoursLabel')"
            />
            <span class="hours-unit">{{ t('plan.daily.unit') }}</span>
            <AppButton size="sm" type="submit" :loading="savingHours">{{
              t('common.save')
            }}</AppButton>
            <AppButton size="sm" variant="ghost" @click="editingHours = false">{{
              t('common.cancel')
            }}</AppButton>
          </form>
        </div>
        <p v-if="hoursError" class="error" role="alert">{{ t(hoursError) }}</p>
        <AllocationList :papers="plan.papers" :phase="plan.exam.phase" />
        <p class="note">{{ t('plan.daily.explain') }}</p>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2 class="panel-title">{{ t('plan.week.title') }}</h2>
          <span class="panel-hint">
            {{ d(parseIsoDate(plan.weekStart), 'short') }} –
            {{ d(parseIsoDate(plan.weekEnd), 'short') }}
          </span>
        </div>
        <WeekBoard :papers="plan.papers" :unclassified-minutes="plan.unclassified.weekMinutes" />
      </section>

      <section class="panel">
        <h2 class="panel-title">{{ t('plan.exam.title') }}</h2>
        <ExamTimetable :slots="plan.examDays" :estimated="plan.exam.estimated" />
        <p class="note">{{ t('plan.exam.rehearse') }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.plan {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-8);
}

.skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
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

.guide {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: var(--border-width-sm) solid var(--color-border);
}

.guide-lead {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.guide-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.guide-item {
  display: grid;
  grid-template-columns: auto 64px minmax(0, 1fr);
  align-items: baseline;
  gap: var(--space-2);
  font-size: var(--text-sm);
}

.guide-paper {
  font-weight: 500;
  color: var(--color-text);
}

.guide-text {
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.hours {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--color-text);
}

.hours-form {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.hours-input {
  width: 72px;
  padding: var(--space-1) var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

.hours-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.hours-unit {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.note {
  margin: 0;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--color-text-tertiary);
}

.error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

@media (max-width: 768px) {
  .plan {
    padding: var(--space-4);
  }

  .panel {
    padding: var(--space-4);
  }

  .guide-item {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .guide-text {
    grid-column: 2;
  }
}
</style>
