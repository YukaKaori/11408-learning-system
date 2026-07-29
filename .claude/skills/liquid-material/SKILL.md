---
name: liquid-material
description: The one material system of this project — Apple-style Liquid Glass optics, the design constitution, Vue 3 component standards, and the OKLCH color law. Read BEFORE designing or implementing ANY glass, translucent, frosted, blurred, dock, overlay, dialog, palette, toolbar, card, toast or premium surface, and before adding ANY new color token. Triggers: glass, frosted, blur, translucent, liquid, material, premium surface, GlassSurface, glass.css, refraction, backdrop-filter, new color/palette/theme token.
---

# Liquid Material

The single material system of the AI Learning Platform. One skill, five
references: this file decides *whether and what*, the references say *how*.

The product's visual identity is **heavy optical glass** — not the web's default
frosted rectangle. Ordinary glassmorphism is a white blur with a border; it
hides content and signals nothing. Our glass is a physical material with
thickness, mass, and density: light enters, bends, disperses at the rim,
reflects off internal faces, and leaves. Content behind is *revealed through* it.

**Authority:** `references/constitution.md` is the design constitution — the
material's law, framework-free. Everything else in this skill operationalizes
it and may never contradict it. On conflict, the constitution wins and the
other file is corrected.

## Read the one you need

| File | Read when |
|---|---|
| `references/constitution.md` | Deciding what the material *is* — principles, tokens, allowed surfaces, forbidden patterns, roadmap fit |
| `references/materials.md` | Designing how a surface *looks* — layer stack, refraction, dispersion, Fresnel, depth, fallback tiers |
| `references/components.md` | Creating or composing a glass component — the primitive, the skin system, the catalogue, material ranks |
| `references/implementation.md` | Writing the actual Vue 3 + TypeScript code — variable channel, spotlight loop, map generation, traps, tests |
| `references/color.md` | Adding or changing **any** color token — OKLCH law, anchor palettes, chroma tapering, what stays frozen |

Living sources of truth in the repo: `docs/liquid-material-system.md` (the
material system), `docs/liquid-glass-analysis.md` (the optical research),
`components/experience/GlassSurface.vue` (the primitive), `styles/glass.css`
(the presets and skins).

---

## 1. When this skill applies

Use it whenever a task involves:

- Any **translucent, refracting, or elevated surface** — dialog, palette,
  toolbar, dock, sidebar, card, overlay, popover, toast
- The words *glass*, *frosted*, *blur*, *translucent*, *premium surface*,
  *liquid*, *material* in a UI request
- Modifying `GlassSurface.vue`, `GlassScene.vue`, `glass.css`,
  `useGlassSpotlight.ts`, `materials.ts`, or the displacement filter chain
- Deciding whether a new surface should be glass at all (often the answer is no)
- AI-state UI (thinking, streaming, complete) on a glass surface
- **Adding any color token anywhere in the app** — see `references/color.md`

Do **not** apply the glass parts to solid surfaces: lesson bodies, tables, code
editors, long forms, data-dense reading views. Those are never glass. The color
law in `references/color.md` applies to the whole app regardless.

## 2. The one test

**Glassmorphism hides what is behind it; Liquid Glass transmits what is behind
it, altered.**

If a treatment reduces the legibility of what is behind it or on it, the
treatment loses — not the content.

**FORBIDDEN (glassmorphism):**

- ❌ Simple transparency — `background: rgba(255,255,255,.2)` posing as glass
- ❌ Blur cards — `backdrop-filter: blur()` as the whole material
- ❌ White frosted panels — milky frost that *hides* the scene behind it
- ❌ Uniform 1px white borders on all four edges
- ❌ Depth faked with big drop shadows

**REQUIRED (Liquid Glass):**

- ✅ **Refraction** — the backdrop visibly *bends* through the surface
  (`feDisplacementMap`), strongest at edges and corners, clear at the centre
- ✅ **Dynamic lighting** — one implied light per scene; rim highlights, Fresnel
  arcs and sheens that answer to it. Reflections move; objects don't
- ✅ **Spatial depth** — double rim, back-face reflection, internal scattering;
  a *thick slab*, not a film
- ✅ **Material hierarchy** — density/depth/edge-energy scale with a surface's
  rank in the scene
- ✅ **Mass** — damped interactions, sub-pixel press settle; no springs, no
  stretch, no bounce

Every effect must be explainable as physics: *"the light is up-left, so this rim
glows."* If it exists only because it looks cool, it doesn't ship.

## 3. The non-negotiables

These are enforced by tests or by the constitution. Breaking one is a
deliberate, documented renegotiation — never a side effect.

1. **One refracting primitive: `GlassSurface.vue`.** Every other glass surface
   is a *composition* of it. Never fork the displacement chain; never write a
   second `backdrop-filter: url(#…)` anywhere.
2. **The material guard is a test** (`components/experience/__tests__/glassBudget.spec.ts`).
   Since Phase 17.2 it fails on four things: a fourth `GlassSurface` (budget is
   **3** — GlassDock, LoginView card, NoteSelectionToolbar), a forked refraction
   chain, any `backdrop-filter` outside `GlassSurface.vue` and `GlassScene.vue`
   (the environmental veil), and any reference to the retired glassmorphism
   family. `styles/__tests__/materialTokens.spec.ts` additionally pins each
   preset to the values its surface shipped with.
3. **Every surface declares its material.** `GlassSurface`'s `material` prop is
   **required**: `chrome | hero | floating` (`experience/materials.ts`), each a
   preset in `glass.css`. There is no anonymous glass and no hand-typed dial at
   a call site.
4. **On-glass control skins live in `glass.css` under `.glass-material`.**
   Extend that file. Never carry private glass styles in a component; never
   modify base components (`AppButton`, `AppInput`) for glass.
5. **All optical state flows through CSS custom properties with inert
   defaults.** A surface that opts into nothing renders as the calm baseline.
   Stage composables write variables; components never reach into each other.
6. **Content is never filtered.** Warp and lighting layers sit behind content
   (`pointer-events: none`, `aria-hidden`); content stays sharp at `z-index: 1`.
7. **Layout neutrality.** Glass components size via props and fall-through
   `class`/`style`; they never force `position`/`top`/`left` on themselves.
8. **Colors are law, not taste.** No new hard-coded color anywhere — see
   `references/color.md`.

## 4. Platform expression

**Desktop (a pointer exists).** The full expression runs: `useGlassSpotlight`
steers `--glass-light-x/y/angle/strength` and `--glass-proximity`; the Fresnel
arc rotates with the *real* light bearing (`atan2`, unwrapped across ±180°).
Hover pools light on the surface — it never scales, lifts, or bobs it. Focus
lifts an edge; keyboard focus rings always survive. Press is a sub-pixel settle
under mass (≤1px translate, slightly heavy duration) — never `scale(0.96)`,
never a spring.

**Mobile (no fine pointer).** The travelling light **never ignites** — the
`(hover: hover) and (pointer: fine)` gate keeps `--glass-light-strength` at 0 by
construction. Mobile glass must stand up *statically*: fixed implied light from
above (the Fresnel conic rests as a top highlight when `--glass-light-angle` is
undriven), plus depth and ND density carrying the material. **Never substitute
device orientation or scroll position as a pseudo-cursor** — that is a second
light source and a battery cost for decoration. Touch targets stay ≥44px.
Always review mobile surfaces with the spotlight disabled; that IS the mobile
appearance.

## 5. Motion budget

- **Reflections move; objects don't.** Cursor and scroll steer light *across*
  surfaces; geometry stays planted. No elastic stretch toward the pointer —
  that is a liquid-blob metaphor from a different material, rejected in
  `docs/liquid-glass-analysis.md` §5.1.
- **One-shot over infinite.** Entrances and emphasis play once and rest. The
  only allowed loop is the ambient surface-flow layer: 20–40s, low opacity,
  near-imperceptible.
- **Only compositor channels animate:** opacity, transform, gradient positions
  via custom properties. Never animate `filter`, `backdrop-filter`, blur radii,
  or box-shadow spreads per frame.
- **Enumerate transitioned properties.** `transition: all` is forbidden.
- **Damped, never springy.** Slightly heavier than app defaults, no overshoot.
- **Reduced motion is zero-by-construction:** the light variables default to 0
  and the gates never enable them under `prefers-reduced-motion`. Freezing is
  the default state, not a patch.

## 6. Performance

- **Displacement maps are build-once artifacts.** Generated on mount and on
  *debounced* resize (~120ms trailing) — never per frame. Each new `feImage`
  data URI forces a filter re-decode; a regen per resize-observer tick during a
  window drag is the classic failure.
- **No per-frame JS for decoration.** Reactive lighting is one self-settling rAF
  loop that writes CSS variables and stops when values settle. Pointer events
  only move goalposts. Never bind light position to Vue reactive state used in
  `:style` — that re-renders Vue at 60fps.
- **Rects are cached** behind a dirty flag; re-measured at most once per frame,
  only after resize/scroll marked them dirty.
- **No WebGL.** SVG filters + CSS deliver the material (proven — even the
  reference "shader" mode is a CPU loop, not GPU). A WebGL request requires
  written justification plus a full non-WebGL fallback.
- **Maps are procedural SVG data URIs (~1KB, lossless).** Never base64
  JPEG/PNG — JPEG chroma subsampling corrupts the displacement channels.
- **Fallbacks are mandatory.** Chromium gets refraction; Safari/Firefox get the
  frosted tier, which must stay fully legible and functional.

## 7. Pre-flight checklist

Before shipping any material work:

- [ ] Surface qualifies: elevated, transient, or premium — not a reading surface
      (check the allowed list in `references/constitution.md` §6)
- [ ] Composes `GlassSurface` with a declared `material` preset; budget test
      updated only as a deliberate renegotiation
- [ ] New optical layers gated by custom properties that default to inert
- [ ] One light direction across the whole view
- [ ] Frost ≈ 0; legibility via ND density; on-glass text uses the fixed dusk
      palette
- [ ] Optical hierarchy respected — dials match the surface's rank; never two
      heroes in one view
- [ ] Only opacity/transform/gradient-positions animate; map regen debounced
- [ ] No geometry deformation on hover/press; press is a sub-pixel settle
- [ ] Reduced-motion + coarse-pointer paths verified with the spotlight disabled
- [ ] Safari/Firefox fallback tier checked, legible, and fully functional
- [ ] Focus rings, contrast, keyboard paths, 44px touch targets intact
- [ ] Every color obeys `references/color.md` — no new hex/rgba literals
- [ ] Verified at the real surface with the `verify` skill, both themes
