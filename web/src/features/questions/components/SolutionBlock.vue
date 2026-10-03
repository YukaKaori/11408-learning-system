<script setup lang="ts">
/**
 * The reference answer and its 解析, as released after an answer is given (or
 * on request while browsing). For choice questions the answer is its letters;
 * for fill-blanks the value; for open questions a model answer — which is why
 * the answer goes through RichText too.
 */
import { useI18n } from 'vue-i18n'
import RichText from '@/components/RichText.vue'
import type { QuestionTypeCode } from '@/api/modules/exam'
import type { QuestionSolutionDto } from '@/api/modules/question'

defineProps<{
  solution: QuestionSolutionDto
  type: QuestionTypeCode
}>()

const { t } = useI18n()
</script>

<template>
  <section class="solution" :aria-label="t('question.solution')">
    <div class="part">
      <h3 class="label">{{ t(type === 'open' ? 'question.modelAnswer' : 'question.answer') }}</h3>
      <p v-if="type === 'single_choice' || type === 'multi_choice'" class="letters">{{ solution.answer }}</p>
      <RichText v-else :source="solution.answer" />
    </div>
    <div v-if="solution.analysis" class="part">
      <h3 class="label">{{ t('question.analysis') }}</h3>
      <RichText :source="solution.analysis" />
    </div>
  </section>
</template>

<style scoped>
.solution {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface-hover);
}

.part {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.label {
  margin: 0;
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.letters {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--color-success);
}
</style>
