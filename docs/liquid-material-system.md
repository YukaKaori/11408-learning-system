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
| `--material-density-regular` | `.62` | Regular, carries legibility alone |

Density is how much smoke is in the slab. **Legibility is bought by darkening,
never by whitening** — there is no white-fill dial and there will not be one.

### Body tint — the colour of the smoke

| Token | Light | Dark |
|---|---|---|
| `--material-tint-chrome` | `rgb(24 26 36)` | `rgb(13 15 24)` |
| `--material-tint-hero` | `rgb(17 18 24)` | `rgb(7 9 15)` |
| `--material-tint-floating` | `rgb(12 13 19)` | `rgb(7 8 14)` |

Resolved per theme with `light-dark()`, which is safe here because `tokens.css`
declares `color-scheme` in both `:root` and `html.dark`.

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

## 5. The presets

Declared in `src/styles/glass.css`. A surface says **what it is** and inherits
the whole optical prescription; it never hand-types dials.

| Preset | Variant | Density | Tint | Edge | Inner | The surface |
|---|---|---|---|---|---|---|
| `data-material="chrome"` | Clear | `.16` | chrome | `.85` | `.65` | `GlassDock` — permanent navigation, the lowest optical drama in the system |
| `data-material="hero"` | Clear | `.34` | hero | `.68` | `.55` | `LoginView` card — the flagship slab, denser because the lotus artwork behind it is brighter than the dock's backdrop |
| `data-material="floating"` | Regular | `.62` | floating | `.7` | `.5` | `NoteSelectionToolbar` — transient, over arbitrary note content |

The hero's `.34` is a **documented stage override inside the Clear band**, not a
magic number and not a third variant.

`src/styles/__tests__/materialTokens.spec.ts` asserts each preset resolves to
exactly what its surface ships today, so tagging the surfaces cannot move a
pixel. Retuning any token fails that spec and names the surface that would have
moved.

## 6. Theme behaviour

- The **body tint** resolves per theme via `light-dark()`.
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
2. Does it need the *primitive*? The displacement budget is **5** logical
   surfaces (the registry in `experience/__tests__/materialSurfaces.ts`, checked
   by `glassBudget.spec.ts`). A sixth is a deliberate renegotiation with a
   written justification, not an implementation detail — the way the app dock
   (B5) and the landing dock's selection lens (Amendment A1, 2026-10-02) were.
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

Phase 17.2 delivers material *coherence*. What it deliberately does not do:

| | Item | Why deferred |
|---|---|---|
| **P1** | **Adaptivity** — `--material-backdrop: dark \| light` as a *stage-declared* token remapping density and rim polarity (audit R4) | This is the real unlock for reach: today the material can only be placed on stages whose backdrop the team already controls, which is why it reached three surfaces in eight phases. Depends on this token layer. Do **not** sample backdrop luminance — the view knows whether it is bright; let it say so. |
| **P2** | **Accessibility** — `prefers-reduced-transparency` folded into one boot-resolved quality tier alongside SVG support, reduced motion, coarse pointer; exposed as `data-glass-tier` on `<html>` (audit R8) | Apple treats Reduce Transparency as a first-class material state; we handle it nowhere. One decision applied everywhere, rather than three independent per-instance probes. |
| **P2** | **Press/focus illumination** (audit R6) — the rim brightens under a press, focus lifts an edge | The gated variables already exist and nothing drives them. Cheapest real gain in tactile quality. |
| **P3** | **Transition morphing** (audit R7) — a menu emerging from its button, the dock contracting on scroll | A **constitution amendment**, not a task. Note the distinction the audit drew: hover-follow deformation stays rejected forever; *transition-time* morphing was rejected only by association with it and deserves its own decision. |
| — | Map quality — SDF profile, explicit edge mask, `yChannel` resolution | Owned by `docs/phase18-glass-upgrade-plan.md`. Sharpening three surfaces mattered less than the rest of the app running a forbidden material, which is why this phase went first. |
| — | Debounced map regeneration (audit §3.3) | A genuine defect and a one-line fix, but a *performance* change inside the primitive. Ships on its own so a regression is attributable. |
