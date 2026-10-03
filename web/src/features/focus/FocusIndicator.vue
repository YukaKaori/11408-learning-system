<script setup lang="ts">
/**
 * The study timer, present on every screen of the shell — in the sidebar
 * (`rail`, or `collapsed` when the rail is folded) and in the mobile header
 * (`compact`). Idle, it is one quiet way in ("开始专注"); running, it is the
 * paper, the running time and a stop button.
 *
 * Solid chrome-side status, never glass, and never animated: the clock text
 * changes once a second and nothing pulses — the one-shot rule of the
 * material constitution forbids an attention loop, and a timer that pulses
 * for three hours is exactly that.
 *
 * After a stop it says what was recorded, briefly, and — for a sitting-length
 * block — offers to record the paper's score, the natural next step after
 * timing a 真题 (the sittings page opens its dialog prefilled from the query).
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { AppIcon, AppTooltip } from '@/components'
import { useDuration } from '@/composables/useDuration'
import { FOCUS_TOO_LONG, useFocusStore } from '@/stores/focus'
import { paperAccentColor } from '@/features/syllabus/papers'
import { subjectOfCode } from '@/features/syllabus/syllabusIndex'
import { formatClock } from './clock'
import FocusStartDialog from './FocusStartDialog.vue'
import FocusEndDialog from './FocusEndDialog.vue'

const props = withDefaults(defineProps<{ variant?: 'rail' | 'collapsed' | 'compact' }>(), {
  variant: 'rail',
})

const { t } = useI18n()
const router = useRouter()
const focusStore = useFocusStore()
const { formatMinutes } = useDuration()

/** A block this long could be a whole paper sat under time. */
const SITTING_LENGTH_MINUTES = 60
const FEEDBACK_MS = 8000

const startOpen = ref(false)
const endOpen = ref(false)

const paper = computed(() => subjectOfCode(focusStore.timer?.nodeCode))
const paperName = computed(() =>
  paper.value ? t(`exam.papers.${paper.value}`) : t('focus.unclassified'),
)
const clockText = computed(() => formatClock(focusStore.elapsedSeconds))

/** What the last stop from this control recorded, shown for a few seconds. */
const feedback = ref<{ minutes: number; paper: string | null; date: string } | null>(null)
let feedbackTimer: ReturnType<typeof setTimeout> | null = null

function showFeedback(minutes: number, paperCode: string | null, endsAt: number) {
  const day = new Date(endsAt)
  const pad = (n: number) => String(n).padStart(2, '0')
  feedback.value = {
    minutes,
    paper: paperCode,
    date: `${day.getFullYear()}-${pad(day.getMonth() + 1)}-${pad(day.getDate())}`,
  }
  if (feedbackTimer) clearTimeout(feedbackTimer)
  feedbackTimer = setTimeout(() => (feedback.value = null), FEEDBACK_MS)
}

onBeforeUnmount(() => {
  if (feedbackTimer) clearTimeout(feedbackTimer)
})

async function stop() {
  if (focusStore.overCeiling) {
    endOpen.value = true
    return
  }
  const stopping = paper.value
  const ok = await focusStore.stop()
  if (ok && focusStore.lastSaved) {
    showFeedback(focusStore.lastSaved.durationMinutes, stopping, focusStore.lastSaved.endsAt)
  } else if (!ok && focusStore.error?.code === FOCUS_TOO_LONG) {
    endOpen.value = true
  }
}

function logSitting() {
  const recorded = feedback.value
  if (!recorded) return
  feedback.value = null
  void router.push({
    name: 'sittings',
    query: {
      log: '1',
      ...(recorded.paper ? { subject: recorded.paper } : {}),
      minutes: String(recorded.minutes),
      date: recorded.date,
    },
  })
}

const feedbackText = computed(() =>
  feedback.value
    ? t('focus.recorded', {
        time: formatMinutes(feedback.value.minutes),
        paper: feedback.value.paper
          ? t(`exam.papers.${feedback.value.paper}`)
          : t('focus.unclassified'),
      })
    : '',
)
</script>

<template>
  <!-- RAIL — the expanded sidebar -->
  <div v-if="props.variant === 'rail'" class="focus-rail" :class="{ running: focusStore.running }">
    <template v-if="focusStore.running">
      <div class="rail-head">
        <span
          class="dot"
          :style="{ backgroundColor: paperAccentColor(paper) }"
          aria-hidden="true"
        ></span>
        <span class="rail-label">{{ t('focus.running', { paper: paperName }) }}</span>
      </div>
      <div class="rail-body">
        <span class="clock" role="timer" :aria-label="t('focus.elapsedLabel')">{{
          clockText
        }}</span>
        <button
          type="button"
          class="stop"
          :disabled="focusStore.pending"
          :aria-label="t('focus.stop')"
          @click="stop"
        >
          <AppIcon name="stop" size="sm" />
          <span>{{ t('focus.stop') }}</span>
        </button>
      </div>
      <p v-if="focusStore.timer?.title" class="rail-title">{{ focusStore.timer.title }}</p>
    </template>
    <template v-else>
      <button type="button" class="start" @click="startOpen = true">
        <AppIcon name="timer" />
        <span>{{ t('focus.startCta') }}</span>
      </button>
    </template>
    <p v-if="feedback" class="feedback" role="status">
      <span>{{ feedbackText }}</span>
      <button
        v-if="feedback.minutes >= SITTING_LENGTH_MINUTES"
        type="button"
        class="link"
        @click="logSitting"
      >
        {{ t('focus.logSitting') }}
      </button>
    </p>
  </div>

  <!-- COLLAPSED — the folded sidebar: one icon, the clock in its tooltip -->
  <div v-else-if="props.variant === 'collapsed'" class="focus-collapsed">
    <AppTooltip
      :content="
        focusStore.running
          ? `${t('focus.running', { paper: paperName })} · ${clockText}`
          : t('focus.startCta')
      "
      placement="right"
    >
      <button
        type="button"
        class="icon-button"
        :class="{ running: focusStore.running }"
        :aria-label="focusStore.running ? t('focus.stop') : t('focus.startCta')"
        @click="focusStore.running ? stop() : (startOpen = true)"
      >
        <AppIcon :name="focusStore.running ? 'stop' : 'timer'" />
        <span
          v-if="focusStore.running"
          class="corner-dot"
          :style="{ backgroundColor: paperAccentColor(paper) }"
          aria-hidden="true"
        ></span>
      </button>
    </AppTooltip>
  </div>

  <!-- COMPACT — the mobile header -->
  <div v-else class="focus-compact">
    <template v-if="focusStore.running">
      <span
        class="dot"
        :style="{ backgroundColor: paperAccentColor(paper) }"
        aria-hidden="true"
      ></span>
      <span class="clock compact-clock" role="timer" :aria-label="t('focus.elapsedLabel')">{{
        clockText
      }}</span>
      <button
        type="button"
        class="icon-button"
        :disabled="focusStore.pending"
        :aria-label="t('focus.stop')"
        @click="stop"
      >
        <AppIcon name="stop" size="sm" />
      </button>
    </template>
    <button
      v-else
      type="button"
      class="icon-button"
      :aria-label="t('focus.startCta')"
      @click="startOpen = true"
    >
      <AppIcon name="timer" />
    </button>
    <p v-if="feedback" class="compact-feedback" role="status">{{ feedbackText }}</p>
  </div>

  <FocusStartDialog v-model="startOpen" />
  <FocusEndDialog v-model="endOpen" />
</template>

<style scoped>
.focus-rail {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
  margin-bottom: var(--space-3);
}

.focus-rail.running {
  padding: var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
}

.rail-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
}

.rail-label {
  overflow: hidden;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.clock {
  font-size: var(--text-lg);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
  color: var(--color-text);
}

.rail-title {
  margin: 0;
  overflow: hidden;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stop {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: 32px;
  padding: 0 var(--space-3);
  border: var(--border-width-sm) solid var(--color-border-strong);
  border-radius: var(--radius-full);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-xs);
  font-weight: 500;
  cursor: pointer;
}

.stop:hover:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.start {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  min-height: 36px;
  padding: 0 var(--space-3);
  border: var(--border-width-sm) dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
}

.start:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.feedback {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-2);
  margin: 0;
  font-size: var(--text-xs);
  line-height: 1.5;
  color: var(--color-text-secondary);
}

.link {
  padding: 0;
  border: none;
  background: none;
  color: var(--color-primary);
  font: inherit;
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.focus-collapsed {
  display: flex;
  justify-content: center;
  margin-bottom: var(--space-3);
}

.icon-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.icon-button:hover:not(:disabled) {
  background-color: var(--color-surface-hover);
  color: var(--color-text);
}

.icon-button.running {
  color: var(--color-text);
}

.icon-button:focus-visible,
.stop:focus-visible,
.start:focus-visible,
.link:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.corner-dot {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 7px;
  height: 7px;
  border-radius: var(--radius-full);
}

.focus-compact {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
}

.compact-clock {
  font-size: var(--text-sm);
}

.compact-feedback {
  position: absolute;
  top: calc(100% + var(--space-1));
  right: 0;
  margin: 0;
  padding: var(--space-1) var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-sm);
  background-color: var(--color-surface);
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  white-space: nowrap;
}
</style>
