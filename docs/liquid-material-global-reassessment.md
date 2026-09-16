# Liquid Material — Global Reassessment (Phase A) and Skill Consolidation (Phase B0)

**Status:** Phase A audit **complete**, Phase B0 skill consolidation **landed** (2026-09-16),
**Phase B1 material correctness** and **Phase B2 navigation physics** committed (`e20595d`,
`5fb8b32`), **Phase B3 Login recomposition implemented** (2026-09-16, uncommitted — see §20
row B3). Phases B4–B6 wait for explicit approval and the decisions in §23 (R, T and Y were
exercised by B1; W and V by B3).
**Supersedes:** the 2026-09-14 draft of this document and
`docs/authenticated-liquid-material-rollout.md` (2026-08-01, kept as history).
**Authority:** `.claude/skills/liquid-material/` — `SKILL.md`, `references/constitution.md`
and the eleven references (ten prior + `environment.md`, added in B0). On conflict the skill
wins and this document is corrected.
**Read against:** `docs/liquid-material-system.md`, `docs/liquid-glass-apple-audit.md`,
`docs/liquid-glass-analysis.md`, `docs/phase17-handoff.md`, `docs/phase18-plan.md`,
`docs/roadmap.md`, `docs/authenticated-liquid-material-rollout.md`.
**Baseline verified this session:** `glassBudget.spec.ts` (7) + `materialTokens.spec.ts` (9)
green on the working tree at `710539c` (16/16, 579 ms); Chrome and Edge installed, bundled
Chromium 1228 in the Playwright cache, **no Firefox, no WebKit** on this machine.

---

## 1. Executive summary

The primitive is right and the composition around it is wrong. `GlassSurface.vue` performs
real displacement (three `feDisplacementMap` passes, per-channel dispersion, screen blend),
computes a real Fresnel bearing, layers depth instead of shadow, and gates every optical
layer behind an inert-by-default custom property. Nothing in it needs replacing; this
document proposes no second primitive, no new token family, no WebGL, and no change to the
three shipped preset values.

Nine findings drive everything below. H1–H5 restate the 2026-09-14 audit and were
re-verified against source this session; N1–N4 are new.

| # | Finding | Evidence | Consequence |
|---|---|---|---|
| **H1** | The login "reveal" is a single spotlight, not a wake. Radius = `--glass-light-radius × strength × 1.6` = **576 px** at full strength, cut into a `rgba(0,0,0,.95)` shroud. At rest the stage is a lotus object on a black void. | `LoginView.vue:160-165, 466-473` | Login is recomposed as **wallpaper → atmosphere → reveal wake → glass chrome → content** (§10). |
| **H2** | The authenticated shell has **no chrome layer**. Sidebar and header are flex siblings that *displace* `main.content`; nothing floats over anything. | `AppLayout.vue:13-29, 45-63` | Desktop rail stays solid; the honest chrome is a **floating bottom dock on mobile** (§11, §12). |
| **H3** | Outside Chromium the product ships the forbidden material: white fill + `blur(12px)` + uniform 1 px white border + blue halo, ignoring every preset dial. `chrome`/`hero`/`floating` are indistinguishable there. | `GlassSurface.vue:581-629` | A **three-tier material** with engine-level detection is B1, step one (§8). |
| **H4** | Navigation state is colour, not a physical indicator: the dock's active item is brighter text + bloom, the rail's is a brand-soft fill + glow. Nothing travels. | `GlassDock.vue:177-182`, `AppSidebar.vue:235-240` | A measured, first-paint-correct **indicator light** (§12). |
| **H5** | Adaptivity was deferred as expensive; in the shell it is a declaration. The theme is already on `<html>` (`html.dark`). | `stores/app.ts:85-87`; `tokens.css:174-176` already flip tint via `light-dark()` | `--material-backdrop` becomes a **stage declaration** (§9, B4). |
| **N1** | **The guard cannot see the proposed dock.** `glassBudget.spec.ts` counts *files containing `<GlassSurface`*. A generalized `GlassDock` mounted a second time in `AppLayout` adds **zero** to that count. The previous audit's "add an `ALLOWED` entry for `layouts/AppLayout.vue`" would have failed the very test it meant to extend. | `glassBudget.spec.ts:122-126` | The budget needs a **defined unit** (logical material surface) and a surface registry the guard reads (§7, F). |
| **N2** | `filter` is transitioned in **four** places, not three: the recessed card, both gallery transitions, and `ProductPresentation.vue:2200`; `motion.css:165` puts `filter` into the global `.app-fade` transition. Constitution §3 forbids animating `filter`. | `LoginView.vue:510, 645, 651`; `ProductPresentation.vue:2200`; `motion.css:161-166` | B1 removes all four; a guard forbids it (§20). |
| **N3** | **Concentricity arithmetic was over-stated.** The primitive's own content padding (`0.5rem`, `GlassSurface.vue:553`) participates in every nested inset. Dock: 30 px bar − 16 px inset = 14 px derived vs 22 px shipped. Toolbar: 16 − 16 = 0 → floor. Card: 28 − 40 < 0 → floor. | `GlassSurface.vue:547-557`, `GlassDock.vue:121-146`, `NoteSelectionToolbar.vue:398`, `LoginView.vue:513-516` | Decision R shrinks to a **dock-only pixel change** plus tokenization (§15). |
| **N4** | Doc drift: `docs/liquid-material-system.md` §3 documents `--material-light-radius` "with `--glass-light-radius` as an alias". `tokens.css:197` defines only `--glass-light-radius`; the alias does not exist. | `grep -rn material-light-radius` → docs only | Corrected in B0 (doc-only). |

Material budget: **3 logical surfaces today**; proposed **4** (the app dock), P19 palette
pre-approved as the 5th — under the unit defined in §7, pending decision **B**.

---

## 2. Current architecture

Three products share one codebase and speak three visual languages.

```
/login       black optical installation   GlassDock (chrome) + sign-in slab (hero), 576px spotlight
/welcome     white frosted bridge         GlassScene veil: rgba(252,250,249,.4) + blur(26px), 340px hole
/ (app)      flat frame-and-document      solid rail + solid header + scrolling main; 0 glass at rest
```

Layering, as it exists (`vue-patterns.md` §1, verified):

```
tokens.css        --material-*        vocabulary   (density .16/.34/.62, tints via light-dark(), depth 1, fresnel 1, glows)
glass.css         [data-material]     presets      (chrome | hero | floating → --glass-*), .glass-material skins
GlassSurface.vue  --glass-*           runtime      (6 layers; map → 3× displace → isolate → screen → blur)
useGlassSpotlight one loop / stage    scene        (writes x/y/radius/strength/proximity/angle)
call sites        material="…"        declaration  (3 sites)
```

The seam the user crosses: black installation → white frost → flat app. One product should
have one material language and one environment language.

## 3. Current material implementation

Verified in `GlassSurface.vue`:

| Aspect | Reality | Skill claim | Verdict |
|---|---|---|---|
| Refraction | `feImage` map → 3× `feDisplacementMap` (`scale ± offsets 0/10/20`) → `feColorMatrix` isolate → 2× `feBlend screen` → `feGaussianBlur` (`:229-285`) | matches `materials.md` §2/§4 | ✅ |
| Map | two linear-gradient plates (`difference`) + blurred bright inset rect; `computed` on `measured`; **rewritten on every `ResizeObserver` tick** (`:129-153, 182-189`) | "regen on mount + debounced resize" (`SKILL.md` §6) | **defect** — the toolbar's `width` transition (`NoteSelectionToolbar.vue:394-396`) fires it per frame |
| Detection | per-instance UA regex `/Safari/ && !/Chrome/`, `/Firefox/`, then a `style.backdropFilter` parse probe (`:166-174`) | "feature detection, not UA; known debt" (`constitution.md` §4.7) | debt, contained |
| Fallback | `.glass-surface--fallback`: white fill, `blur(12px) saturate(1.8) brightness(1.1)`, uniform white border, `0 8px 32px rgba(31,38,135,.2)` halo (`:581-629`) | "dignified frosted tier" (`materials.md` §8) | **the forbidden material, sanctioned by the skill** |
| Depth | double rim (`::before` inset 3 px, `translateY(1px)`), back-face bloom, scatter gated on `depth × strength × proximity` (`:387-450`) | matches `materials.md` §6 | ✅ |
| Fresnel | conic arc, `padding 1.5px`, `mask-composite: exclude`, `@supports not` guard (`:458-484`) | matches | ✅ |
| Flow | `surfaceFlow` prop, 34 s / 26 s transform-only loops (`:492-535`) | matches | ✅ (dock enables it) |
| Inert defaults | every gate `var(--x, 0)` except inner/edge glow (`.55`/`.5`) and flow (`.6`) | matches §7 | ✅ |
| Focus | `outline: 2px solid light-dark(#007aff, #0a84ff)` (`:631-634`) | "no literals" (`color.md` §5) | violation (literal) |
| `yChannel` default | `'G'`; the map's second plate is blue → `G` may read an unencoded channel | open (`implementation.md` §4) | unresolved, blocks map work |
| Layout neutrality | `position: relative`, size via props, `class`/`style` fall through | matches | ✅ |
| `defineExpose({ element })` | present (`:198`) | matches | ✅ |

Colour literals in material components (all small): `NoteSelectionToolbar.vue:447` (`rgba(12,11,18,.62)`),
`LoginView.vue:709` (colophon), `GlassDock.vue:159,181` (halo defaults), `GlassSurface.vue:632`
(focus hex), `glass.css` on-glass palette (accepted as the frozen dusk palette, `color.md` §1).

## 4. Current Login architecture

```
main.login-stage      black in both themes, overflow: clip, isolation
  .stage-artwork      lotus PNG, min(60vw, 900px), app-breathe 14s, edge-feathered mask
  .stage-shroud       rgba(0,0,0,.95); mask = card aperture (SVG, measured) ∩ revealMask (576px radial)
  GlassSurface hero   radius 28, frost .10, distortion −110, offsets 0/5/10
  ProductPresentation / SponsorPanel   full-screen gallery layers (z 40), solid by design
  .dock-anchor (z 50) .stage-underlight (3 drifting blobs, 36–58s) + GlassDock chrome (radius 30)
  .stage-colophon     literal rgba(228,226,240,.38)
```

One stage light (`useGlassSpotlight` with card + 5 facet selectors). Measured against the brief:

| Brief | Today |
|---|---|
| base wallpaper | none — an *object* on a void |
| secondary reactive layer | the shroud, opened by one radial gradient |
| pointer creates a localized transient reveal | one 576 px hole that follows the pointer and closes only when it leaves the stage (release k = .022 → ~2.5 s) |
| region expands, moves, decays | radius = strength × constant; no independent decay, no irregularity, perfect circle |
| stage alive at rest | lotus breathes; 95 % of the stage is inert black |
| touch / reduced motion | the shroud stays shut → card aperture only; correct in kind |
| glass chrome retained | dock + slab correct in material |

Two constitution violations live here (N2): `filter: blur()` transitions on the recessed card
and both gallery transitions. The bright-room flip (`.is-on-light`) is a private token
override on `.glass-material` (`LoginView.vue:629-635`) — the right idea, wrong owner.

## 5. Current authenticated shell

```
.layout (column, height 100%)
  AppHeader           ≤768 only, 56px, solid --color-surface, border-bottom, brand + menu
  .body (row)
    aside.sidebar-static  240 / 64px, solid, border-right, transition width — DISPLACES content
    AppDrawer           teleported ElDrawer, 272px, solid, over .el-overlay
    main.content        flex 1, overflow-y: auto — every route scrolls inside this box
```

Neither the rail nor the header occludes content it does not own (`navigation.md` §1 test 3
fails), so neither is honest glass. Twelve routes, zero material at rest, one transient
surface (`NoteSelectionToolbar`, `/notes`, while text is selected). Dialogs, drawers,
poppers, tooltips: solid since 17.2. The Phase 17.5 rollout plan's mobile *drawer* is glass
only while open — material on the least-seen surface in the product; superseded here.

## 6. Current navigation architecture

| Surface | Items | Active state | Geometry | Motion |
|---|---|---|---|---|
| `GlassDock` (landing) | 3 galleries, text-only `<button>`s, `aria-current` | `.is-active`: full dusk text + 18 px bloom (`--dock-halo-active`) | bar `min(100%, 720px)`, items 44 px / radius 22 | `color`/`text-shadow` 400 ms; press `translateY(.5px)` |
| `AppSidebar` (desktop) | 7 + 2 `RouterLink`s | `.router-link-active`: `--color-primary-soft` fill + `--shadow-glow-primary` | 240 / 64 px | `background-color`/`color` 120 ms |
| `AppDrawer` (mobile) | same sidebar | same | 272 px | ElDrawer |

There is no measured indicator anywhere; no element moves *through* the navigation space.
The rail's active fill is exactly the "brand-coloured fill … a random gradient by another
name" `navigation.md` §4 forbids — a known inconsistency between the skill and the shell.

## 7. Current material budget — and what a budget instance is

### 7.1 What ships

| # | Logical surface | Recipe | Preset | Host | On screen |
|---|---|---|---|---|---|
| 1 | landing dock | `GlassDock.vue` | chrome | `LoginView` | permanent on `/login` |
| 2 | sign-in slab | inline `<GlassSurface>` | hero | `LoginView` | permanent on `/login` (recessed in other galleries, still mounted) |
| 3 | note selection toolbar | inline `<GlassSurface>` | floating | `NotesView` | while a selection exists |

Concurrent maximum on one screen: **2**. Authenticated at rest: **0**.

### 7.2 What the guard actually measures (N1)

`glassBudget.spec.ts:122-126` asserts the sorted list of **files whose source contains
`<GlassSurface`** equals `ALLOWED` and has length 3. That measures *primitive call sites per
file*. It does **not** measure how many times a recipe component is mounted, how many
primitives are simultaneously in the DOM, or logical surfaces. Consequences:

- Mounting `<GlassDock>` a second time (the proposed app dock) is **invisible** to the guard.
- Two `<GlassSurface>` in one file count as one.
- A `v-for` over `<GlassSurface>` counts as one.

The guard is a **fork guard** (nobody instantiates the primitive outside the recipes). It is
not, and never was, a budget of surfaces. This must be stated, and a second registry added.

### 7.3 The definition (adopted into the skill, `components.md` §1)

**One budget instance = one logical material surface: a distinct role, identified by
(recipe, host container), that mounts the refracting primitive, whether or not it is
currently visible.**

| Counts as an instance | Does **not** count |
|---|---|
| each (recipe, host) pair — the landing dock and the app dock are **two** instances of one recipe | environment layers: wallpaper, atmosphere/shroud, ambient underlight, the reveal wake, `GlassScene`'s veil |
| a shared material container that composes several controls on **one** slab — one instance | the navigation indicator (a light on an existing slab) |
| a surface mounted only while a state holds (toolbar, palette) — still one instance | `.glass-material` control skins on an existing slab |
| | adaptive states, tier variants, backdrop remaps, per-theme values |
| | the same instance re-rendered under `v-if`/`v-show` |

Two further numbers travel with the budget:

- **Concurrency ceiling** — the maximum number of primitives simultaneously mounted on any
  screen. Today **2**. Measured at the real surface (`verify`), not by regex.
- **Fork count** — the number of files containing `<GlassSurface` (today 3). Must always be
  ≤ the budget and every entry must be a recipe or a sanctioned inline surface.

### 7.4 Baseline and proposal

Baseline **3**. Proposed **4** (app dock, `chrome`, host `AppLayout`, mobile only).
Explicitly **not** counted: the login wake, wallpaper and atmosphere (environment), the
indicator (light), the light-backdrop remap (adaptive state). The roadmap's running budget
(P19 → 4, P20 → 5, P22 → 6) shifts by one *if* B is approved; the roadmap is not edited
until then.

## 8. Browser capability audit

### 8.1 Capability matrix — engine, never brand

| Capability | Blink (Chrome, Edge, Brave, Arc, Opera, Vivaldi) | WebKit | Gecko |
|---|---|---|---|
| `backdrop-filter: url(#svg)` — refraction | ✅ | parses, **renders nothing** (recorded finding, `liquid-glass-analysis.md` §6.5; to be re-verified in B1 under F) | same |
| `backdrop-filter: blur()/saturate()` | ✅ | ✅ | ✅ (103+) |
| `color-mix()`, `light-dark()`, custom properties | ✅ | ✅ | ✅ |
| `conic-gradient` + `mask-composite: exclude` | ✅ | ✅ (verify) | ✅ |
| `mask-image` gradients (scroll edge, shroud) | ✅ | ✅ | ✅ |
| Canvas 2D `destination-out` (the wake) | ✅ | ✅ | ✅ |
| `navigator.userAgentData.brands` | ✅ (a literal `"Chromium"` brand on every Blink browser) | ✗ | ✗ |
| `prefers-reduced-transparency` | ✅ (118+) | ✅ | ✅ (133+) |

Exactly **one of six layers** is lost, in two engines. Today's fallback throws away all six.

### 8.2 The tier model

```
capability probes  →  tier (one decision, at boot, on <html>)  →  material implementation
```

| Tier | `data-glass-tier` | Condition | Material |
|---|---|---|---|
| **A** | `refract` | SVG-in-backdrop renders **and** engine is Blink | refraction + all layers (unchanged, byte-identical) |
| **B** | `diffuse` | `backdrop-filter: blur()` only | the **same slab, same preset dials, minus refraction**; a rim-masked diffusion band may stand in for edge-weighted bend (gated on measurement); ND body, double rim, back-face, Fresnel, edge glow survive verbatim |
| **C** | `dense` | no `backdrop-filter`, or `prefers-reduced-transparency`, or `prefers-contrast: more` | transmission down, density up toward opaque; every rim and depth cue kept — a designed state |

Tier B is authored by **deleting** the white fill, the uniform border and the halo and
letting the existing layers paint with `--glass-density`/`--glass-tint` reaching them. The
acceptance test is perceptual: at equal size, `chrome`, `hero`, `floating` must be visibly
different in tier B.

### 8.3 Detection contract

One resolver, called once from `main.ts` before mount, re-evaluated on media-query change:

1. `CSS.supports('backdrop-filter', 'url(#p)')` — necessary, not sufficient (all engines parse it).
2. Engine identity: `navigator.userAgentData?.brands.some(b => b.brand === 'Chromium')` — sufficient.
3. UA-string fallback **only** when `userAgentData` is absent, contained in the resolver, treated
   as the one explicit technical-debt exception.
4. `prefers-reduced-transparency` / `prefers-contrast` / `backdrop-filter` absence fold into the same
   decision → tier C.
5. Result written once as `data-glass-tier` on `<html>`; `GlassSurface` **reads** it (CSS selectors
   `[data-glass-tier='…'] .glass-surface`) and stops probing per instance.

A **brand guard** test asserts zero occurrences of `Safari|Firefox|Chrome|Edg|Gecko|WebKit`
in `src/` outside the resolver file. Edge cannot differ from Chrome because nothing can see
the difference. SSR/hydration: the tier is a root attribute, markup is identical in every
tier, the pre-hydration state is the inert baseline (`implementation.md` §13).

### 8.4 Verifiable on this machine

Chrome (`channel: 'chrome'`), Edge (`channel: 'msedge'`), bundled Chromium 1228. Firefox and
WebKit need `npx playwright install firefox webkit` (decision **F**). Safari on Apple hardware
is unavailable; WebKit results are reported as *engine verified, browser not verified*.

## 9. Environment architecture

**Environment is not Material.** The environment is everything the material sits *in front
of* and answers *to*; the material is the slab. The skill had no model for the first; the
new `references/environment.md` supplies it. Its structure:

```
E1  WALLPAPER          the stage's full-bleed image or field; cover-fit; may breathe
E2  ATMOSPHERE         the ND shroud (declared band), the veil — never a page-colour sheet, never white frost
E3  AMBIENT LIGHT      slow drifting pools (underlight); compositor-only; one family with the stage light
E4  REACTIVE LAYER     the reveal wake — pointer-driven, localized, transient, organic
E5  DECLARED BACKDROP  data-material-backdrop="dark|light" on the stage — the environmental contract the material reads
----------------------------------------------------------------- material boundary
M   GLASS CHROME       GlassSurface recipes (budgeted)
C   CONTENT            solid
```

Rules that fall out:

- Environment layers are **not budgeted** and **never** carry `backdrop-filter: url()`; the
  `GlassScene` veil's `backdrop-filter: blur()` is a legacy exception listed in the guard and
  retired under decision V.
- The environment owns exactly **one eased cursor** per stage. Today there are two composables
  (`useSpotlight` for the veil, `useGlassSpotlight` for glass) with two independent easings.
  Under the contract the wake subscribes to `useGlassSpotlight`'s `smoothedCursor`, never to
  raw pointer events.
- **Canvas 2D is permitted for E4 only**, under the spotlight-loop discipline (self-settling,
  capped, gated, never mounted on touch or reduced motion). Never for M.
- The declared backdrop (E5) is a **stage** property. In the authenticated shell its source is
  the theme; on the landing it is authored per gallery (the black stage is `dark` in both
  themes; the Product room is `light` in both). Theme ≠ backdrop in general.
- Mobile gets E1 + E2 + E3 (+E5). E4 is never mounted there.

## 10. Login interaction reassessment

### 10.1 The MiMo mechanism, read from source (2026-09-14 review, verdict unchanged)

`mimo.xiaomi.com/zh/mimocode` → iframe `/coder/index.html`: a painting as `background-image`,
a 2D `<canvas>` mask (`pointer-events: none`, hidden on touch), and on `(hover: hover)` only:
`mousemove` stamps "ink dots" every 12 px along the path; each dot grows 8 → `128 × (0.55..1)`
px over 520 ms (`easeOutCubic`), alpha `1 − t²`, edge radius × `(0.78 + Σ three seeded sine
wobbles)`; ≤160 living dots; the rAF loop stops when none live; the mask is painted in the
page colour and dots are `destination-out`. Its nav is `blur(12px) saturate(180%)` + a
translucent fill — glassmorphism.

**Keep:** *the reveal is a wake, not a spotlight* — locality from a small per-dot radius,
life from independent decay, organic edge from a seeded wobble not from blur, touch gets the
wallpaper not a dead mask, self-settling loop.
**Adapt:** our mask is an ND atmosphere, not a page-colour sheet; the dots open the
atmosphere, they do not paint white. Parameters retuned to our stage (§10.3).
**Reject:** its navigation material; the ink-brush metaphor as *style*; the painting asset.

### 10.2 Composition

```
1  WALLPAPER      full-bleed image the stage owns (decision W); object-fit: cover; app-breathe kept
2  ATMOSPHERE     the shroud retuned from .95 to a band where the wallpaper is faintly legible at rest
                  (target .80–.86, tuned by eye in both themes); the underlight blobs promoted from
                  behind the dock to the whole stage as slow ambient light
3  REVEAL WAKE    RevealField.vue + useRevealField.ts — one 2D canvas per stage, mask layer for the
                  atmosphere; REPLACES the 576 px revealMask. The card aperture stays (the slab must
                  always have something to refract).
4  GLASS CHROME   GlassDock (chrome) + indicator light; sign-in slab (hero); both unchanged in material
5  CONTENT        the form
```

### 10.3 Wake parameters (initial, to be tuned in B3)

| Parameter | Value | Why |
|---|---|---|
| stamp spacing | every ≥12 px of eased-cursor travel | locality; avoids a continuous tube |
| dot life | 600 ms, `easeOutCubic` growth, alpha `1 − t²` | transient; decays behind the pointer |
| dot radius | 10 → ~120 px × seed (0.55..1) | a *local* opening, one fifth of the old spotlight |
| edge | radius × `(0.78 + Σ3 seeded sines)` | irregular, not a circle, not blur |
| living dots | ≤ 64 | worst-case fill cost bounded |
| DPR | capped at 1.5 | canvas cost ∝ pixels |
| source cursor | `useGlassSpotlight.smoothedCursor` | one light, one easing |
| gates | `(hover: hover) and (pointer: fine)` **and not** reduced motion; otherwise **never mounted** | zero-by-construction |
| a11y | `aria-hidden`, `pointer-events: none` | decorative |
| composition | canvas paints alpha; applied as `mask-image` on the atmosphere layer, `mask-composite: intersect` with the card aperture (same idiom as today, `LoginView.vue:172-183`) | the shroud stays the shroud |

Position-driven vs time-driven (§14): the wake is a **hybrid** like the spotlight — the
pointer supplies positions, the decay is a clock. That is why it is gated on both.

### 10.4 The `/welcome` bridge

The veil is `rgba(252,250,249,.4)` + `blur(26px)` — perceptually the white-frosted sheet the
constitution forbids, and the first screen after a password. Decision **V**: (V-A,
recommended) plain sign-in lands on `/today` (`LoginView.vue:231`, one line; closes
`phase17-handoff.md` §5.1); `/welcome` stays a route and its veil is retuned to the login
atmosphere (same tokens, one environment family). (V-B) keep it as the landing, retune only
the veil. In both, the white sheet does not survive.

## 11. Authenticated Liquid Material reassessment

Every candidate runs the skill's procedure: four chrome tests (`navigation.md` §1) → Q1
glass at all / Q2 Clear or Regular / Q3 preset (`navigation.md` §5).

| Surface | Verdict | Reason |
|---|---|---|
| Desktop docked rail | **solid** | fails test 3 (displaces; nothing behind it); floating it leaves nothing to transmit or occludes line starts. Gains the indicator *mechanics* on solid tokens. |
| Mobile header | **solid** | fails test 3. Loses the brand name once the dock exists (branding belongs to content); keeps the menu control. |
| Mobile navigation | **floating bottom dock, `chrome`** — the one new surface | passes all four; persistent; the plan/note/calendar scroll under it; the same object as the landing dock. 5 destinations (Today, Subjects, Notes, Flashcards, Tutor) + **More** → the existing drawer (solid). |
| The "More" drawer | solid | two glass surfaces a summon apart are one badly-cut sheet (`components.md` §7). |
| `NoteSelectionToolbar` | **glass, rank unchanged** (`floating`) | gains tiers B/C, press/focus light, the literal fix, the map-regen fix. |
| Today (Line, PlanList, verbs, settle, Ledger, review stage) | solid | the work; Phase 17 gate stays green (0 `backdrop-filter`, 0 `[data-material]`). |
| Note editor, canvas head, rails, list, wiki popover | solid | document and document metadata. |
| Calendar (toolbar, grids, dialogs) | solid | densest data surface. |
| Subjects, Flashcards, Settings, Analytics, Profile | solid | content, forms, dataviz. |
| AI Tutor transcript and composer | solid | AI content is solid; authoring fails test 2. |
| AI "thinking" presence (P18) | **deferred; roadmap corrected** | there is no existing glass surface in the tutor; a strip would be a 5th instance. `phase18-plan.md` §14 already lands the AI light vocabulary on the toolbar. |
| EP dialogs / drawers / poppers / selects / tooltips | solid | unbounded family. |
| Toasts | deferred (P22) | whisper rank has no preset. |
| `DesignSystemView` | solid catalogue | must not mount a live instance. |
| Landing Product / Sponsor rooms | solid, unchanged | by design; the Product room gains the scroll edge under the dock. |

Q2 for the app dock: over uncontrolled, theme-dependent content Clear is only legal if the
stage dims for it — **the stage can declare its luminance** (§9 E5). Dark theme → the Clear
band by construction; light theme → the remapped, denser body with dark-ink rims. If the
light-backdrop remap cannot reach AA on labels in measurement, the escape hatch in order is:
deepen the remap → `floating`/Regular for the light backdrop only → solid. **Never whiten.**

Desktop chrome options: D-A rail solid + palette (P19) as the desktop summons — **adopt now**;
D-B summoned navigator — reject (the palette does it better); D-C floating rail — reject;
D-D floating page deck — defer (sits on plain padding at rest); D-E desktop bottom dock
replacing the rail — **decision D**, re-decided after B5 proves the recipe and P19 lands.

## 12. Navigation reassessment

### 12.1 The NavBar reference, read from source

Fetched this session from `lib/widgets/navbar_draggable_indicator.dart` and
`lib/providers/navbar_providers.dart`:

- Two-stage placement: `initPositions` divides the container evenly; `initMeasuredPositions`
  reads each `GlobalKey`'s `RenderBox` centre (`localToGlobal(...).dx + size.width / 2`).
  No `addPostFrameCallback` in the provider; measurement is invoked by the widget.
- Width: `adaptiveWidth = (baseSize * (3.5 / itemCount).clamp(1, 1.2)).w` — **source**. The
  README-level summary reports `(3 / itemCount).clamp(0.7, 1.0)`; the source wins and the
  discrepancy is recorded in `source-review.md`.
- Clamp: centre clamped to `[adaptiveWidth/2, screenWidth − adaptiveWidth/2]`.
- Drag: `onHorizontalDragUpdate` offsets continuously; `onHorizontalDragEnd` snaps to the
  nearest measured centre.
- Material: `LiquidGlassLayer(lightIntensity 1.5, thickness 20, blur 1)`,
  `LiquidStretch(stretch .7, interactionScale 1.05)`, `LiquidGlass(shape:
  LiquidRoundedSuperellipse(30))`, `GlassGlow` — renderer package, blur-based.

**Keep:** measured centres; first-paint correctness; width follows the marked item with a
floor; clamp inside the bar. **Adapt:** drag — permissible **for the indicator light only**
(indicator physics ≠ material physics), clamped, snap-on-release, no bar deformation;
deferred to a later phase (decision **I**), not B2. **Reject:** `LiquidStretch`,
`interactionScale` (geometry deformation), the blur renderer, the superellipse as a shape
change to our maps.

### 12.2 The indicator contract (adopted into `navigation.md` §4/§6, `interaction.md` §6)

- **A light, not a slab.** A pill of edge glow + a slightly denser body under the current
  item, painted by the dock's own layers from two variables (`--nav-indicator-x`,
  `--nav-indicator-w`). Never a second `GlassSurface`, never nested material, never a
  budget instance.
- **Measure, then place.** Item rects read after mount and after debounced resize; the first
  paint of a route shows the indicator under the right item. Pre-measure state is *no
  indicator*, never a wrong one (SSR/hydration-safe).
- **Travel:** one damped `translateX` + width interpolation on the light layer; heavier than
  app defaults; no overshoot; instant under reduced motion. Width interpolation is permitted
  *because the indicator is light*; the bar's geometry never changes.
- **Rank discipline:** current (indicator + weight + `aria-current`) > selection > focus (ring +
  lifted edge) > hover (pooled light) > press (settle + rim). Fully present with the light off.
- **Floor:** below ~44 px it stops reading as a location; labels ellipsize before the bar reflows.
- **Applies to:** the landing dock, the app dock, and — on solid tokens — the desktop rail's
  active row, so "you are here" is one behaviour across the product.

## 13. Accessibility reassessment

| Concern | Today | Required |
|---|---|---|
| Reduced motion | light zero-by-construction; global 0.01 ms override in `motion.css:207-215` | wake never mounts; indicator jumps; scroll edge allowed (position-driven); the blunt override stays until a per-effect gate replaces it (not this phase) |
| Reduced transparency | unhandled | tier C |
| Increased contrast | unhandled | tier C (labels and edges strengthen, optics recede) |
| Keyboard focus | ring survives on dock items and glass controls; `GlassSurface:focus-visible` uses a literal | keep; literal → token; add the lifted edge (additive) |
| Contrast | dusk palette on `.16` Clear over black: fine; over light content: fails | light-backdrop remap ≥ 4.5:1 on dock labels, both themes, measured |
| Pointer-unavailable | light never ignites; static Fresnel from above | every state legible in a still screenshot; the wake absent, the atmosphere carries mobile |
| Touch | 44 px dock items (40 px ≤640) | ≥ 44 px everywhere; `scroll-padding-bottom` so the last row is reachable above the bar |
| Screen readers | decorative layers `aria-hidden` | canvas `aria-hidden` + `pointer-events: none`; indicator decorative, `aria-current` carries state |
| Non-Chromium | forbidden material | tier B/C preserve hierarchy; every control works in every tier |
| Degraded optics | — | fallbacks preserve **hierarchy**, never merely remove structure |

## 14. Performance reassessment

| Item | Today | Verdict / action |
|---|---|---|
| Displacement-map generation | string build per `measured` write; undebounced; live trigger: toolbar width transition, drawer open, window drag | **B1**: debounce ~120 ms trailing, memoise by `w×h×radius×profile`; ships alone (attributable) |
| Tier detection | per instance, at mount | **B1**: once at boot |
| Spotlight loop | self-settling, variables only, rects dirty-flagged, one observer | keep |
| `useSpotlight` (veil) | a second eased loop for the same pointer | retire with the veil (V) |
| Reveal wake (new) | — | one 2D canvas per stage; clear + ≤64 radial fills per frame while dots live; DPR ≤ 1.5; stops when empty; target < 4 ms/frame at 1440×900 |
| Underlight blobs | 3 transform loops, compositor | keep; become stage-wide |
| `filter` transitions | 4 sites + `.app-fade` | **B1**: remove; guard |
| `app-breathe` on a 900 px image | compositor scale | keep |
| Authenticated shell | 0 per-frame JS at rest | stays 0: no spotlight in `layouts/`; indicator moves on route change only; scroll edge = one passive listener writing one variable |
| Compositing layers | dock, card, shroud, underlight | + canvas + `.content` mask — measured (long tasks > 50 ms = 0) |
| Width transitions | toolbar `width 320ms` regenerates the map per frame | fixed by the debounce; the transition itself stays (opacity/transform-class cost is acceptable for a 48 px slab) |

**Position-driven** (pure function of measured state, allowed under reduced motion): scroll
edge, indicator placement, Fresnel bearing. **Time-driven** (a clock runs): transitions,
flow loops, the spotlight's easing, the wake's decay — all gated. Time-driven animation is
used only where it carries meaning: the wake (discovery), the indicator travel (causality),
the settle (mass).

## 15. Concentricity and the radius system

Shipped radii: dock 30 / item 22; card 28 / inputs 8 (`--radius-input`); toolbar 16 /
buttons 8 (`--radius-button`). Inset arithmetic (N3), where the primitive's `0.5rem` content
padding always participates:

| Surface | Outer | Inset to control | Derived (outer − inset) | Shipped | Result |
|---|---|---|---|---|---|
| dock | 30 | 8 (primitive) + 8 (`.dock` block padding) = 16 | **14** | 22 | not concentric — the one real retune |
| toolbar | 16 | 8 + 8 (`.toolbar-body`) = 16 | 0 → **floor** | 8 | floor rule; correct |
| card | 28 | 8 + 32 (`.card-body`) = 40 | < 0 → **floor** | 8 | floor rule; correct |

The system (adopted into `components.md` §7):

```
structural radii   --radius-sm/md/lg/xl (app scale)                         unchanged
material radii     --material-radius-chrome | -hero | -floating  (30 / 28 / 16)   NEW tokens, B1; replace call-site props
control radius     max(material radius − total inset, --radius-md)          derivation rule
indicator radius   control radius − indicator inset                          derivation rule
primitive padding  --material-inset (0.5rem)                                NEW token, B1; makes the inset visible
```

Decision **R** therefore is: tokenize (zero pixels) **and** correct the dock item radius
22 → 14 (a visible change on one surface, gated by captures). Nobody derives at runtime.

## 16. External research findings

| Source | What was read | Finding |
|---|---|---|
| `liquidglassresources.com` | index page, this session | A **directory**, not a specification: mobile / web / design categories, ~60 entries. No entries on backdrop adaptivity or cross-browser SVG technique. Useful as a map; its survey result (`source-review.md` §4) stands: the ecosystem is mostly glassmorphism wearing Apple's name. |
| Apple HIG *Materials* / *Liquid Glass* overview | pages are JS-rendered and returned no body to the fetcher; corroborated via WWDC25 "Meet Liquid Glass" (session 219) and secondary write-ups | Two variants (**Regular** legible by default, **Clear** needs a dimming layer and more care); the material is *"best reserved for the navigation layer that floats above the content"*; adapts to accessibility settings (Reduce Transparency, Increase Contrast, Reduce Motion); scroll edge effects keep controls legible while content passes under; nested rounded elements use concentric geometry; avoid glass on glass. Every one of these corroborates an existing rule; none contradicts one. |
| `ZyadWKhedr/LiquidGlass-NavBar` | `README`, `navbar_draggable_indicator.dart`, `navbar_providers.dart` | §12.1. Two-stage measured placement and width-with-floor accepted; stretch/scale/blur/drag-as-geometry rejected; drag reclassified as *permissible for the indicator light* (decision I). README/source formula discrepancy recorded. |
| `rdev/liquid-glass-react` | prior deep review | Optics kept (the ancestor of our chain); elasticity, React state architecture, fixed positioning, CPU "shader" rejected. Unchanged. |
| `unobtuse/einui-claude-skill` | prior deep review | Colour law kept; material rejected. Unchanged. |
| MiMo Code landing | source-level, 2026-09-14 | §10.1. The wake principle; its nav material rejected. |
| `GonzaloFuentes28/LiquidGlassCheatsheet`, `lucasromerodb`, `QmDeve` | prior deep reviews | corroboration only: two variants, container concept, CSS+SVG sufficiency, dispersion as a requirement. Unchanged. |

## 17. Keep / Adapt / Reject matrix

| Concept | Source | Verdict | Why |
|---|---|---|---|
| Material belongs to the navigation layer, not everywhere | Apple | **Keep** | already the law (`navigation.md` §1); the app dock is its first authenticated consumer |
| Two variants chosen on legibility | Apple, cheatsheet | **Keep** | Clear/Regular already encoded as `chrome`/`hero` vs `floating` |
| Adaptivity to backdrop | Apple | **Adapt** | as a *declaration* (`data-material-backdrop`), never sampling |
| Reduce Transparency / Increase Contrast as material states | Apple | **Adapt** | tier C of the engine-tier model |
| Concentric nested radii | Apple | **Adapt** | tokenized derivation with a floor; runtime derivation rejected |
| Scroll edge effect | Apple | **Adapt** | content-side `mask-image` band, position-driven, no auto-hide/shrink |
| Glass-on-glass avoidance | Apple | **Keep** | forbidden already |
| Morphing / fluid transitions between glass states | Apple | **Reject (still)** | constitution amendment P3 stays open; the indicator light is not this |
| The reveal wake (local, decaying, seeded edge, self-settling, touch = wallpaper) | MiMo | **Adapt** | as an **environment** layer over an ND atmosphere, subscribing to our one cursor |
| Nav = translucent fill + blur | MiMo, NavBar | **Reject** | glassmorphism |
| Measured two-stage indicator placement, width with floor, clamp | NavBar | **Keep** | indicator contract |
| Drag-linked indicator | NavBar | **Adapt (deferred)** | allowed for the light, not the slab; decision I |
| `LiquidStretch`, `interactionScale`, `GlassGlow` | NavBar | **Reject** | geometry deformation; scale on press |
| Superellipse shape | NavBar | **Reject** | our maps are rounded-rect; shape change is a map rewrite |
| Displacement optics, mid-grey neutral, per-channel offsets | rdev | **Keep** | the chain |
| Elasticity | rdev | **Reject** | permanently |
| OKLCH ladder, chroma tapering | Ein UI | **Keep** | `color.md` |
| `rgba` fill + blur "glass" | Ein UI, most of the index | **Reject** | the forbidden material |
| Canvas for the material | (none propose it correctly) | **Reject** | canvas is environment-only |
| Backdrop sampling | (none) | **Reject** | `adaptive-material.md` §7 |

## 18. Skill inconsistencies found (before B0)

| # | Item (brief §16) | Contradiction / stale assumption | Where |
|---|---|---|---|
| 1 | morph semantics | `interaction.md` §9 correctly splits hover-follow (rejected) from transition morph (P3, open) — but `navigation.md` §4 said "the selection indicator is the only thing that moves" without saying whether an indicator that changes *width* is a morph. | `navigation.md` §4/§6, `interaction.md` §6 |
| 2 | focus-edge vs implementation | `interaction.md` §5 / `SKILL.md` §4 say "focus lifts an edge"; the code has a ring only (`GlassSurface.vue:631`, `GlassDock.vue:184`). | `SKILL.md` §4, `interaction.md` §5 |
| 3 | `--radius-glass` | `components.md` §7 points at "the existing glass radius token … consumed by nobody" — deleted in 17.2 (`liquid-material-system.md` §7). | `components.md` §7 |
| 4 | GlassSurface runtime contract | `materials.md` §7 table correct; `SKILL.md` §6 says maps regenerate on debounced resize — the code does not. | `SKILL.md` §6, `implementation.md` §4 |
| 5 | required material prop | consistent everywhere (✅); but `components.md` §3 still lists `GlassCard`/`GlassSidebar` "future recipes" while `navigation.md` §4 says sidebars default to solid. | `components.md` §3 |
| 6 | Clear / Regular | consistent (✅); missing: what Clear over a *theme-dependent* backdrop requires (the declaration). | `navigation.md` §5, `adaptive-material.md` §3 |
| 7 | Chromium / Edge handling | `implementation.md` §7 shows the brand regex as *the* pattern; `SKILL.md` §6 and `constitution.md` §4.7 name browsers ("Safari, Firefox") as the fallback criterion. | all three |
| 8 | fallback material | `materials.md` §8 sanctions "`blur(12px) saturate(1.8)` + border + inset highlights" — the forbidden idiom. `SKILL.md` §6/§7 call it "the frosted tier". | `materials.md` §8, `SKILL.md` |
| 9 | navigation vs material physics | not distinguished anywhere; a reader applying "reflections move, objects don't" to the indicator would forbid it. | `interaction.md`, `navigation.md` |
| 10 | environment layer | undefined; the shroud, underlight, veil and `GlassScene` exist with no rule; `GlassScene` is called "the environmental veil" in one test comment only. | new `environment.md` |
| 11 | scroll edge responsibility | `scroll-edge.md` §7 "not built, not authorized"; mechanism unspecified. | `scroll-edge.md` §7 |
| 12 | reduced transparency | "handled nowhere, P2" in three files; no tier defined. | `adaptive-material.md` §2/§6, `materials.md` §8 |
| 13 | SSR / hydration | `implementation.md` §13 and `vue-patterns.md` §6 correct; not connected to the tier attribute contract or the indicator's pre-measure state. | both |
| 14 | pointer-driven effect categories | `implementation.md` §12 defines position/time-driven; the spotlight named as the hybrid; the wake not classified. | `implementation.md` §12, `environment.md` |
| 15 | performance budget | `SKILL.md` §6 claims the debounce exists; canvas not mentioned; no rule on ResizeObserver count per stage or on `filter` transitions. | `SKILL.md` §6/§7 |
| 16 | budget semantics | "budget is 3" in four files; the unit is never defined; the guard's regex is described as "the instance budget" though it counts files (N1). | `SKILL.md` §3, `components.md` §1, `navigation.md` §4, `vue-patterns.md` §7 |
| + | doc drift | `liquid-material-system.md` §3 documents a token that does not exist (N4). | doc |
| + | roadmap drift | P18 "glass status strip … budget unchanged" contradicts the guard; P19+ budgets shift by one if B is approved. | `docs/roadmap.md` |

## 19. Skill changes landed in B0

| File | Change | Purpose |
|---|---|---|
| **`references/environment.md`** (new) | wallpaper, atmosphere, ambient light, reactive layer (the wake), declared backdrop; Login and authenticated environments; animation budget; a11y; canvas rule; anti-patterns | S1, S2, S9, items 10/14 |
| `SKILL.md` | eleven references; budget unit + baseline 3 (proposed 4); tier vocabulary; §6 debounce stated as *contract, not shipped*; environment rule; checklist gains `filter`-transition and wake-disabled review items; §4 mobile paragraph gains the backdrop declaration | items 4, 7, 8, 15, 16 |
| `constitution.md` | §3 adds "never animate `filter`/`backdrop-filter`" explicitly; §4.5 canvas-2D-for-environment-only; §4.7 restated as engine-level detection, one resolver, zero brand names outside it; §6 adds the app dock and names environment layers as *not surfaces*; §7 adds animating filters and a second cursor listener; new §9 "Environment is not Material" | items 7, 8, 9, 10 |
| `materials.md` | §8 replaced by the tier model A/B/C with `data-glass-tier` names; §7 gains `--material-backdrop`, `--material-radius-*`, `--material-inset`; §2 hygiene notes the shipped regen defect | items 4, 8, 12 |
| `components.md` | §1 budget **unit** definition, fork count vs logical surfaces, concurrency ceiling; §3 `GlassDock` generalized recipe (landing + app), `GlassSidebar` demoted to "solid by default"; §7 radius family with the floor rule and the primitive-padding contributor; environment listed as "not a composition of the primitive" | items 3, 5, 16 |
| `implementation.md` | §4 debounce/memoise as the B1 contract with the shipped state named; §6 tier store renamed to `refract|diffuse|dense`; §7 the boot resolver + brand guard, UA fallback as the one debt exception; §3 press/focus variables; §12 the wake classified as a hybrid; §13 tier attribute + indicator pre-measure state; new §14 the scroll-edge mechanism; new §15 the indicator composable contract | items 2, 4, 7, 11, 13, 14 |
| `navigation.md` | §4 dock recipe split into *landing dock* and *app dock*; the **indicator contract**; §6 "width interpolation is light, not geometry" + "indicator physics ≠ material physics"; §7 anti-patterns (drawer and dock both glass; indicator as a slab) | items 1, 9 |
| `adaptive-material.md` | §6 P1 → *declared, B4*; §7 records why the shell's backdrop is knowable (theme) and why the landing's is authored; §2 accessibility inputs → tier C; §3 light-backdrop response points at the remap | items 6, 12 |
| `interaction.md` | §4–5 press/focus illumination: *specified, B2* (mechanism named: `:active`/`:focus-within` → opacity-only variables); §6 the indicator contract; §3 the wake is a reveal, the spotlight is the light, one cursor; §9 clarifies the indicator is not a morph | items 1, 2, 9 |
| `scroll-edge.md` | §7 recipe: content-side `mask-image` band, strength = f(`scrollHeight − scrollTop − clientHeight`), one passive listener, one variable; owner = the scroll container, obligation triggered by any floating chrome | item 11 |
| `vue-patterns.md` | §7 budget wording → unit + registry; §2 environment composables follow the same ownership rules; §6 tier attribute; §9 adds "a second pointer listener on a stage that owns a light" | items 13, 16 |
| `source-review.md` | MiMo Code landing (source-level); NavBar upgraded to source-level with the formula discrepancy; Apple HIG entry; `liquidglassresources.com` re-read 2026-09-16; drag reclassified | item + |
| `color.md` | §1 table gains "environment tokens (`--environment-*`) are OKLCH-derived new tokens"; no rule change | — |

Doc-only corrections outside the skill: `docs/liquid-material-system.md` §3 (N4),
`docs/roadmap.md` P18 design paragraph (one correction note; budgets **not** shifted).

## 20. Proposed implementation phases (B1–B6) — NOT implemented

Each phase = one or more commits, each leaving `vue-tsc --build` · `vitest run` · `eslint` ·
`oxlint` · `vite build` green with no test edited to pass. ◆ marks a coherent stop point.

| Phase | Objective | Scope | Likely files | Depends on | Tests | Perf checks | A11y checks | Acceptance | Commit boundary / stop |
|---|---|---|---|---|---|---|---|---|---|
| **B0** | Skill / design-system consolidation | this document; `environment.md`; 11 reference edits; N4 + roadmap note | `.claude/skills/liquid-material/**`, `docs/*.md` | approval of Phase A | link check; guard tests unchanged (16/16) | — | — | §21 checklist passes | **landed; STOP** |
| **B1** | **Material correctness** | tier resolver → `data-glass-tier`; tier B/C CSS replaces `.glass-surface--fallback`; debounced + memoised maps; `yChannel` resolved by eye (Y); 5 colour literals → tokens; 4 `filter` transitions + `.app-fade` filter → opacity/transform; radius tokens + `--material-inset`; dock item 22 → 14 (R) | `GlassSurface.vue`, `glass.css`, `tokens.css`, new `styles/materialTier.ts` (or `stores/material.ts`), `main.ts`, `LoginView.vue`, `GlassDock.vue`, `NoteSelectionToolbar.vue`, `ProductPresentation.vue`, `motion.css`, guards | B0; F for non-Chromium captures | brand guard; tier guard (tier B declares no white fill / uniform border / halo and consumes `--glass-density`); no-filter-transition guard; token guard extended (radius, inset); 16 existing green | map regenerations during a toolbar expand = 1; long tasks 0 | focus ring present in tiers A/B/C; tier C under `prefers-reduced-transparency` and `prefers-contrast: more` | tier A byte-identical at default props except the dock radius; `chrome`/`hero`/`floating` visibly different in tier B; Firefox/WebKit captures | ◆ *every existing surface correct in every engine; nothing new* — **implemented 2026-09-16 (uncommitted):** `styles/materialTier.ts` + `data-glass-tier`; tiers B/C in `GlassSurface.vue` (base = diffuse, `refract` adds the chain, `dense` = tint × (.72 + .28 × density)); `displacementMap.ts` (settled measure + memo); five `filter` transitions removed; four literal sites tokenized; `--material-radius-*`, `--material-inset`, `--material-radius-chrome-control` (dock item 22 → 14px). Tests 215/215; typecheck, lint, build green. Verified in Chrome, Edge and bundled Chromium (all `refract`; `diffuse`/`dense` forced via the root attribute and via emulated reduced transparency; the note toolbar verified in Chrome). **Not executed:** Gecko, WebKit. `yChannel` (Y) left at `G` — no side-by-side render was made in B1; carried to B6. |
| **B2** | **Navigation physics** — **implemented 2026-09-16 (uncommitted):** `composables/useNavIndicator.ts` (pure `createIndicatorMotion` + `relativeGeometry`, measured on mount/resize/layout-key/fonts, direct placement before first paint, damped k .16 travel of x and w, direct under reduced motion); `.dock-indicator` light layer in `GlassDock.vue` from `--on-glass-indicator-*` tokens (all < .3 alpha; ink variants in the bright room); `--glass-press`/`--glass-focus` registered properties set by `:has(:active)` / `:has(:focus-visible)` on the primitive, consumed opacity-only by edge glow and Fresnel; press pool under the pressed dock label. **Not done in B2:** the desktop rail's active row (kept solid brand fill; re-decided with D). Tests 227/227; gates green. Verified in bundled Chromium, Chrome and Edge: first paint correct, monotone damped travel, width interpolation on EN labels, locale and resize re-placement, focus ring + `--glass-focus` 1, press `--glass-press` 1 + pool, reduced motion direct, dense tier legible. Gecko/WebKit not executed. | new `composables/useNavIndicator.ts`, `GlassDock.vue`, `AppSidebar.vue`, `glass.css` | B1 (tier gates the light) | indicator unit tests through returned refs (placement math, floor, clamp); no per-frame Vue binding | indicator moves on route change only; 0 per-frame JS at rest | `aria-current` carries state; reduced motion → jump; first paint correct | indicator under the right item on first paint of every gallery/route; no overshoot | commit per surface |
| **B3** | **Login recomposition** | wallpaper (W); atmosphere retune; stage-wide ambient light; `RevealField` + `useRevealField` replace the 576 px mask; bright-room flip via backdrop declaration; `/welcome` per V; `useSpotlight` retired if V-A | `LoginView.vue`, new `experience/RevealField.vue`, new `composables/useRevealField.ts`, `WelcomeView.vue`, `GlassScene.vue`, `tokens.css` (`--environment-*`), guards | B2, W, V | wake gate tests (never mounted on coarse / reduced); guard: `RevealField` only inside a stage owning a spotlight; `backdrop-filter` owners shrink to 1 if the veil goes | wake < 4 ms/frame at 64 dots, DPR 1.5, 1440×900; 0 long tasks | canvas `aria-hidden`; wallpaper faintly legible at rest with the light off | scene alive with nobody touching it; wake local and decaying; touch gets wallpaper + atmosphere | ◆ *the login is the welcome scene; the app is unchanged* — **implemented 2026-09-16 (uncommitted):** E1 the rose photograph (W), cover-fit, not breathing; E2 `--environment-atmosphere` (OKLCH, per theme): a dusk graded from above (light theme .08 → .22 → .76 → .80) plus a soft pool under the slab, deeper under reduced transparency / more contrast; secondary = the lotus drawing, screen-blended at .30/.42 on the shadowed wall (opacity-only `app-glow`); E3 ambient pools stage-wide; E4 `RevealField` + `useRevealField` (≤64 openings, 112px ceiling, 760ms life, seeded edge, quarter-res mask, bbox-clipped composite, DPR ≤1.5, reads `smoothedCursor`, gated `v-if`). Removed: `.stage-shroud`, `revealMask`, the card aperture, `useSpotlight.ts`, the veil tokens. `GlassScene` = wallpaper + the same atmosphere; guard `backdrop-filter` owners 2 → 1. **Two deviations, both forced or asked for:** the wake is a reveal layer *above* the atmosphere, not a canvas `mask-image` (no portable canvas-as-mask; a per-frame data URI is forbidden regeneration); the atmosphere is graded, not a flat .80–.86 band (brief: bright and transparent; the dimming is placed where glass stands and measured). Tests 265/265 (+38: `useRevealField.spec.ts` 21, `environment.spec.ts` 17); typecheck, eslint, oxlint, build green. Real surface, 33/33 in bundled Chromium, Chrome and Edge: first-paint indicator, travel, press/focus, wake local (~2% of stage for a 330×340 path) and decaying, 0 frames at rest and after decay, reduced motion (never mounted), reduced transparency (dense + deeper atmosphere), forced tiers, 390/640/768/1280, 0 console errors. Wake paint JS p95 2.7–3.7 ms on the GPU path (64 openings, DPR 1.5); sweep pacing equal to the pre-B3 login; ~50 ms long tasks during sweeps occur on the pre-B3 login too. Label contrast (light theme, rest, 1440): title 1.82 → 5.77, subtitle 1.57 → 3.96 (**still < 4.5**), dock idle 6.80 → 4.56, dock active 11.5 → 7.24 (dark theme: subtitle 4.98). **Known limitation, deferred (closeout re-measure, same tree):** the light-theme subtitle stays at 3.96:1. It is `--on-glass-text-dim` on the `hero` preset over the brightest part of the room, so reaching 4.5:1 means changing the material preset or the on-glass tokens. B3 deliberately does not retune presets for one label. It carries to the backdrop/theme work (B4 light-backdrop remap, re-measured there). Gecko/WebKit not executed. |
| **B4** | **Backdrop declaration** | `--material-backdrop` tokens; `[data-material-backdrop='light']` remap (density up in a declared band, rim polarity → dark ink, `--dock-halo` tokenized); `AppLayout` declares from theme; login declares per gallery | `tokens.css`, `glass.css`, `AppLayout.vue`, `LoginView.vue`, `materialTokens.spec.ts` (extended) | B1 | token guard pins light-backdrop values; declaration present on both stages | none (near-static) | dock label contrast ≥ 4.5:1 light theme over `/today`, `/notes` (measured after B5); Login `hero` subtitle ≥ 4.5:1 light theme (carried from B3, 3.96 at B3) | three shipped surfaces unaffected in tier A (they declare `dark` or ignore) | one commit |
| **B5** | **The app dock** | `GlassDock` items → data prop; mounted once in `AppLayout` ≤768, fixed, inset; 5 + More; header loses brand; drawer = More; scroll edge + `scroll-padding-bottom` on `.content` and the Product room; surface registry + `ALLOWED`/`SURFACES` 3 → 4; no-light guard | `AppLayout.vue`, `AppHeader.vue`, `GlassDock.vue`, `LoginView.vue`, `ProductPresentation.vue`, guards | B2, B4, N, B | registry guard = 4 logical surfaces; fork count 3 (unchanged — the dock is a recipe); no `useGlassSpotlight` under `layouts/`; concurrency ceiling 2 measured | 0 per-frame JS at rest in the shell; scroll edge = 1 passive listener | 44 px items; last row reachable; `aria-current="page"`; drawer focus management | mobile: 1 primitive at rest, ≤2 on `/notes` while selecting; content dissolves under the bar | ◆ *the material has returned to authenticated navigation on mobile* |
| **B6** | **Release gate + handoff** | per-browser matrix (§21.3); captures; a11y snapshots; perf measures; `docs/liquid-material-global-handoff.md`; `liquid-material-system.md` §9 rewritten; rollout doc marked superseded; roadmap budgets shifted (B) | docs only + captures | all | full suite ≥ 184 + new | all §21.2 targets | all §13 rows | matrix filled | final commit; **STOP** |

Not in this plan: desktop dock (D-E), page deck (D-D), P19 palette, P18 AI light, P22 toasts,
route transitions, transition morphing (P3), indicator drag (I).

## 21. Verification strategy

### 21.1 B0 (this session) — see §24 for results

Skill references resolve; no broken internal links; one tier vocabulary; one budget
definition; environment/material boundary explicit; no document says blur alone is Liquid
Glass; skill rules consistent with the *current* GlassSurface contract (every target rule is
labelled with the phase that builds it); no contradiction with Phase 17 (budget 3, Today
solid, `/welcome` decision open); no unsupported Phase 18 assumption (P18 adds no glass;
the AI light vocabulary lands on the toolbar per `phase18-plan.md` §14).

### 21.2 Static gates (every B-phase commit)

`vue-tsc --build`, `eslint .`, `oxlint .`, `vite build` clean; `vitest run` with no test
edited to pass; fork guard exact; **surface registry** exact (3 until B5, 4 after); token
guard unchanged for the three shipped presets and extended for remap/radius/inset; **brand
guard**; **tier guard**; **no-light guard** (`layouts/`); **no-filter-transition guard**
(`transition`/`animation` naming `filter`/`backdrop-filter` in `src/` = 0); **environment
guard** (`RevealField` mounts only inside a stage that owns `useGlassSpotlight`; no
`backdrop-filter: url()` outside the primitive — unchanged).

### 21.3 Measured at the real surface (`verify` skill, both themes, 375/768/1280)

| Measure | Target |
|---|---|
| primitives mounted per authenticated route | desktop 0 at rest; mobile 1; `/notes` mobile ≤ 2 while selecting |
| `feDisplacementMap` chains in the document | 1 per mounted primitive |
| `[data-material]` / `backdrop-filter` on content | 0 |
| tier B body alpha for `chrome`/`hero`/`floating` | three measurably different values |
| map regenerations during toolbar expand / drawer open / window drag | 1 |
| long tasks during wake, dock travel, drawer open | none > 50 ms |
| wake frame time (64 dots, DPR 1.5, 1440×900) | < 4 ms |
| indicator on first paint, every route/gallery | under the correct item, no travel |
| indicator travel | one damped move, no overshoot; instant under reduced motion |
| dock label contrast, light theme, `/today` and `/notes` | ≥ 4.5:1 |
| touch targets | ≥ 44 px; last content row reachable above the bar |
| focus ring on every dock item and glass control, every tier | present, unclipped |
| reduced motion | `--glass-light-strength` = 0; wake not mounted; indicator jumps |
| `prefers-reduced-transparency` / `prefers-contrast: more` | tier C on every glass surface |
| login at rest, spotlight disabled | wallpaper faintly legible; scene not dead |
| console | 0 errors, 0 page errors |

Per-browser rows (B6 fills): Chrome, Edge, bundled Chromium; Brave/Arc/Opera/Vivaldi via the
brand guard + a resolver unit test replaying their `userAgentData`; Firefox and WebKit via
Playwright installs (F); Safari on Apple hardware **NOT VERIFIED** (stated).

## 22. Risks

| Risk | Mitigation |
|---|---|
| Tier B looks worse than the (forbidden) frost on some backdrops | perceptual acceptance is hierarchy, not prettiness; the diffusion band is gated on measurement; never whiten |
| `userAgentData` absent in some Chromium builds (privacy settings) | UA fallback contained in the resolver; a misclassified Blink browser degrades to tier B, never to broken |
| WebKit/Gecko actually *do* render SVG backdrop filters in some version | the resolver's step 1 is a real `CSS.supports`; step 2 is engine identity; if the recorded finding is wrong the fix is one line in one file, verified under F |
| Light-theme dock labels fail AA | escape hatch order fixed: deepen remap → Regular for light only → solid |
| The wake becomes the new spotlight (radius creep) | parameters tokenized and guarded (max radius, max dots, DPR) |
| Radius change on the dock reads as a regression | gated captures before/after; decision R explicit |
| Two docks (landing + app) diverge | one recipe, items as data; registry counts both |
| The `/welcome` deletion loses a Phase 8/9 surface | V-A keeps the route; only the material changes |
| Guard changes amend the constitution | each guard change is named in the commit and in the handoff |

## 23. Open decisions (require your approval)

| Id | Decision | Recommendation |
|---|---|---|
| **B** | Budget unit as defined in §7.3, baseline 3, proposed 4 for the app dock; roadmap budgets shift by one | approve |
| **N** | Mobile navigation model: floating dock (5 + More, drawer demoted) vs the rollout's glass drawer | dock |
| **D** | Desktop: rail solid + palette (D-A) now; desktop dock (D-E) re-decided after B5 + P19 | D-A now |
| **W** | Login wallpaper asset: lotus placed on a full-bleed field, the rose as wallpaper for both scenes, or a new asset | **exercised in B3:** the rose is the base wallpaper of both scenes; the lotus becomes the Login's secondary layer (a luminous drawing on the shadowed wall). No new asset. |
| **V** | `/welcome`: V-A sign-in lands on `/today`, veil retuned; V-B keep as landing, retune veil | **exercised in B3: V-A.** Plain sign-in → `/today` (closes `phase17-handoff.md` §5.1); `/welcome` stays a route; the white frosted veil and `useSpotlight` are gone — the hero is the Login environment. |
| **R** | Radius tokenization + dock item 22 → 14 | approve, gated by captures |
| **F** | `npx playwright install firefox webkit` (~250 MB) | approve |
| **T** | Tier names `refract` / `diffuse` / `dense` on `data-glass-tier` | approve (replaces the never-built `full/reduced/flat`) |
| **I** | Indicator drag (light only, clamped, snap) — permitted in principle, not scheduled | defer past B6 |
| **Y** | `yChannel` G vs B — resolved by rendering both in B1 | in-phase |

## 24. Decisions and assumptions made in this session

- **Skill-first.** Every rule the B phases need is in the skill now, labelled *Contract (Bn)*
  where the code does not yet match, so the skill is never wrong about the present.
- **The budget unit is the logical surface**, not the file, not the DOM node, not the visible
  count; the concurrency ceiling and fork count travel with it.
- **Environment is not Material** is elevated to a constitution section.
- **Engine tiers, never brands**; the UA fallback is the single named debt exception.
- **Theme ≠ backdrop**: the declaration is stage-owned; the theme is only the shell's source.
- **The indicator is a light**; navigation physics ≠ material physics; drag is permissible for
  the light and deferred.
- **Canvas 2D is environment-only.**
- **Decision R re-scoped** from "all three slabs move" to "one dock radius moves" after the
  inset arithmetic.
- **Roadmap budgets not shifted** until B is approved; only the P18 factual note was added.
- **Phase 17 and 18 untouched**: Today stays solid; P18 adds no glass; `/welcome` is decision V.
- Apple's own pages could not be fetched (JS-rendered); their guidance is cited through the
  WWDC25 session and secondary sources and, more importantly, through the prior cheatsheet
  review — no rule in this document depends on a sentence that could not be read.

### B0 verification results

Recorded in §24 by the session log: see the final report. `glassBudget.spec.ts` +
`materialTokens.spec.ts` 16/16 green before and after B0 (no source touched); every
`references/*.md` referenced from `SKILL.md` exists; every `§` cross-reference between
reference files resolves to an existing heading; `grep` for "frosted tier" as a sanctioned
material returns only historical notes; one tier vocabulary (`refract|diffuse|dense`) and
one budget definition across the skill.

---

*Audit complete. Skill consolidated. No product code, test, or runtime file was modified.
Waiting for approval before B1.*
