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
import { useNavIndicator, type IndicatorGeometry } from '@/composables/useNavIndicator'

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
 * Amendment A1 (2026-10-02) — THE SELECTION LENS (`navigation.md` §4, the
 * owner's request: "the glass block taller than the bar, like the iPhone").
 * A host may pass `lens`: the current item is then marked by a small slab of
 * its own — a capsule of the same `chrome` glass — instead of the light. The
 * labels share the row in equal cells (a segmented control, as Apple's tab
 * bars and segmented controls do), and the lens answers the hand: pressing a
 * label lifts it — it swells past the bar's top and bottom edges — and
 * carries it to that cell; dragging steers it along the bar; it settles back
 * into the bar when it lands. Damped, never sprung; no lift under reduced
 * motion. The light indicator stays the marker everywhere else (the app dock).
 *
 * The frame is three layers in one box, back to front: the bar's slab, the
 * lens, the labels. The labels ride ABOVE both slabs so text is never
 * refracted (constitution: content is never filtered), and the lens is a
 * sibling of the bar rather than inside it because a slab clips its content
 * and is a backdrop root — a lens nested in it could neither swell past its
 * edges nor see the stage behind. The frame declares the rank all three share
 * (`data-material="chrome"`), which is how the label layer still receives the
 * on-glass tokens and the light-backdrop ink flip (`glass.css`).
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
    /** Mark the current item with the selection lens (A1) instead of the light. */
    lens?: boolean
  }>(),
  {
    currentTitle: undefined,
    layout: 'labels',
    lens: false,
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
// of this bar, not a second material. Its geometry is written on the frame,
// so the light, the lens and the labels all read the same numbers.
//
// The layout key is the item set as text rather than the array itself: a host
// that rebuilds the list to flip one item's `expanded` must not read as a
// layout change, while a locale switch or a changed destination must.
const frameRef = ref<HTMLElement | null>(null)
const layoutKey = computed(() => props.items.map((item) => `${item.key}:${item.label}`).join('|'))

const indicator = useNavIndicator(frameRef, {
  target: () => (props.active == null ? null : (itemEls.get(props.active) ?? null)),
  layoutKey: () => layoutKey.value,
})

/*
 * A1 — the lens answers the hand. A press lifts the lens and carries it to the
 * pressed cell; past a few pixels of travel the press becomes a drag that
 * steers it along the bar (clamped between the first and last cell centres),
 * and releasing over a cell navigates there. A plain click still navigates
 * through its button, so keyboard and assistive paths are untouched. Cells
 * are measured once per gesture; every move after that is arithmetic.
 */
const DRAG_THRESHOLD = 6

const pressed = ref(false)
const lifted = computed(() => props.lens && (pressed.value || indicator.moving.value))

interface Cell {
  key: string
  geometry: IndicatorGeometry
}

interface Gesture {
  pointerId: number
  startX: number
  /** The frame's left edge at the press — pointer x minus this is frame-local. */
  originX: number
  cells: Cell[]
  dragging: boolean
}

let gesture: Gesture | null = null
let suppressClick = false

const centreOf = (geometry: IndicatorGeometry) => geometry.x + geometry.w / 2

/** Frame-local x of the pointer, clamped to the span of cell centres. */
function trackX(g: Gesture, clientX: number) {
  const first = centreOf(g.cells[0]!.geometry)
  const last = centreOf(g.cells[g.cells.length - 1]!.geometry)
  return Math.min(Math.max(clientX - g.originX, first), last)
}

function nearestCell(g: Gesture, x: number): Cell {
  return g.cells.reduce((best, cell) =>
    Math.abs(centreOf(cell.geometry) - x) < Math.abs(centreOf(best.geometry) - x) ? cell : best,
  )
}

function steerTo(g: Gesture, clientX: number) {
  const x = trackX(g, clientX)
  const cell = nearestCell(g, x)
  // A press snaps the lens to the pressed cell; a drag lets it follow the hand.
  indicator.steer(g.dragging ? { ...cell.geometry, x: x - cell.geometry.w / 2 } : cell.geometry)
}

function onPointerDown(event: PointerEvent) {
  if (!props.lens || event.button !== 0 || !frameRef.value) return
  const cells = props.items.flatMap((item) => {
    const geometry = indicator.geometryOf(itemEls.get(item.key))
    return geometry ? [{ key: item.key, geometry }] : []
  })
  if (cells.length === 0) return
  suppressClick = false
  gesture = {
    pointerId: event.pointerId,
    startX: event.clientX,
    originX: frameRef.value.getBoundingClientRect().left,
    cells,
    dragging: false,
  }
  pressed.value = true
  steerTo(gesture, event.clientX)
}

function onPointerMove(event: PointerEvent) {
  const g = gesture
  if (!g || event.pointerId !== g.pointerId) return
  if (!g.dragging) {
    if (Math.abs(event.clientX - g.startX) < DRAG_THRESHOLD) return
    g.dragging = true
    // Captured only once it is a drag: a plain click keeps landing on its button.
    ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  }
  steerTo(g, event.clientX)
}

function endGesture(event: PointerEvent, commit: boolean) {
  const g = gesture
  if (!g || event.pointerId !== g.pointerId) return
  gesture = null
  pressed.value = false
  const landing = commit && g.dragging ? nearestCell(g, trackX(g, event.clientX)) : null
  if (g.dragging) {
    // The release lands on a button that never received the press: the drag
    // navigates itself and swallows the stray click the browser may still send.
    suppressClick = true
    window.setTimeout(() => {
      suppressClick = false
    }, 0)
  }
  indicator.steer(null)
  if (landing && landing.key !== props.active) emit('navigate', landing.key)
}

const onPointerUp = (event: PointerEvent) => endGesture(event, true)
const onPointerCancel = (event: PointerEvent) => endGesture(event, false)

function onItemClick(key: string) {
  if (suppressClick) {
    suppressClick = false
    return
  }
  emit('navigate', key)
}

defineExpose({ focusItem })
</script>

<template>
  <div
    ref="frameRef"
    class="dock-frame"
    data-material="chrome"
    :class="{ 'has-lens': lens, 'is-lifted': lifted }"
  >
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
    />
    <div v-if="lens" class="dock-lens-track" aria-hidden="true">
      <!-- A small slab bends gently: a third of the bar's displacement, a
           whisper of dispersion — at full strength it would pull the bar's
           own rims inside itself. -->
      <GlassSurface
        class="dock-lens"
        material="chrome"
        width="100%"
        height="100%"
        :border-width="0.2"
        :blur="8"
        :opacity="0.93"
        :displace="0.5"
        :background-opacity="0"
        :saturation="1.15"
        :distortion-scale="-36"
        :red-offset="0"
        :green-offset="2"
        :blue-offset="4"
      />
    </div>
    <nav
      class="dock glass-material"
      :class="`dock--${props.layout}`"
      :aria-label="props.label"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerCancel"
    >
      <span v-if="!lens" class="dock-indicator" aria-hidden="true"></span>
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
        @click="onItemClick(item.key)"
      >
        <AppIcon v-if="item.icon" class="dock-item__glyph" :name="item.icon" />
        <span class="dock-item__label">{{ item.label }}</span>
      </button>
    </nav>
  </div>
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
 * The frame — three layers in one box (A1): the label row is in flow and
 * gives the frame its size; the bar's slab fills it from behind; the lens,
 * when there is one, rides between them.
 */
.dock-frame {
  position: relative;
  width: 100%;
}

.dock-frame > .glass-dock {
  position: absolute;
  inset: 0;
  z-index: 0;
}

/* Press and focus illumination (Phase B2) reach the slab from the label layer
   above it — they used to arrive through `:has()` inside the slab itself. */
.dock-frame:has(.dock-item:active) > .glass-dock {
  --glass-press: 1;
}

.dock-frame:has(.dock-item:focus-visible) > .glass-dock {
  --glass-focus: 1;
}

/*
 * One row of floating labels, centered like the Text meshes on the bar.
 * The spacing steps mirror FluidGlass's DEVICE table (desktop 0.3 →
 * tablet 0.24 → mobile 0.2 world units) as a viewport-driven clamp. The
 * padding is the primitive's own inset plus the row's: the labels sit exactly
 * where they sat when they lived inside the slab (`components.md` §7).
 */
.dock {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  gap: clamp(var(--space-2), 4vw, var(--space-8));
  padding: calc(var(--material-inset) + var(--space-2)) calc(var(--material-inset) + var(--space-4));
}

/*
 * The indicator — light gathered under the current item, not a slab. Its
 * geometry is written by useNavIndicator as custom properties on the frame
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
/* The selection lens (A1) — the landing dock                          */
/* ------------------------------------------------------------------ */

/*
 * A segmented bar: equal cells across the whole width (Apple's tab bars and
 * segmented controls), larger type, and one capsule of glass under the
 * current cell. The cell height is the lens's resting height; the bar is the
 * cell plus the primitive's inset, so at rest the lens sits inside the bar
 * with an even margin, and lifted (×1.5) it stands proud of both edges.
 */
.dock-frame.has-lens {
  --dock-cell: 56px;
  --dock-lens-lift: 1.5;
  /* As wide as a label needs, never wider than its cell. */
  --dock-lens-w: min(156px, calc(var(--nav-indicator-w, 0px) - var(--space-2)));
}

.dock-frame.has-lens > .dock {
  gap: 0;
  padding: var(--material-inset);
  /* Horizontal drags steer the lens; vertical swipes stay the page's. */
  touch-action: pan-y;
}

.dock-frame.has-lens .dock-item {
  flex: 1 1 0;
  min-width: 0;
  height: var(--dock-cell);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

/* The lens is the press feedback and the selection here; the pressed pool and
   the per-label pointer light would paint patches over it. The travelling
   light stays on the bar itself (its `.glass-dock` facet). */
.dock-frame.has-lens .dock-item {
  background-image: none;
}

.dock-frame.has-lens .dock-item::before {
  display: none;
}

/*
 * The lens rides on the indicator's geometry: centred in the marked cell,
 * a cell high. The track carries the position (written per frame by the
 * composable, so it has no transition of its own); the slab inside it carries
 * the lift (a CSS transition, so the two never fight). Invisible until the
 * first measurement lands, like the light.
 */
.dock-lens-track {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 1;
  width: var(--dock-lens-w);
  height: var(--nav-indicator-h, 0px);
  transform: translate(
    calc(var(--nav-indicator-x, 0px) + (var(--nav-indicator-w, 0px) - var(--dock-lens-w)) / 2),
    var(--nav-indicator-y, 0px)
  );
  opacity: var(--nav-indicator-ready, 0);
  pointer-events: none;
  transition: opacity var(--duration-base) var(--ease-out);
}

/*
 * The lift — the one place this material changes size, and only the
 * selection object, never the bar (A1, `interaction.md` §4 and §9). Damped:
 * it swells faster than it settles (light arrives faster than it leaves), on
 * the house ease-out — no overshoot, no spring. Transform only, on the
 * compositor; the displacement map is built once for the resting size and
 * scales with the slab.
 */
.dock-lens-track > .dock-lens {
  transform: scale(1);
  transition: transform 520ms var(--ease-out);
}

.dock-frame.is-lifted > .dock-lens-track > .dock-lens {
  transform: scale(var(--dock-lens-lift));
  transition-duration: 280ms;
}

@media (prefers-reduced-motion: reduce) {
  .dock-frame.is-lifted > .dock-lens-track > .dock-lens {
    transform: none;
  }
}

@media (max-width: 640px) {
  .dock-frame.has-lens {
    --dock-cell: 48px;
  }

  .dock-frame.has-lens .dock-item {
    font-size: var(--text-sm);
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
  padding: calc(var(--material-inset) + var(--space-2));
}

.dock--stacked .dock-item {
  /* Equal shares of the row, so the bar's shape never depends on the labels. */
  flex: 1 1 0;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  /* ≥44px touch target at every viewport — this overrides the 640px step
     above, which tightens a three-label bar that has room to spare. */
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
