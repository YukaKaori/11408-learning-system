<script setup lang="ts">
/**
 * Day complete — the terminal state, and the only one that earns a moment.
 *
 * This component exists to make the distinction *structural*. `clear` and
 * `empty` are also empty plans, and both stay plain sections inside
 * `TodayView`; only `complete` gets its own file, the success token and the
 * settle. Collapsing them would congratulate a user who did nothing, which is
 * the fabrication this phase exists to avoid.
 *
 * **It decides nothing.** The server derives `complete` — plan empty *and* real
 * work recorded today in the caller's own timezone — and this component only
 * renders that verdict with the counts behind it. There is no client-side
 * "looks finished to me" path, deliberately: two clients must never disagree
 * about whether a day ended.
 *
 * **One settle, once.** A single one-shot animation on the section itself: it
 * arrives slightly high and comes to rest. No confetti, no badge, no sound, no
 * second animation on the mark — the material's rule is that weight settles and
 * nothing springs, so the curve is `--ease-out` (monotonic) and never
 * `--ease-spring` (overshoots). It plays when the state flips and then rests
 * for as long as the day stays complete.
 *
 * **Material:** solid. Today is the work, and the work is never glass
 * (docs/liquid-material-system.md §1, docs/phase17-material-audit.md §2.3).
 * The moment is motion on existing tokens, not a material.
 */
import { useI18n } from 'vue-i18n'
import { AppIcon } from '@/components'

defineProps<{
  reviews: number
  questions: number
  tasks: number
  minutes: number
}>()

const { t } = useI18n()
</script>

<template>
  <section class="day-complete">
    <AppIcon name="check-circle" class="mark" aria-hidden="true" />
    <h2 class="title">{{ t('today.complete.title') }}</h2>
    <!--
      The day reported back in its own numbers. Every one of them is a real
      count the server bucketed into today; none is rounded up, and none
      appears when the user did nothing, because then this state never renders.
    -->
    <p class="text">{{ t('today.complete.text', { reviews, questions, tasks, minutes }) }}</p>
  </section>
</template>

<style scoped>
/*
 * Same geometry as the `clear` and `empty` sections in TodayView — they are one
 * family of terminal states and must not look like three different products.
 * The success token and the settle are the only things this state adds.
 */
.day-complete {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-16) var(--space-6);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  text-align: center;
  animation: day-complete-settle var(--duration-slow) var(--ease-out) both;
}

/* The one place a terminal state carries colour: the user worked for it. */
.mark {
  color: var(--color-success);
}

.title {
  margin: 0;
  font-family: var(--font-title-family);
  font-size: var(--font-title-size);
  font-weight: var(--font-title-weight);
}

.text {
  margin: 0;
  max-width: 42ch;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

/*
 * The settle: it arrives a little high and comes down to rest. Opacity and
 * transform only — compositor channels, nothing that repaints — and a curve
 * that approaches its target without overshoot. 6px is the whole distance;
 * anything a user could measure would be a celebration rather than a settle.
 */
@keyframes day-complete-settle {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/*
 * Zero by construction rather than by the global override in motion.css: this
 * state is the one place in Today that animates, so it states its own answer
 * instead of relying on a blanket `!important` elsewhere. With the animation
 * off, the section renders in its final position — the content is never gated
 * on the moment.
 */
@media (prefers-reduced-motion: reduce) {
  .day-complete {
    animation: none;
  }
}

/* Matches the `clear` and `empty` sections in TodayView at the same tier. */
@media (max-width: 768px) {
  .day-complete {
    padding: var(--space-10) var(--space-4);
  }
}
</style>
