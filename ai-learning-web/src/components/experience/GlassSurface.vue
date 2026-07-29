<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import type { MaterialPreset } from './materials'

/**
 * Liquid-glass surface ported from React Bits' <GlassSurface />.
 *
 * A per-instance SVG displacement filter (fed by a generated data-URI map)
 * refracts whatever sits behind the element via `backdrop-filter: url(#…)`.
 * Browsers that can't apply SVG filters as backdrop-filters (Safari, Firefox)
 * fall back to a plain frosted-glass look.
 *
 * The surface is a *living* optical object: on top of the refraction it
 * carries three CSS-driven lighting layers (inner glow, edge glow, and a
 * light-tracking sheen) steered entirely by custom properties, so a stage
 * composable (useGlassSpotlight) — or any future page — can bring it near a
 * light source without touching this component:
 *
 *   --glass-inner-glow      base opacity of the permanent inner glow
 *   --glass-edge-glow       base opacity of the permanent edge highlight
 *   --glass-proximity       0..1 nearby-light factor: lifts both glows and
 *                           thins the frost (background clarity)
 *   --glass-light-x/y       card-local light position (px) for the sheen
 *   --glass-light-radius    light radius (px)
 *   --glass-light-strength  0..1 presence of the travelling light
 *
 * Phase 9 adds the *thick optical slab* vocabulary — three more layers, all
 * fully gated by custom properties that default to 0/transparent, so every
 * surface that doesn't opt in renders exactly as before:
 *
 *   --glass-depth           0..1 presence of the depth layer (front rim,
 *                           inner back rim, back-face reflection, internal
 *                           scattering) — the "thick glass" cues
 *   --glass-density         0..1 smoked neutral-density tint strength; the
 *                           optical replacement for frost-driven legibility
 *   --glass-tint            color of the ND body (default: near-black smoke)
 *   --glass-fresnel         0..1 presence of the directional Fresnel ring
 *   --glass-light-angle     angle (deg, 0 = light above) aiming the Fresnel
 *                           arc; without it the ring rests as a top highlight
 *   --glass-flow-opacity    presence of the surfaceFlow layer (default .6)
 *
 * The optional `surfaceFlow` prop renders a slow travelling highlight and
 * faint internal caustics (20–40s loops, transform/opacity only).
 *
 * All lighting reacts through gradient/opacity only — the SVG displacement
 * chain is never regenerated per frame. With no variables set, the defaults
 * yield a calm, permanently lit surface.
 *
 * `class`/`style` from the caller fall through to the root element.
 */
type Channel = 'R' | 'G' | 'B'

const props = withDefaults(
  defineProps<{
    /**
     * Which named slab this is. REQUIRED — there is no anonymous glass: the
     * preset carries density, tint, depth, fresnel and the two glow strengths
     * from `glass.css`, so surfaces never hand-type optical dials. See
     * `materials.ts` and `docs/liquid-material-system.md`.
     */
    material: MaterialPreset
    /** Width in px (number) or any CSS length (string). */
    width?: number | string
    /** Height in px (number) or any CSS length (string). */
    height?: number | string
    borderRadius?: number
    /** Border width factor for the displacement map edge. */
    borderWidth?: number
    /** Brightness percentage of the displacement map core. */
    brightness?: number
    /** Opacity of the displacement map core. */
    opacity?: number
    /** Blur of the displacement map core, px. */
    blur?: number
    /** Output blur (feGaussianBlur stdDeviation). */
    displace?: number
    /** Background frost opacity (0-1). */
    backgroundOpacity?: number
    /** Backdrop saturation factor. */
    saturation?: number
    /** Main displacement scale. */
    distortionScale?: number
    redOffset?: number
    greenOffset?: number
    blueOffset?: number
    xChannel?: Channel
    yChannel?: Channel
    /** Blend mode between the two gradient plates of the map. */
    mixBlendMode?: string
    /**
     * Living-surface mode: a slow travelling highlight + faint internal
     * caustics (20–40s, transform/opacity only — the displacement filter is
     * never touched). Off by default; reduced motion freezes it globally.
     */
    surfaceFlow?: boolean
  }>(),
  {
    width: 200,
    height: 80,
    borderRadius: 20,
    borderWidth: 0.07,
    brightness: 50,
    opacity: 0.93,
    blur: 11,
    displace: 0,
    backgroundOpacity: 0,
    saturation: 1,
    distortionScale: -180,
    redOffset: 0,
    greenOffset: 10,
    blueOffset: 20,
    xChannel: 'R',
    yChannel: 'G',
    mixBlendMode: 'difference',
    surfaceFlow: false,
  },
)

const uniqueId = useId().replace(/:/g, '-')
const filterId = `glass-filter-${uniqueId}`
const redGradId = `red-grad-${uniqueId}`
const blueGradId = `blue-grad-${uniqueId}`

const containerRef = ref<HTMLElement | null>(null)
const svgSupported = ref(false)
// Rendered size of the surface; the displacement map is regenerated from it.
const measured = ref({ width: 400, height: 200 })

const displacementMap = computed(() => {
  const { width: actualWidth, height: actualHeight } = measured.value
  const edgeSize = Math.min(actualWidth, actualHeight) * (props.borderWidth * 0.5)

  const svgContent = `
    <svg viewBox="0 0 ${actualWidth} ${actualHeight}" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="${redGradId}" x1="100%" y1="0%" x2="0%" y2="0%">
          <stop offset="0%" stop-color="#0000"/>
          <stop offset="100%" stop-color="red"/>
        </linearGradient>
        <linearGradient id="${blueGradId}" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stop-color="#0000"/>
          <stop offset="100%" stop-color="blue"/>
        </linearGradient>
      </defs>
      <rect x="0" y="0" width="${actualWidth}" height="${actualHeight}" fill="black"></rect>
      <rect x="0" y="0" width="${actualWidth}" height="${actualHeight}" rx="${props.borderRadius}" fill="url(#${redGradId})" />
      <rect x="0" y="0" width="${actualWidth}" height="${actualHeight}" rx="${props.borderRadius}" fill="url(#${blueGradId})" style="mix-blend-mode: ${props.mixBlendMode}" />
      <rect x="${edgeSize}" y="${edgeSize}" width="${actualWidth - edgeSize * 2}" height="${actualHeight - edgeSize * 2}" rx="${props.borderRadius}" fill="hsl(0 0% ${props.brightness}% / ${props.opacity})" style="filter:blur(${props.blur}px)" />
    </svg>
  `

  return `data:image/svg+xml,${encodeURIComponent(svgContent)}`
})

const containerStyle = computed(() => ({
  width: typeof props.width === 'number' ? `${props.width}px` : props.width,
  height: typeof props.height === 'number' ? `${props.height}px` : props.height,
  borderRadius: `${props.borderRadius}px`,
  '--glass-frost': String(props.backgroundOpacity),
  '--glass-saturation': String(props.saturation),
  '--filter-id': `url(#${filterId})`,
}))

// SVG filters inside backdrop-filter only work in Chromium; WebKit and
// Firefox parse the value but render nothing, so they get the fallback skin.
function supportsSVGFilters(): boolean {
  const isWebkit = /Safari/.test(navigator.userAgent) && !/Chrome/.test(navigator.userAgent)
  const isFirefox = /Firefox/.test(navigator.userAgent)
  if (isWebkit || isFirefox) return false

  const div = document.createElement('div')
  div.style.backdropFilter = `url(#${filterId})`
  return div.style.backdropFilter !== ''
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  svgSupported.value = supportsSVGFilters()

  if (containerRef.value) {
    resizeObserver = new ResizeObserver((entries) => {
      const rect = entries[0]?.contentRect
      if (rect && rect.width > 0 && rect.height > 0) {
        measured.value = { width: rect.width, height: rect.height }
      }
    })
    resizeObserver.observe(containerRef.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})

// Stage composables (useGlassSpotlight) need the root element to measure
// proximity and write card-local lighting variables.
defineExpose({ element: containerRef })
</script>

<template>
  <div
    ref="containerRef"
    class="glass-surface"
    :class="svgSupported ? 'glass-surface--svg' : 'glass-surface--fallback'"
    :data-material="material"
    :style="containerStyle"
  >
    <svg class="glass-surface__filter" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <defs>
        <filter
          :id="filterId"
          color-interpolation-filters="sRGB"
          x="0%"
          y="0%"
          width="100%"
          height="100%"
        >
          <feImage
            :href="displacementMap"
            x="0"
            y="0"
            width="100%"
            height="100%"
            preserveAspectRatio="none"
            result="map"
          />

          <feDisplacementMap
            in="SourceGraphic"
            in2="map"
            :scale="distortionScale + redOffset"
            :xChannelSelector="xChannel"
            :yChannelSelector="yChannel"
            result="dispRed"
          />
          <feColorMatrix
            in="dispRed"
            type="matrix"
            values="1 0 0 0 0
                    0 0 0 0 0
                    0 0 0 0 0
                    0 0 0 1 0"
            result="red"
          />

          <feDisplacementMap
            in="SourceGraphic"
            in2="map"
            :scale="distortionScale + greenOffset"
            :xChannelSelector="xChannel"
            :yChannelSelector="yChannel"
            result="dispGreen"
          />
          <feColorMatrix
            in="dispGreen"
            type="matrix"
            values="0 0 0 0 0
                    0 1 0 0 0
                    0 0 0 0 0
                    0 0 0 1 0"
            result="green"
          />

          <feDisplacementMap
            in="SourceGraphic"
            in2="map"
            :scale="distortionScale + blueOffset"
            :xChannelSelector="xChannel"
            :yChannelSelector="yChannel"
            result="dispBlue"
          />
          <feColorMatrix
            in="dispBlue"
            type="matrix"
            values="0 0 0 0 0
                    0 0 0 0 0
                    0 0 1 0 0
                    0 0 0 1 0"
            result="blue"
          />

          <feBlend in="red" in2="green" mode="screen" result="rg" />
          <feBlend in="rg" in2="blue" mode="screen" result="output" />
          <feGaussianBlur in="output" :stdDeviation="displace" />
        </filter>
      </defs>
    </svg>

    <div class="glass-surface__light" aria-hidden="true"></div>
    <div class="glass-surface__depth" aria-hidden="true"></div>
    <div class="glass-surface__fresnel" aria-hidden="true"></div>
    <div v-if="surfaceFlow" class="glass-surface__flow" aria-hidden="true"></div>

    <div class="glass-surface__content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.glass-surface {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: opacity 0.26s ease-out;
}

/*
 * Optical lighting — three layers driven purely by CSS custom properties
 * (see the script docblock for the contract). No transitions here: when a
 * spotlight composable steers the variables it interpolates per frame
 * already, and a transition would fight it. Discrete consumers can wrap the
 * variables themselves.
 *
 * Inner glow — the sheet permanently gathers light: a soft top light and a
 * faint answering bloom from below. Proximity breathes more light into it.
 */
.glass-surface::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  pointer-events: none;
  background:
    radial-gradient(
      140% 90% at 50% -14%,
      light-dark(rgba(255, 255, 255, 0.17), rgba(255, 255, 255, 0.11)),
      transparent 58%
    ),
    radial-gradient(
      130% 110% at 50% 120%,
      light-dark(rgba(255, 255, 255, 0.09), rgba(168, 158, 255, 0.07)),
      transparent 60%
    );
  opacity: calc(var(--glass-inner-glow, 0.55) + var(--glass-proximity, 0) * 0.3);
}

/* Edge glow — the physical rim of the sheet catching light. Nearby light
   makes the bevel read brighter, like tilting real glass toward a lamp. */
.glass-surface::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  pointer-events: none;
  box-shadow:
    inset 0 0 0 1px light-dark(rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0.2)),
    inset 0 1px 0 light-dark(rgba(255, 255, 255, 0.5), rgba(216, 210, 255, 0.32)),
    inset 0 0 22px -8px light-dark(rgba(255, 255, 255, 0.5), rgba(190, 182, 255, 0.34));
  opacity: calc(var(--glass-edge-glow, 0.5) + var(--glass-proximity, 0) * 0.4);
}

/* Light-tracking sheen — a whisper of the travelling light glancing across
   the glass face. Dormant (opacity 0) until a composable feeds strength. */
.glass-surface__light {
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  pointer-events: none;
  background: radial-gradient(
    circle calc(var(--glass-light-radius, 360px) * 1.1) at var(--glass-light-x, 50%)
      var(--glass-light-y, 50%),
    light-dark(rgba(255, 255, 255, 0.2), rgba(255, 255, 255, 0.1)) 0%,
    light-dark(rgba(255, 255, 255, 0.07), rgba(255, 255, 255, 0.04)) 38%,
    transparent 72%
  );
  opacity: calc(var(--glass-light-strength, 0) * (0.2 + var(--glass-proximity, 0) * 0.8));
  will-change: opacity;
}

/*
 * Depth layer — the thick-slab cues (Phase 9). Every alpha is produced by
 * color-mix() against the gating variables, so with the defaults
 * (--glass-depth: 0, --glass-density: 0) this layer paints nothing at all.
 *
 * The element itself carries the smoked neutral-density body (legibility by
 * optical density, not white frost — reads thicker toward the base), the
 * back-face reflection (light re-emerging from the slab's rear surface) and
 * the sharp front rim.
 */
.glass-surface__depth {
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  pointer-events: none;
  background:
    radial-gradient(
      120% 60% at 50% 110%,
      color-mix(
        in srgb,
        light-dark(#ffffff, #beb6ff) calc(var(--glass-depth, 0) * 11%),
        transparent
      ),
      transparent 64%
    ),
    linear-gradient(
      to bottom,
      color-mix(
        in srgb,
        var(--glass-tint, rgb(10 12 18)) calc(var(--glass-density, 0) * 74%),
        transparent
      ),
      color-mix(
        in srgb,
        var(--glass-tint, rgb(10 12 18)) calc(var(--glass-density, 0) * 100%),
        transparent
      )
    );
  box-shadow: inset 0 0 0 1px
    color-mix(in srgb, light-dark(#ffffff, #e7e3ff) calc(var(--glass-depth, 0) * 34%), transparent);
}

/* Inner (back) rim — a second contour a few px inside the front edge, biased
   1px downward: the far edge of a thick slab seen through its own body. This
   double edge is the strongest single "heavy glass" cue. */
.glass-surface__depth::before {
  content: '';
  position: absolute;
  inset: 3px;
  border-radius: inherit;
  transform: translateY(1px);
  box-shadow:
    inset 0 0 0 1px
      color-mix(in srgb, light-dark(#ffffff, #d8d2ff) calc(var(--glass-depth, 0) * 13%), transparent),
    inset 0 -1px 0
      color-mix(in srgb, light-dark(#ffffff, #d8d2ff) calc(var(--glass-depth, 0) * 8%), transparent);
}

/* Internal scattering — light entering the slab diffuses inside the body.
   Dormant until the travelling light is both present and near. */
.glass-surface__depth::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: radial-gradient(
    circle calc(var(--glass-light-radius, 360px) * 1.5) at var(--glass-light-x, 50%)
      var(--glass-light-y, 50%),
    light-dark(rgba(255, 255, 255, 0.16), rgba(214, 206, 255, 0.12)),
    transparent 78%
  );
  opacity: calc(var(--glass-depth, 0) * var(--glass-light-strength, 0) * var(--glass-proximity, 0));
}

/*
 * Directional Fresnel ring — a conic arc masked to the slab's rim; only the
 * edge facing the light brightens, like a lens catching a lamp. The angle is
 * driven by --glass-light-angle (0deg = light above); undriven it rests as a
 * gentle top highlight. Gated fully off by --glass-fresnel: 0 (default).
 */
.glass-surface__fresnel {
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  pointer-events: none;
  padding: 1.5px;
  background: conic-gradient(
    from calc(var(--glass-light-angle, 0deg) - 100deg),
    transparent 0deg,
    light-dark(rgba(255, 255, 255, 0.55), rgba(234, 230, 255, 0.45)) 100deg,
    transparent 200deg
  );
  mask:
    linear-gradient(#fff 0 0) content-box,
    linear-gradient(#fff 0 0);
  mask-composite: exclude;
  opacity: calc(var(--glass-fresnel, 0) * (0.55 + var(--glass-proximity, 0) * 0.45));
}

/* Without mask-composite the conic would flood the whole face — hide the
   ring entirely rather than degrade badly. */
@supports not (mask-composite: exclude) {
  .glass-surface__fresnel {
    display: none;
  }
}

/*
 * Surface flow (surfaceFlow prop) — the slab is alive: a slow travelling
 * highlight band and two faint caustic pools, 20–40s transform/opacity
 * loops on the compositor. Never touches the displacement filter. The
 * global reduced-motion override freezes both keyframes.
 */
.glass-surface__flow {
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  pointer-events: none;
  overflow: hidden;
  opacity: var(--glass-flow-opacity, 0.6);
  transition: opacity 900ms var(--ease-out);
}

.glass-surface__flow::before {
  content: '';
  position: absolute;
  top: -60%;
  bottom: -60%;
  left: -40%;
  width: 55%;
  background: linear-gradient(
    to right,
    transparent,
    light-dark(rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.07)) 50%,
    transparent
  );
  animation: app-glass-flow 34s var(--ease-in-out) infinite alternate;
}

.glass-surface__flow::after {
  content: '';
  position: absolute;
  inset: -30%;
  background:
    radial-gradient(
      38% 30% at 30% 34%,
      light-dark(rgba(255, 255, 255, 0.08), rgba(196, 188, 255, 0.07)),
      transparent 70%
    ),
    radial-gradient(
      30% 26% at 72% 68%,
      light-dark(rgba(255, 214, 236, 0.06), rgba(255, 214, 236, 0.05)),
      transparent 70%
    );
  animation: app-glass-caustics 26s var(--ease-in-out) infinite alternate;
}

.glass-surface__filter {
  width: 100%;
  height: 100%;
  pointer-events: none;
  position: absolute;
  inset: 0;
  opacity: 0;
  z-index: -1;
}

.glass-surface__content {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem;
  border-radius: inherit;
  position: relative;
  z-index: 1;
}

/* Nearby light thins the frost slightly (background clarity rises); the
   factor is deliberately small — felt, not seen — and capped where on-glass
   text still clears the brightest petals behind it. */
.glass-surface--svg {
  background: light-dark(
    hsl(0 0% 100% / calc(var(--glass-frost, 0) * (1 - var(--glass-proximity, 0) * 0.1))),
    hsl(0 0% 0% / calc(var(--glass-frost, 0) * (1 - var(--glass-proximity, 0) * 0.1)))
  );
  backdrop-filter: var(--filter-id) saturate(var(--glass-saturation, 1));
  box-shadow:
    0 0 2px 1px light-dark(color-mix(in oklch, black, transparent 85%), color-mix(in oklch, white, transparent 65%))
      inset,
    0 0 10px 4px light-dark(color-mix(in oklch, black, transparent 90%), color-mix(in oklch, white, transparent 85%))
      inset,
    0px 4px 16px rgba(17, 17, 26, 0.05),
    0px 8px 24px rgba(17, 17, 26, 0.05),
    0px 16px 56px rgba(17, 17, 26, 0.05),
    0px 4px 16px rgba(17, 17, 26, 0.05) inset,
    0px 8px 24px rgba(17, 17, 26, 0.05) inset,
    0px 16px 56px rgba(17, 17, 26, 0.05) inset;
}

.glass-surface--fallback {
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(12px) saturate(1.8) brightness(1.1);
  -webkit-backdrop-filter: blur(12px) saturate(1.8) brightness(1.1);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow:
    0 8px 32px 0 rgba(31, 38, 135, 0.2),
    0 2px 16px 0 rgba(31, 38, 135, 0.1),
    inset 0 1px 0 0 rgba(255, 255, 255, 0.4),
    inset 0 -1px 0 0 rgba(255, 255, 255, 0.2);
}

/* The app toggles theme with `html.dark` (see tokens.css), so key the dark
   fallback off that instead of prefers-color-scheme. */
html.dark .glass-surface--fallback {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(12px) saturate(1.8) brightness(1.2);
  -webkit-backdrop-filter: blur(12px) saturate(1.8) brightness(1.2);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow:
    inset 0 1px 0 0 rgba(255, 255, 255, 0.2),
    inset 0 -1px 0 0 rgba(255, 255, 255, 0.1);
}

@supports not (backdrop-filter: blur(10px)) {
  .glass-surface--fallback {
    background: rgba(255, 255, 255, 0.4);
    box-shadow:
      inset 0 1px 0 0 rgba(255, 255, 255, 0.5),
      inset 0 -1px 0 0 rgba(255, 255, 255, 0.3);
  }

  .glass-surface--fallback::before {
    content: '';
    position: absolute;
    inset: 0;
    background: rgba(255, 255, 255, 0.15);
    border-radius: inherit;
    z-index: -1;
  }

  html.dark .glass-surface--fallback {
    background: rgba(0, 0, 0, 0.4);
  }

  html.dark .glass-surface--fallback::before {
    background: rgba(255, 255, 255, 0.05);
  }
}

.glass-surface:focus-visible {
  outline: 2px solid light-dark(#007aff, #0a84ff);
  outline-offset: 2px;
}
</style>
