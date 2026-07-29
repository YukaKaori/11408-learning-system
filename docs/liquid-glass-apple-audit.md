# Liquid Glass — Audit against Apple's direction

**Date:** 2026-07-27 · **Status:** audit only, no code modified
**Scope reviewed:** `GlassSurface.vue`, `glass.css`, `useGlassSpotlight.ts`,
`tokens.css`, `motion.css`, `GlassDock.vue`, `GlassScene.vue`,
`NoteSelectionToolbar.vue`, `LoginView.vue`, `AppCard.vue`,
`element-theme.css`, `glassBudget.spec.ts`
**Related:** `docs/liquid-glass-analysis.md` (rdev study), `docs/phase18-glass-upgrade-plan.md` (proposed plan)

> **Calibration note.** "Apple's latest Liquid Glass direction" here means the
> material introduced at WWDC 2025 for iOS/iPadOS/macOS 26, as characterized
> from Apple's published design guidance up to my knowledge cutoff (May 2026) —
> including the post-launch adjustments that *increased* opacity for legibility.
> I am describing observable material behavior, not citing current API. Re-check
> the live HIG before committing to anything in §4 that depends on a specific
> Apple behavior.

---

## 0. Headline verdict

This project does not implement Apple's Liquid Glass. It implements a
**deliberately different material** — heavy smoked optical glass — and it does
so with more rigor than most Liquid Glass clones on the web.

Measured against Apple's direction:

| Dimension | Closeness | Note |
|---|---|---|
| Refraction / lensing | **Strong** | Real `feDisplacementMap`, per-channel dispersion. Ahead of most web attempts. |
| Specular / directional light | **Strong** | Real `atan2` light bearing, unwrapped Fresnel arc. Genuinely close to the idea. |
| Spatial depth | **Strong (different)** | Richer static depth than Apple's; heavier, less lightweight. |
| Layer discipline | **Strong** | Content never filtered; glass is chrome only. Matches Apple's core rule. |
| **Adaptivity to content** | **Absent** | Apple's glass is content-aware and self-adjusting. Ours is fixed dusk + hard-coded densities. |
| **Fluid morphing** | **Absent** | The "Liquid" in Liquid Glass. Rejected here by association with a different feature. |
| **Concentricity** | **Absent** | Radii are magic numbers; the radius token is dead. |
| **Reach** | **Weak** | 3 optical surfaces after 8 phases; the rest of the app runs a *second, forbidden* glass material. |

**The one-line summary:** the material physics are excellent and the material
*system* is not. Apple's Liquid Glass is a system that adapts and spreads;
ours is a bespoke installation that has to be hand-placed, which is why it has
reached three surfaces in eight phases while an unsanctioned glassmorphism
lives in more places than it does.

---

## 1. Current strengths

### 1.1 Refraction is real (GlassSurface)

`GlassSurface.vue:200-277` runs an actual optical chain: a procedurally
generated SVG displacement map fed through three `feDisplacementMap` passes at
staggered scales (`distortionScale + redOffset/greenOffset/blueOffset`),
isolated per channel and screen-blended. This is genuine chromatic dispersion,
physically motivated, and it is what separates lensing from blur. Most web
"Liquid Glass" is `backdrop-filter: blur()` with a white border; this is not.

The map is a ~1KB lossless procedural data URI (`GlassSurface.vue:121-145`) —
not a base64 JPEG blob, whose chroma subsampling would corrupt the exact R/B
channels that encode the displacement vector.

### 1.2 Lighting is computed, not faked

`useGlassSpotlight.ts:173-186` derives a real light bearing from the card centre
with `atan2`, then **unwraps it across ±180°** so the Fresnel arc glides through
the seam instead of snapping. That is fed to a `conic-gradient` masked to the
rim (`GlassSurface.vue:449-467`), so only the edge facing the light brightens.

This is materially better than the reference implementation's
`135 + mouseX * 1.2` heuristic, and it is the correct reading of Fresnel
behavior — reflection rises at grazing angles.

The intensity envelope is asymmetric on purpose (`useGlassSpotlight.ts:38-39`):
light seeps in over ~0.6s and drains over ~2.5s. That is a considered material
decision, not a default.

### 1.3 Depth is layered, not shadowed

Front rim, inner back rim offset 1px downward, back-face reflection, and
internal scattering gated on actual light presence (`GlassSurface.vue:378-441`).
Apple's own depth model leans harder on shadow separation; this one buys depth
from internal optics. Against the constitution's own ranking, this is the
correct spend, and it holds up under a second look — which is the stated goal.

### 1.4 Inert-by-default gating

Every optical layer's alpha is produced by
`color-mix(… calc(var(--glass-depth, 0) * 34%) …)`. With the variable at 0 the
layer paints *literally nothing*. A surface that opts into nothing renders as
the calm baseline, at zero cost. This is an unusually disciplined pattern and it
is what makes the primitive safe to extend.

### 1.5 Performance architecture (the loop, not the map)

`useGlassSpotlight` is close to textbook: pointer events only move goalposts, a
single rAF loop interpolates, **the loop stops itself when everything settles**
(`useGlassSpotlight.ts:229-234`), rects are cached behind a dirty flag and
re-measured at most once per frame, one `ResizeObserver` total, and only CSS
custom properties are written — Vue never re-renders. The composable's own
docblock forbids binding the returned refs in templates
(`useGlassSpotlight.ts:63-64`).

### 1.6 Accessibility posture is zero-by-construction

Reduced motion and coarse pointers do not *disable* the light; the light never
ignites, because the gate never enables it (`useGlassSpotlight.ts:315-321`).
Freezing is the default state rather than a patch. On-glass text uses a fixed
dusk palette (`glass.css:36-38`) precisely because theme-relative text on a
smoked slab goes dark-on-dark. There is a documented `@supports not
(mask-composite: exclude)` bail-out (`GlassSurface.vue:471-475`).

### 1.7 Layer separation matches Apple's central rule

Apple's structure is a content layer with a floating functional layer above it,
and Liquid Glass belongs only to the functional layer. This project states the
same rule and enforces it: warp and lighting layers are `pointer-events: none`,
`aria-hidden`, behind content at `z-index: 1` (`GlassSurface.vue:281-288`), and
both skills state "AI chrome is glass; AI output is solid." Dense reading
surfaces are explicitly excluded from the material.

---

## 2. Missing capabilities

Ordered by distance from Apple's direction.

### 2.1 Adaptivity — the material does not respond to what is behind it

This is the largest single gap. Apple's Liquid Glass is *content-aware*: it
samples what is behind it and adapts — shifting between light and dark
appearance, adjusting symbol and label color, boosting opacity where legibility
demands it. That adaptivity is what lets one material work everywhere.

Ours is fixed by construction:

- The on-glass palette is a hard-coded dusk ramp (`glass.css:36-38`).
- Density is a magic number typed at each of the three call sites — 0.34
  (login), 0.16 (dock), 0.62 (toolbar).
- Brand steps are pinned to dark-theme values inside `.glass-material`
  (`glass.css:52-56`) because the light theme's primary loses contrast on dark
  glass.

Every one of these decisions is *defensible and documented*. Together they mean
the material can only be placed on stages whose backdrop the team already
controls. **That is the actual reason glass has reached three surfaces in eight
phases** — not the budget test. The budget test is a symptom; the fixed palette
is the cause.

`docs/liquid-glass-analysis.md` §7.5 proposes the right shape of fix — a
stage-declared `--glass-backdrop: dark | light` — and explicitly rejects
automatic luminance sampling. That remains the correct call. It is unbuilt.

### 2.2 Fluid morphing — the "Liquid" is missing, and was rejected by conflation

Apple's signature behavior is state-transition morphing: a button that grows
into the menu it opened, a tab bar that contracts as content scrolls under it,
controls that merge and separate. Elements share a material and flow between
shapes.

The project has none of it, and the reason is a **conflation worth naming
precisely**:

- `docs/liquid-glass-analysis.md` §5.1 rejects rdev's `elasticity` — a
  hover-time deformation where the surface stretches ±30% *toward the cursor*.
  **That rejection is correct.** It is a liquid-blob metaphor, and a slab of
  glass does not lean toward your finger.
- But the constitution then generalizes it to "reflections move; objects don't"
  and "no geometry deformation, ever" — which also forbids Apple's
  *transition-time* morphing, a different behavior with a different
  justification. Apple's morph is causal (this menu came from that button), not
  decorative, and it is bounded in time rather than continuous.

Two distinct things were rejected in one motion. The hover-follow rejection
should stand permanently; the transition-morph rejection deserves a conscious
re-decision rather than inheritance. Note that a morph is also *fully
compatible* with "glass has mass" — mass constrains the easing curve, not the
existence of the transition.

### 2.3 Interactive illumination on press

Apple's glass brightens and gels under a touch — the material acknowledges the
press optically. Here, press is `transform: translateY(0.5px)`
(`glass.css:173-175`). The damping is right and the anti-spring stance is right,
but the *optical* half is absent: no rim brighten, no light pooling at the
contact point, nothing driving `--glass-edge-glow` or `--glass-proximity` on
press. The variables to do it already exist and are already gated.

Related: **focus does not lift an edge either**, though
`liquid-material/SKILL.md` §4 promises exactly that. Focus is a
`:focus-visible` outline only (`GlassSurface.vue:622-625`). Keyboard users get
no material response at all.

### 2.4 Concentricity

Apple derives corner radii concentrically from the containing shape so nested
rounded elements stay optically parallel. Here radii are unrelated magic
numbers: `borderRadius: 20` default, `30` on the dock, `16` on the toolbar, a
`CARD_RADIUS` constant on login. Meanwhile `--radius-glass: var(--radius-xl)` is
defined in `tokens.css:140` and **consumed by nobody** — a dead token.

### 2.5 Variant system (Regular vs Clear)

Apple ships two variants — an adaptive default, and a clearer one reserved for
media-rich backdrops that requires a dimming layer beneath it. This project has
the *dials* for both (`--glass-density` 0.16 on the dock is effectively Clear;
0.62 on the toolbar is effectively Regular) but no named variants, no tokens,
and no rule about when each applies. The knowledge lives in three inline
comments.

### 2.6 Scroll edge treatment

Apple applies a graduated blur/fade where content scrolls beneath a glass bar,
so text dissolves rather than colliding with the edge. `GlassDock` is a fixed
bottom bar with content behind it and has no such treatment.

### 2.7 `prefers-reduced-transparency`

Apple treats Reduce Transparency and Increase Contrast as first-class material
states. This codebase handles `prefers-reduced-motion` thoroughly and
`prefers-reduced-transparency` **nowhere**. Users who ask the OS for less
translucency get the full material.

---

## 3. Technical debt

### 3.1 🔴 Two glass materials coexist, and the forbidden one has wider reach

This is the most serious finding in the audit.

`tokens.css:151-154` defines a second, older glass family:

```css
--glass-bg: rgba(255, 255, 255, 0.72);   /* white, in light mode */
--glass-border: rgba(255, 255, 255, 0.5); /* uniform, all four edges */
--glass-blur: 20px;
--glass-highlight: rgba(255, 255, 255, 0.55);
```

That is, precisely, the material both skills forbid by name — "white frosted
rectangles", "uniform 1px white border on all four edges", "blur as the whole
material". And it is consumed in **more places than the optical system**:

| Consumer | What it skins |
|---|---|
| `AppCard.vue:52-57` | `variant="glass"` — used in `WorkspaceView`, `WelcomeView`, `DesignSystemView` |
| `element-theme.css:59-63` | Element Plus dialogs / popovers — app-wide |
| `WelcomeView.vue:263-265, 328-329` | Welcome surfaces |
| `LoginView.vue:538, 594` | Inside the flagship optical view itself |
| `GlassScene.vue:84` | Stage highlight |

So the authenticated app *does* have glass — it is just the wrong kind. The
constitution's Allowed Components list names dialogs, dropdowns, and cards as
optical-glass surfaces; today they are glassmorphism.

### 3.2 🔴 The budget test enforces a false sense of control

`glassBudget.spec.ts` caps `GlassSurface` at 3 instances and greps for
`feDisplacementMap|backdrop-filter:\s*url\(`. A plain `backdrop-filter: blur()`
card passes it untouched. The test therefore **rations the good material and is
blind to the bad one**.

Its second assertion — *"the authenticated app mounts at most one glass surface,
in Notes"* (`glassBudget.spec.ts:59-62`) — reads as a statement about the app's
glass, and is false in that sense: `WorkspaceView.vue:665` renders a glass
`AppCard`. True for the primitive, misleading as documentation.

### 3.3 🔴 Map regeneration is not debounced

`displacementMap` is a `computed` on `measured` (`GlassSurface.vue:121-145`),
and the `ResizeObserver` writes `measured` on every observed frame
(`GlassSurface.vue:174-181`). Each new data URI forces the browser to re-decode
the `feImage` and rebuild the filter — a filter rebuild per frame during a
window drag. This is the one outright defect, and it violates the constitution's
own performance rule.

### 3.4 🟠 The optical dials are not tokens

`tokens.css` opens by declaring itself "the single source of truth… never
hard-code colors, radii, spacing, shadows or motion in component styles." Yet
`--glass-density`, `--glass-depth`, `--glass-fresnel`, `--glass-tint` have **no
token definitions at all** — only `--glass-light-radius` is tokenized
(`tokens.css:157`). The real material vocabulary lives in a 40-line docblock and
is set by hand-typed magic numbers at three call sites. Nothing validates a
typo: `--glass-fresnal` silently no-ops.

### 3.5 🟠 The fallback is the forbidden material

`.glass-surface--fallback` (`GlassSurface.vue:572-594`) is
`rgba(255,255,255,.25)` + `blur(12px)` + a uniform 1px white border, and it
ignores `--glass-density`/`--glass-tint` entirely — so the three surfaces that
carefully differ in density render **identically** in Safari and Firefox. Every
non-Chromium user sees generic glassmorphism.

### 3.6 🟠 `yChannel` likely reads an unencoded channel

Default `yChannel: 'G'` (`GlassSurface.vue:104`), but the map paints X into R
and Y into B; nothing writes a Y gradient into G. Outside the blurred core
rect G ≈ 0, which `feDisplacementMap` reads as a *constant* offset — roughly a
uniform 90px vertical pull at the rim rather than a position-varying bend.
**This is inference from reading the generator, not a rendered comparison**, and
it may well be the empirically-tuned look shipped since Phase 9. Unresolved
either way.

### 3.7 🟡 Motion vocabulary contains constitution traps

- `--ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1)` (`tokens.css:197`) — an
  overshooting curve, used by `.app-scale-enter-active` (`motion.css:175`).
  No glass surface currently uses it; if one ever does, it violates "nothing
  springs, bounces, or overshoots."
- `@keyframes app-float` is commented *"Soft vertical drift for floating glass
  cards"* (`motion.css:71`) — recommending, in the shared vocabulary, the exact
  pattern the constitution forbids ("constant floating / hover-bobbing"). It is
  used 10+ times in `ProductPresentation.vue`, which carries no `GlassSurface`,
  so it is a documented trap rather than a live violation.
- `--motion-scale-hover: 1.02` / `--motion-scale-press: 0.98` are global;
  `glass.css:173-175` correctly overrides press for on-glass buttons, but the
  hover-scale token remains available to future glass work.

### 3.8 🟡 Capability detection is per-instance and UA-gated

Each surface runs `supportsSVGFilters()` on mount (`GlassSurface.vue:158-171`) —
three surfaces, three probes, three independent verdicts, no global tier, no way
to say "low-end device, everything goes flat." The UA check is honestly
commented and load-bearing (those engines parse `backdrop-filter: url()` but
render nothing), but it means the "never UA-gate" rule is not actually
satisfied, and it will mis-classify Safari the day WebKit ships support.

### 3.9 🟡 The reduced-motion override is a blunt instrument

`motion.css:207-215` nukes *all* animation and transition durations to 0.01ms
with `!important` on `*`. Effective, but it also flattens legitimate
non-vestibular transitions (color, opacity) that reduced-motion users generally
still want, and it cannot be opted out of locally.

---

## 4. Recommended improvements

Framed as outcomes, not implementations. None of this is authorized work.

### R1 — Retire the second glass material
Delete the `--glass-bg/border/blur/highlight` family or redefine it in terms of
the optical dials, then migrate `AppCard variant="glass"`, `element-theme.css`,
`WelcomeView`, and the two strays inside `LoginView`. Until this lands, the app
has two contradictory answers to "what does glass look like here," and the
forbidden one wins on reach. **Largest single closeness gain available.**

### R2 — Make the budget test measure the right thing
Have it fail on *unsanctioned glassmorphism* (`backdrop-filter: blur()` outside
the primitive) rather than only on extra `GlassSurface` instances. Correct the
misleading second assertion. A guard that rations the good material while
ignoring the bad one is worse than no guard.

### R3 — Promote the optical dials to real tokens
`--glass-density/depth/fresnel/tint` become tokens with named presets. This is
also where Apple's variant system lands naturally: define **Regular** and
**Clear** as token bundles instead of three hand-typed numbers. Add the typed
`GlassVars` helper so a typo is a compile error. Wire `--radius-glass` up or
delete it.

### R4 — Give the material a backdrop contract
Implement `--glass-backdrop: dark | light` as a *stage-declared* token
(analysis §7.5) remapping density and rim polarity. Do **not** sample backdrop
luminance — the view knows whether it is bright; let it say so. This is the
minimum viable adaptivity, and it is what unblocks glass beyond the three
hand-placed surfaces.

### R5 — Fix the performance defect and the fallback
Debounce map regeneration (~120ms trailing, last value held). Rebuild the
Safari/Firefox fallback from the same density/tint dials so the three surfaces
read as one material at three densities. Both are pure correctness with no
visual change on Chromium.

### R6 — Complete the interaction loop
Press and focus should both brighten the rim through the existing gated
variables. The mechanism is already built; nothing consumes it. Cheapest
meaningful step toward Apple's tactile quality.

### R7 — Re-decide morphing deliberately
Put the §2.2 conflation to an explicit decision: hover-follow deformation stays
rejected forever; *transition-time* morphing (a menu emerging from its button,
the dock contracting on scroll) gets evaluated on its own merits. If the answer
is still no, record *that* reasoning separately so it stops being inherited from
a rejection of a different feature. If yes, it is the single biggest step toward
"Liquid" rather than "optical" glass — and it is a design-system decision, not
an implementation task.

### R8 — Handle `prefers-reduced-transparency`
Fold it into a single boot-resolved quality tier alongside SVG support, reduced
motion, coarse pointer, and device capability. One decision applied everywhere,
exposed as `data-glass-tier` on `<html>`.

### R9 — Defuse the motion traps
Re-comment `app-float` so it stops recommending itself for glass cards; document
that `--ease-spring` is forbidden on glass. Cheap, prevents a future violation.

### R10 — Resolve `yChannel` by eye
Render `G` vs `B` side by side and decide. If B is better it is a one-word fix
that improves corner refraction; if G is the shipped look, comment the
deliberate bias so it is never "fixed" by accident. Blocking for any map-quality
work.

---

## 5. Priority order

Ranked by *closeness gained per unit of risk*, not by effort.

| # | Item | Why this rank | Risk |
|---|---|---|---|
| **P0** | **R1** — retire the second glass material | The app currently contradicts itself in public. Every other improvement is decoration while `variant="glass"` renders white glassmorphism in the workspace. | Medium — touches shared components |
| **P0** | **R5** — debounce + fallback | The only outright defect, plus the only fix that improves the material for every non-Chromium user. No visual change on Chromium. | Low |
| **P1** | **R2** — fix the budget test | Without it, R1 regresses the moment someone adds a blurred card. Guard first, migrate second. | Low |
| **P1** | **R3** — dials become tokens (Regular/Clear) | Prerequisite for R4 and for any fourth surface. Turns tribal knowledge into a system. | Low |
| **P2** | **R4** — backdrop contract | The real unlock for adaptivity and reach. Depends on R3. | Medium |
| **P2** | **R6** — press/focus illumination | Highest tactile return for the smallest change; mechanism already exists. | Low |
| **P3** | **R10** — resolve `yChannel` | Cheap, blocking for map-quality work, no product risk. | None |
| **P3** | **R8** — reduced-transparency tier | Accessibility gap Apple treats as first-class. | Low |
| **P3** | **R9** — defuse motion traps | Comment-level prevention. | None |
| **P4** | **R7** — re-decide morphing | Highest ceiling, but it is a **constitution amendment**, not a task. Should be decided deliberately and unhurried, after the system beneath it is coherent. | High (philosophical) |

### Relationship to the proposed Phase 18 plan

`docs/phase18-glass-upgrade-plan.md` optimizes the *optics* of the primitive —
map quality, edge mask, corner awareness. This audit finds that the primitive is
already the strongest part of the system, and that **P0/P1 above are not in that
plan at all**. Only R5 and R10 overlap with it (its Steps 1, 4, 0).

Recommendation: **re-sequence.** Land material coherence (R1, R2, R3) before
optical refinement (edge mask, SDF map). Sharpening the refraction of three
surfaces matters less than the fact that the rest of the app is running a
material the constitution forbids.

---

*Audit complete. No source files were modified.*
