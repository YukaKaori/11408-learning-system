# Phase 18 — Optical Glass Upgrade (audit + plan)

**Status:** PLAN ONLY. No source file modified. Awaiting approval.
**Governing documents (in authority order):**
1. `.claude/skills/liquid-material/references/constitution.md` — the constitution
2. the rest of `.claude/skills/liquid-material/` (`SKILL.md` + `materials.md`,
   `components.md`, `implementation.md`, `color.md`) — the implementation manual
3. `docs/liquid-glass-analysis.md` — the research this plan operationalizes

> The two skills this plan was written against (`optical-glass-design-system`,
> `ai-liquid-material`) were merged into the single `liquid-material` skill on
> 2026-07-29. Authority order is unchanged; only the paths and section numbers
> below were repointed.

**Premise:** the design system is not being replaced. This phase closes the gaps
between what the constitution *promises* and what the code currently *does*,
plus the four optical ideas worth taking from `rdev/liquid-glass-react`
(analysis §4, items A-1 → A-4). Every change is additive and defaults to
today's rendering.

---

## 1. Audit — what exists today

| Layer | File | Size | State |
|---|---|---|---|
| Refracting primitive | `components/experience/GlassSurface.vue` | 626 L | Stable, shipped P9–P16 |
| On-glass material | `styles/glass.css` (`.glass-material`) | 268 L | Stable |
| Lighting engine | `composables/useGlassSpotlight.ts` | 346 L | Stable, self-settling rAF |
| Ambient keyframes | `styles/motion.css` | 215 L | Stable |
| Tokens | `styles/tokens.css` | 281 L | Legacy `--glass-bg/border/blur/highlight` + `--glass-light-radius` |
| Budget guard | `experience/__tests__/glassBudget.spec.ts` | 72 L | Enforces 1 primitive / 3 instances |

**The three sanctioned instances and their dials:**

| Surface | Density | Depth | Fresnel | Character |
|---|---|---|---|---|
| `views/LoginView.vue` (sign-in slab) | 0.34 | 1 | 1 | Smoked hero slab |
| `experience/GlassDock.vue` (nav bar) | 0.16 | 1 | 1 | Clear water glass |
| `features/notes/editor/NoteSelectionToolbar.vue` | 0.62 | 1 | 1 | Dense transient toolbar |

**Verdict:** the architecture is sound and is *ahead* of the reference library on
depth layering, directional Fresnel, ND smoke body, inert-by-default gating,
rAF interpolation with asymmetric attack/release, reduced-motion correctness,
and layout neutrality. Nothing here needs rewriting. The gaps are in **map
quality, filter-space discipline, fallback honesty, and one real performance
defect**.

---

## 2. Gap analysis

Twelve findings, grouped by the six requested categories. Severity is
*product impact*, not effort.

### 2.1 Refraction capability

| # | Sev | Finding |
|---|---|---|
| **G1** | High | **`yChannel` reads an unencoded channel.** Default `yChannel: 'G'` (`GlassSurface.vue:104`) but the map paints X into R (horizontal gradient) and Y into B (vertical gradient) — nothing writes a Y gradient into G (`GlassSurface.vue:125-142`). Outside the blurred core rect G ≈ 0, which `feDisplacementMap` reads as a *constant* `−0.5 × scale` offset — with `distortionScale: -180`, a uniform ~90px vertical pull at the rim instead of a position-varying bend. **Inference from reading the generator, not from a rendered comparison.** Two readings: (a) a bug, and `yChannel: 'B'` gives true 2D refraction; (b) empirically tuned, and the constant bias *is* the look shipped across P9–P16. Must be resolved by eye before any map work. |
| **G2** | Med | **The map is not corner-aware.** Two full-width linear-gradient plates give a rectangular direction field; the refraction does not know where the rounded corners are. The reference derives its map from a rounded-rect SDF, which is what makes glass read as a *shape*. (analysis §1.2, A-2) |
| **G3** | Med | **No feather at the map boundary.** Displacement does not ramp to zero at the map's own edge, which produces a hard tearing ring at the surface border. Trivial, real artifact fix. (analysis §A-3) |
| **G4** | Low | **Filter region is tight** (`x/y=0%`, `w/h=100%`, `GlassSurface.vue:205-208`) vs. the reference's 170%. Ours is the *correct* default at `distortionScale: -180` (faster, no halo bleed) — recorded so it is never "fixed" by accident. No action beyond a comment. |

### 2.2 Distortion effects

| # | Sev | Finding |
|---|---|---|
| **G5** | High | **Edge Energy and Transmission are fused.** Centre clarity today is implicit — the blurred mid-grey core rect (`GlassSurface.vue:140`) flattens the middle. So raising `distortionScale` for more rim bend also softens the centre. The reference decouples them with an explicit filter-space edge mask (`feColorMatrix` → `feComponentTransfer` discrete → `feComposite in/over`, analysis §1.4). This is the single most valuable idea in that repository and it directly serves constitution principles 3 and 11. |

### 2.3 Dynamic highlights

| # | Sev | Finding |
|---|---|---|
| — | — | **We are ahead.** Real light bearing via `atan2`, unwrapped across ±180° (`useGlassSpotlight.ts:173-186`), conic Fresnel arc, asymmetric attack/release envelope. The reference fakes it with `135 + mouseX * 1.2` deg. Keep ours unchanged. |
| **G6** | Med | **The AI light vocabulary is specified but not implemented.** The design system defines thinking / streaming / complete / error as optical states (`liquid-material/references/constitution.md` §8, analysis §7.7), and nothing in the codebase drives them. Phase 17+ AI surfaces will each invent their own, which is how a design system fragments. |

### 2.4 Material depth

| # | Sev | Finding |
|---|---|---|
| **G7** | High | **The non-Chromium fallback is the forbidden material.** `.glass-surface--fallback` (`GlassSurface.vue:572-594`) is `rgba(255,255,255,.25)` + `blur(12px)` + a uniform 1px white border — textbook glassmorphism, explicitly banned by constitution principle 7 ("smoked ND glass, never white in light mode") and the forbidden-patterns list ("white frosted rectangles", "uniform 1px white border on all four edges"). It also ignores `--glass-density` / `--glass-tint` entirely, so the three surfaces that carefully differ in density (0.16 / 0.34 / 0.62) render identically in Safari and Firefox. The depth and Fresnel layers *do* still paint there, so the fix is bounded: make the fallback body consume the same dials. |
| — | — | Depth model itself (front rim, inner back rim offset 1px, back-face reflection, gated internal scatter) is richer than the reference's. Nothing to adopt. |

### 2.5 Interaction feedback

| # | Sev | Finding |
|---|---|---|
| **G8** | Med | **Focus does not lift an edge.** `liquid-material/SKILL.md` §4 promises "focus lifts an edge (edge-glow variable)". In code, focus is only a `:focus-visible` outline (`GlassSurface.vue:622-625`). Keyboard users get no material response. |
| — | — | Damped press exists and is correct (`glass.css:173-175`, 0.5px settle). Proximity envelope independently matches the reference's activation-zone model — validated, no change (analysis §A-6). |

### 2.6 Performance

| # | Sev | Finding |
|---|---|---|
| **G10** | High | **Displacement map regeneration is not debounced.** `displacementMap` is a `computed` on `measured` (`GlassSurface.vue:121-145`), and the `ResizeObserver` writes `measured` on every observed frame (`GlassSurface.vue:174-181`). The string build is cheap, but *each new data URI forces the browser to re-decode the `feImage` and rebuild the filter*. During a window drag that is a filter rebuild per frame — precisely what constitution rule 5 forbids ("expensive work happens on mount/resize, never per frame"). This is the one outright defect in the audit. |
| **G11** | Med | **Capability detection is per-instance and UA-gated.** Each surface runs `supportsSVGFilters()` on mount — three surfaces, three probes, three independent verdicts, and no way for the app to say "low-end device, everything goes flat". The UA check (`GlassSurface.vue:158-166`) is load-bearing and honestly commented, but it means constitution rule 6 ("feature-detect; never UA-gate") is not actually satisfied, and it will mis-classify Safari the day WebKit ships support. Nothing anywhere handles `prefers-reduced-transparency`. |
| **G12** | Low | **The custom-property contract is enforced by nothing.** ~12 variables documented in a 40-line docblock; a typo (`--glass-fresnal`) silently no-ops with zero feedback. |

---

## 3. Explicitly rejected (do not implement)

Re-stated here so a future step cannot quietly reintroduce them. Full reasoning
in analysis §5.

- ❌ **`elasticity` / stretch toward the cursor** — a liquid-blob metaphor from a
  different material. Violates constitution principles 12, 13 and motion
  principle 1 by name. This is the feature the demo GIF sells and the answer is
  still no; a "responds to me" quality is spent on **light**, never geometry.
- ❌ `scale(0.96)` press, layout coupling (`position/top/left` forced),
  sibling-layer duplication, per-mousemove state, base64 JPEG maps,
  stacked `mix-blend-mode: screen/overlay`, `transition: all`,
  heavy drop shadows, `cornerRadius: 999` default.
- ❌ **A second glass primitive, or a 4th instance.** The budget stays at 3;
  `glassBudget.spec.ts` is not touched by this phase.
- ❌ **WebGL / canvas map generation.** The reference's "shader" mode is an
  80,000-iteration CPU loop run synchronously on mount *and every resize*. Our
  SVG-string map is orders of magnitude cheaper and stays.

---

## 4. Upgrade plan

Seven steps. **Every step is additive; every new prop defaults to today's
rendering.** No existing prop, event, slot, or CSS variable changes meaning.
No stable component is rewritten.

### Step 0 — Resolve G1 (verification only, no product change)

Render the login slab and the dock side by side with `yChannel="G"` (today) and
`yChannel="B"`, via the `verify` skill (Playwright screenshots at :5173).

- If **B is better** → it becomes the default in Step 2, and the corner work
  builds on true 2D refraction.
- If **G is the shipped look** → keep it and add a comment explaining the
  deliberate constant rim bias so it is never "fixed" by accident.

Blocking for Step 2 only. Nothing else depends on it.
**Deliverable:** a decision recorded in this file. Zero code change.

### Step 1 — Performance floor (G10, G11)

The one defect, plus the capability model that later steps need.

1. **Debounce map regeneration** — ~120ms trailing on `measured`, last value
   held. Mount stays synchronous (no first-paint regression).
2. **`stores/material.ts`** — a Pinia store holding one thing: the global
   quality tier (`'full' | 'reduced' | 'flat'`), resolved **once at boot** from
   SVG-backdrop support, `prefers-reduced-motion`, `prefers-reduced-transparency`,
   coarse pointer, `saveData`, `deviceMemory`. Mirrored to `data-glass-tier` on
   `<html>` so `glass.css` can respond without any component knowing.
3. `GlassSurface` reads the tier instead of probing per instance. The UA gate
   moves into the store behind one documented function, with a TODO to replace
   it with a render-level probe (analysis §6.5).

**API impact:** none. Props, slots and the exposed `element` ref are untouched.
**Verify:** window-drag with the Performance panel — no filter rebuild per
frame; forced `flat` tier renders legibly; existing three surfaces unchanged
visually at `full`.

### Step 2 — Map quality (G2, G3, G4)

Additive props on `GlassSurface`, defaulting to today's generator:

```ts
profile?: 'gradient' | 'lens'   // default 'gradient' = today, byte-identical
mapFeather?: number             // default 0 = today
```

- `'lens'` paints the direction field as **four directional wedges clipped to
  the rounded rect** — corner-aware, still a browser-rasterised SVG string,
  still ~1KB, still zero main-thread pixel work (analysis §7.2 option 1).
  *Not* the reference's CPU loop.
- `mapFeather` ramps displacement to zero within N px of the map boundary.
- Memoise the generated data URI at module scope, keyed
  `${w}x${h}r${radius}f${feather}p${profile}` — resize back to a seen size is free.
- Comment the tight filter region (G4) as a deliberate choice.

**API impact:** two optional props. Existing call sites render identically.
**Verify:** unit test on the generator string (shape + cache hits); visual A/B
of `gradient` vs `lens` on the login slab, judged at the corners.

### Step 3 — Explicit edge mask (G5)

```ts
centerClarity?: number   // default 0 = today's chain, primitive-for-primitive
```

Adds the `feColorMatrix` → `feComponentTransfer(discrete)` →
`feComposite(in)` / `feComposite(over)` sub-chain (analysis §1.4), emitted
**only when `centerClarity > 0`** so non-opting surfaces pay nothing.

Buys the dial the constitution assumes exists: raise Edge Energy without
spending Transmission. Candidate consumer: the NoteSelectionToolbar at density
0.62, where centre clarity matters most.

**API impact:** one optional prop.
**Verify:** filter primitive count unchanged at default; text behind the slab
centre stays crisp while rim bend rises.

### Step 4 — Fallback material honesty (G7)

Rebuild `.glass-surface--fallback` from the same dials as the SVG path:
ND body from `--glass-density` / `--glass-tint`, directional rim instead of a
uniform white border, `blur()` + `saturate()` for diffusion only. No white fill,
in either theme.

**API impact:** none — CSS only, inside `GlassSurface`'s scoped block.
**Verify:** Firefox side-by-side against Chromium; the three surfaces must read
as *the same material at three densities*, not one generic frosted rectangle.
Contrast measured on on-glass text, not eyeballed.

### Step 5 — Interaction + AI light vocabulary (G8, G6)

1. **Focus lifts an edge** — `:focus-within` on `GlassSurface` raises
   `--glass-edge-glow` through the existing gated layer. Opacity only. The
   `:focus-visible` outline stays exactly as is (a material cue never replaces
   a focus ring).
2. **AI states as CSS, not JS** — add to `glass.css`:
   `.glass-ai--thinking` (raise `--glass-flow-opacity`; the existing 20–40s loop
   is the right speed — never accelerate it into a spinner),
   `.glass-ai--streaming` (back-face bloom via `--glass-depth`, opacity only),
   `.glass-ai--complete` (one-shot Fresnel brighten that settles; no loop),
   `.glass-ai--error` (density up, light down; never a red glow).
   Consumers toggle a class. No new composable, no per-frame JS.

**API impact:** none — additive CSS classes.
**Rule restated:** AI *chrome* is glass; AI *output* is solid. Streamed text
never renders on a refracting backdrop.

### Step 6 — Contract + documentation

1. `styles/glass-vars.ts` — the typed `GlassVars` interface + `glassVars()`
   helper (analysis §7.3). Zero runtime cost, full autocomplete, kills G12.
   Adopt at the three existing call sites; existing raw-CSS usage keeps working.
2. Update the `liquid-material` skill: `references/components.md` +
   `references/implementation.md` (new props, tier store, AI classes) and
   `references/constitution.md` §5 (new dials). Restate constitution rule 7 as
   *"never UA-gate without a documented render-level probe attempt"*
   (G11/§6.5 honesty).
3. Mark `docs/liquid-glass-analysis.md` A-1 → A-4 as adopted, and record the
   Step 0 verdict.
4. `glassBudget.spec.ts` unchanged — still 1 primitive, still 3 instances.

---

## 5. Sequencing and risk

| Step | Risk | Reversible | Blocks |
|---|---|---|---|
| 0 — verify `yChannel` | none | n/a | Step 2 |
| 1 — perf floor + tier | low | yes | Steps 2, 4 |
| 2 — map quality | **medium** (changes the look when opted in) | yes, `profile` default | — |
| 3 — edge mask | low (opt-in) | yes, `centerClarity` default | — |
| 4 — fallback | low (non-Chromium only) | yes | — |
| 5 — focus + AI light | low | yes | — |
| 6 — contract + docs | none | yes | — |

**Recommended order:** 0 → 1 → 4 → 2 → 3 → 5 → 6. Steps 1 and 4 are pure
correctness with no visual change on Chromium and can land independently of the
optics work.

**Relationship to Phase 17:** "Today" (Step 3, the frontend) is mid-flight and
touches no glass. This is a separate phase and should not interleave — except
Step 1, which is a bug fix with zero API surface and could land at any time.

**Definition of done:** all twelve findings closed or explicitly accepted with a
comment; `glassBudget.spec.ts` green and unchanged; the three existing surfaces
visually unchanged at default props on Chromium; Firefox renders the same
material at three densities; the `liquid-material/SKILL.md` §7 pre-flight
checklist passes end to end.
