<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { AppButton, AppSkeleton } from '@/components'

/**
 * One section of the Knowledge Workspace's context rail (Phase 16 Step 6).
 *
 * Every rail section answers the same question — "what does this note connect
 * to?" — so every one of them renders the same shape: an uppercase micro-label,
 * then the design system's view-state sequence (skeleton → error + retry →
 * empty line → content) at section scale rather than page scale. Outline,
 * Backlinks and the two reserved future slots all compose this, which is what
 * keeps the rail reading as one column instead of four stacked widgets.
 *
 * The section is emphatically **solid**: the rail is persistent chrome, not an
 * elevated or transient layer, so the Optical Glass boundary keeps it out of
 * the material.
 */
withDefaults(
  defineProps<{
    title: string
    loading?: boolean
    /** Already-translated failure message; renders with a retry action. */
    error?: string | null
    /** Already-translated "nothing here yet" line. */
    empty?: string
    /** Whether to show `empty` instead of the default slot. */
    isEmpty?: boolean
    /** Skeleton lines to reserve while loading — match the usual content height. */
    lines?: number
  }>(),
  { loading: false, error: null, empty: '', isEmpty: false, lines: 2 },
)

const emit = defineEmits<{ retry: [] }>()

const { t } = useI18n()
</script>

<template>
  <section class="rail-section">
    <h3 class="rail-title">{{ title }}</h3>
    <AppSkeleton v-if="loading" :lines="lines" />
    <div v-else-if="error" class="rail-error" role="alert">
      <p class="rail-note">{{ error }}</p>
      <AppButton size="sm" variant="ghost" @click="emit('retry')">{{ t('common.retry') }}</AppButton>
    </div>
    <p v-else-if="isEmpty" class="rail-note">{{ empty }}</p>
    <slot v-else />
  </section>
</template>

<style scoped>
.rail-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

/* `--color-text-secondary`, not tertiary: at this size the tertiary ramp lands
   at ~2.5:1 on the page background, and a section label the reader has to hunt
   for is not a quiet label, it is an unreadable one. Uppercase, tracking and
   size carry the hierarchy instead of low contrast. */
.rail-title {
  margin: 0;
  font-family: var(--font-label-family);
  font-size: var(--font-label-size);
  font-weight: var(--font-label-weight);
  letter-spacing: var(--font-label-tracking);
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.rail-note {
  margin: 0;
  font-size: var(--text-xs);
  line-height: var(--leading-normal);
  color: var(--color-text-secondary);
}

.rail-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}
</style>
