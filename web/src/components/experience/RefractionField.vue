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
 * wallpaper (under any atmosphere a stage might have, so a dusk would fall on it
 * exactly as on the wallpaper — the daylight room has none) that re-draws the
 * wallpaper bent — a small liquid lens where the
 * pointer is, relaxing residue where it has just been. Every pixel it draws is
 * a wallpaper pixel; it never lights, dims or reveals. It owns no pointer
 * listener: it reads the stage's one listener's raw position from
 * `useGlassSpotlight` (`cursor`, not the eased `smoothedCursor` — the lens must
 * sit on the hand, not chase it).
 *
 * Gated to nothing: the canvas is only mounted for a fine hovering pointer with
 * motion allowed, the system's own colours not forced, and a stage that is not
 * phone-shaped (there the picture is a masked crown the plate cannot re-draw,
 * 2026-10-23), decided after mount and re-decided when any of those queries
 * changes — a window resized across the phone shape included. Touch,
 * keyboard-only, reduced-motion and high-contrast users get the stage at rest,
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

let queries: MediaQueryList[] = []

function evaluate() {
  qualifies.value = wakeQualifies((query) => window.matchMedia(query).matches)
}

onMounted(() => {
  queries = Object.values(WAKE_QUERIES).map((query) => window.matchMedia(query))
  for (const query of queries) query.addEventListener('change', evaluate)
  evaluate()
})

onBeforeUnmount(() => {
  for (const query of queries) query.removeEventListener('change', evaluate)
  queries = []
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
