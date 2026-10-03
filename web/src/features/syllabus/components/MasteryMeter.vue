<script setup lang="ts">
/**
 * How well something is held, as a quiet bar and a number. A 考点 shows its
 * mastery, coloured by level; an aggregate shows readiness in its paper's
 * accent. Untested renders as "—", never as 0% — unknown is not zero.
 *
 * Colours are existing semantic tokens (danger → warning → info → success),
 * the same ramp everywhere mastery appears.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { MasteryLevel } from '@/api/modules/mastery'

const props = withDefaults(
  defineProps<{
    /** 0–1, or null when there is no evidence. */
    value: number | null | undefined
    /** A 考点's level; aggregates pass none and use `color`. */
    level?: MasteryLevel | null
    /** Fill colour for aggregates (a paper accent token). */
    color?: string
    /** What the number measures, for assistive tech. */
    label: string
    compact?: boolean
  }>(),
  { level: null, color: undefined, compact: false },
)

const { t } = useI18n()

const LEVEL_COLOR: Record<MasteryLevel, string> = {
  untested: 'var(--color-muted)',
  weak: 'var(--color-danger)',
  developing: 'var(--color-warning)',
  proficient: 'var(--color-info)',
  mastered: 'var(--color-success)',
}

const known = computed(() => props.value !== null && props.value !== undefined)
const percent = computed(() => (known.value ? Math.round((props.value as number) * 100) : 0))
const fill = computed(() => props.color ?? LEVEL_COLOR[props.level ?? 'untested'])
const text = computed(() => (known.value ? `${percent.value}%` : '—'))
</script>

<template>
  <span class="meter" :class="{ compact }">
    <span
      class="track"
      role="meter"
      :aria-label="label"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="known ? percent : undefined"
      :aria-valuetext="known ? text : t('exam.levels.untested')"
    >
      <span v-if="known" class="fill" :style="{ width: `${percent}%`, backgroundColor: fill }"></span>
    </span>
    <span class="value" aria-hidden="true">{{ text }}</span>
  </span>
</template>

<style scoped>
.meter {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.track {
  position: relative;
  flex: 1;
  min-width: 48px;
  height: 6px;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
  overflow: hidden;
}

.fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: var(--radius-full);
  transition: width var(--duration-slow) var(--ease-out);
}

.value {
  flex-shrink: 0;
  min-width: 3ch;
  font-size: var(--text-xs);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  text-align: right;
  color: var(--color-text-secondary);
}

.compact .track {
  height: 4px;
  min-width: 36px;
}

@media (prefers-reduced-motion: reduce) {
  .fill {
    transition: none;
  }
}
</style>
