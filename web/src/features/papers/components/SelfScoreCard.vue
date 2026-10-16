<script setup lang="ts">
/**
 * One answer the grader could not judge, scored by the candidate the way an
 * examiner would: their answer beside the reference and its scoring points,
 * and a mark. A fill-blank is worth all or nothing (as printed); an open answer
 * takes any half point from 0 to its worth.
 *
 * The card only proposes a mark (`change`); the result view saves it, and the
 * server checks it again.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'
import type { PaperItemDto } from '@/api/modules/paper'
import QuestionContent from '@/features/questions/components/QuestionContent.vue'
import SolutionBlock from '@/features/questions/components/SolutionBlock.vue'
import { formatPoints, snapScore } from '../paperFormat'

const props = defineProps<{
  item: PaperItemDto
  sectionName: string
  /** The mark given so far, or null. */
  value: number | null
}>()

const emit = defineEmits<{ change: [points: number | null] }>()

const { t } = useI18n()

const isFill = computed(() => props.item.question.type === 'fill_blank')
const showQuestion = ref(false)

/** The text in the box, kept as typed until it is a mark. */
const draft = ref(props.value === null ? '' : formatPoints(props.value))
watch(
  () => props.value,
  (value) => {
    const typed = Number(draft.value)
    if (value === null ? draft.value !== '' : typed !== value) {
      draft.value = value === null ? '' : formatPoints(value)
    }
  },
)

function give(points: number): void {
  const snapped = snapScore(points, props.item.worth)
  draft.value = formatPoints(snapped)
  emit('change', snapped)
}

function step(delta: number): void {
  give((props.value ?? 0) + delta)
}

function onInput(event: Event): void {
  const raw = (event.target as HTMLInputElement).value
  draft.value = raw
  if (raw.trim() === '') {
    emit('change', null)
    return
  }
  const parsed = Number(raw)
  if (Number.isFinite(parsed)) emit('change', snapScore(parsed, props.item.worth))
}

function onBlur(): void {
  draft.value = props.value === null ? '' : formatPoints(props.value)
}
</script>

<template>
  <article class="card" :class="{ scored: value !== null }" :aria-labelledby="`score-${item.question.id}`">
    <header class="card-head">
      <p :id="`score-${item.question.id}`" class="card-title">
        <span class="section">{{ sectionName }}</span>
        <span>{{ t('papers.exam.number', { n: item.number }) }}</span>
        <span class="worth">{{ t('papers.exam.worth', { n: formatPoints(item.worth) }) }}</span>
      </p>
      <span v-if="value !== null" class="given">
        <AppIcon name="check" size="sm" aria-hidden="true" />
        {{ t('papers.scoring.given', { points: formatPoints(value), worth: formatPoints(item.worth) }) }}
      </span>
      <span v-else class="pending">{{ t('papers.scoring.pendingOne') }}</span>
    </header>

    <button type="button" class="toggle" :aria-expanded="showQuestion" @click="showQuestion = !showQuestion">
      <AppIcon :name="showQuestion ? 'chevron-up' : 'chevron-down'" size="sm" aria-hidden="true" />
      {{ showQuestion ? t('papers.scoring.hideQuestion') : t('papers.scoring.showQuestion') }}
    </button>
    <QuestionContent v-if="showQuestion" :question="item.question" :number="item.number" />

    <div class="answers">
      <section class="yours" :aria-label="t('papers.scoring.yourAnswer')">
        <h3 class="label">{{ t('papers.scoring.yourAnswer') }}</h3>
        <p class="your-text">{{ item.response }}</p>
      </section>
      <SolutionBlock v-if="item.solution" :solution="item.solution" :type="item.question.type" />
    </div>

    <div class="mark" role="group" :aria-label="t('papers.scoring.markLabel', { worth: formatPoints(item.worth) })">
      <template v-if="isFill">
        <button type="button" class="choice" :class="{ on: value === item.worth }" @click="give(item.worth)">
          <AppIcon name="check" size="sm" aria-hidden="true" />
          {{ t('papers.scoring.right', { n: formatPoints(item.worth) }) }}
        </button>
        <button type="button" class="choice" :class="{ on: value === 0 }" @click="give(0)">
          <AppIcon name="close" size="sm" aria-hidden="true" />
          {{ t('papers.scoring.wrong') }}
        </button>
      </template>
      <template v-else>
        <span class="stepper">
          <button type="button" class="step" :aria-label="t('papers.scoring.less')" @click="step(-0.5)">
            <AppIcon name="minus" size="sm" aria-hidden="true" />
          </button>
          <input
            class="points"
            type="text"
            inputmode="decimal"
            :value="draft"
            :aria-label="t('papers.scoring.points')"
            :placeholder="t('papers.scoring.placeholder')"
            @input="onInput"
            @blur="onBlur"
          />
          <button type="button" class="step" :aria-label="t('papers.scoring.more')" @click="step(0.5)">
            <AppIcon name="plus" size="sm" aria-hidden="true" />
          </button>
        </span>
        <span class="out-of">/ {{ formatPoints(item.worth) }}</span>
        <button type="button" class="choice" :class="{ on: value === 0 }" @click="give(0)">
          {{ t('papers.scoring.zero') }}
        </button>
        <button type="button" class="choice" :class="{ on: value === item.worth }" @click="give(item.worth)">
          {{ t('papers.scoring.full') }}
        </button>
      </template>
    </div>
  </article>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.card.scored {
  border-color: var(--color-border-strong);
}

.card-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.card-title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-3);
  margin: 0;
  font-weight: 600;
}

.section {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--paper-accent, var(--color-primary));
}

.worth {
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-text-secondary);
}

.given {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-success);
}

.pending {
  font-size: var(--text-sm);
  color: var(--color-warning);
}

.toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  width: fit-content;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
}

.toggle:hover {
  color: var(--color-primary);
}

.answers {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr);
  gap: var(--space-5);
}

.label {
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
}

.your-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
  color: var(--color-text);
}

.mark {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: var(--border-width-sm) solid var(--color-border);
}

.stepper {
  display: inline-flex;
  align-items: stretch;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  overflow: hidden;
}

.step {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  min-height: 36px;
  border: none;
  background-color: var(--color-surface-hover);
  color: var(--color-text);
  cursor: pointer;
}

.points {
  width: 64px;
  border: none;
  border-left: var(--border-width-sm) solid var(--color-border);
  border-right: var(--border-width-sm) solid var(--color-border);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-base);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: center;
}

.points:focus {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--color-focus-ring);
}

.out-of {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.choice {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: 36px;
  padding: 0 var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-button);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
}

.choice:hover,
.step:hover {
  border-color: var(--color-border-strong);
}

.choice.on {
  border-color: var(--color-primary);
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}

.choice:focus-visible,
.step:focus-visible,
.toggle:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

@media (max-width: 760px) {
  .card {
    padding: var(--space-4);
  }

  .answers {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
