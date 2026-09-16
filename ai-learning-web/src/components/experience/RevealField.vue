<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import { useRevealField, wakeQualifies, WAKE_QUERIES, type Point } from '@/composables/useRevealField'

/**
 * The reveal wake — environment layer E4 of a stage (`environment.md` §1).
 *
 * Not glass and not a budget instance: a 2D canvas that paints where the
 * pointer has just travelled, revealing the awake plate (the wallpaper without
 * its atmosphere, the secondary layer at full light). It owns no pointer
 * listener — it reads the stage's one eased cursor from `useGlassSpotlight`.
 *
 * Gated to nothing: the canvas is only mounted for a fine hovering pointer with
 * motion allowed, decided after mount and re-decided when either preference
 * changes. Touch, keyboard-only and reduced-motion users get the stage at rest,
 * which is complete without it.
 */
const props = defineProps<{
  /** The stage's spotlight (or anything carrying its eased cursor). */
  light: { smoothedCursor: Readonly<Ref<Point>> }
  stage: HTMLElement | null
  wallpaper: HTMLImageElement | null
  secondary: HTMLImageElement | null
  /** Glass slabs the wake stays mostly out of, so their labels keep their dimming. */
  shelters: ReadonlyArray<HTMLElement | null>
  /** False while another gallery covers the stage. */
  active: boolean
}>()

const qualifies = ref(false)
const canvasRef = ref<HTMLCanvasElement | null>(null)

let pointerQuery: MediaQueryList | null = null
let motionQuery: MediaQueryList | null = null

function evaluate() {
  qualifies.value = wakeQualifies((query) => window.matchMedia(query).matches)
}

onMounted(() => {
  pointerQuery = window.matchMedia(WAKE_QUERIES.pointer)
  motionQuery = window.matchMedia(WAKE_QUERIES.reducedMotion)
  pointerQuery.addEventListener('change', evaluate)
  motionQuery.addEventListener('change', evaluate)
  evaluate()
})

onBeforeUnmount(() => {
  pointerQuery?.removeEventListener('change', evaluate)
  motionQuery?.removeEventListener('change', evaluate)
})

useRevealField(canvasRef, {
  cursor: props.light.smoothedCursor,
  stage: () => props.stage,
  wallpaper: () => props.wallpaper,
  secondary: () => props.secondary,
  shelters: () => props.shelters,
  active: () => props.active,
})
</script>

<template>
  <canvas v-if="qualifies" ref="canvasRef" class="reveal-field" aria-hidden="true"></canvas>
</template>

<style scoped>
/*
 * Sized to the stage by CSS; its backing store is sized by the composable
 * (stage × min(devicePixelRatio, 1.5)). Nothing here animates: the wake's own
 * frames repaint the bitmap, and the layer's presence is a stage token.
 */
.reveal-field {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  opacity: var(--environment-wake-strength, 1);
}
</style>
