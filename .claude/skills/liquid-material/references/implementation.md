# Implementation — Vue 3 + TypeScript patterns for Liquid Glass

Concrete coding patterns for this stack (Vue 3, TypeScript, Vite, Pinia).
Governed by `constitution.md`. Reference implementations: `GlassSurface.vue`,
`useGlassSpotlight.ts`, `glass.css`, `materials.ts`. Research base:
`docs/liquid-glass-analysis.md` §7.

---

## 1. State architecture: CSS variables are the render channel

The single most important pattern. Optical state has two tiers:

- **Per-frame state** (light position, strength, proximity, angle) → written as
  CSS custom properties via `el.style.setProperty()` from a rAF loop. Vue never
  sees it; nothing re-renders.
- **Per-mount state** (dial presets, variant, map parameters) → normal Vue
  props/computed, changing rarely.

```ts
// ✅ per-frame: direct variable writes, Vue not involved
cardEl.style.setProperty('--glass-light-x', `${x.toFixed(1)}px`)

// ❌ NEVER: reactive light state bound in a template
// :style="{ '--glass-light-x': lightX + 'px' }"  ← re-renders Vue at 60fps
```

The reference library's core failure was two React `setState` calls per
mousemove. The Vue equivalent is binding rAF-speed values through reactivity.
Returned refs from composables exist for logic/tests only — never bind them in
templates.

## 2. The spotlight loop pattern (`useGlassSpotlight.ts`)

Any future interactive-lighting composable copies this contract:

```
pointer events  →  move goalposts only (goalX/goalY/goalIntensity) + schedule()
rAF tick        →  exponential approach: current += (goal − current) × k
                →  measure rects ONLY if dirty (resize/scroll marked them)
                →  write CSS variables
                →  if everything settled → stop the loop (frame = 0)
```

Key constants worth reusing:

- Position easing `k ≈ 0.12`; proximity `k ≈ 0.1`.
- **Asymmetric presence envelope:** attack `0.08`, release `0.022` — light
  seeps in over ~0.6s, darkness flows back over ~2.5s. A reveal, not a switch.
- Proximity = `1 − distanceToRect(cursor, rect) / influence`, clamped 0..1,
  where `distanceToRect` is distance to the *nearest edge* (0 inside).
- Light bearing = `atan2(dy, dx) + 90°`, then **unwrap** against the previous
  angle (`while (angle - last > 180) angle -= 360; …`) so conic arcs glide
  through ±180° instead of snapping.
- Ignite in place on pointerenter (`if intensity < 0.02, snap current to goal`)
  so the light doesn't sweep in from the last exit point.

Gating (zero-by-construction, never a patch):

```ts
const supportsHover = window.matchMedia('(hover: hover) and (pointer: fine)')
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
// enable() only when supportsHover && !reducedMotion; re-evaluate on 'change'
```

One `ResizeObserver` total per composable; `disable()` cancels the frame,
zeroes strength/proximity variables, and removes every listener.

## 3. Inert-by-default optical layers (CSS)

Every new optical layer must paint **nothing** until a variable drives it:

```css
/* alpha produced by the gate itself — var()=0 ⇒ fully transparent */
background: radial-gradient(
  120% 60% at 50% 110%,
  color-mix(in srgb, light-dark(#fff, #beb6ff) calc(var(--glass-depth, 0) * 11%), transparent),
  transparent 64%
);

/* multiplicative gates: layer exists only when ALL conditions hold */
opacity: calc(var(--glass-depth, 0) * var(--glass-light-strength, 0) * var(--glass-proximity, 0));
```

Ring layers use the mask idiom, always with its guard:

```css
.ring {
  padding: 1.5px;
  background: conic-gradient(from calc(var(--glass-light-angle, 0deg) - 100deg), …);
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask-composite: exclude;
}
@supports not (mask-composite: exclude) {
  .ring { display: none; }  /* hide entirely rather than flood the face */
}
```

Use `light-dark()` for theme-aware light colors; use explicit `color-mix()`
alphas instead of `mix-blend-mode` (blend results depend on unknown backdrops).

### Press and focus illumination — Shipped (B2)

Two more gated variables, written by CSS state (no JS), consumed by the rim
layers, opacity-only. They are **registered** (`@property` in `glass.css`,
`<number>`, inherits, initial 0) so the slab can tween them:

```css
/* glass.css */
@property --glass-press { syntax: '<number>'; inherits: true; initial-value: 0; }
/* GlassSurface.vue */
.glass-surface { --glass-press: 0; --glass-focus: 0;
  transition: opacity .26s, --glass-press var(--duration-fast), --glass-focus var(--duration-base); }
.glass-surface:has(:active)         { --glass-press: 1; }   /* rim brightens: edge glow +.3, Fresnel +.2 */
.glass-surface:has(:focus-visible)  { --glass-focus: 1; }   /* the light-facing arc lifts: Fresnel +.4; the ring stays */
```

The ring (`:focus-visible` outline) is never replaced. Works on touch because
it is state, not pointer. Engines without `@property` snap instead of tween —
the same states, no animation. Local contact light (a pool under the pressed
dock label) is the recipe's own `::before` at opacity 0 → 1.

## 4. Displacement map generation

The map is a procedural SVG string → `data:image/svg+xml,${encodeURIComponent(...)}`
→ `feImage`. Browser-rasterised, lossless, ~1KB. Rules:

- Regenerate on mount and on **debounced** resize (~120ms trailing on the
  `ResizeObserver`-fed size ref). Each new data URI forces a filter re-decode —
  per-tick regeneration during a window drag is the canonical perf bug.
  **Shipped (B1)** as `components/experience/displacementMap.ts`:
  `createSettledMeasure` commits the first observation immediately and
  trailing-debounces the rest (rounded, so jitter is not a change);
  `createDisplacementMapCache` memoises per parameter set. Measured at the real
  surface: a 30-frame width burst produced 2 rebuilds (Chrome, Edge) instead of
  30.
- Memoise by parameters — always, not only "if maps repeat":

```ts
const mapCache = new Map<string, string>()  // key: `${w}x${h}r${radius}…`
```

- If SDF-quality maps are ever needed: approximate with SVG gradients first
  (option 1 in the analysis); if truly computing pixels, cap the longest side
  to ~128px and stretch via `feImage preserveAspectRatio="none"` — displacement
  data is low-frequency, downsampling is visually free. Never an 80k-iteration
  main-thread loop at full resolution.
- Known open question: the default `yChannel: 'G'` may read a channel the map
  barely encodes (analysis §6.4). Verify visually against `yChannel="B"` before
  touching map internals; the shipped look may depend on the current bias.

## 5. Typed variable contract

For **stage-level** overrides — a scene declaring its own light radius or flow —
prevent `--glass-fresnal` typos with a typed helper (add when first needed).
This is not a back door for per-surface dials: a surface's optics come from its
`material` preset (`components.md` §4), never from variables typed at its call
site.

```ts
// styles/glass-vars.ts
export interface GlassVars {
  '--glass-depth'?: number
  '--glass-density'?: number
  '--glass-tint'?: string
  '--glass-fresnel'?: number
  '--glass-inner-glow'?: number
  '--glass-edge-glow'?: number
  '--glass-flow-opacity'?: number
  '--glass-light-angle'?: `${number}deg`
}

export const glassVars = (v: GlassVars): Record<string, string> =>
  Object.fromEntries(Object.entries(v).map(([k, val]) => [k, String(val)]))
```

```vue
<!-- ✅ a stage tuning its own light, surfaces still declare their material -->
<section :style="glassVars({ '--glass-light-radius': '420px' })">
  <GlassSurface material="hero" />
</section>

<!-- ❌ a call site hand-typing a surface's optics -->
<GlassSurface material="hero" :style="glassVars({ '--glass-density': 0.8 })" />
```

Zero runtime cost, full autocomplete, makes the stage contract discoverable.

## 6. The material tier: one decision, on the root — Shipped (B1)

The only global material concern is the **tier** (`materials.md` §8). It is
resolved once at boot and reflected on `<html>`; a Pinia store is optional (a
plain module is fine) and, if used, holds nothing else:

```ts
// styles/materialTier.ts (or stores/material.ts)
export type GlassTier = 'refract' | 'diffuse' | 'dense'   // A / B / C
// resolved ONCE in main.ts before mount, re-evaluated on media-query change:
//   refract  ← SVG-in-backdrop renders AND engine is Blink
//   diffuse  ← backdrop-filter: blur() available
//   dense    ← no backdrop-filter, or prefers-reduced-transparency, or prefers-contrast: more
document.documentElement.dataset.glassTier = resolveGlassTier()
```

`glass.css` responds with `[data-glass-tier='diffuse'] .glass-surface { … }`;
`GlassSurface.vue` stops probing per instance and reads the attribute (or
simply lets the CSS cascade decide). Reduced motion and pointer coarseness are
**not** tier inputs — they gate the *light*, not the material.

*(Renamed 2026-09-16 from the never-built `'full' | 'reduced' | 'flat'`.)*

**Never** put light positions, proximity, or per-surface dials in a store —
that's reactive 60fps state with extra steps (§1).

## 7. Capability resolution — engine, never brand — Shipped (B1)

The shipped resolver is `styles/materialTier.ts` (`resolveGlassTier` is pure
and takes an injectable environment; `applyGlassTier` writes the root attribute
from `main.ts` and re-runs on the two accessibility media queries). The sketch
below is its shape.

```ts
// The ONE place a brand-ish string may exist. A test asserts no other file
// under src/ contains Safari|Firefox|Chrome|Edg|Gecko|WebKit.
export function resolveGlassTier(): GlassTier {
  if (
    matchMedia('(prefers-reduced-transparency: reduce)').matches ||
    matchMedia('(prefers-contrast: more)').matches ||
    !CSS.supports('backdrop-filter', 'blur(1px)')
  ) return 'dense'
  // 1. necessary, not sufficient — every engine parses url() in backdrop-filter
  const parses = CSS.supports('backdrop-filter', 'url(#p)')
  // 2. sufficient — every Blink browser (Chrome, Edge, Brave, Arc, Opera, Vivaldi)
  //    reports a literal "Chromium" brand; WebKit and Gecko lack the API entirely
  const brands = (navigator as { userAgentData?: { brands: { brand: string }[] } }).userAgentData?.brands
  const blink = brands ? brands.some((b) => b.brand === 'Chromium') : legacyUaIsBlink()
  return parses && blink ? 'refract' : 'diffuse'
}
// 3. the single named debt exception: consulted only when userAgentData is absent
// every Blink build — Chrome, Edge, Opera, Brave, Arc — carries "Chrome/" in its UA string
function legacyUaIsBlink(): boolean { return /Chrome\//.test(navigator.userAgent) }
```

Why not a pure feature test: WebKit and Gecko *parse* `backdrop-filter: url()`
but render nothing (recorded finding, `docs/liquid-glass-analysis.md` §6.5, to
be re-verified on real Gecko/WebKit in B1). Why not brands: Edge must be Chrome
by construction, and the only way to prove that is to make the code unable to
tell them apart.

Before B1, `GlassSurface.vue` ran a per-instance `/Safari/ && !/Chrome/` and
`/Firefox/` regex and toggled `.glass-surface--fallback`. Both are gone; the
brand guard in `materialTier.spec.ts` fails on any browser name outside the
resolver.

CSS side: tier styles hang off `html[data-glass-tier='…']` in the primitive's
scoped block — the base rule is tier B, `refract` adds the SVG chain, `dense`
replaces the body and drops the backdrop work; markup is identical in every
tier; every interactive element must work in every tier.

## 8. Vue-specific traps

- **Non-bubbling events:** `@blur`/`@focus` on a component that doesn't declare
  the emit is silently dead (cost Phase 16 its note-title saves). Use
  `focusout`/capture or declared emits for anything on glass toolbars/inputs.
- **Template refs through components:** the card element usually mounts after
  the stage — `watch` the ref and fold it into the existing `ResizeObserver`
  (see the end of `useGlassSpotlight.ts`), don't create a second observer.
- **`useId()` for filter IDs:** per-instance SVG filter IDs must be unique;
  sanitise (`replace(/:/g, '-')`) before use in `url(#…)`.
- **Scoped styles + pseudo-layers:** optical layers as pseudo-elements or
  dedicated `aria-hidden` divs inside the scoped component; `border-radius: inherit`
  everywhere so geometry is declared once.
- **Cleanup discipline:** every listener, observer, and rAF handle registered
  in `enable()` is released in `disable()`/`onBeforeUnmount` — spotlights
  outliving their stage keep writing variables onto detached nodes.

## 9. Animation implementation rules

```css
/* ✅ enumerate; slightly heavy; compositor-only */
transition: opacity 260ms var(--ease-out), transform 260ms var(--ease-out);

/* ❌ forbidden */
transition: all 0.2s ease-in-out;
```

- Ambient loops: 20–40s, `transform`/`opacity` only, `alternate`, low opacity;
  frozen globally under reduced motion (via the shared motion.css override).
- Entrances: one-shot, opacity + small translate (≤4–8px), damped, no
  overshoot. Press: ≤1px settle.
- Never animate `filter`, `backdrop-filter`, `box-shadow` spreads, or anything
  triggering paint per frame. If a light must move, move a gradient position
  through a variable.

## 10. Testing

- **Budget guard:** `components/experience/__tests__/glassBudget.spec.ts` — any
  new `GlassSurface` instance, any file matching
  `feDisplacementMap|backdrop-filter:\s*url\(` outside the primitive, any stray
  `backdrop-filter` outside `GlassSurface.vue`/`GlassScene.vue`, or any
  reference to the retired glassmorphism family fails CI. Update `ALLOWED` only
  as a deliberate, documented renegotiation.
- **Token guard:** `styles/__tests__/materialTokens.spec.ts` — each preset is
  pinned to the values its surface shipped with. A preset change that isn't a
  deliberate retune shows up here first.
- **Verification:** use the `verify` skill (Playwright against the real app)
  for glass work — screenshots at the real surface, including:
  - the fallback tier (Firefox/WebKit or forced),
  - reduced-motion / coarse-pointer (spotlight disabled — the true mobile look),
  - both themes (fallback is keyed to `html.dark`).
- Unit-test composable *logic* through its returned refs (they exist for
  tests); never assert on per-frame variable values with tight timing.

## 11. Color in code

Full law in `color.md`. The coding rules:

- A color literal in a component `<style>` block is a defect. Consume tokens.
- New tokens are OKLCH derived from a declared anchor ladder; existing hex/rgba
  tokens stay in their current format until a dedicated migration phase.
- Derived variants use relative color syntax, guarded when load-bearing:

```css
--x-soft: rgba(94, 106, 210, 0.1); /* fallback literal, frozen */
@supports (color: oklch(from red l c h)) {
  --x-soft: oklch(from var(--color-primary) l c h / 0.1);
}
```

- Theme switching is a **remap of semantic tokens** (`light-dark()` or the
  `html.dark` class the app already uses) — never a second set of literals, and
  never a runtime JS color computation.
- On-glass text stays on the fixed dusk palette; it is not theme-relative and
  not palette-derived (`constitution.md` §2.7).

## 12. Position-driven effects vs. time-driven animation

*Added 2026-07-31. The implementation half of `scroll-edge.md` §6.*

A distinction the motion budget depends on and never spelled out:

- **Position-driven** — every value is a pure function of a measured position
  (scroll offset, pointer distance, element geometry). At a given position the
  screen looks a specific way, regardless of how it got there. No duration, no
  easing, no trigger.
- **Time-driven** — a value changes because a clock is running: a transition, a
  keyframe animation, an eased approach toward a goal.

Why it matters in code:

- **Position-driven effects may run under `prefers-reduced-motion`.** The user
  is the clock. Freezing them would freeze the document under the user's own
  scrolling, which the preference does not ask for.
- **Time-driven effects must be gated**, zero-by-construction as everywhere
  else.
- The spotlight loop is a **hybrid** and worth naming as the exception: pointer
  position is the input, but the exponential approach toward the goal is a
  clock. That is exactly why it is gated on both fine-pointer and reduced-motion
  rather than on pointer alone. The **reveal wake** (`environment.md` §1 E4) is
  the same class — positions from the eased cursor, decay from a clock — and
  carries the same double gate; the **navigation indicator's travel** is
  time-driven (gated → jumps under reduced motion) while its *placement* is
  position-driven (always correct).

Rules for writing a position-driven effect:

- Derive from a measured value, never from an event *count* or a velocity
  threshold. A threshold turns a continuous function into a trigger, and a
  trigger implies an animation.
- Keep it **continuous across the whole range**. Stepped output shows a seam as
  content crosses each step, and is worse for motion-sensitive users than a
  smooth ramp, not better.
- Make it **stateless**. The output depends on the current position only, never
  on scroll history or direction — otherwise the effect disagrees with itself
  after a jump-to-top or a route change.
- Write it through the same custom-property channel as everything else (§1),
  and honour the same rect-caching and dirty-flag discipline (§2). Reading
  layout on every scroll event is the classic way to make a "cheap" effect the
  most expensive thing on the page.
- Never produce it by animating `filter`, `backdrop-filter`, or a blur radius
  (§9). If a boundary must soften, it is a mask or a gradient whose *position*
  moves — never a filter whose *strength* is recomputed.

## 13. SSR and hydration safety

*Added 2026-07-31. The app is client-rendered today; every rule below is also a
live bug class in an SPA, which is why they apply now.*

- **No browser globals at module scope.** Media queries, capability probes, and
  `document`/`window` access run in `onMounted` or later. A `window` reference
  evaluated at import time breaks the module everywhere it is imported — most
  visibly in unit tests, which have no DOM at import time.
- **Filter IDs must be deterministic across render passes.** Per-instance SVG
  filter identifiers come from Vue's own ID mechanism (§8), not from a module
  counter and not from a random value. A counter yields different IDs on server
  and client; a random value yields a mismatch *and* a silently broken
  `url(#…)` reference, which fails as an unfiltered surface rather than as an
  error.
- **No measurement during render.** All geometry is read after mount, behind the
  dirty flag.
- **The inert state is the pre-hydration state.** Because every optical layer is
  gated by a variable defaulting to zero (§3), a surface with no JavaScript yet
  renders as the calm baseline: preset tokens applied, no light, fully legible.
  This is free, and it is the reason to keep it free — any layer that paints
  something by default breaks it.
- **Resolve the quality tier once, at boot, onto the document root** (§6) rather
  than per instance during render. A tier decided inside a component's render is
  a tier that can differ between passes.
- **Never gate DOM structure on capability.** Markup is identical in every tier;
  only appearance differs. Conditional structure makes hydration mismatches
  unavoidable and turns the fallback into a separate, untested product.
- **The tier is an attribute on the root, written once before mount** (§6). It
  is the only capability the CSS ever sees.
- **The indicator's pre-measure state is "no indicator", never a wrong one.**
  Until item rects are read after mount, the light layer is at opacity 0; it
  never renders at a guessed position and slides.
- **The wake is not mounted on the server or on any non-qualifying client.** Its
  gate is `v-if` on a mounted-time media-query result, so there is no canvas to
  hydrate.

## 14. The scroll-edge mechanism — Contract (B5)

The recipe for `scroll-edge.md` §7. Content-side, position-driven, continuous,
symmetric, stateless:

```
owner        the scroll container that has floating chrome over it (.content in AppLayout; the Product room)
input        remaining = scrollHeight − scrollTop − clientHeight   (one passive scroll listener, rects cached)
output       one custom property, e.g. --scroll-edge: clamp(0, remaining / BAND, 1)   (BAND ≈ 48px)
paint        mask-image: linear-gradient(to bottom, #000 calc(100% − BAND − BAR), rgba(0,0,0, 1 − var(--scroll-edge)) calc(100% − BAR))
             — the band dissolves in proportion to how much content continues under the bar
rest         remaining = 0 → band fully open (nothing left to dissolve); the page's last row is readable
reachability scroll-padding-bottom: BAR + gap so the last row is reachable above the bar
```

No threshold, no duration, allowed under reduced motion, never a shadow under
the bar, never a change to the bar's material. Writing `--scroll-edge` through
the variable channel keeps Vue out of it.

## 15. The indicator composable — Shipped (B2)

`useNavIndicator(container, { target, layoutKey })` in
`composables/useNavIndicator.ts`, the same shape as the spotlight:

```
onMounted                 →  measure (container rect, target rect) → place directly → --nav-indicator-ready: 1
container / window resize →  one rAF-coalesced re-measure → place directly
layoutKey change          →  (items, locale) flush: 'post' → re-measure → place directly
target change             →  measure → travel (or place, under reduced motion / before the first placement)
rAF tick                  →  x += (goalX − x) × .16; w += (goalW − w) × .16; y, h applied at once; write
                             --nav-indicator-x/y/w/h on the container; snap + stop under 0.4 px
```

`createIndicatorMotion` is the pure model (injectable frame scheduler, unit
tested); `relativeGeometry` is the one measurement. The frame loop reads no
layout. Visibility is `opacity: var(--nav-indicator-ready, 0)` on the light
layer; the layer uses `translate()` + `width` only. Never a second
`GlassSurface`, never a template binding of the per-frame values. Bound by the
landing dock today; the app dock (B5) binds the same composable.
