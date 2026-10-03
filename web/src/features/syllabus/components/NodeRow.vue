<script setup lang="ts">
/**
 * One line of the syllabus map: a node's name (a link to its page), how well
 * it is held, what the bank offers there, and the verb — 练习. Used for
 * modules, chapters and 考点 alike; a 考点 shows mastery coloured by level,
 * anything above it shows readiness in the paper's accent.
 *
 * The `lead` slot takes the disclosure control of an expandable row, so the
 * tree's structure stays in the parent and the row stays a row.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton } from '@/components'
import type { NodeProgressDto } from '@/api/modules/mastery'
import type { SyllabusNode } from '../syllabusIndex'
import MasteryMeter from './MasteryMeter.vue'

const props = withDefaults(
  defineProps<{
    node: SyllabusNode
    /** Absent when the server has nothing to report (an untested 考点 with no questions). */
    progress?: NodeProgressDto
    /** The paper's accent, for aggregate readiness. */
    accent: string
    /** This row's practice launch is in flight. */
    busy?: boolean
    /** Another launch is in flight. */
    locked?: boolean
  }>(),
  { progress: undefined, busy: false, locked: false },
)

defineEmits<{ practice: [code: string] }>()

const { t } = useI18n()

const isPoint = computed(() => props.node.kind === 'point')
const value = computed(() => (isPoint.value ? props.progress?.mastery : props.progress?.readiness) ?? null)
const available = computed(() => props.progress?.available ?? 0)
const mistakes = computed(() => props.progress?.mistakes ?? 0)
</script>

<template>
  <div class="node-row" :class="`node-row--${node.kind}`">
    <span class="lead"><slot name="lead" /></span>

    <span class="name">
      <RouterLink :to="{ name: 'syllabus-node', params: { code: node.code } }" class="name-link">
        {{ node.name }}
      </RouterLink>
      <span v-if="isPoint && node.weight >= 3" class="hot">{{ t('syllabus.weight.3') }}</span>
      <span v-if="node.kind === 'module' && node.score > 0" class="score">
        {{ t('syllabus.score', { n: node.score }) }}
      </span>
    </span>

    <MasteryMeter
      class="meter"
      compact
      :value="value"
      :level="isPoint ? (progress?.level ?? 'untested') : null"
      :color="isPoint ? undefined : accent"
      :label="t(isPoint ? 'syllabus.mastery' : 'syllabus.readiness')"
    />

    <span class="stats">
      <span>{{ t('syllabus.questions', { n: available }) }}</span>
      <span v-if="mistakes > 0" class="stat-mistakes">{{ t('syllabus.mistakes', { n: mistakes }) }}</span>
    </span>

    <AppButton
      class="verb"
      size="sm"
      variant="ghost"
      :loading="busy"
      :disabled="available === 0 || locked"
      :aria-label="t('syllabus.practiceNode', { name: node.name })"
      @click="$emit('practice', node.code)"
    >
      {{ t('syllabus.practice') }}
    </AppButton>
  </div>
</template>

<style scoped>
.node-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) minmax(96px, 150px) auto auto;
  align-items: center;
  gap: var(--space-3);
  min-height: 40px;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-md);
}

.node-row:hover {
  background-color: var(--color-surface-hover);
}

.lead {
  display: inline-flex;
  width: 24px;
  justify-content: center;
}

.name {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.name-link {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text);
  text-decoration: none;
}

.name-link:hover {
  color: var(--color-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.node-row--module .name-link {
  font-weight: 600;
}

.node-row--chapter .name-link {
  font-weight: 500;
}

.node-row--point .name-link {
  font-size: var(--text-sm);
}

.hot {
  flex-shrink: 0;
  padding: 0 var(--space-2);
  border-radius: var(--radius-full);
  background-color: var(--color-warning-soft);
  color: var(--color-warning);
  font-size: var(--text-xs);
  font-weight: 500;
  line-height: 1.6;
}

.score {
  flex-shrink: 0;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

.stats {
  display: inline-flex;
  gap: var(--space-2);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.stat-mistakes {
  color: var(--color-danger);
}

@media (max-width: 640px) {
  /* Two lines: name and verb, then meter and counts beneath the name. */
  .node-row {
    grid-template-columns: auto minmax(0, 1fr) auto;
    row-gap: var(--space-1);
  }

  .lead {
    grid-area: 1 / 1;
  }

  .name {
    grid-area: 1 / 2;
  }

  .verb {
    grid-area: 1 / 3;
  }

  .meter {
    grid-area: 2 / 2;
  }

  .stats {
    grid-area: 2 / 3;
    justify-self: end;
  }
}
</style>
