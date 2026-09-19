<script lang="ts">
import type { IconName } from '../icons/registry'

/**
 * One destination on the bar (Phase B5 — items are data, not a hardcoded list).
 *
 * The recipe has two hosts and one implementation: the landing stage passes
 * three bare-label galleries, the authenticated shell passes five routes plus
 * More. Everything optical, geometric and motional is shared; only this list
 * and the `layout` presentation differ.
 */
export interface DockItem {
  /** Stable identity: what `active` is matched against and what `navigate` emits. */
  key: string
  /** Already-localized label — the host owns the wording. */
  label: string
  /** Optional glyph above the label (the `stacked` layout). */
  icon?: IconName
  /** Set when the item summons a panel instead of moving the view. */
  haspopup?: 'dialog'
  /** Reflected as `aria-expanded`; only meaningful with `haspopup`. */
  expanded?: boolean
}
</script>

<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from '../AppIcon.vue'
import GlassSurface from './GlassSurface.vue'
import { useNavIndicator } from '@/composables/useNavIndicator'

/**
 * Fluid glass bar — the persistent navigation slab of the product, rebuilt
 * after a close study of React Bits' FluidGlass "bar" mode (the R3F original
 * stays out of the project — see the Phase 8 evaluation; this is its optical
 * translation into the GlassSurface vocabulary):
 *
 *   - lockToBottom, followPointer: false → a wide slim bar parked at the
 *     stage's bottom edge, still, never chasing the pointer;
 *   - scale clamped to ~90% of the viewport → the bar spans its host (the
 *     host's anchor owns the width) instead of hugging its content;
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
 *
 * Phase B5 — TWO HOSTS, ONE RECIPE. The bar became the authenticated shell's
 * chrome on compact viewports as well as the landing's (`navigation.md` §4;
 * two logical surfaces of one recipe — `components.md` §1). Nothing optical
 * moved: the `chrome` preset, every map dial, the radius family, the indicator
 * light and the press/focus illumination are shared byte-for-byte. What the
 * app dock adds is a second *presentation* of the same item — `layout`:
 *
 *   labels    bare text, generously spaced (the landing's three galleries)
 *   stacked   an 18px glyph above a small label, items sharing the row
 *             evenly, labels ellipsizing — six destinations on a phone
 *             without the bar ever reflowing (`navigation.md` §4: adapt
 *             spacing and truncation, keep the bar's shape stable)
 *
 * Navigation is the host's job: the bar emits `navigate` with a key and never
 * touches the router, so one implementation serves galleries and routes alike.
 */

const props = withDefaults(
  defineProps<{
    /** The destinations, in bar order. */
    items: ReadonlyArray<DockItem>
    /** Key of the item that reads lit, or null while nothing here is current. */
    active: string | null
    /** Accessible name of the bar itself. */
    label: string
    /** Tooltip on the marked item — "you are here" in words. */
    currentTitle?: string
    /** Item presentation; see the docblock. */
    layout?: 'labels' | 'stacked'
  }>(),
  {
    currentTitle: undefined,
    layout: 'labels',
  },
)

const emit = defineEmits<{ navigate: [target: string] }>()

// Item elements, kept for focus restoration when a summoned panel closes:
// focus returns to the label that opened it, so keyboard travel never resets.
const itemEls = new Map<string, HTMLButtonElement>()

function registerItem(key: string, el: unknown) {
  if (el instanceof HTMLButtonElement) itemEls.set(key, el)
  else itemEls.delete(key)
}

function focusItem(key: string) {
  itemEls.get(key)?.focus()
}

// The indicator (Phase B2) — a light the bar paints under the current item
// from measured geometry: correct on first paint, travels on navigation,
// re-placed directly when the layout (locale, viewport) changes. It is a state
// of this bar, not a second material.
//
// The layout key is the item set as text rather than the array itself: a host
// that rebuilds the list to flip one item's `expanded` must not read as a
// layout change, while a locale switch or a changed destination must.
const navRef = ref<HTMLElement | null>(null)
const layoutKey = computed(() => props.items.map((item) => `${item.key}:${item.label}`).join('|'))

useNavIndicator(navRef, {
  target: () => (props.active == null ? null : (itemEls.get(props.active) ?? null)),
  layoutKey: () => layoutKey.value,
})

defineExpose({ focusItem })
</script>

<template>
  <GlassSurface
    class="glass-dock"
    material="chrome"
    width="100%"
    height="auto"
    surface-flow
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
    <nav
      ref="navRef"
      class="dock glass-material"
      :class="`dock--${props.layout}`"
      :aria-label="props.label"
    >
      <span class="dock-indicator" aria-hidden="true"></span>
      <button
        v-for="item in items"
        :key="item.key"
        :ref="(el) => registerItem(item.key, el)"
        type="button"
        class="dock-item"
        :class="{ 'is-active': item.key === props.active }"
        :aria-current="item.key === props.active ? 'page' : undefined"
        :aria-haspopup="item.haspopup"
        :aria-expanded="item.haspopup ? (item.expanded ? 'true' : 'false') : undefined"
        :title="item.key === props.active ? props.currentTitle : undefined"
        @click="emit('navigate', item.key)"
      >
        <AppIcon v-if="item.icon" class="dock-item__glyph" :name="item.icon" />
        <span class="dock-item__label">{{ item.label }}</span>
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
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  gap: clamp(var(--space-2), 4vw, var(--space-8));
  padding: var(--space-2) var(--space-4);
}

/*
 * The indicator — light gathered under the current item, not a slab. Its
 * geometry is written by useNavIndicator as custom properties on the nav
 * (measured, never guessed); only x and w travel. It is invisible until the
 * first measurement lands, so it is never seen in the wrong place. Body: a
 * faint pool rising from below; rim: a thin light-catching lip — both from
 * the on-glass light tokens (glass.css), so the bar stays transparent and
 * the stage keeps showing through. Width changes are the light's extent,
 * not the bar's geometry.
 */
.dock-indicator {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 0;
  width: var(--nav-indicator-w, 0px);
  height: var(--nav-indicator-h, 44px);
  border-radius: var(--material-radius-chrome-control);
  transform: translate(var(--nav-indicator-x, 0px), var(--nav-indicator-y, 0px));
  opacity: var(--nav-indicator-ready, 0);
  pointer-events: none;
  background: radial-gradient(
    120% 90% at 50% 115%,
    var(--on-glass-indicator-pool),
    transparent 70%
  );
  box-shadow:
    inset 0 0 0 1px var(--on-glass-indicator-rim),
    inset 0 1px 0 var(--on-glass-indicator-lip);
  transition: opacity var(--duration-base) var(--ease-out);
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
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  padding-inline: var(--space-3);
  border: none;
  /* Concentric with the bar: the chrome radius minus the primitive's inset
     and this row's block padding (`components.md` §7), authored once as a
     token. Phase B1 corrected this from a 22px pill. */
  border-radius: var(--material-radius-chrome-control);
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
  /* Halo tokens (glass.css), overridable from the stage: over the bright
     Product page the labels flip to dark ink and the dark halo would read
     as smudge. */
  text-shadow: 0 1px 10px var(--on-glass-halo);
  cursor: pointer;
  transition:
    color 400ms var(--ease-out),
    text-shadow 400ms var(--ease-out);
}

.dock-item:hover {
  color: var(--on-glass-text);
}

/* Press illumination (Phase B2): light gathers at the point of contact — a
   pool under the pressed label, opacity only, gone when the press ends. The
   bar's rim answers too, through --glass-press on the surface. */
.dock-item::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background: radial-gradient(90% 80% at 50% 60%, var(--on-glass-indicator-press), transparent 72%);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.dock-item:active::before {
  opacity: 1;
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
    0 1px 10px var(--on-glass-halo),
    0 0 18px var(--on-glass-halo-active);
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

/* ------------------------------------------------------------------ */
/* The `stacked` presentation (Phase B5) — the app dock's six items    */
/* ------------------------------------------------------------------ */

/*
 * The same bar and the same item, re-laid-out so five destinations plus More
 * fit a 390px phone: the glyph sits above a small label, items share the row
 * evenly instead of being gap-spaced, and labels ellipsize. `navigation.md` §4
 * allows exactly this — adapt spacing and truncation, never the bar's shape —
 * so nothing here touches geometry the material depends on: the slab's radius,
 * the concentric item radius, the indicator light and the press pool are the
 * landing dock's, unchanged.
 *
 * Nothing optical is declared here either: still `material="chrome"`, still
 * the clear water glass. Only layout.
 */
.dock--stacked {
  gap: var(--space-1);
  padding: var(--space-2);
}

.dock--stacked .dock-item {
  /* Equal shares of the row, so the bar's shape never depends on the labels. */
  flex: 1 1 0;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  /* ≥44px touch target at every viewport — this overrides the 640px step
     below, which tightens a three-label bar that has room to spare. */
  height: var(--app-dock-item);
  padding-inline: var(--space-1);
  letter-spacing: normal;
}

.dock--stacked .dock-item__glyph {
  flex-shrink: 0;
  opacity: 0.85;
  transition: opacity 400ms var(--ease-out);
}

.dock--stacked .dock-item:hover .dock-item__glyph,
.dock--stacked .dock-item.is-active .dock-item__glyph {
  opacity: 1;
}

/* The label ellipsizes before the bar reflows (`navigation.md` §4). */
.dock--stacked .dock-item__label {
  max-width: 100%;
  overflow: hidden;
  font-size: var(--app-dock-label);
  font-weight: 500;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
