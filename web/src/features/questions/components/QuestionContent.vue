<script setup lang="ts">
/**
 * A question as it is printed: its provenance, the shared passage, the stem
 * and — for choice questions — the options. The same component asks the
 * question in a practice set, shows it in the mistake book and previews it on
 * a 考点 page, so a question reads identically wherever it appears.
 *
 * Options are buttons only while `selectable`; the selection is the canonical
 * letter string the server grades (`"B"`, `"ACD"`). Once `correct` is given the
 * options are marked — the right answer, a wrong pick, a right answer missed —
 * with a word as well as a colour, so the marking never relies on colour alone.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'
import RichText from '@/components/RichText.vue'
import type { QuestionDto } from '@/api/modules/question'

const props = withDefaults(
  defineProps<{
    question: QuestionDto
    /** Selected letters, canonical (upper-case, sorted). */
    modelValue?: string
    selectable?: boolean
    /** The correct letters, once revealed; marks the options. */
    correct?: string | null
    /** 1-based position in a set, shown before the stem. */
    number?: number | null
  }>(),
  { modelValue: '', selectable: false, correct: null, number: null },
)

const emit = defineEmits<{ 'update:modelValue': [letters: string] }>()

const { t } = useI18n()

const isChoice = computed(() => props.question.type === 'single_choice' || props.question.type === 'multi_choice')
const letters = computed(() => props.question.options.map((_, index) => String.fromCharCode(65 + index)))

const provenance = computed(() => {
  const { source, sourceYear } = props.question
  if (source && sourceYear) return t('question.sourceYear', { source, year: sourceYear })
  return source ?? null
})

function toggle(letter: string) {
  if (!props.selectable) return
  if (props.question.type === 'single_choice') {
    emit('update:modelValue', props.modelValue === letter ? '' : letter)
    return
  }
  const chosen = new Set(props.modelValue.split('').filter(Boolean))
  if (chosen.has(letter)) chosen.delete(letter)
  else chosen.add(letter)
  emit('update:modelValue', [...chosen].sort().join(''))
}

type Mark = 'correct' | 'wrong' | 'missed' | null

function markOf(letter: string): Mark {
  if (props.correct === null) return null
  const isCorrect = props.correct.includes(letter)
  const isPicked = props.modelValue.includes(letter)
  if (isCorrect && isPicked) return 'correct'
  // "Missed" only means something when several letters were due.
  if (isCorrect) return props.modelValue && props.question.type === 'multi_choice' ? 'missed' : 'correct'
  return isPicked ? 'wrong' : null
}
</script>

<template>
  <article class="question">
    <p class="meta">
      <span class="type">{{ t(`question.type.${question.type}`) }}</span>
      <span v-if="question.type === 'multi_choice'" class="hint">{{ t('question.multiHint') }}</span>
      <span v-if="provenance" class="source">{{ provenance }}</span>
      <span v-if="question.score" class="score">{{ t('question.score', { n: question.score }) }}</span>
      <span
        class="difficulty"
        :title="t('question.difficulty', { n: question.difficulty })"
        :aria-label="t('question.difficulty', { n: question.difficulty })"
      >
        <span v-for="level in 5" :key="level" class="pip" :class="{ on: level <= question.difficulty }"></span>
      </span>
      <span v-if="question.mine" class="mine">{{ t('question.mine') }}</span>
    </p>

    <div v-if="question.passage" class="passage">
      <RichText :source="question.passage" />
    </div>

    <div class="stem">
      <span v-if="number" class="number">{{ number }}.</span>
      <RichText :source="question.stem" />
    </div>

    <ol v-if="isChoice" class="options" :aria-label="t('question.options')">
      <li v-for="(option, index) in question.options" :key="index">
        <component
          :is="selectable ? 'button' : 'div'"
          :type="selectable ? 'button' : undefined"
          class="option"
          :class="[
            markOf(letters[index]!) ? `mark-${markOf(letters[index]!)}` : '',
            { selected: modelValue.includes(letters[index]!), interactive: selectable },
          ]"
          :aria-pressed="selectable ? modelValue.includes(letters[index]!) : undefined"
          @click="toggle(letters[index]!)"
        >
          <span class="letter" aria-hidden="true">{{ letters[index] }}</span>
          <span class="sr-only">{{ letters[index] }}.</span>
          <RichText :source="option" inline class="option-text" />
          <span v-if="markOf(letters[index]!)" class="mark">
            <AppIcon
              :name="markOf(letters[index]!) === 'wrong' ? 'close' : 'check'"
              size="sm"
              aria-hidden="true"
            />
            {{ t(`question.mark.${markOf(letters[index]!)}`) }}
          </span>
        </component>
      </li>
    </ol>
  </article>
</template>

<style scoped>
.question {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-width: 0;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.type {
  padding: 1px var(--space-2);
  border-radius: var(--radius-sm);
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}

.hint {
  color: var(--color-warning);
  font-weight: 500;
}

.score {
  font-variant-numeric: tabular-nums;
}

.difficulty {
  display: inline-flex;
  gap: 2px;
}

.pip {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
}

.pip.on {
  background-color: var(--color-text-secondary);
}

.mine {
  color: var(--color-text-tertiary);
}

.passage {
  max-height: 360px;
  padding: var(--space-3) var(--space-4);
  overflow-y: auto;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface-hover);
}

.stem {
  display: flex;
  gap: var(--space-2);
  min-width: 0;
}

.stem > :deep(.rich-text) {
  flex: 1;
  min-width: 0;
}

.number {
  flex-shrink: 0;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.75;
  color: var(--color-text);
}

.options {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.option {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  text-align: left;
}

.option.interactive {
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.option.interactive:hover {
  border-color: var(--color-border-strong);
}

.option.interactive:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.option.selected {
  border-color: var(--color-primary);
  background-color: var(--color-primary-soft);
}

.letter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border: var(--border-width-sm) solid var(--color-border-strong);
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  font-weight: 600;
}

.selected .letter {
  border-color: var(--color-primary);
  background-color: var(--color-primary);
  color: var(--color-surface);
}

.option-text {
  flex: 1;
  min-width: 0;
  padding-top: 2px;
}

.mark {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
  padding-top: 3px;
  font-size: var(--text-xs);
  font-weight: 600;
}

.mark-correct {
  border-color: var(--color-success);
  background-color: var(--color-success-soft);
}

.mark-correct .mark {
  color: var(--color-success);
}

.mark-wrong {
  border-color: var(--color-danger);
  background-color: var(--color-danger-soft);
}

.mark-wrong .mark {
  color: var(--color-danger);
}

.mark-missed {
  border-color: var(--color-success);
  border-style: dashed;
}

.mark-missed .mark {
  color: var(--color-success);
}

.mark-correct .letter,
.mark-missed .letter {
  border-color: var(--color-success);
}

.mark-wrong .letter {
  border-color: var(--color-danger);
  background-color: var(--color-danger);
  color: var(--color-surface);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (prefers-reduced-motion: reduce) {
  .option.interactive {
    transition: none;
  }
}
</style>
