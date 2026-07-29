<script lang="ts">
/** The three galleries the login stage can show; the dock navigates between them. */
export type GalleryName = 'login' | 'product' | 'sponsor'
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import GlassSurface from './GlassSurface.vue'

/**
 * Fluid glass bar — the persistent navigation slab of the login stage,
 * rebuilt after a close study of React Bits' FluidGlass "bar" mode (the
 * R3F original stays out of the project — see the Phase 8 evaluation; this
 * is its optical translation into the GlassSurface vocabulary):
 *
 *   - lockToBottom, followPointer: false → a wide slim bar parked at the
 *     stage's bottom edge, still, never chasing the pointer;
 *   - scale clamped to ~90% of the viewport → the bar spans the stage
 *     (LoginView's anchor owns the width) instead of hugging its content;
 *   - bar-mode material — transmission 1, roughness 0, thickness 10,
 *     ior 1.15, WHITE attenuation at 0.25 → clear water glass, not the
 *     smoked slab: density drops to a breath, the tint lightens, and the
 *     rim glow rises (white attenuation reads as edges gathering light);
 *   - chromaticAberration 0.1 with thickness 10 → a slightly stronger
 *     displacement + RGB split than the sign-in card;
 *   - nav items are floating Text, not buttons: bare labels resting on the
 *     glass, white over a soft dark halo (outlineBlur "20%", opacity 0.5),
 *     spacing tightening responsively (desktop → tablet → mobile).
 *
 * What the study deliberately keeps from the house rules: items stay real
 * <button>s (aria-current, focus restoration via focusItem), interaction
 * stays purely optical — hover lifts the label out of the dusk, press is
 * the damped half-pixel settle, the active label simply holds more light.
 * The `.dock-item` class remains the facet hook for useGlassSpotlight.
 */

const props = defineProps<{
  /** The gallery currently on stage; its label reads lit. */
  active: GalleryName
}>()

const emit = defineEmits<{ navigate: [target: GalleryName] }>()

const { t } = useI18n()

const ITEMS: ReadonlyArray<GalleryName> = ['login', 'product', 'sponsor']

const items = computed(() => ITEMS.map((key) => ({ key, label: t(`landing.dock.${key}`) })))

// Item elements, kept for focus restoration when a gallery closes: focus
// returns to the label that opened it, so keyboard travel never resets.
const itemEls = new Map<GalleryName, HTMLButtonElement>()

function registerItem(key: GalleryName, el: unknown) {
  if (el instanceof HTMLButtonElement) itemEls.set(key, el)
}

function focusItem(key: GalleryName) {
  itemEls.get(key)?.focus()
}

defineExpose({ focusItem })
</script>

<template>
  <GlassSurface
    class="glass-dock"
    material="chrome"
    width="100%"
    height="auto"
    surface-flow
    :border-radius="30"
    :border-width="0.09"
    :blur="10"
    :opacity="0.97"
    :displace="0.4"
    :background-opacity="0.05"
    :saturation="1.2"
    :distortion-scale="-88"
    :red-offset="0"
    :green-offset="5"
    :blue-offset="10"
  >
    <nav class="dock glass-material" :aria-label="t('landing.dock.label')">
      <button
        v-for="item in items"
        :key="item.key"
        :ref="(el) => registerItem(item.key, el)"
        type="button"
        class="dock-item"
        :class="{ 'is-active': item.key === props.active }"
        :aria-current="item.key === props.active ? 'page' : undefined"
        :title="item.key === props.active ? t('landing.dock.current') : undefined"
        @click="emit('navigate', item.key)"
      >
        {{ item.label }}
      </button>
    </nav>
  </GlassSurface>
</template>

<style scoped>
/*
 * Bar-mode material — clear water glass, one family with the smoked card
 * but read through FluidGlass bar defaults: transmission 1 / white
 * attenuation means barely any body density and bright, light-gathering
 * edges. The slab stays dark enough for the dusk labels; it must never
 * read frosted or white.
 *
 * The optical dials come from `material="chrome"` (the Clear variant, over a
 * stage that supplies its own dimming) — see styles/glass.css. Nothing
 * optical is declared here.
 */

/*
 * One row of floating labels, centered like the Text meshes on the bar.
 * The spacing steps mirror FluidGlass's DEVICE table (desktop 0.3 →
 * tablet 0.24 → mobile 0.2 world units) as a viewport-driven clamp.
 */
.dock {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  gap: clamp(var(--space-2), 4vw, var(--space-8));
  padding: var(--space-2) var(--space-4);
}

/*
 * Nav labels — bare text resting on the glass: no borders, no panes, no
 * chrome. The FluidGlass halo (outlineBlur "20%", black at 0.5) becomes a
 * soft dark text-shadow so white text clears whatever the bar refracts.
 * Interaction is light only: hover lifts the label out of the dusk, press
 * is the damped settle of mass, the active label holds a faint white
 * bloom instead of an underline.
 */
.dock-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  padding-inline: var(--space-3);
  border: none;
  border-radius: 22px;
  background-color: transparent;
  background-image: radial-gradient(
    circle 110px at var(--glass-light-x, 50%) var(--glass-light-y, 50%),
    color-mix(in srgb, #ffffff calc(var(--glass-light-strength, 0) * 10%), transparent),
    transparent 72%
  );
  color: var(--on-glass-text-dim);
  font-size: var(--text-sm);
  font-weight: 550;
  letter-spacing: 0.02em;
  /* Halo tokens, overridable from the stage: over the bright Product page
     the labels flip to dark ink and the dark halo would read as smudge. */
  text-shadow: 0 1px 10px var(--dock-halo, rgba(0, 0, 0, 0.5));
  cursor: pointer;
  transition:
    color 400ms var(--ease-out),
    text-shadow 400ms var(--ease-out);
}

.dock-item:hover {
  color: var(--on-glass-text);
}

/* Mass settling, never a spring. */
.dock-item:active {
  transform: translateY(0.5px);
}

/* The active label simply holds more light: full dusk white plus a faint
   bloom breathing through the glass around it. */
.dock-item.is-active {
  color: var(--on-glass-text);
  text-shadow:
    0 1px 10px var(--dock-halo, rgba(0, 0, 0, 0.5)),
    0 0 18px var(--dock-halo-active, rgba(255, 255, 255, 0.38));
}

.dock-item:focus-visible {
  outline: var(--border-width-md) solid var(--color-focus-ring);
  outline-offset: 2px;
}

/* Mobile step of the DEVICE table: labels stay (the bar is text-first),
   only the type and breathing room tighten. */
@media (max-width: 640px) {
  .dock-item {
    height: 40px;
    padding-inline: var(--space-2);
    font-size: var(--text-xs);
  }
}
</style>
