<script setup lang="ts">
/**
 * One question of a handed-in paper, reviewed: the question as printed with
 * the options marked, the candidate's answer, the reference and its 解析, what
 * the answer did to the mistake book, and the AI walk-through on request —
 * the same pieces the practice stage uses, so a question reads the same
 * wherever it is reviewed.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppIcon } from '@/components'
import type { PaperItemDto } from '@/api/modules/paper'
import { streamQuestionExplain } from '@/api/modules/ai'
import AiExplainPanel from '@/features/ai-tutor/AiExplainPanel.vue'
import QuestionContent from '@/features/questions/components/QuestionContent.vue'
import SolutionBlock from '@/features/questions/components/SolutionBlock.vue'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'
import { formatPoints, toMinutes } from '../paperFormat'

const props = defineProps<{ item: PaperItemDto }>()

const { t, d } = useI18n()

const explaining = ref(false)
watch(
  () => props.item.question.id,
  () => (explaining.value = false),
)

const isChoice = computed(
  () => props.item.question.type === 'single_choice' || props.item.question.type === 'multi_choice',
)

const verdict = computed(() => {
  const mark = props.item.mark
  if (!mark || mark.verdict === 'blank') return 'blank'
  return mark.result ?? 'blank'
})

const tone = computed(() =>
  verdict.value === 'correct' ? 'success' : verdict.value === 'partial' ? 'warning' : verdict.value === 'wrong' ? 'danger' : 'muted',
)

/** What the answer did to the mistake book, in words — the practice stage's sentence. */
const mistakeLine = computed(() => {
  const change = props.item.mark?.mistake
  if (!change || change.change === 'none') return null
  return t(`practice.mistake.${change.change}`, {
    date: change.nextDueAt ? d(change.nextDueAt, 'short') : '',
    streak: change.correctStreak,
    goal: change.resolveStreak,
  })
})

function run(signal: AbortSignal) {
  return streamQuestionExplain(props.item.question.id, props.item.response, signal)
}
</script>

<template>
  <div class="review">
    <p class="verdict" :class="`tone-${tone}`" role="status">
      <AppIcon :name="verdict === 'correct' ? 'check-circle' : verdict === 'blank' ? 'minus' : 'alert-circle'" aria-hidden="true" />
      {{ t(`papers.report.result.${verdict}`) }}
      <span class="verdict-points">
        {{ formatPoints(item.mark?.points ?? 0) }} / {{ formatPoints(item.worth) }}
      </span>
      <span v-if="item.mark?.verdict === 'self'" class="verdict-note">{{ t('papers.review.selfScored') }}</span>
    </p>

    <div class="points">
      <NodeChip v-for="code in item.question.points" :key="code" :code="code" />
    </div>

    <QuestionContent
      :question="item.question"
      :number="item.number"
      :model-value="isChoice ? (item.response ?? '') : ''"
      :correct="isChoice ? (item.solution?.answer ?? null) : null"
    />

    <section v-if="!isChoice" class="given">
      <h3 class="label">{{ t('papers.scoring.yourAnswer') }}</h3>
      <p class="given-text">{{ item.response ?? t('papers.review.blank') }}</p>
    </section>
    <p v-else-if="!item.response" class="given-text muted">{{ t('papers.review.blank') }}</p>

    <p class="meta">
      <AppIcon name="clock" size="sm" aria-hidden="true" />
      {{ t('papers.review.time', { n: toMinutes(item.seconds) }) }}
    </p>

    <p v-if="mistakeLine" class="mistake-line">
      <AppIcon name="book-x" size="sm" aria-hidden="true" />
      {{ mistakeLine }}
    </p>

    <SolutionBlock v-if="item.solution" :solution="item.solution" :type="item.question.type" />

    <AiExplainPanel v-if="explaining" :title="t('practice.explainTitle')" :run="run" @close="explaining = false" />
    <AppButton v-else variant="soft" tone="secondary" icon-left="sparkles" @click="explaining = true">
      {{ verdict === 'correct' ? t('practice.explain') : t('practice.explainWrong') }}
    </AppButton>
  </div>
</template>

<style scoped>
.review {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.verdict {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-weight: 600;
}

.tone-success {
  color: var(--color-success);
}

.tone-warning {
  color: var(--color-warning);
}

.tone-danger {
  color: var(--color-danger);
}

.tone-muted {
  color: var(--color-text-secondary);
}

.verdict-points {
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.verdict-note {
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-text-tertiary);
}

.points {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-4);
}

.label {
  margin: 0 0 var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.given-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
}

.muted,
.meta {
  color: var(--color-text-secondary);
}

.meta,
.mistake-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
}

.mistake-line {
  color: var(--color-text-secondary);
}
</style>
