<script setup lang="ts">
/**
 * Where a paper's points are lost: each printed section, the weighted share of
 * its worth the candidate earns, and that share as points. A meter per
 * section in the paper's accent; the value is text beside it, never the bar's
 * color. An untested section is "未测" with an empty track — not 0%, which
 * would claim a result that does not exist.
 */
import { useI18n } from 'vue-i18n'
import type { SittingSectionProfileDto } from '@/api/modules/sitting'
import { formatScore } from '../sittingFormat'

defineProps<{
  sections: SittingSectionProfileDto[]
  /** Section code → printed name (syllabus content). */
  names: Record<string, string>
  accent: string
}>()

const { t } = useI18n()
</script>

<template>
  <ul class="section-profile">
    <li v-for="section in sections" :key="section.code" class="row">
      <div class="head">
        <span class="name">{{ names[section.code] ?? section.code }}</span>
        <span v-if="section.rate !== null" class="value">
          <strong>{{ formatScore(section.averageScore ?? 0) }}</strong>
          <span class="of">/ {{ formatScore(section.full) }}</span>
          <span class="rate">{{ Math.round(section.rate * 100) }}%</span>
        </span>
        <span v-else class="value untested">{{ t('sittings.sections.untested') }}</span>
      </div>
      <div
        class="track"
        role="meter"
        :aria-label="names[section.code] ?? section.code"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuenow="section.rate === null ? undefined : Math.round(section.rate * 100)"
        :aria-valuetext="
          section.rate === null
            ? t('sittings.sections.untested')
            : `${formatScore(section.averageScore ?? 0)} / ${formatScore(section.full)}`
        "
      >
        <span
          v-if="section.rate !== null && section.rate > 0"
          class="fill"
          :style="{ width: `${Math.max(2, section.rate * 100)}%`, backgroundColor: accent }"
        ></span>
      </div>
      <span v-if="section.sittings > 0" class="basis">{{
        t('sittings.sections.basis', { n: section.sittings })
      }}</span>
    </li>
  </ul>
</template>

<style scoped>
.section-profile {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.name {
  min-width: 0;
  font-size: var(--text-sm);
  color: var(--color-text);
}

.value {
  display: inline-flex;
  flex-shrink: 0;
  align-items: baseline;
  gap: var(--space-1);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.of,
.rate {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.rate {
  margin-left: var(--space-1);
}

.untested {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.track {
  position: relative;
  height: 8px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
}

.fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: var(--radius-full);
}

.basis {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}
</style>
