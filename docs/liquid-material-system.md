# Liquid Material System

**Status:** shipped (Phase 17.2 Phase B, 2026-07-28) · **Scope:** token layer only
**Authority chain:** `.claude/skills/liquid-material/references/constitution.md`
(constitution) → the rest of `.claude/skills/liquid-material/` (the implementation
manual: `materials.md`, `components.md`, `implementation.md`, `color.md`) → this
document (the shipped token vocabulary). The two former skills
(`optical-glass-design-system`, `ai-liquid-material`) were merged into that one
skill on 2026-07-29; the authority order is unchanged.
**Related:** `docs/liquid-glass-apple-audit.md` (the findings this answers),
`docs/liquid-material-migration.md` (the migration record).

This document describes **the one material**. When it disagrees with
`docs/design-system.md` § "Glass tokens", this document wins — that section
describes the legacy family being retired.

---

## 1. The rule, in one line

**Chrome is glass. Content is solid.**

Glass marks *elevated, transient or premium* layers — the things floating above
the work. It is never the work itself. Dense reading surfaces — lesson bodies,
tables, editors, long forms, dashboards, the workspace canvas — stay solid, and
no amount of visual ambition changes that.

## 2. Architecture — two layers, on purpose

```
  tokens.css        --material-*        the canonical vocabulary (what the material IS)
       │
       ▼
  glass.css         [data-material]     named presets (which slab this surface IS)
       │
       ▼
  GlassSurface.vue  --glass-*           the runtime contract (how it is PAINTED)
```

The split exists so the primitive's API never moves. `GlassSurface` has read the
`--glass-*` contract since Phase 9 and continues to; Phase 17.2 changed only
what that contract is *set from* — three sets of hand-typed magic numbers became
one token layer. Renaming the runtime contract would have meant rewriting the
primitive's optical CSS, which is precisely the redesign this phase forbids.

This mirrors how the rest of the design system already works: `--radius-card`
is a semantic alias over `--radius-lg`, not a parallel mechanism.

## 3. The canonical tokens

All in `src/styles/tokens.css`.

### Body — the legibility dial

| Token | Value | Meaning |
|---|---|---|
| `--material-density-clear` | `.16` | Clear, over a stage that dims for it |
| `--material-density-clear-stage` | `.34` | Clear, stage-tuned for brighter artwork |
| `--material-density-regular` | `.78` | Regular, carries legibility alone (`.62` until 2026-10-23: a white heading under the note toolbar in the dark theme brought its labels to 3.65 : 1) |

Density is how much smoke is in the slab. **Legibility is bought by darkening,
never by whitening** — there is no white-fill dial and there will not be one.

### Body tint — the colour of the smoke

| Token | Light | Dark |
|---|---|---|
| `--material-tint-chrome` | `rgb(24 26 36)` | `rgb(13 15 24)` |
| `--material-tint-hero` | `rgb(17 18 24)` | `rgb(7 9 15)` |
| `--material-tint-floating` | `rgb(12 13 19)` | `rgb(7 8 14)` |

Resolved with `light-dark()`, which is safe here because `tokens.css` declares
`color-scheme` in both `:root` and `html.dark`. In development (native
`light-dark()`) the pair resolves where the slab paints, so it follows the
stage's declared backdrop; in a production build the CSS minifier rewrites it as
a variable pair resolved where the token is declared — the root — so the tints
follow the **theme** there (a few levels of near-black apart; the slab's own
pairs follow the backdrop in both, see §6).

### Thickness and edge

| Token | Value | Meaning |
|---|---|---|
| `--material-depth` | `1` | Presence of the thickness cues — double rim, back-face reflection, internal scatter |
| `--material-fresnel` | `1` | Presence of the directional rim arc |
| `--material-edge-glow-{chrome,hero,floating}` | `.85` / `.68` / `.7` | Permanent edge highlight, per rank |
| `--material-inner-glow-{chrome,hero,floating}` | `.65` / `.55` / `.5` | Permanent interior glow, per rank |

### Refraction

| Token | Value | Meaning |
|---|---|---|
| `--glass-light-radius` | `360px` | Base radius of the travelling light — the one member of the runtime contract with a configurable base |

*Correction 2026-09-16:* an earlier revision of this table documented a
`--material-light-radius` token with `--glass-light-radius` as its alias. That
token was never defined in `tokens.css`; the runtime name is the only one.
See `docs/liquid-material-global-reassessment.md` (N4).

## 4. Clear and Regular — Apple's two variants

Apple's rule is about legibility, not looks:

- **Clear** may be used **only where the stage supplies its own dimming** — a
  shroud, a dark stage, a controlled underlight — and the backdrop is *meant*
  to be seen through the surface.
- **Regular** floats over arbitrary, uncontrolled content and must carry
  legibility in its own neutral-density body.

Getting this backwards is the classic failure: Clear over an uncontrolled
backdrop is unreadable, and Regular over a dark stage is a smudge.

Over a stage that declares a **light** backdrop the Clear ranks are made legal
by their polarity rather than by dimming: ink labels and ink rims on a clear
body (the dock since B5.3, the sign-in slab since the daylight room of
2026-10-21). Dimming a bright stage would only grey it; whitening is forbidden.

## 5. The presets

Declared in `src/styles/glass.css`. A surface says **what it is** and inherits
the whole optical prescription; it never hand-types dials.

| Preset | Variant | Density | Tint | Edge | Inner | The surface |
|---|---|---|---|---|---|---|
| `data-material="chrome"` | Clear | `.16` | chrome | `.85` | `.65` | `GlassDock` — permanent navigation, the lowest optical drama in the system |
| `data-material="hero"` | Clear | `.34` | hero | `.68` | `.55` | `LoginView` card — the flagship slab, denser because the lotus artwork behind it was brighter than the dock's backdrop |
| `data-material="floating"` | Regular | `.78` | floating | `.7` | `.5` | `NoteSelectionToolbar` and `WikiLinkSuggestions` — transient, over arbitrary note content; refracts in one pass on every backdrop (2026-10-23) |

The hero's `.34` is a **documented stage override inside the Clear band**, not a
magic number and not a third variant. Over a declared light backdrop each rank
answers differently (`adaptive-material.md` §6): the chrome dock keeps `.16`
with ink rims, the floating rank rises to `.86`, and — since the sign-in room
became a daylight room on 2026-10-21 — the hero drops to the Clear `.16` in the
same ink polarity as the dock beside it.

`src/styles/__tests__/materialTokens.spec.ts` asserts each preset resolves to
exactly what its surface ships today, so tagging the surfaces cannot move a
pixel. Retuning any token fails that spec and names the surface that would have
moved.

## 6. Theme behaviour

- The **body tint** resolves via `light-dark()` (§3: by the backdrop in
  development, by the theme in a production build).
- The slab's **own `light-dark()` pairs** — its frost, rims, depth and Fresnel
  tints — follow the stage's **declared backdrop**, not the theme
  (`data-material-backdrop`, B4): the primitive takes its `color-scheme` from
  `--material-backdrop`, and since 2026-10-23 it also writes each declared value as
  a literal `color-scheme` (`[data-material-backdrop='light'] .glass-surface`),
  because the production minifier switches its `light-dark()` fallback only beside
  a literal. Before that fix, a built app drew the dark theme's pairs on the light
  login stage (a black frost and violet rims; the sign-in title 5.4 : 1 instead
  of 7.0 on a 320px phone).
- The **density and geometry dials are deliberately theme-invariant.** The house
  material is smoked in *both* themes — never white in light mode — which is
  exactly what lets on-glass text use one fixed dusk palette (`--on-glass-text*`
  in `glass.css`). Per-theme densities would be a retune, and would reintroduce
  the light-mode-white problem the constitution forbids.

## 7. What is deliberately **not** tokenized

A token nobody consumes is the `--radius-glass` mistake — defined in Phase 3,
consumed by nobody for eight phases, deleted in Phase 17.2. So:

| Property | Why not a token |
|---|---|
| `--glass-light-x`, `-y`, `-angle`, `-strength`, `--glass-proximity` | **Per-frame state**, written by `useGlassSpotlight` via `setProperty`. They must default to `0`, or reduced-motion and coarse-pointer users lose the zero-by-construction frozen light. Presetting them would be an accessibility regression. |
| `--glass-frost`, `--glass-saturation` | Written by `GlassSurface` from its `backgroundOpacity` / `saturation` **props**. An ambient token would be silently overwritten. |
| `--glass-flow-opacity` | Belongs to the `surfaceFlow` layer, which no shipped surface enables. Tokenize it when a surface turns it on. |

The presets are asserted to declare **exactly** the six ambient dials and no
per-frame state.

## 8. Rules for new work

**Before adding any translucent surface:**

1. Does it qualify — elevated, transient or premium? If it is a reading surface,
   a form, an editor, a dashboard or a canvas, **the answer is no**. Use
   `--color-surface` and the elevation scale.
2. Does it need the *primitive*? The displacement budget is **6** logical
   surfaces (the registry in `experience/__tests__/materialSurfaces.ts`, checked
   by `glassBudget.spec.ts`; fork count 4, at most 3 on one screen). A seventh is
   a deliberate renegotiation with a written justification, not an
   implementation detail — the way the app dock (B5), the landing dock's
   selection lens (Amendment A1, 2026-10-02) and the note editor's `[[` list
   (2026-10-21) were. (This line read "5" until 2026-10-23.)
3. If it sits **on** an existing slab, it needs no primitive at all — use
   `.glass-material` and extend `glass.css` if a control skin is missing.

**Never:**

- Invent an rgba glass background, a blur radius, a border opacity or a
  highlight colour in a component. `glassBudget.spec.ts` fails on
  `backdrop-filter` outside the primitive, on the retired token family, and on
  hand-rolled copies of the facet vocabulary.
- Nest glass inside glass — double refraction reads as a rendering error. (The
  A1 selection lens overlaps the dock as a *sibling*, never inside it, and bends
  at a third of the bar's strength for exactly this reason.)
- Put two hero slabs in one view.

## 9. Roadmap

> **2026-09-16:** P1, P2 and the map-regeneration item below now have owning
> phases (B4, B1, B1) in `docs/liquid-material-global-reassessment.md` §20;
> the skill records each as *Contract (Bn)*. The table is kept as the
> historical record of the deferral.
>
> **2026-09-17:** P1's declaration shipped in B4. `data-material-backdrop` on
> the stage feeds `--material-backdrop`, which becomes the primitive's
> `color-scheme`. The Login stage declares per gallery and `AppLayout` declares
> from the theme. The density/rim remap for light backdrops was moved to B5
> (decision L-A).
>
> **2026-10-17:** P1 is complete. B5.3 shipped the light-backdrop optics,
> measured against the shell's real content: the clear dock keeps its density
> and flips its rims to ink (`--glass-rim-polarity`), the Regular toolbar's body
> rises to .86, tier A refracts in one pass over a light page (the per-channel
> split drew saturated lines along the rim there), and tier C keeps the dusk
> labels. B5.2 shipped the scroll edge under the app dock (`useScrollEdge` on
> `.content`). Every glass label in the shell now measures ≥ 4.5:1 in both themes
> and all three tiers. Details: `.claude/skills/liquid-material/references/adaptive-material.md`
> §6 and `scroll-edge.md` §7.
>
> **2026-10-21:** the sign-in room became a daylight room (the supplied
> photograph), so the hero gained its light-backdrop answer: the Clear `.16`, ink
> rims, the ink palette and ink-polarity control skins; the ink's idle step went
> .7 → .8 with a tight near halo for dock labels over a picture. Forced colours
> joined tier C (opaque system palette, a real edge, `Highlight` for the current
> item). The note editor's `[[` link list became the first desktop glass beyond
> the toolbar (budget 5 → 6), and both editor surfaces now play their entrance on
> the slab — on the wrapper it had cut the toolbar off from its backdrop since
> Phase 16. Record: `docs/liquid-material-global-reassessment.md` §25.
>
> **2026-10-23 (committed 2026-10-27, branch `welcome-room-crown-verification`):** the welcome hero left the
> dusk lotus for the sign-in room's daylight — environment only, the route mounts
> no glass — and phone-shaped stages wear the picture as a crown band; the
> sign-in room shares its free height 2 : 3. The Regular rank's body went
> .62 → .78 and it refracts in one pass on every backdrop; the shell's scroll
> container drops its mask while a slab is mounted. On smoke a brand fill takes
> the anchor's steps and every slab declares its own keyboard ring (both clear
> slabs over a light stage: the rim ink). A production build now honours the declared
> backdrop (a literal `color-scheme` per value — the minifier's `light-dark()`
> fallback had followed the theme). The sign-in error line's ink went one step
> deeper. No surface added (budget 6). Record and measurements:
> `docs/liquid-material-global-reassessment.md` §26.
> **Verified 2026-10-27** on production builds, in Chrome only: the unit suite
> (463), type-check, lint and build, 564 interaction checks and the contrast
> tables of §26.4. **Open, all pre-existing:** the landing dock's current label on
> upright tablets (3.4–4.3 : 1 bare), the app-wide dark-theme primary button
> (3.27 : 1), and in tier C the dock's focus rings (1.3–3.1) and, at desktop
> widths, three of the slab's (2.6–3.0) and the sign-in error line (4.2–4.3).
> **Not tested:** Gecko and WebKit, real Windows High Contrast, physical devices.

Phase 17.2 delivers material *coherence*. What it deliberately does not do:

| | Item | Why deferred |
|---|---|---|
| **P1** | **Adaptivity** — `--material-backdrop: dark \| light` as a *stage-declared* token remapping density and rim polarity (audit R4) | This is the real unlock for reach: today the material can only be placed on stages whose backdrop the team already controls, which is why it reached three surfaces in eight phases. Depends on this token layer. Do **not** sample backdrop luminance — the view knows whether it is bright; let it say so. |
| **P2** | **Accessibility** — `prefers-reduced-transparency` folded into one boot-resolved quality tier alongside SVG support, reduced motion, coarse pointer; exposed as `data-glass-tier` on `<html>` (audit R8) | Apple treats Reduce Transparency as a first-class material state; we handle it nowhere. One decision applied everywhere, rather than three independent per-instance probes. |
| **P2** | **Press/focus illumination** (audit R6) — the rim brightens under a press, focus lifts an edge | The gated variables already exist and nothing drives them. Cheapest real gain in tactile quality. |
| **P3** | **Transition morphing** (audit R7) — a menu emerging from its button, the dock contracting on scroll | A **constitution amendment**, not a task. Note the distinction the audit drew: hover-follow deformation stays rejected forever; *transition-time* morphing was rejected only by association with it and deserves its own decision. |
| — | Map quality — SDF profile, explicit edge mask, `yChannel` resolution | Owned by `docs/phase18-glass-upgrade-plan.md`. Sharpening three surfaces mattered less than the rest of the app running a forbidden material, which is why this phase went first. |
| — | Debounced map regeneration (audit §3.3) | A genuine defect and a one-line fix, but a *performance* change inside the primitive. Ships on its own so a regression is attributable. |
