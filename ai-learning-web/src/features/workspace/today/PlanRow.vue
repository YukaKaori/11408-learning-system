<script setup lang="ts">
/**
 * One commitment: an icon, a title, when it is owed, one primary verb.
 *
 * A row is *content* — the work the user is here to do — so it is a solid
 * surface by law (docs/liquid-material-system.md §1, phase17-material-audit
 * §2.3). No glass, no elevation drama, no card chrome per row.
 *
 * The row never decides its own rank: `tier` arrives from the server and is
 * rendered, never recomputed. The only thing this component derives is how to
 * *say* a timestamp, which is presentation.
 *
 * Step 3 scope: the verb navigates to the owning module. Acting in place —
 * mounting the review session, completing a task inline — is Step 4.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppIcon, type IconName } from '@/components'
import type { PlanItemDto } from '@/api/modules/workspace'
import { useSubjectsStore } from '@/stores/subjects'

const props = defineProps<{ item: PlanItemDto }>()

defineEmits<{ activate: [item: PlanItemDto] }>()

const { t, d } = useI18n()
const subjectsStore = useSubjectsStore()

const DAY = 86_400_000

const icon = computed<IconName>(() => {
  if (props.item.tier === 'overdue') return 'alert-triangle'
  switch (props.item.kind) {
    case 'review':
      return 'layers'
    case 'session':
      return 'calendar-days'
    default:
      return 'circle'
  }
})

const title = computed(() => {
  const { kind, review, task, session } = props.item
  if (kind === 'review' && review) {
    return t('today.plan.reviewTitle', { n: review.total })
  }
  if (kind === 'task' && task) return task.title
  if (kind === 'session' && session) {
    return (
      session.title ||
      subjectsStore.byId(session.subjectId)?.name ||
      t('calendar.session')
    )
  }
  return ''
})

/**
 * When it is owed, in the row's own words. Overdue is counted in whole days so
 * "overdue 2d" stays stable through the day rather than ticking.
 */
const when = computed(() => {
  const { kind, tier, task, session } = props.item
  if (tier === 'overdue' && task?.dueAt) {
    const days = Math.max(1, Math.floor((Date.now() - task.dueAt) / DAY))
    return t('today.plan.overdueBy', { n: days })
  }
  if (kind === 'review') return t('today.plan.now')
  if (kind === 'session' && session) {
    return `${d(session.startsAt, 'time')}–${d(session.endsAt, 'time')}`
  }
  if (kind === 'task' && task?.dueAt) return t('today.plan.today')
  return ''
})

const verb = computed(() => {
  switch (props.item.kind) {
    case 'review':
      return t('today.plan.verb.review')
    case 'session':
      return t('today.plan.verb.open')
    default:
      return t('today.plan.verb.open')
  }
})

/** Screen-reader context the visual tier colour carries silently. */
const tierLabel = computed(() =>
  props.item.tier === 'overdue' ? t('today.plan.tier.overdue') : '',
)
</script>

<template>
  <li class="plan-row" :class="`tier-${item.tier}`">
    <span class="row-icon" aria-hidden="true"><AppIcon :name="icon" size="sm" /></span>

    <span class="row-main">
      <span class="row-title">
        <span v-if="tierLabel" class="sr-only">{{ tierLabel }}</span>
        {{ title }}
      </span>
      <span v-if="when" class="row-when">{{ when }}</span>
    </span>

    <button type="button" class="row-verb" @click="$emit('activate', item)">
      {{ verb }}
      <AppIcon name="arrow-right" size="sm" aria-hidden="true" />
    </button>
  </li>
</template>

<style scoped>
.plan-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-bottom: var(--border-width-sm) solid var(--color-border);
}

.plan-row:last-child {
  border-bottom: none;
}

.row-icon {
  display: flex;
  flex-shrink: 0;
  color: var(--color-text-tertiary);
}

/* The only place tier is expressed visually. Rank is the product, so overdue
   earns a colour and everything else stays quiet — three coloured tiers would
   be a legend, not a priority. */
.tier-overdue .row-icon {
  color: var(--color-danger);
}

.tier-now .row-icon {
  color: var(--color-primary);
}

.row-main {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: baseline;
  gap: var(--space-3);
}

.row-title {
  font-size: var(--font-body-size);
  font-weight: 500;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-when {
  flex-shrink: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.tier-overdue .row-when {
  color: var(--color-danger);
}

.row-verb {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
  min-height: 32px;
  padding: 0 var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-button);
  background-color: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.row-verb:hover {
  background-color: var(--color-surface-hover);
  border-color: var(--color-border-strong);
  color: var(--color-text);
}

.row-verb:focus-visible {
  outline: var(--border-width-md) solid var(--color-focus-ring);
  outline-offset: 2px;
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

@media (max-width: 640px) {
  .row-main {
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }

  .row-title {
    white-space: normal;
  }
}
</style>
