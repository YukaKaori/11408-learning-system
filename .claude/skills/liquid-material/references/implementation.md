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

## 4. Displacement map generation

The map is a procedural SVG string → `data:image/svg+xml,${encodeURIComponent(...)}`
→ `feImage`. Browser-rasterised, lossless, ~1KB. Rules:

- Regenerate on mount and on **debounced** resize (~120ms trailing on the
  `ResizeObserver`-fed size ref). Each new data URI forces a filter re-decode —
  per-tick regeneration during a window drag is the canonical perf bug.
- Memoise by parameters if maps repeat:

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

## 6. Pinia: quality tier only, never optical state

Pinia holds exactly one glass concern — the **global material quality tier**:

```ts
// stores/material.ts
export type GlassTier = 'full' | 'reduced' | 'flat'

export const useMaterialStore = defineStore('material', () => {
  const tier = ref<GlassTier>('full')
  // resolve ONCE at boot from: SVG-backdrop support, prefers-reduced-motion,
  // prefers-reduced-transparency, pointer coarseness, saveData/deviceMemory
  const refracts = computed(() => tier.value === 'full')
  return { tier, refracts }
})
```

Rationale: the tier must be one decision applied everywhere (today each surface
probes independently). Reflect it as `data-glass-tier` on `<html>` so
`glass.css` can respond without components knowing.

**Never** put light positions, proximity, or per-surface dials in a store —
that's reactive 60fps state with extra steps (§1).

## 7. Feature detection & fallback tiers

```ts
// Current, load-bearing: WebKit/Firefox PARSE backdrop-filter: url() but
// render nothing — a pure feature-test false-positives there.
function supportsSVGFilters(): boolean {
  const isWebkit = /Safari/.test(navigator.userAgent) && !/Chrome/.test(navigator.userAgent)
  const isFirefox = /Firefox/.test(navigator.userAgent)
  if (isWebkit || isFirefox) return false
  const div = document.createElement('div')
  div.style.backdropFilter = 'url(#probe)'
  return div.style.backdropFilter !== ''
}
```

This UA gate is documented technical debt (analysis §6.5): keep it, don't
spread it, and replace it with a render-level probe (ideally in the boot-time
tier resolution, §6) when practical. CSS-side fallbacks:

- `.glass-surface--fallback` — frosted tier, keyed to `html.dark` (the app
  toggles theme by class, **not** `prefers-color-scheme`).
- `@supports not (backdrop-filter: blur(10px))` — solid-ish tier.

Every interactive element must work in every tier.

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
  rather than on pointer alone.

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
