# Source Review — what the ecosystem offers and what we take

A standing verdict on the public Liquid Glass ecosystem, indexed by
`liquidglassresources.com`. Reviewed 2026-07-31; index re-read and three
entries added or upgraded 2026-09-16 (Apple HIG, MiMo Code landing, NavBar at
source level).

**The rule this document enforces:** we import *ideas*, never architecture.
Nothing here becomes a dependency, a component API, or a code path. When a
project is marked ACCEPT it means an idea was extracted into this knowledge
base; it never means the project was adopted.

**Review depth is recorded per entry.** Most of the ecosystem was reviewed at
the level of the index metadata plus its category page — enough to classify the
technique family, not enough to audit a codebase. Entries reviewed in depth
(README, documented API, or stated design rationale) are marked **[deep]**;
the rest are marked **[survey]** and their verdicts are verdicts about the
*technique family*, which is what we actually needed.

---

## 1. The verdict criteria

A project is judged on four questions, in order:

1. **Does it refract, or does it blur?** Bending the backdrop is the material.
   Blurring it is glassmorphism, which this project forbids. This question alone
   decides most verdicts.
2. **Does it treat glass as a *layer* or as a *style*?** Chrome-only, hierarchy-
   expressing use is correct. A library that offers a glass class for any element
   is selling decoration.
3. **Is there portable knowledge in it?** A design rule, an optical insight, a
   failure mode worth recording. Code is never portable knowledge here — we run
   Vue 3 and one primitive.
4. **Does it respect legibility, motion budget, and accessibility?** Or does it
   spend the user's clarity on the effect?

Verdicts:

- **ACCEPT** — a specific idea was extracted and is now recorded in this skill.
- **PARTIAL** — one idea worth keeping, wrapped in an approach we reject.
- **REJECT** — nothing to take, or actively contrary to the constitution.

No verdict authorizes code. **ACCEPT means "documented," not "adopted."**

## 2. Deep reviews

### Apple — Human Interface Guidelines *Materials* / *Liquid Glass* overview — ACCEPT (the reference the constitution is measured against) **[deep, 2026-09-16]**

The pages are JS-rendered and could not be fetched directly this session; the
positions below are corroborated through WWDC25 session 219 ("Meet Liquid
Glass"), the SwiftUI cheatsheet review below, and secondary write-ups. No rule
in this skill depends on a sentence that could not be read.

- **Accepted (already law):** the material is *"best reserved for the
  navigation layer that floats above the content"*; two variants, Regular
  legible by default and Clear needing a dimming layer; avoid glass on glass;
  nested rounded elements are concentric; content passes under chrome with a
  scroll edge effect that keeps controls legible.
- **Accepted as material states, adapted as tiers:** Reduce Transparency,
  Increase Contrast and Reduce Motion are first-class — ours are tier C and the
  zero-by-construction light (`materials.md` §8).
- **Adapted:** adaptivity to the backdrop — Apple's material reads its
  surroundings; ours is *declared* by the stage (`adaptive-material.md` §7).
- **Not adopted:** fluid morphing between glass states (constitution amendment
  P3 stays open); tinting as a feature.

### MiMo Code landing (`mimo.xiaomi.com/zh/mimocode`) — PARTIAL **[deep, source, 2026-09-14]**

A painting as `background-image`, a 2D `<canvas>` mask (`pointer-events:
none`, hidden on touch), and on `(hover: hover)` only: `mousemove` stamps
"ink dots" every 12px along the path; each grows 8 → 128 × (0.55..1) px over
520ms (`easeOutCubic`), alpha `1 − t²`, edge radius × (0.78 + Σ three seeded
sine wobbles); ≤160 living dots; the loop stops when none live; dots are
`destination-out` from a mask painted in the page colour.

- **Accepted (the principle behind `environment.md` §1 E4):** *the reveal is
  a wake, not a spotlight* — locality from a small per-dot radius, life from
  independent decay, an organic edge from a seeded wobble rather than blur,
  touch gets the wallpaper rather than a dead mask, and the loop settles.
- **Adapted:** our mask opens a neutral-density atmosphere, not a page-colour
  sheet; the wake subscribes to the stage's one eased cursor instead of raw
  `mousemove`; radius, life and count are retuned and capped.
- **Rejected:** its navigation (`blur(12px) saturate(180%)` + a translucent
  fill — glassmorphism); the ink-brush metaphor as *style*; the painting asset.

### `rdev/liquid-glass-react` — PARTIAL **[deep, prior]**

Reviewed in Phase 17 (`docs/liquid-glass-analysis.md`), verdict unchanged and
restated here for completeness.

- **Accepted:** the optics. Edge-weighted displacement mapping, the map as an
  authored optical prescription, chromatic offsets per channel, the "mid-grey is
  neutral" model. This is the closest any web project gets to the real material,
  and it is the ancestor of our own refraction chain.
- **Rejected:** elasticity — continuous hover-time deformation toward the
  cursor. A liquid-blob metaphor from a different material; a slab of glass does
  not lean toward your finger. Rejected permanently (`interaction.md` §9).
- **Rejected:** its React state architecture (two state updates per mousemove)
  and its baked-in fixed positioning. Both are recorded as named failure modes
  in `implementation.md` and `vue-patterns.md` rather than as things to avoid
  by memory.
- **Rejected:** its "shader" mode, which is a CPU loop rather than GPU work —
  the evidence that this material does not need WebGL.

### `unobtuse/einui-claude-skill` — PARTIAL **[deep, prior]**

Reviewed in Phase 18, verdict unchanged.

- **Accepted:** the color methodology. OKLCH-derived palettes with documented
  perceptual math is now the color law (`color.md`).
- **Rejected:** its material. An `rgba` fill plus `backdrop-filter: blur()` is
  precisely the glassmorphism the constitution forbids, regardless of how well
  the rest of the skill is written.
- **The lesson worth keeping:** a well-built knowledge base can be right about
  one domain and wrong about another. Judge per-claim, not per-source.

### `ZyadWKhedr/LiquidGlass-NavBar` (Flutter) — PARTIAL **[deep, source, upgraded 2026-09-16]**

The navigation reference. Flutter, Riverpod, and a third-party glass renderer —
none of which is portable. Its *navigation* thinking is, and it is the primary
source behind `navigation.md` §4. Read at source level this session
(`lib/widgets/navbar_draggable_indicator.dart`, `lib/providers/navbar_providers.dart`):

- **Accepted:** the bar adapts to item count without changing its own shape.
  Spacing and label truncation absorb the variation; the bar's geometry stays
  stable so muscle memory survives.
- **Accepted:** two-stage placement — `initPositions` divides the container
  evenly, then `initMeasuredPositions` reads each item's `RenderBox` centre
  (`localToGlobal(...).dx + size.width / 2`). Restated as a *design*
  requirement: **the indicator must be correct on first paint**, and our
  pre-measure state is *no indicator*, not an approximate one.
- **Accepted:** width follows the marked item with a floor —
  `adaptiveWidth = baseSize × (3.5 / itemCount).clamp(1, 1.2)` in source (the
  README-level summary reports `(3 / itemCount).clamp(0.7, 1.0)`; the source
  wins). We take the idea (width with a floor, clamped inside the bar), not
  the formula.
- **Accepted:** labels ellipsize rather than letting the bar reflow.
- **Reclassified — Adapt, deferred (decision I):** the drag-linked indicator
  (`onHorizontalDragUpdate` offsets continuously, `onHorizontalDragEnd` snaps
  to the nearest measured centre). Previously rejected as "continuous geometry
  following a gesture". Corrected: the constraint governs the *material*; the
  indicator is a *light* on the material (`navigation.md` §6), so a clamped,
  snap-on-release drag of the light is permissible in principle. Not scheduled.
- **Rejected by name:** `LiquidStretch(stretch: .7, interactionScale: 1.05)`
  (hover-follow deformation and scale on interaction), `GlassGlow`,
  `LiquidGlassLayer(lightIntensity 1.5, thickness 20, blur 1)` (a blur
  renderer), and `LiquidRoundedSuperellipse` as a shape (our maps are
  rounded-rect; a superellipse is a map rewrite, not a style).
- **Rejected:** everything structural — Flutter widget composition, Riverpod,
  the renderer package.

### `GonzaloFuentes28/LiquidGlassCheatsheet` (SwiftUI) — PARTIAL **[deep]**

A catalogue of the first-party API, which makes it the best available record of
Apple's own *semantics*.

- **Accepted:** the confirmation that Apple ships exactly **two variants** and
  chooses between them on legibility grounds — which is the rule this project
  already encodes as Clear vs Regular. Independent corroboration of a decision
  already made.
- **Accepted:** the existence of a **container** concept — multiple glass
  elements grouped so they compose as one material rather than as N independent
  surfaces. Our analogue is "one scene, one light" plus the instance budget; the
  underlying insight is the same one, that glass elements in proximity must be
  reasoned about collectively.
- **Accepted, as a distinction:** the framework separates *interactivity* from
  *appearance* as an explicit opt-in. Interaction is a property a surface
  declares, not something every glass thing gets for free.
- **Noted, not adopted:** identity-based morphing between glass elements. This
  is the transition-morph question, which is a constitution amendment and not
  ours to take from a cheatsheet (`interaction.md` §9).
- **Rejected:** tint-as-a-feature. Arbitrary color applied to the material is a
  brand wash, which the constitution forbids. Our color comes from tokens and
  the body is smoke.
- **Rejected:** everything API-shaped. SwiftUI modifiers are not a component
  contract we can or should mirror.

### `lucasromerodb/liquid-glass-effect-macos` (CSS + SVG) — ACCEPT (idea only) **[deep]**

Pure CSS and SVG filters, no framework. The closest technique cousin to our own
primitive in the whole web ecosystem.

- **Accepted:** the confirmation that CSS plus SVG filters is sufficient — no
  canvas, no WebGL, no per-pixel JavaScript. This is the load-bearing
  justification for `constitution.md` §4.5, and it is worth having an
  independent implementation prove it.
- **Rejected:** its structure. A demo page is not an architecture, and our
  primitive already exists.
- **The general lesson:** the demos that look most like the real material are
  invariably the ones using displacement, not blur. Technique predicts result
  reliably enough to be used as a filter when triaging any new resource.

### `QmDeve/AndroidLiquidGlassView` (Jetpack Compose) — PARTIAL **[deep]**

Notable for claiming *real refraction and dispersion*, which almost nothing
else outside Apple does.

- **Accepted:** corroboration that **dispersion at the rim is a required
  ingredient**, not a flourish. Two independent non-Apple implementations
  reaching for it, on different platforms, from different starting points, is
  the strongest available evidence that the constitution is right to require it.
- **Rejected:** platform architecture entirely.

## 3. Survey verdicts by family

Every remaining resource in the index falls into one of these families. The
verdict applies to the family; individual projects were not audited.

| Family | Representative entries | Verdict | Why |
|---|---|---|---|
| **CSS + SVG displacement demos** | WWDC 2025 Liquid Glass Effect, Liquid Glass Demo, glass-refraction | **PARTIAL** | Correct technique, no architecture. Confirms CSS+SVG sufficiency; occasionally a useful map-authoring idea. Nothing to import. |
| **Pure-CSS "Apple glass" cards** | Pure CSS Apple Liquid Glass User Card, Apple Liquid Glass UI (2025), Apple Liquid Glass with CSS, Liquid Glass Effect 🌟 | **REJECT** | Blur plus a border plus transparency. This is the frosted rectangle the constitution names as the forbidden idiom, wearing Apple's name. |
| **Tailwind glass utilities** | Water, Tailwind CSS Liquid Glass, glasswindui, creativoma/liquid-glass, Liquid Glass for React & Tailwind, David UI components | **REJECT** | Two independent disqualifications: the material is blur-based, and utility classes make glass a *style applicable to anything*, which inverts the layer rule. Glass as a utility is how a codebase ends up with glass on content. |
| **React component libraries** | Vaso, liquid-glass-react (Specy), Liquid Glass React (Rovensky), React Premium Glass, react-magic-ui, Liquid Glass UI | **REJECT** | Component APIs for a framework we do not use, wrapping materials that are mostly blur. We have one primitive; importing a second component model is the exact fragmentation the guards exist to prevent. |
| **Vue component libraries** | vue-liquid-glass (Daisigu), Liquid Glass Vue (Muggleee), Liquid Glass Vue (WXperia), Liquid Web | **REJECT** | Same framework as us, which makes them *more* dangerous, not less. Adopting any of them means a second glass primitive on day one — the single rule the whole system is built to hold. Reviewed and closed. |
| **Svelte libraries** | Svelte Liquid Glassifier, Liquid Glass Svelte | **REJECT** | Wrong framework, no transferable design content. "Glassifier" — a transformer that makes arbitrary elements glass — is the layer rule violated by design. |
| **CSS/JS frameworks and switchers** | Liquid Glass Framework, Apple Liquid glass switcher | **REJECT** | A framework-shaped dependency for a material we already own, at a lower fidelity. |
| **Flutter packages** | flutter_liquid_glass, Liquid Glass Renderer, Liquido, Liquid Glass Easy, Liquid-Glass-Bar, OneClient, Flutter Liquid Glass Demo, Cupertino Native, Adaptive Platform UI | **REJECT** | No web target. Shader/renderer approaches confirm the effect is achievable at high fidelity given GPU access; that observation is already recorded and needs no further sourcing. |
| **Android / Compose libraries** | Android Liquid Glass, Liquid Glass Compose, LiquidGlass-JetpackCompose, Apple Liquid Glass for Android, Liquid-Glass-Android, Cloudy, FloatingTabBar | **REJECT** (except the dispersion note in §2) | No web target, no portable design rationale. |
| **GPU / shader implementations** | Prismal (OpenGL) | **REJECT** | Directly contrary to `constitution.md` §4.5. Also the least portable class of work in the index. Its existence is useful only as a bound: what a shader buys over CSS+SVG is smoothness, not correctness. |
| **iOS / SwiftUI native** | Glasskit, CrystalKit, FabBar, Liquid Glass Swift | **REJECT** as code; **useful as evidence** | Native libraries mirror Apple's own semantics — two variants, chrome-layer usage, container grouping. That evidence is already captured in §2 via the cheatsheet. |
| **Figma / Framer / Webflow kits** | iOS 26 Liquid Glass, Liquid Glass Edge Refraction, Liquid Glass Pro Plugin, AppleLiquidButton, Glass Navbar Effect, and the rest of the design category | **PARTIAL** (one entry), **REJECT** (rest) | Design kits reproduce the *look* without the optics; a Figma glass style is a blur with a gradient by necessity. The one recurring exception is edge-refraction studies, which visualize how much of the material's identity lives in the rim — a point the constitution already makes and these corroborate. |
| **The index itself** (`liquidglassresources.com`) | — | **ACCEPT** as a map | A curated **directory**, not a specification (re-read 2026-09-16: mobile / web / design categories, no entries on backdrop adaptivity or cross-browser SVG technique). Valuable as a survey of what the ecosystem is doing, and the survey's own result is the finding in §4. |

## 4. What the survey actually proved

The most useful output of reviewing ~60 projects is not a list of ideas. It is
the shape of the distribution:

1. **The overwhelming majority of the web ecosystem is building glassmorphism
   and calling it Liquid Glass.** Blur plus transparency plus a border, in every
   framework. Our constitution's core distinction is not a niche stylistic
   position — it is the thing almost nobody else is doing.
2. **The projects that look right are the ones that displace rather than blur.**
   Technique predicts outcome closely enough to triage on. This is a reusable
   filter for any future resource: *does it bend the backdrop?* If not, stop
   reading.
3. **Almost nobody addresses the layer rule.** Utility classes, "glassifiers,"
   and general-purpose glass components all encourage glass on arbitrary
   elements, including content. Apple's own guidance says the opposite — the
   navigation layer, and not everywhere — and so does ours. This is the second
   largest divergence between the ecosystem and the real material.
4. **Adaptivity is absent everywhere outside Apple.** Not one surveyed web
   project adapts to its backdrop. Our gap (`adaptive-material.md`) is the
   ecosystem's gap; there is no prior art to borrow, which is a reason to design
   it deliberately rather than to copy.
5. **Nothing surveyed changes any decision already taken.** Every idea marked
   ACCEPT above either corroborates an existing rule or fills a documentation
   gap. No contradiction with the constitution was found.

## 5. Standing rules for future resources

- Triage on technique first: **displacement or blur?** Blur-based work is
  closed at that point.
- Extract rules, failure modes, and vocabulary. Never extract components, props,
  class names, or file layouts.
- A Vue library in this space is a **higher** risk than a React one, not a
  lower one — proximity makes importing it feel reasonable.
- Corroboration has real value. When two independent implementations converge on
  a requirement, record the convergence; it is the strongest evidence available
  outside Apple's own documentation.
- Record the verdict here when a resource is reviewed, including REJECT, so the
  same repository is not re-litigated next phase.
- **Distinguish the layer a technique targets.** A behaviour rejected for the
  material (geometry following a gesture) may be acceptable for an environment
  layer or an indicator light. Classify per layer, not per source.
- **Read the source, not the README, before quoting a number.** The NavBar
  width formula differs between the two.
