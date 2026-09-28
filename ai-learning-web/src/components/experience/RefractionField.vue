<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import {
  useRefractionField,
  wakeQualifies,
  WAKE_QUERIES,
  type Point,
} from '@/composables/useRefractionField'

/**
 * The refraction wake — environment layer E4 of a stage (`environment.md` §1).
 *
 * Not glass and not a budget instance: a 2D canvas laid directly over the
 * wallpaper (under the atmosphere, so the dusk falls on it exactly as on the
 * wallpaper) that re-draws the wallpaper bent — a small liquid lens where the
 * pointer is, relaxing residue where it has just been. Every pixel it draws is
 * a wallpaper pixel; it never lights, dims or reveals. It owns no pointer
 * listener: it reads the stage's one listener's raw position from
 * `useGlassSpotlight` (`cursor`, not the eased `smoothedCursor` — the lens must
 * sit on the hand, not chase it).
 *
 * Gated to nothing: the canvas is only mounted for a fine hovering pointer with
 * motion allowed, decided after mount and re-decided when either preference
 * changes. Touch, keyboard-only and reduced-motion users get the stage at rest,
 * which is complete without it.
 */
const props = defineProps<{
  /** The stage's spotlight (or anything carrying its raw pointer). */
  light: { cursor: Readonly<Ref<Point>> }
  stage: HTMLElement | null
  /** The wallpaper's image layers, each filling an untransformed frame. */
  layers: ReadonlyArray<HTMLImageElement | null>
  /** Glass slabs a lens fades out under, so their labels keep a calm backdrop. */
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

useRefractionField(canvasRef, {
  cursor: props.light.cursor,
  stage: () => props.stage,
  layers: () => props.layers,
  shelters: () => props.shelters,
  active: () => props.active,
})
</script>

<template>
  <canvas v-if="qualifies" ref="canvasRef" class="refraction-field" aria-hidden="true"></canvas>
</template>

<style scoped>
/*
 * Sized to the stage by CSS; its backing store is the plate's (stage × the
 * working device-pixel ratio). Fully opaque by construction: it re-draws the
 * wallpaper pixel for pixel, so any opacity here would show the unbent
 * wallpaper through the bent one — a double image. Nothing here animates.
 */
.refraction-field {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
</style>
