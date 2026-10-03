<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppIcon, AppLoading } from '@/components'
import {
  Grade,
  fetchReviewQueue,
  getReviewSummary,
  gradeCard,
  type ReviewCardDto,
  type ReviewSummaryDto,
} from '@/api/modules/flashcard'

const props = defineProps<{
  /** Scope the session to one deck; omit/undefined = every due card across decks. */
  deckId?: string | null
  /** Shown in the stage header for context. */
  deckName?: string
}>()

const emit = defineEmits<{
  /** The session was exited or finished; the parent should refresh due counts. */
  (e: 'close', reviewed: number): void
}>()

const { t } = useI18n()

type Phase = 'loading' | 'error' | 'reviewing' | 'empty' | 'done'
const phase = ref<Phase>('loading')

const queue = ref<ReviewCardDto[]>([])
const index = ref(0)
const revealed = ref(false)
const grading = ref(false)
const gradeError = ref(false)
const summary = ref<ReviewSummaryDto | null>(null)

const tally = ref({ again: 0, hard: 0, good: 0, easy: 0 })
const reviewedCount = computed(() => tally.value.again + tally.value.hard + tally.value.good + tally.value.easy)

const current = computed<ReviewCardDto | undefined>(() => queue.value[index.value])
const total = computed(() => queue.value.length)
const progressPercent = computed(() => (total.value === 0 ? 0 : Math.round((index.value / total.value) * 100)))

const stage = ref<HTMLElement | null>(null)

const GRADES = [
  { grade: Grade.Again, key: '1', tone: 'danger', variant: 'solid' },
  { grade: Grade.Hard, key: '2', tone: 'secondary', variant: 'soft' },
  { grade: Grade.Good, key: '3', tone: 'primary', variant: 'solid' },
  { grade: Grade.Easy, key: '4', tone: 'success', variant: 'soft' },
] as const

const GRADE_KEY: Record<Grade, string> = {
  [Grade.Again]: 'again',
  [Grade.Hard]: 'hard',
  [Grade.Good]: 'good',
  [Grade.Easy]: 'easy',
}

async function load() {
  phase.value = 'loading'
  try {
    const result = await fetchReviewQueue(props.deckId ?? undefined)
    queue.value = result.cards
    index.value = 0
    revealed.value = false
    phase.value = result.total === 0 ? 'empty' : 'reviewing'
  } catch (error) {
    console.error(error)
    phase.value = 'error'
  }
}

function reveal() {
  if (phase.value === 'reviewing' && !revealed.value) revealed.value = true
}

async function grade(g: Grade) {
  if (phase.value !== 'reviewing' || !revealed.value || grading.value) return
  const card = current.value
  if (!card) return
  grading.value = true
  gradeError.value = false
  try {
    await gradeCard(card.id, g)
    tally.value[GRADE_KEY[g] as keyof typeof tally.value] += 1
    advance()
  } catch (error) {
    console.error(error)
    gradeError.value = true // keep the card up so the grade can be retried
  } finally {
    grading.value = false
  }
}

function advance() {
  revealed.value = false
  if (index.value + 1 >= queue.value.length) {
    finish()
  } else {
    index.value += 1
  }
}

async function finish() {
  phase.value = 'done'
  try {
    summary.value = await getReviewSummary()
  } catch (error) {
    console.error(error) // the session tally still renders without it
  }
}

function close() {
  emit('close', reviewedCount.value)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (phase.value !== 'reviewing') return

  if (!revealed.value) {
    if (event.key === ' ' || event.key === 'Enter') {
      event.preventDefault()
      reveal()
    }
    return
  }
  // Answer is showing: digits grade explicitly; space/enter = Good (Anki default).
  if (event.key === ' ' || event.key === 'Enter') {
    event.preventDefault()
    void grade(Grade.Good)
  } else if (event.key >= '1' && event.key <= '4') {
    event.preventDefault()
    void grade(Number(event.key) as Grade)
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  stage.value?.focus()
  void load()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div
      ref="stage"
      class="stage"
      role="dialog"
      aria-modal="true"
      tabindex="-1"
      :aria-label="t('flashcards.review.title')"
    >
      <header class="stage-head">
        <div class="head-context">
          <span class="head-title">{{ t('flashcards.review.title') }}</span>
          <span v-if="deckName" class="head-deck">{{ deckName }}</span>
        </div>
        <div v-if="phase === 'reviewing'" class="head-progress">
          <span class="progress-count">{{ t('flashcards.review.progress', { done: index, total }) }}</span>
          <div class="progress-track" aria-hidden="true">
            <div class="progress-fill" :style="{ width: `${progressPercent}%` }"></div>
          </div>
        </div>
        <AppButton variant="ghost" tone="secondary" icon-left="close" :aria-label="t('flashcards.review.exit')" @click="close">
          {{ t('flashcards.review.exit') }}
        </AppButton>
      </header>

      <main class="stage-body">
        <!-- Loading -->
        <div v-if="phase === 'loading'" class="state">
          <AppLoading />
          <p class="state-text">{{ t('flashcards.review.loading') }}</p>
        </div>

        <!-- Error -->
        <div v-else-if="phase === 'error'" class="state">
          <AppIcon name="alert-triangle" :size="32" class="state-icon" />
          <p class="state-title">{{ t('flashcards.review.errorTitle') }}</p>
          <p class="state-text">{{ t('flashcards.review.errorBody') }}</p>
          <div class="state-actions">
            <AppButton variant="soft" @click="load">{{ t('flashcards.review.retry') }}</AppButton>
            <AppButton variant="ghost" tone="secondary" @click="close">{{ t('flashcards.review.exit') }}</AppButton>
          </div>
        </div>

        <!-- Nothing due -->
        <div v-else-if="phase === 'empty'" class="state">
          <AppIcon name="check-circle" :size="32" class="state-icon done-icon" />
          <p class="state-title">{{ t('flashcards.review.emptyTitle') }}</p>
          <p class="state-text">{{ t('flashcards.review.emptyBody') }}</p>
          <AppButton @click="close">{{ t('flashcards.review.finish') }}</AppButton>
        </div>

        <!-- Session done -->
        <div v-else-if="phase === 'done'" class="state">
          <AppIcon name="check-circle" :size="32" class="state-icon done-icon" />
          <p class="state-title">{{ t('flashcards.review.doneTitle') }}</p>
          <p class="state-text">{{ t('flashcards.review.doneReviewed', { n: reviewedCount }) }}</p>
          <ul class="done-breakdown">
            <li><span class="dot again"></span>{{ t('flashcards.review.grades.again') }} · {{ tally.again }}</li>
            <li><span class="dot hard"></span>{{ t('flashcards.review.grades.hard') }} · {{ tally.hard }}</li>
            <li><span class="dot good"></span>{{ t('flashcards.review.grades.good') }} · {{ tally.good }}</li>
            <li><span class="dot easy"></span>{{ t('flashcards.review.grades.easy') }} · {{ tally.easy }}</li>
          </ul>
          <p v-if="summary" class="state-text remaining">
            {{ t('flashcards.review.remaining', { due: summary.dueRemaining, new: summary.newRemaining }) }}
          </p>
          <AppButton @click="close">{{ t('flashcards.review.finish') }}</AppButton>
        </div>

        <!-- Reviewing -->
        <template v-else-if="current">
          <div class="card" @click="reveal">
            <span v-if="current.isNew" class="new-flag">{{ t('flashcards.review.newFlag') }}</span>

            <div class="card-face">
              <span class="face-label">{{ t('flashcards.front') }}</span>
              <p class="face-text">{{ current.front }}</p>
            </div>

            <template v-if="revealed">
              <hr class="divider" />
              <div class="card-face">
                <span class="face-label">{{ t('flashcards.back') }}</span>
                <p class="face-text answer">{{ current.back }}</p>
              </div>
            </template>
          </div>
          <p v-if="gradeError" class="grade-error">{{ t('flashcards.review.gradeError') }}</p>
        </template>
      </main>

      <footer v-if="phase === 'reviewing' && current" class="stage-foot">
        <AppButton v-if="!revealed" class="reveal-btn" icon-left="eye" @click="reveal">
          {{ t('flashcards.review.reveal') }}
          <kbd class="kbd">Space</kbd>
        </AppButton>
        <div v-else class="grades">
          <AppButton
            v-for="g in GRADES"
            :key="g.grade"
            :variant="g.variant"
            :tone="g.tone"
            :loading="grading"
            class="grade-btn"
            @click="grade(g.grade)"
          >
            {{ t(`flashcards.review.grades.${GRADE_KEY[g.grade]}`) }}
            <kbd class="kbd">{{ g.key }}</kbd>
          </AppButton>
        </div>
      </footer>
    </div>
  </Teleport>
</template>

<style scoped>
.stage {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  flex-direction: column;
  background-color: var(--color-bg);
  outline: none;
}

.stage-head {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  padding: var(--space-4) var(--space-6);
  border-bottom: var(--border-width-sm) solid var(--color-border);
}

.head-context {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.head-title {
  font-size: var(--text-sm);
  font-weight: 650;
}

.head-deck {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.head-progress {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  max-width: 520px;
  margin: 0 auto;
}

.progress-count {
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

.progress-track {
  flex: 1;
  height: 4px;
  border-radius: var(--radius-full);
  background-color: var(--color-border);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: var(--radius-full);
  background-color: var(--color-primary);
  transition: width var(--duration-base) var(--ease-out);
}

.stage-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  padding: var(--space-6);
  min-height: 0;
}

/* States */
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  text-align: center;
  max-width: 420px;
}

.state-icon {
  font-size: 2rem;
  color: var(--color-text-tertiary);
}

.done-icon {
  color: var(--color-primary);
}

.state-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 650;
}

.state-text {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.state-text.remaining {
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
}

.state-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.done-breakdown {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-4);
  margin: var(--space-2) 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.done-breakdown li {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-variant-numeric: tabular-nums;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-full);
}

.dot.again {
  background-color: var(--color-danger);
}

.dot.hard {
  background-color: var(--color-text-tertiary);
}

.dot.good {
  background-color: var(--color-primary);
}

.dot.easy {
  background-color: var(--color-success, var(--color-primary));
}

/* Card */
.card {
  position: relative;
  width: min(640px, 100%);
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-5);
  padding: var(--space-8);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-xl);
  background-color: var(--color-surface);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  text-align: center;
}

.new-flag {
  position: absolute;
  top: var(--space-4);
  right: var(--space-4);
  padding: 2px var(--space-2);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-primary);
  background-color: var(--color-primary-soft);
}

.card-face {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
}

.face-label {
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.face-text {
  margin: 0;
  font-size: var(--text-xl);
  line-height: var(--leading-normal);
  white-space: pre-wrap;
}

.face-text.answer {
  font-size: var(--text-lg);
  color: var(--color-text-secondary);
}

.divider {
  width: 100%;
  height: var(--border-width-sm);
  border: none;
  background-color: var(--color-border);
}

.grade-error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

/* Footer actions */
.stage-foot {
  display: flex;
  justify-content: center;
  padding: var(--space-5) var(--space-6) var(--space-8);
  border-top: var(--border-width-sm) solid var(--color-border);
}

.reveal-btn {
  min-width: 220px;
}

.grades {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 160px));
  gap: var(--space-3);
  width: 100%;
  max-width: 720px;
}

.grade-btn {
  flex-direction: column;
  gap: 2px;
}

.kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.4em;
  padding: 0 4px;
  border-radius: var(--radius-sm);
  background-color: color-mix(in srgb, currentColor 16%, transparent);
  font-size: var(--text-xs);
  font-family: var(--font-mono, monospace);
  line-height: 1.6;
}

@media (max-width: 640px) {
  .head-progress {
    display: none;
  }

  .grades {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
