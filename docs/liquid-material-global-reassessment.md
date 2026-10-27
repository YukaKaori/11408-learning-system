# Liquid Material — Global Reassessment (Phase A) and Skill Consolidation (Phase B0)

**Status:** Phase A audit **complete**, Phase B0 skill consolidation **landed** (2026-09-16),
**Phase B1 material correctness** and **Phase B2 navigation physics** committed (`e20595d`,
`5fb8b32`), **Phase B3 Login recomposition** committed (`049ec3b`), **Phase B4 backdrop
declaration implemented** (2026-09-17, uncommitted — see §20 row B4). Phases B5–B6 wait for
explicit approval and the decisions in §23 (R, T and Y were exercised by B1; W and V by B3;
L by B4).
**Since then** (the status line above is kept as written): B4 committed (`4904a7a`, 2026-09-17),
B5.1 the app dock (`e47804b`), B5.2/B5.3 the scroll edge and the light-backdrop optics
(`71c4829`), the daylight sign-in room and the desktop link popover (`75e4e30`, record §25), and
a welcome-room, phone-crown and verification pass — verified on production builds in Chrome and
committed on branch `welcome-room-crown-verification` on 2026-10-27, with its open exceptions
listed (record §26).
B6 is not started.
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
| **B4** | **Backdrop declaration** | `--material-backdrop` tokens; `[data-material-backdrop='light']` remap (density up in a declared band, rim polarity → dark ink, `--dock-halo` tokenized); `AppLayout` declares from theme; login declares per gallery | `tokens.css`, `glass.css`, `AppLayout.vue`, `LoginView.vue`, `materialTokens.spec.ts` (extended) | B1 | token guard pins light-backdrop values; declaration present on both stages | none (near-static) | dock label contrast ≥ 4.5:1 light theme over `/today`, `/notes` (measured after B5); Login `hero` subtitle ≥ 4.5:1 light theme (carried from B3, 3.96 at B3) | three shipped surfaces unaffected in tier A (they declare `dark` or ignore) — *corrected in B4:* this line contradicted the subtitle criterion, because the fix **is** a tier-A change on the light-theme Login | one commit — **implemented 2026-09-17 (uncommitted), decision L-A.** Root cause of the 3.96 subtitle: the primitive's 19 `light-dark()` pairs and `--material-tint-*` resolved against the **theme's** `color-scheme`, so the light-theme Login slab rendered its light variant over a dusk backdrop. Neither the `light` remap (the Login declares `dark`) nor the atmosphere could reach it (a dark atmosphere alone: 3.87). Shipped: `data-material-backdrop` → `--material-backdrop` (glass.css; root fallback = theme, tokens.css) → `color-scheme: var(--material-backdrop)` on `.glass-surface`. `MaterialBackdrop` type. `LoginView` declares per gallery (product `light`, else `dark`, both themes). `AppLayout` declares from `isDark`. The `.is-on-light` override moved to `glass.css` with values unchanged, scoped to the `chrome` rank (all-rank would have inked the floating toolbar's labels in the light-theme shell). No preset, density, rim, token or environment value changed; `SHIPPED` untouched. Prerequisite fix (separate commit): `stores/app.ts` `isDark` was a cached getter over an untracked `matchMedia` read, so an OS flip in `system` mode never reached `html.dark`. It now tracks `systemPrefersDark`. Tests 275/275 (+3 store, +7 backdrop guards); typecheck, eslint, oxlint, build green. Real surface, 36/36 each in Chrome 155, Edge 153 and bundled Chromium 149: per-gallery declarations and schemes in both themes, ink only on the chrome dock, footer toggle changes the environment but not the slab, OS flip live on `/login` and in the shell, toolbar (`floating`) resolves with the theme and keeps dusk labels, pixel identity vs `049ec3b` (reduced motion) for dark login, dark sponsor, light product, `/today` both themes, and the `/notes` toolbar both themes. Changes only where intended: light login (card + dock), light sponsor (dock), dark product (dock). The B3 wake, environment layers, sole `backdrop-filter` owner, focus ring and 0 console errors all hold. **Contrast, subtitle (refract / diffuse / dense), Chrome = Edge ±.05:** light 1440 **5.09 / 5.32 / 6.91** (was 3.93 / 4.14 / 6.55); dark 1440 5.01 / 5.29 / 6.90 (unchanged); light 390 **4.14 / 4.17 / 6.79** (was 3.19 / 3.23 / 6.40); dark 390 4.64 / 4.80 / 6.86 (unchanged). **Still below 4.5 on the light-theme Login at 390 px** in refract and diffuse: the slab is already the dark-backdrop material there, so the remaining gap is the lighter light-theme atmosphere behind the card at phone width. That is environment/visual tuning, which B4 was told not to do; carried forward as an open item. Gecko/WebKit not executed. |
| **B5** | **The app dock** | *(+ from B4, decision L-A: the `[data-material-backdrop='light']` density band and rim polarity per rank, tuned and pinned against `/today` and `/notes`)* `GlassDock` items → data prop; mounted once in `AppLayout` ≤768, fixed, inset; 5 + More; header loses brand; drawer = More; scroll edge + `scroll-padding-bottom` on `.content` and the Product room; surface registry + `ALLOWED`/`SURFACES` 3 → 4; no-light guard | `AppLayout.vue`, `AppHeader.vue`, `GlassDock.vue`, `LoginView.vue`, `ProductPresentation.vue`, guards | B2, B4, N, B | registry guard = 4 logical surfaces; fork count 3 (unchanged — the dock is a recipe); no `useGlassSpotlight` under `layouts/`; concurrency ceiling 2 measured | 0 per-frame JS at rest in the shell; scroll edge = 1 passive listener | 44 px items; last row reachable; `aria-current="page"`; drawer focus management | mobile: 1 primitive at rest, ≤2 on `/notes` while selecting; content dissolves under the bar | ◆ *the material has returned to authenticated navigation on mobile* — **B5.1 committed `e47804b` (2026-09-19). B5.2 + B5.3 implemented 2026-10-17 (uncommitted), in a visual pass on the functional UI:** B5.2 `composables/useScrollEdge.ts` writes `--scroll-edge` from position alone; `.content` carries the mask (48px band, depth .9) at ≤768px; a view's pinned foot (`data-scroll-edge="pinned"`, the scoring bar) moves the band below the bar's edge; the Product room's edge is **not** built. B5.3, measured against `/today`, `/mistakes`, `/syllabus`, `/notes`: chrome keeps .16 and gets ink rims (`--glass-rim-polarity`, `--material-rim-ink`); floating rises .62 → .86; tier A refracts in one pass over a light backdrop (`--filter-id-mono`) — the per-channel split drew the blue rim line; tier C keeps dusk labels (light dense was 1.6:1) with its floor raised .72 → .86; idle ink .6 → .7. Contrast, light theme, refract/diffuse/dense: dock idle 3.74 → 4.96 / 3.73 → 4.96 / 1.59 → 5.36; toolbar 2.29 → 6.51 / 2.46 → 6.92 / 6.65 → 14.77 (the toolbar's ghost labels had never received its full-dusk lift). Budget, fork count and concurrency unchanged (5 / 3 / 3). Tests 426/426; typecheck, eslint, oxlint, build green. HEAD-vs-tree pixel diffs (bundled Chromium): the sign-in room (both themes, 1440 and 390), desktop `/today` `/syllabus` `/sittings`, the dark app dock at rest and the scoring foot are identical (at most ±1 raster noise); desktop `/mistakes` is identical outside one KaTeX line the HEAD harness could not give its fonts. Gecko/WebKit not executed. |
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
| **L** | Light-backdrop optics (density band, rim polarity): L-A tune in B5 against the shell; L-B tune in B4 against the Product room | **exercised in B4: L-A.** B4 only routes the declaration and moves the shipped ink flip. |

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

---

## 25. Record — the daylight sign-in room, the desktop link popover, the audit (2026-10-21)

A pre-M3 visual pass on top of `71c4829`, at the owner's request: a new welcome wallpaper,
a deliberate desktop glass presence, and an audit of the shipped implementation.
Committed on branch `welcome-daylight-desktop-glass` on the owner's request (2026-10-21).

**1. The sign-in room became a daylight room.** E1 is the supplied artwork
(`web/public/images/welcome-bg.png`, 1691 × 930, kept as supplied; the browser downloads its
WebP twin, 140 KB, from `scripts/environment/welcome.cjs`). E2 and E3 left with the dusk: an
atmosphere could only turn the high-key picture to slate, and the drifting pools behind three
refracting slabs re-ran their filters every frame. E5 is `light` in all three rooms and both
themes. The crop is authored per stage shape (centred; the left edge at `10% 80%` on portrait
tablets; the left edge whole on tall phones). The wake's plate learned `object-fit: cover`
(`coverSource`). `/welcome` keeps the lotus.

**2. The hero's light-backdrop answer** (the row `adaptive-material.md` §6 had as "never stands
on a light stage"): the Clear `.16`, rim polarity 1, the dock's ink palette, and a hero-only
remap — hairlines, hover pools, the brand at `--landing-accent-ink`, the error line at
`--material-danger-ink`, a secondary step at 93 % of the primary ink, a deeper link, ink-polarity
input and checkbox skins. A dark slab beside the clear dock was rendered and rejected (two
materials on one stage). The ink's idle step went .7 → .8 and ink dock labels gained a near halo
(transparent for dusk labels — the dark-theme app dock is pixel-identical, A/B 0 px).
Contrast over the picture, worst 5 % of pixels under each text, 320/390/820/1024/1440 px × both
themes × both locales: title ≥ 8.4, subtitle ≥ 5.5, field labels ≥ 5.5, "remember me" ≥ 4.5,
"forgot password" ≥ 4.9, submit ≥ 5.3, language ≥ 4.9, dock labels ≥ 4.7, colophon ≥ 9.9,
input icons ≥ 3.5. Tier C (dense): all ≥ 4.5 except the dusk-pinned brand button's label,
4.34 on phones — unchanged skin, the same in the old room.

**3. Desktop glass: the note editor's `[[` link popover** (`WikiLinkSuggestions`, `floating`),
the toolbar's sibling — a popover summoned over the note being written, never on screen with the
toolbar. Budget 5 → 6, fork count 3 → 4, concurrency unchanged (3, the login stage). The other
candidates were read and left solid by `navigation.md` §5: the AI tutor's composer and the
paper-result scoring bar are permanent bars over arbitrary content (Regular would be a dark slab
in the light theme), the focus timer is docked, the exam head sits over the paper.

**4. Audit findings, fixed:**

| Finding | Evidence | Fix |
|---|---|---|
| The note toolbar never refracted or diffused the note once settled | its settle ran on the wrapper with `both`: the wrapper stayed a backdrop root; a solid red slab hid the text, a plain `blur(6px)` left it crisp | the settle is played by the slab (`backwards`); guard added |
| No forced-colours state | under emulated forced colours the slabs kept tier A: 10 %-alpha `Canvas` body, no edge, forced text over a live refraction, no current-item marker | tier C on `(forced-colors: active)`; opaque `Canvas` body + 1px `CanvasText` edge; `Highlight` for current items; the wallpaper and the wake step back |
| Rest-state cost on the login stage | 38.9 ms mean frame interval at rest (software-rendered harness) — the ambient pools *and* the dock's flow band under the lens each kept it there | pools gone with the dusk; the flow band withdrawn over a light backdrop (it moved no channel by more than 7/255): 6.2–6.7 ms |
| English app-dock labels ellipsized below 360 px | "Syllabus", "Practice", "Mistakes" cut at 320 px | `:lang(en)` < 360 px: the row gives the cells its gaps (45 px) and labels 11 px; Chinese untouched |
| `<html lang>` stayed `zh-CN` after a reload in English | `init()` never wrote it | written at boot (assistive voices, `:lang()`) |
| On-glass controls under 44 px on touch | sign-in footer 28 px, password reveal 14 px, "forgot password" 18 px, "remember me" 21 px | invisible centred hit areas on a coarse pointer: 42–44 px effective, stacked neighbours sharing the overlap |

**Verified:** 455 unit tests, `vue-tsc`, oxlint, eslint, production build. Real-surface runs
in Chrome (Playwright, headless): 24 login shots (6 viewports × 2 themes × 2 locales), the
popover in 8 states, tiers B/C forced via the root attribute, reduced transparency through
emulated media, forced colours in both system palettes, reduced motion, keyboard order, hit
testing, the galleries' round trip, sign-in → `/today`, `/welcome`; no console errors.
**Not run:** Gecko, WebKit, real Windows High Contrast, physical devices.

**Open:** the Product room's scroll edge; Gecko/WebKit; on 320 px phones the peony stands
behind the form (legible, measured, but busy); the dense tier's brand-button label at 4.34.

---

## 26. Record — the welcome hero's daylight room, the phone crown, and a verification pass (2026-10-23)

Made on top of `75e4e30` between 2026-10-23 and 2026-10-27: the implementation, then a
verification, narrowly scoped repair and documentation pass (interrupted three times — two
session limits, then a machine restart; its last runs finished on 2026-10-27). Committed on
branch `welcome-room-crown-verification` on the owner's request once the verification had
finished (2026-10-27). Every figure below comes from the verification, measured on **production
builds** with one instrument (26.2).

### 26.1 What the working tree changes

1. **The welcome hero stands in the sign-in room's daylight** (`GlassScene`): the supplied
   picture through the same `<picture>` pair, cover-fit; its copy in the daylight rooms' ink
   (`--scene-*` → `--landing-ink*`), the smaller lines with the colophon's halo pair, the eyebrow
   and the three words in the full ink; the actions on the daylight brand ladder; every keyboard ring in the room the
   brand ink (declared on `.hero`); the sections' aura the peonies' blue. Environment only — the
   route mounts no glass. The dusk lotus room is retired: `--environment-field`, `-lotus-light`,
   `-atmosphere` and their reduced-transparency deepening are gone (guarded); the lotus artwork
   and its encoder stay, unmounted.
2. **Phone-shaped stages** (≤ 640px wide, or taller than 3 : 5) **wear the picture as a crown** on
   both rooms — a band across the top half, cropped to the right peony, masked into the room's own
   daylight (`--environment-daylight-sky` → `-field`). The wake's gate excludes the same query, and
   re-decides when a window crosses it.
3. **The sign-in room's composition:** a grid sharing the free height 2 : 3 above and below the
   slab (1 : 1 on a phone), floors in proportion — it replaced a 12vh margin that left the dock 9px
   under the slab on a 768px-tall laptop.
4. **The Regular rank** (`floating`, the note toolbar and the `[[` list): density **.62 → .78**,
   refraction in **one pass** on every backdrop.
5. **The shell's scroll container drops its mask while a slab is mounted inside it**
   (`.content:has(.glass-surface)`), or the toolbar and the list refract nothing on phones.
6. **On smoke**, a brand fill under a white label takes the anchor's steps (`--on-glass-brand-fill*`),
   and **every slab declares its own keyboard ring**: smoke `--landing-accent-soft`; the clear dock
   and the clear sign-in slab over a light stage `--material-rim-ink`.
7. **A production build honours the declared backdrop:** each declared value is also written as a
   literal `color-scheme` on the slab (26.3, finding 1).
8. **`--material-danger-ink`** oklch(0.49 0.173 27.3) → **oklch(0.375 0.13 27.3)** (finding 5).
9. Comments only: `motion.css` (`app-float` never on glass), `tokens.css` (`--ease-spring` solid UI
   only), `RefractionField.vue`, `GlassSurface.vue`.

No surface was added: budget 6, fork count 4, at most 3 primitives on one screen.

### 26.2 One instrument, and why the earlier figures disagreed

The pass inherited contradictory numbers for the same text — the landing dock's current label at
820 × 1180 was "3.31", "4.8", "5.89" and "5.54" in different reports — and figures that could not
be compared: the earlier scripts differed in five ways.

| | 2026-10-21 baseline | 2026-10-23, first session | this pass |
|---|---|---|---|
| served from | dev server | dev server | **production build** (`vite build` + `vite preview`) |
| browser | Chrome | bundled Chromium (headless shell) | Chrome |
| sampled region | the element's box | the text's line boxes | the text's line boxes |
| halo | kept | removed (`after-native` log), then **kept** (`final-native` log) | **both**, reported separately |
| colour parsing | canvas (correct) | `oklch()` read as **black** | the browser's own serialisation (`color(srgb …)`) |
| clipped or covered text | sampled anyway | sampled anyway | **refused** (hit test + every clipping ancestor) |

So the "5.89 / 4.8" of the first session's final log (11:32) was the "3.31 / 3.70" of its 11:26 log
re-measured with the halo kept — the instrument changed at 11:28, the code did not. Its colophon
and "forgot password" were computed as black text: 15.9 and 13.4 at 320 × 640 (they are 12.6 and
7.3), 13.2 and 13.2 at 820 × 1180 (10.5 and 7.2). Its dense-tier log covered 320 × 640 only. The
4.34 (2026-10-21) and 4.41 (first session) are one finding — the sign-in button's label in tier
C — measured by two instruments.

**The method of this pass:** per-pixel WCAG 2 contrast of the computed colour (times the
opacity of the element and its ancestors) composited over the pixels rendered behind each line
box, with the glyphs made transparent; **bare** (the text's own `text-shadow` removed too) is the
headline figure, **halo** (shadow kept) is reported beside it; the statistic is the 5th percentile
of the pixels (the worst 5 %). Rings: the outline colour over the band it occupies, against the
same pixels with the ring made transparent. Fourteen stage shapes (320 × 640 to 1920 × 1080,
phones and upright tablets emulated as touch devices at DPR 2, desktops at DPR 1), both themes,
both locales, tiers A (native), B (`diffuse`, forced through the root attribute — Blink rendering
the WebKit/Gecko tier) and C (`dense`, through emulated `prefers-reduced-transparency`).
Signed-in pages answer the preferences read with the run's theme (nothing written). The
backend's CORS allow-list knows only the dev origin, so a write from a preview port is refused
(403 "Invalid CORS request" — a harness artefact, not a product defect); the harness presents the
dev origin for API requests. The proposal stream is answered in the browser — no AI call.
Regression or pre-existing was decided against a production build of `75e4e30`. A temporary-folder
cleanup removed the instrument's scripts and logs mid-pass; it was re-created from the session
record with the same rules and checked against a context measured before any repair (820 × 1180,
light, zh-CN: all twelve texts, bare and halo, identical to two decimals). Figures from before the
cleanup that this record uses were taken on builds whose source differed from the final one only
where stated, and each such difference was re-measured on the final build.

### 26.3 Findings and what was done

| # | Finding | Evidence | Kind | Done |
|---|---|---|---|---|
| 1 | In a built app the light-stage slabs drew the **dark theme's** `light-dark()` pairs — a black frost, violet rims — under the sign-in slab, the dock and the lens | the minifier (Lightning CSS) rewrites every `light-dark()` as `var(--lightningcss-light, A) var(--lightningcss-dark, B)` and switches it only beside a literal `color-scheme`; `var(--material-backdrop)` switched nothing. Sign-in title at 320px: 6.95 light vs 5.41 dark. The dev server (native) never showed it | pre-existing (since B4) | literal `color-scheme` per declared value on `.glass-surface`; title now 6.86 dark (the root-token tints remain theme-resolved in a build: ~1 %) |
| 2 | The **landing dock's keyboard ring** (brand anchor) read **1.6–2.3 : 1** against what is behind it at every stage shape | rings sampled per cell, both locales; the anchor's ink step 2.3–3.3; the rim ink 5.9–8.5 from the same pixels | the colour is pre-existing; the rule naming it was this pass's | the dock's ring is the rim ink |
| 3 | The **sign-in slab's ring** (brand ink) dipped to **2.76–3.0** at 744, 810 and 1440 px | same method; the rim ink ≥ 7.09 (7.24 in the light theme) | pre-existing | the slab's ring is the rim ink |
| 4 | `/welcome`: the **scroll hint's ring** read **2.36** (dark theme) on the bright picture; the **eyebrow** **3.88** bare at 360px in English; the **three words** **4.33** at 1024 × 768 | the ring was declared on `.hero-content`, outside which the hint sits; the soft ink over the crown's peony, and over the picture's darker middle on a landscape laptop | regression (this pass's room) | the ring on `.hero`; the eyebrow and the words in the full ink |
| 5 | The **sign-in error line** failed almost everywhere | `75e4e30`: 2.4–4.7; after the 2 : 3 grid 3.1–4.4 (better on phones, worse on upright tablets and 1024/1366, where the slab now stands lower) — the deepest case with the "forgot password" hint open above the line | pre-existing, shifted by this pass | the ink two measured steps deeper (`#771816`) |
| 6 | The **tier C sign-in button** (4.34 / 4.41) and the **toolbar's proposal button** (4.0–4.1 in tier A, first session) | the pinned dusk brand (`#7b86e8`…) under a white label | pre-existing / the first session's fix | `--on-glass-brand-fill*` (first session) — confirmed below in every tier, for Accept and Retry |
| 7 | The **app-wide dark-theme primary button**: a white label on `#7b86e8` | rendered **3.27 : 1** on `/flashcards`, `/calendar` and the welcome page's closing CTA (light theme 4.70) | pre-existing, app-wide | **documented, not changed** — every solid primary button in the dark theme; a fill token for the dark theme (e.g. the anchor step, 4.7 : 1) is the likely fix and a visual decision |
| 8 | The **landing dock's current label on upright tablets** is under 4.5 : 1 **bare** | 744, 820 and 834 portrait, both themes and locales: **3.36–4.27 bare** (halo 4.60–6.36); 768, 810 and 1024 portrait ≥ 4.63 bare. `75e4e30` at 744/820/834 (light): 3.49–4.27 — the same figure in every context measured on both | pre-existing (the portrait crop puts the large peony behind the dock; the dock did not move) | **documented exception** — no change inside the material rules: the label is ink on clear glass over a mid-tone petal; only a different crop or a denser bar would move it |
| 9 | The **note toolbar, its proposal panel and the `[[` list** open into the clipped part of a scrolled editor pane | placed from the editor shell's top, not the pane's visible box (`.canvas-body`, ~290px tall on a phone): 24 of the bar's 60px rows visible after an 81px scroll at 390 × 844; the flip also assumes a 48px bar (it renders 60px). Identical in `75e4e30` | pre-existing (positioning, not material) | **documented** (`components.md` §3); measured with the pane positioned so each slab is whole |
| 10 | A `[` typed into the editor was lost once | 0 of 12 deliberate repetitions on the production preview and the dev server | not reproduced | — |
| 11 | **Tier C (reduced transparency) still has short rings and, at desktop widths, a short error line** | the dock's rings at every shape (390–1920, both themes): the current cell **1.34–1.93**, 赞助 **1.64–1.88**, 产品 **2.74–3.13**; at 1024–1920 also the sign-in button's **2.76–2.92**, the theme's **2.60–2.97** and the language's **2.58–2.88** (5.24–5.90 at 390/820); the error line **4.16–4.31** in the light theme at 1024–1920 (dark 4.64–4.76; 390/820 5.73–6.29). The what-if colours from the same pixels (the brand anchor, its ink step, the rim ink, the light step): none clears 3 : 1 on the dock's cells — the slab is smoke there; its lower part is lifted by the back-face bloom (the band behind the footer rings ≈ `rgb(90 92 96)`), and the dock's end cells put part of their ring band outside the rounded bar, on the bright picture, so no single ring colour clears both | pre-existing — against `75e4e30` at 390, 820 and 1440 (light): the error line (4.11 → 4.18–4.31 at 1440), the sign-in button's label (4.34–4.35 → 6.24–6.26) and 26 of the 27 rings are better (HEAD 1.31–3.43); one is lower — the dock's current cell at 820, **1.49 → 1.34** (the light step this pass declares on smoke reads below the page's brand on that cell's band; both far under 3) | **documented, open**: a two-tone (inset light + outer dark) focus ring for tier C, and a lighter smoke danger step, are the likely fixes — a ring-geometry change, outside a narrow repair |

### 26.4 Final measurements (production build, bare unless marked)

**Sources.** *The final build:* the two lanes of 2026-10-26 on a production build of the final
tree under `vite preview` — lane 1 the sign-in room (40 contexts, the last 8 of them on a
production build of `75e4e30`), lane 2 the welcome route, the note toolbar and `[[` list and the
app-wide primary button, then the interaction checks (finished 2026-10-27, 26.5) — and two single
contexts on the same bundle (820 × 1180 light zh-CN, the instrument's cross-check; 1440 × 900
light zh-CN in tier C). The bundle `vite build` made from the final tree on 2026-10-27 is
byte-identical to the one measured (SHA-256, all 154 files). *Three sweeps of 2026-10-24* that
the lanes did not repeat, each on a build that differed from the final one only in tokens that
do not reach what it measured: the sign-in room's texts in tier A at the twelve shapes from
320 × 640 to 1024 × 768, both themes and locales (48 contexts; that build predates the slab's
rim-ink ring, the final error ink and the welcome words line); the error line on the final ink
(all 14 shapes, both themes and locales, with and without the hint above it: 112 figures); and
the rings in the rim ink at every shape. Where a context was measured on both, the figures agree:
the cross-check to two decimals, the tablet dock label at `75e4e30` context for context. Targets:
4.5 : 1 for text, 3 : 1 for large text, icons and rings. **Bold** = under target.

**The sign-in room.** Final build: tier A at 1024, 1366, 1440 and 1920 (both themes, both
locales) and 820 light zh-CN; tier B (`diffuse`) at 390, 820 and 1440 and tier C (`dense`) at
390, 820, 1024, 1440 and 1920 (both themes, zh-CN). In brackets, the 2026-10-24 tier A sweep at
320–1024.

| | tier A | tier B | tier C |
|---|---|---|---|
| title · subtitle | 8.59–9.55 · 7.50–7.86 (≥ 6.83 · ≥ 6.00) | 8.46–9.55 · 7.37–7.87 | 11.63–12.70 · 6.14–6.59 |
| field labels · remember | 7.07–7.92 · 6.13–7.21 (≥ 6.23 · ≥ 5.78) | 7.12–7.87 · 6.89–7.33 | 6.30–6.71 · 6.24–6.68 |
| forgot · the button's label · language | 4.84–7.27 · 5.30–5.36 · 7.01–7.57 (≥ 6.71 · ≥ 5.29 · ≥ 7.15) | 6.69–7.54 · 5.30–5.41 · 7.16–7.81 | 4.79–5.33 · 6.24–6.36 · 5.40–5.96 |
| the dock's current label | 5.43–6.52 at 1024–1920 (halo ≥ 5.66); **3.53** at 820 (halo 6.31) (≥ 3.36: **under 4.5 in 12 of 48 contexts — exactly the 744, 820 and 834 portraits**, 3.36–4.27, halo 4.60–6.36; finding 8) | 4.90–6.89 (halo ≥ 6.22) | 11.71–13.40 |
| the dock's other labels | 5.39–5.92 (≥ 5.25) | 5.39–6.19 | 5.41–6.15 |
| colophon · the forgot-password hint | 10.48–11.80 · 5.68–7.04 (≥ 10.37 · ≥ 5.67) | 10.48–12.71 · 6.17–7.26 | 10.48–12.71 · 5.81–6.67 |
| the error line, under the open hint | 4.83–6.62; on the final ink, every shape, with and without the hint (2026-10-24): 4.63–6.94 | 5.44–6.92 | **4.16–4.31** light at 1024–1920; dark 4.64–4.76; 390/820 5.73–6.29 |
| rings on the slab | at 1024–1920: reveal 10.48–10.94, remember 10.41–11.35, forgot 7.86–11.00, sign in 9.48–11.06, theme 10.68–12.41, language 10.81–12.30 (every shape, 2026-10-24: ≥ 7.09) | 8.24–11.92 | reveal 4.98–5.98, remember 5.39–7.24, forgot 4.53–7.10; sign in, theme, language 5.24–5.90 at 390/820, **2.58–2.97** at 1024–1920 |
| rings on the dock | 6.62–8.39 at 1024–1920 (every shape, 2026-10-24: 5.89–8.54) | 6.18–8.54 | **the current cell 1.34–1.93, 赞助 1.64–1.88, 产品 2.74–3.13** at every shape |

The rings are the rim ink in tiers A and B and the brand's light step in tier C (smoke). The
text fields draw no outline — a focused field turns its frame to the brand (26.5). `75e4e30`
(production build, for regression): tier A at 744, 820 and 834 light — the dock's current label
3.49–4.27 (halo 4.73–6.36), the error line **3.14–3.47**; tier C at 390, 820 and 1440 light — the
button's label **4.34–4.35**, the error line **4.11** at 1440 (5.73–5.82 at 390/820), rings
**1.31–3.43** (the theme's brand, `#5e6ad2`).

**The welcome route.** Final build: 1024, 1366, 1440 and 1920 light (both locales), 390 and 1440
dark (both locales), 820 and 1920 dark zh-CN — 14 contexts. The hero is the same room in both
themes: at 1440 its light and dark figures agree to two decimals but for the hint's icon
(5.28 / 5.34). In brackets, 22 contexts of 2026-10-24 at 320–1024, measured before the three
words moved to the full ink.

| | final build | (2026-10-24) |
|---|---|---|
| eyebrow | 11.03–15.15 (halo ≥ 12.68) | ≥ 9.44 |
| the title's two lines (large) | 11.84–14.64 | ≥ 11.50 |
| the three words | 10.52–16.00 (halo ≥ 11.97) | ≥ 4.33 — the 1024 × 768 finding (4), since fixed |
| subtitle | 4.61–6.20 (halo ≥ 4.94) | ≥ 4.53 |
| Start (white on the anchor) · Discover | 4.71 · 13.78–15.44 | 4.71 · ≥ 13.72 |
| the scroll hint's icon | 5.16–5.87 | ≥ 5.16 |
| the solid sections: titles · copy | 15.48–18.03 · 6.96–8.29 | ≥ 15.33 · ≥ 6.61 |
| the closing CTA's label | 4.70 light · **3.27** dark (every dark context; finding 7) | **3.27** dark |
| rings | Start 4.82–6.14, Discover 5.35–5.81, the scroll hint 4.80–5.55 (the daylight brand ink); the closing CTA 4.24–4.96 (the theme's ring) | — |

Every section was revealed by scrolling (none left hidden) and no context scrolled sideways.

**The note toolbar and the `[[` list** (Regular rank, `floating`): 1440 and 390, both themes in
tiers A and C, the light theme in tier B; the proposal stream answered in the browser; each slab
measured where it is whole (finding 9).

| | tier A | tier B | tier C |
|---|---|---|---|
| the `[[` list: active · idle row | 5.80–14.57 · 7.97–15.04 | 5.80 · 6.55–7.97 | 13.77–14.58 · 14.25–15.07 |
| the bar's four actions | 6.65–15.31 | 6.65–9.80 | 14.76–15.32 |
| the proposal: title · body | 5.53–15.13 · 5.58–14.45 | 5.55–6.16 · 5.58–12.26 | 14.36–15.07 · 7.01–15.21 |
| Discard (ghost) · Accept / Retry (brand fill) | 8.34–14.76 · 5.72–6.64 | 8.34–9.25 · 5.73–5.83 | 13.64–14.76 · 6.43–6.64 |
| rings: the bar · the proposal | 3.21–3.88 · 4.19–7.41 | 4.40–4.46 · 4.19–4.33 | 7.15–7.55 · 6.41–7.39 |

The shell's scroll-edge mask is on the phone's `.content` before a slab opens and gone while the
toolbar or the list is mounted (desktop has none). No console error, page error or failed
request in any context.

**The app-wide primary button** (outside glass; `/flashcards` and `/calendar` at 1440 and 390):
a white label on `#5e6ad2` 4.70 in the light theme, on `#7b86e8` **3.27** in the dark (finding 7).

**The app dock** (390 × 844, 2026-10-27): the current label 8.18 light / 12.71 dark, the first
idle label 6.65 / 7.06; the first three cells' rings 10.92–11.48 (the rim ink) / 6.31–6.86 (the
light step).

### 26.5 Interaction checks (production build, Chrome headless)

Pass/fail checks, not contrast (except §7's labels and rings); every page is also checked for
console errors, page errors and failed requests. **564 checks, all passing on the final build.**

| § | What | Contexts | Checks | Run |
|---|---|---|---|---|
| 1 | `/` and `/welcome` rendered: signed out, `/` is the sign-in room; signed in, `/today`; theme and language applied; no sideways scroll; the app dock on a phone; the welcome picture decoded; screenshots kept | 1440 and 390 × both themes × both locales | 84 | 2026-10-26 |
| 2 | `/welcome` at every shape: no sideways scroll; the copy clears the scroll hint; the hint on screen; a phone-shaped stage wears the crown (half the height, masked), the rest the whole picture | 15 shapes × 2 locales | 150 | 2026-10-26 |
| 3 | `/welcome` by keyboard and pointer: Tab reaches Start → Discover → the scroll hint → the closing CTA, each with a ring, the hero's three in the daylight brand ink; Discover and the scroll hint scroll to the sections; the copy drifts on scroll; Start lands on `/today`. Reduced motion: no drift, every section revealed at once, the hint plays once. Reduced transparency: tier C, no glass on the route, the picture still shown. Forced colours: tier C, the picture and the crown's gradient gone | 1440 and 390 × both themes | 80 | 2026-10-27 |
| 4 | `/login` at every shape: no sideways scroll; slab, dock and colophon apart; the room scrolls rather than clips; a phone-shaped stage: the crown and no wake; the rest: the whole picture, and the wake only with a fine pointer | 15 shapes × 2 locales | 180 | 2026-10-27 |
| 5 | The wake re-decides when a desktop window crosses the phone shape (1440 → 600 → 1440 px; the crown on at 600) | 1 | 2 | 2026-10-27 |
| 6 | `/login` by keyboard: the fields, reveal, remember, forgot, sign in, theme, language, then the dock, each showing focus (a field by its brand frame); Enter on 产品 opens the Product room, the slab recedes and goes inert; Escape returns to the room, focus on 产品; a real sign-in lands on `/today`. Reduced motion: no wake. Reduced transparency: tier C; the wake still runs on a desktop (it is not a material), none on a phone. Forced colours: tier C, no wake, the picture steps back | 1440 and 390 × both themes | 60 | 2026-10-27 (rerun) |
| 7 | The app dock on a phone: its labels ≥ 4.5 bare, its rings ≥ 3 against what is behind them (figures in 26.4) | 390, both themes | 8 | 2026-10-27 |

Three corrections to the **harness** — none to the product — were needed to finish it:

1. On 2026-10-26 the run stopped in §3, after §1–2 had run through. The process died before it
   wrote its results file, so the 234 passes of §1–2 rest on its log, which prints every failure
   and holds none, and on their 24 screenshots. Playwright's click waits for an element to stand still, and the scroll hint floats
   without end (`app-float`, 2.4 s, 8px — unchanged since `75e4e30`), so the click timed out. The
   harness now checks that the pointer lands on the hint at its centre (a check of its own, among
   the 80) and clicks there with the mouse; the scroll that follows is what it verifies. The run
   resumed at §3 on 2026-10-27 against the same bundle; the machine had restarted overnight, so the
   backend jar and the preview were started again.
2. §6 then failed "every stop shows focus" on the two text fields in all four contexts. The
   harness compared the field's frame colour with the raw text of `--color-primary`, which a
   production build minifies (`oklch(48% .128 275)`), while the browser serialises the frame as
   `oklch(0.48 0.128 275)` — never equal, whatever the field drew. A probe showed the frame in
   exactly that colour 140 ms after Tab in all four contexts. With the comparison made against the
   value the browser resolves, §6 was rerun: 60/60. (The frame's *contrast* is not measured — the
   instrument measures outlines — and its colour is the one `75e4e30` draws.)
3. The `/verify` smoke (26.6) first failed its console check in all four contexts on
   `net::ERR_ABORTED`: it left `/today` the moment the address changed, and its own navigation
   aborted `/today`'s requests and lazy chunks (no HTTP error, no console error). Letting each
   page's network settle before moving on: 24/24.

### 26.6 Verified, not run, open

**Verified** (Chrome — Blink — only):

- **The gates on the final tree** (`/verify`, 2026-10-27): the unit suite 463/463 (36 files);
  `vue-tsc --build` clean; oxlint 0 warnings and 0 errors on 217 files (123 rules); eslint clean
  (both run without `--fix`); `npm run build` (type-check + `vite build`) passed with no warning —
  and its bundle is byte-identical to the one the lanes measured (26.4).
- **The real surface** (`/verify`): the API (`/api/v1/system/info` 200, a sign-in code 0); a smoke
  — sign in through the form, land on `/today`, the welcome route's daylight hero, a note's
  selection toolbar and `[[` list, both themes at 1440 × 900 and 390 × 844, no console error, page
  error or failed request — **24/24 on the production build** (`vite preview`) and **24/24 on the
  dev server** (after one warm-up pass, whose one failure was a cold dev server's first page
  aborting its own module loads, `net::ERR_ABORTED` — the warm-up the `verify` skill prescribes).
  Each run created one note through the API and deleted it (none left); no paper session is open.
- **Every repair in 26.3 holds on the final build** — the production `light-dark()` fix, the
  docks' and the slab's rim-ink rings, the error ink in tiers A and B, the welcome scroll-hint
  ring, eyebrow and three words, the tier C button and the toolbar's proposal buttons on the
  anchor's fill — and the crown, the wake's gate (also across a resized window) and the scroll
  container's mask stepping aside for a slab behave as specified: the figures in 26.4 and the 564
  checks in 26.5.
- The final session (2026-10-27) changed no product code; its corrections were to the measuring
  scripts (26.5), which live outside the repository.

**Not run:** Gecko and WebKit (not installed); real Windows High Contrast (forced colours only in
Chromium's emulation); physical phones and tablets (emulated in Chrome); hover and press states'
contrast (rest, focus and the two proposal states were measured); a live DeepSeek key (out of
scope); the backend's tests (no file under `server/` changed in this pass — the server last
changed in `b974bc3`). **Not re-measured on the final build itself** (26.4 takes them from the
2026-10-24 sweeps, with what differed): the sign-in room's tier A texts at the phone and tablet
shapes, and the welcome hero at the phone and tablet shapes other than 390 and 820. Tiers B and C
were measured in Chinese only, tier B at three shapes; the text fields' focus frame was not
contrast-measured; the comparison with `75e4e30` covers the shapes 26.4 names.

**Open** (each pre-existing, with its measured figures in 26.3–26.4): the landing dock's current
label on upright tablets (8: 3.36–4.27 bare); the app-wide dark-theme primary button (7: 3.27);
tier C's focus rings and its desktop error line (11: rings 1.34–3.13 on the dock and 2.58–2.97 at
desktop widths, the error line 4.16–4.31 — deferred, the two-tone ring being out of scope; one of
its rings, the dock's current cell at 820, reads lower than at `75e4e30`, 1.34 against 1.49); the toolbar/panel/list positioning (9); the root-token smoke tints a build
resolves by theme (1); the Product room's scroll edge; `yChannel` (B6).
