<script setup lang="ts">
/**
 * What a finished set says: the score, the time, and — the part worth reading —
 * how each 考点 went, weakest first, with the next step one click away. Wrong
 * answers are already in the mistake book by the time this renders; the
 * report says so instead of asking the candidate to file them.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton } from '@/components'
import type { PracticeReportDto } from '@/api/modules/practice'
import MasteryMeter from '@/features/syllabus/components/MasteryMeter.vue'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'

const props = defineProps<{
  report: PracticeReportDto
  /** The launch in flight, if any (disables the next-step buttons). */
  launching: string | null
}>()

defineEmits<{
  again: []
  drill: [nodeCode: string]
}>()

const { t } = useI18n()

const answered = computed(() => props.report.session.answered)
const accuracy = computed(() =>
  answered.value > 0 ? Math.round((props.report.session.correct / answered.value) * 100) : null,
)
const minutes = computed(() => Math.floor(props.report.durationSeconds / 60))
const seconds = computed(() => props.report.durationSeconds % 60)
const weakest = computed(() => {
  const first = props.report.points[0]
  return first && first.correct < first.attempted ? first : null
})
const toMistakeBook = computed(() => props.report.wrong + props.report.partial)
</script>

<template>
  <section class="report" :aria-label="t('practice.report.title')">
    <header class="headline">
      <p class="kicker">{{ t('practice.report.title') }}</p>
      <p class="score">
        <span class="score-value">{{ report.session.correct }}</span>
        <span class="score-of">/ {{ answered }}</span>
      </p>
      <p class="facts">
        <span>{{ accuracy === null ? '—' : t('practice.report.accuracy', { p: accuracy }) }}</span>
        <span>{{ t('practice.report.duration', { m: minutes, s: seconds }) }}</span>
        <span v-if="report.partial > 0">{{ t('practice.report.partial', { n: report.partial }) }}</span>
        <span v-if="answered < report.session.total">
          {{ t('practice.report.skipped', { n: report.session.total - answered }) }}
        </span>
      </p>
    </header>

    <p v-if="toMistakeBook > 0" class="filed">
      {{ t('practice.report.filed', { n: toMistakeBook }) }}
      <RouterLink :to="{ name: 'mistakes' }">{{ t('practice.report.openMistakes') }}</RouterLink>
    </p>

    <div v-if="report.points.length > 0" class="points">
      <h2 class="points-title">{{ t('practice.report.byPoint') }}</h2>
      <ul class="point-list">
        <li v-for="point in report.points" :key="point.nodeCode" class="point">
          <RouterLink :to="{ name: 'syllabus-node', params: { code: point.nodeCode } }" class="point-link">
            <NodeChip :code="point.nodeCode" />
          </RouterLink>
          <MasteryMeter
            compact
            :value="point.attempted > 0 ? point.correct / point.attempted : null"
            :level="
              point.correct === point.attempted ? 'mastered' : point.correct === 0 ? 'weak' : 'developing'
            "
            :label="t('practice.report.pointAccuracy')"
          />
          <span class="point-count">{{ point.correct }} / {{ point.attempted }}</span>
        </li>
      </ul>
    </div>

    <footer class="next">
      <AppButton
        v-if="weakest"
        icon-left="target"
        :loading="launching === weakest.nodeCode"
        :disabled="launching !== null"
        @click="$emit('drill', weakest.nodeCode)"
      >
        {{ t('practice.report.drillWeakest') }}
      </AppButton>
      <AppButton
        variant="soft"
        tone="secondary"
        icon-left="refresh"
        :loading="launching === 'again'"
        :disabled="launching !== null"
        @click="$emit('again')"
      >
        {{ t('practice.report.again') }}
      </AppButton>
      <RouterLink :to="{ name: 'practice' }" class="back">{{ t('practice.report.back') }}</RouterLink>
    </footer>
  </section>
</template>

<style scoped>
.report {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  padding: var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.headline {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.kicker {
  margin: 0;
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.score {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  margin: 0;
  font-variant-numeric: tabular-nums;
}

.score-value {
  font-family: var(--font-display-family);
  font-size: var(--font-display-size);
  font-weight: var(--font-display-weight);
  line-height: 1;
  color: var(--color-text);
}

.score-of {
  font-size: var(--text-xl);
  color: var(--color-text-secondary);
}

.facts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.filed {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background-color: var(--color-info-soft);
  font-size: var(--text-sm);
  color: var(--color-text);
}

.filed a {
  margin-left: var(--space-2);
  color: var(--color-primary);
}

.points {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.points-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.point-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.point {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(96px, 160px) auto;
  align-items: center;
  gap: var(--space-3);
}

.point-link {
  display: inline-flex;
  min-width: 0;
  text-decoration: none;
}

.point-link:hover :deep(.path) {
  color: var(--color-primary);
  text-decoration: underline;
}

.point-count {
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.next {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.back {
  margin-left: auto;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.back:hover {
  color: var(--color-primary);
}

@media (max-width: 640px) {
  .report {
    padding: var(--space-4);
  }

  .point {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .point :deep(.meter) {
    display: none;
  }
}
</style>
