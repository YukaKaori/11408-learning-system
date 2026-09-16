# Adaptive Material — what the material responds to

Apple's Liquid Glass is *content-aware*: one material works over a photo, a
document, a video, and a dark home screen, because it adapts. That adaptivity is
the reason it can be a single material rather than a family of them.

Ours does not adapt yet, **by decision, not by oversight** — and since
2026-09-16 the mechanism is specified: a **stage-declared backdrop** (Contract
B4, §6) and a **boot-resolved material tier** (Contract B1, `materials.md` §8).
This document records what adaptation means, what the inputs are, why the
project postponed the mechanism, and why it will be declared, never sampled.
Governed by `constitution.md` §9; the current state of the system is
`docs/liquid-material-system.md` §7 and `docs/liquid-glass-apple-audit.md` §2.1.

---

## 1. The claim adaptation makes

A material that adapts says: *"place me anywhere and I will stay legible."*
A material that does not adapt says: *"place me on a stage you control."*

Both are honest positions. The second is the one this project holds today, and
it has a measurable consequence: the material reached **three surfaces in eight
phases**, because every candidate surface first needed a backdrop the team had
already designed. The budget of 3 is a symptom of that, not the cause.

Understanding this is the point of the document. Adaptation is not a polish
item — it is the *reach* mechanism. Nothing else in the system unlocks new
surfaces.

## 2. The five inputs

Material appearance is a function of five things. Only the last two currently
have any input path in this codebase.

### Background — what is behind the surface

The single largest determinant. Not "what color is the page" but **what the
surface is placed over**: a controlled stage the view designed, or arbitrary
content the view has never seen. This distinction, not brightness, is what
selects Clear vs Regular (`navigation.md` §5).

### Luminance — how bright that background is

The dial that determines *how much* smoke the body needs and *which polarity*
the rim highlights take. A bright backdrop needs a denser body to keep on-glass
labels readable, and its edge highlights must go **darker** to be visible at all
— a white rim on white artwork is invisible, which is why the Phase 12 dock flip
to dark ink existed before any token layer did.

Luminance is a property the **view knows and the surface does not**. See §7.

### Density — the response, not an input

Density (`--material-density-*`) is how the material answers the first two.
It is the legibility dial and the only one: legibility is bought by **darkening,
never by whitening**. There is no white-fill dial and there will not be one
(`docs/liquid-material-system.md` §3).

The three shipped values are a fixed answer to three known backdrops:

| Preset | Density | The backdrop it was tuned against |
|---|---|---|
| `chrome` | `.16` | The app's own stage behind the dock |
| `hero` | `.34` | The login artwork — brighter, so denser, inside the Clear band |
| `floating` | `.62` | Arbitrary note content — unknown, so it carries legibility alone |

Read that table as three *frozen samples* of a curve the system cannot yet
compute.

### Interaction — the material under a pointer or a press

A material that responds to touch is adapting to a fourth thing: the user.
Proximity lifts glows and thins frost; press should brighten the rim; focus
should lift an edge. Full treatment in `interaction.md`. The relevant point
here: **interaction adaptation is per-surface and per-frame, backdrop adaptation
is per-stage and near-static.** They are different mechanisms and must never be
merged into one "smart material" abstraction.

### Accessibility — the user's declared preferences

Apple treats Reduce Transparency and Increase Contrast as **first-class material
states**, not as degradations. So should we:

- `prefers-reduced-transparency` — the user has asked for less translucency.
  The answer is **tier C `dense`** (`materials.md` §8): density up, transmission
  down, every rim and depth cue kept. **Shipped (B1)** in
  `styles/materialTier.ts` (was deferred item P2 in
  `docs/liquid-material-system.md` §7).
- `prefers-contrast: more` — the same tier C: labels and edges strengthen,
  optics recede.
- `prefers-reduced-motion` — already zero-by-construction. The light variables
  default to 0 and the gates never enable them (`SKILL.md` §5).
- Coarse pointer — not an accessibility preference but the same shape of input:
  the spotlight never ignites, so the material must stand up statically.

These belong in **one boot-resolved decision applied everywhere**, not three
independent per-instance probes (`implementation.md` §6).

## 3. The four backdrops

Two axes — luminance and busyness — give four cases. Each has a different
failure mode, and the failure modes are what make the taxonomy worth keeping.

```
            plain                    busy
       ┌────────────────────┬────────────────────┐
 light │  legibility risk   │   worst case       │
       │  labels wash out   │   labels + rim     │
       │  rim invisible     │   both fail        │
       ├────────────────────┼────────────────────┤
  dark │  best case         │  refraction shines │
       │  material vanishes │  needs density     │
       │  if under-dense    │  to stay calm      │
       └────────────────────┴────────────────────┘
```

### Light backdrop

The hard case, and the one the web usually gets wrong. Two things break at once:

- **On-glass labels wash out.** The fixed dusk palette assumes a darkened body
  beneath it; over bright artwork with low density, contrast collapses.
- **The rim disappears.** Bright edge highlights are defined by being brighter
  than what surrounds them. Over white, they are nothing.

The correct response is *not* a lighter, whiter material — that is
glassmorphism, and it hides content. It is **more smoke plus inverted rim
polarity**: the body darkens, the edges go to dark ink. The Phase 12 dock flip
is exactly this behavior, discovered empirically before there was a name for it.
Under Contract B4 it is the `[data-material-backdrop='light']` remap in
`glass.css`: density up inside a declared band per rank, rims to dark ink, the
dock's halo tokenized — and the flip stops being a private override in
`LoginView`.

### Dark backdrop

The comfortable case, and the one every shipped surface uses. The risks are
subtler:

- **Under-density makes the material vanish.** A near-transparent slab on a dark
  stage has no visible body; only its rim proves it exists, and the surface
  reads as a floating border.
- **Over-density makes it a hole.** Past a point, the material stops
  transmitting and becomes a black rectangle — the opposite of the promise. If a
  dark-stage surface needs high density to be legible, its *depth* layers are
  failing, not its body.

### Busy backdrop

Photos, video, dense text, a graph canvas, a code editor. Detail behind the
surface competes with detail on it.

- Refraction is at its most convincing here — a bent edge is only visible when
  there is structure to bend. Busy backdrops are where the material earns its
  cost.
- But **transmission must fall**. This is the Regular case by definition: the
  surface cannot predict what is behind it, so it carries its own legibility.
- The graduated treatment at the boundary matters more than anywhere else —
  content should dissolve as it approaches the surface rather than collide with
  its edge. See `scroll-edge.md`.

### Plain backdrop

A flat color, an empty stage, a solid panel.

- Refraction has **nothing to bend**, so the signature effect is invisible and
  the material reads as a tinted rectangle with a rim.
- This is the strongest argument for *not* using glass. If the backdrop is plain
  and stays plain, a solid surface is more honest and costs nothing
  (`navigation.md` §5, question 1).
- If glass is nonetheless correct (the backdrop is plain *now* but the surface
  is persistent and content will scroll under it), lean the whole expression on
  **depth** — double rim, back-face reflection — which works with no backdrop
  structure at all.

## 4. Adaptation is not the same as decoration

Two things look alike from the outside and are not:

- **Adaptation** changes the material so the *same* design decision keeps
  holding under a different backdrop. It preserves intent.
- **Theming** changes the material because someone wanted a different look. It
  changes intent.

Only the first is legitimate here. A backdrop contract that becomes a way to
give each page its own glass flavour has failed, and would violate
"material exists to express hierarchy, never decoration."

## 5. Adaptation must not change rank

The single hardest constraint. Under any backdrop, the optical hierarchy must
survive: chrome stays quieter than hero, and a view never grows a second hero.
Adaptation moves the *whole scale* to fit the stage; it never reorders it.

A material that becomes denser on a bright page must make **every** surface on
that page denser, or the ranking inverts and the user's read of the hierarchy
inverts with it.

## 6. What is specified, precisely — and by which phase

*Rewritten 2026-09-16. Old decision: P1 and P2 "deferred, not authorized".
New decision: both are contracts with an owning phase; the reasons for the
original deferral (§7) still hold and are why the order is B1 → B4.*

**The backdrop declaration — Contract (B4).** `data-material-backdrop="dark |
light"` on the stage element; `glass.css` remaps, under `[data-material-backdrop
='light']`, exactly two things per rank: density (up, inside a declared band)
and rim polarity (light rims → dark ink). The token guard pins the remapped
values. Nothing samples.

Who declares:

| Stage | Source of the declaration |
|---|---|
| the authenticated shell (`AppLayout`) | derived from the theme (`isDark`) — the shell's backdrop *is* the theme, which is why P1 was unblocked |
| the login stage | authored per gallery: `dark` for the black installation in both themes; `light` for the Product room in both |
| a future stage | whoever owns the stage, at author time |

**Theme ≠ backdrop.** The tint tokens already flip via `light-dark()`
(`--material-tint-*`), which follows the *theme*; the declaration follows the
*stage*. A black stage under the light theme declares `dark`.

**The material tier — Contract (B1).** `prefers-reduced-transparency` and
`prefers-contrast: more` fold into tier C `dense`, decided once at boot with
engine capability, exposed as `data-glass-tier` on `<html>` (`materials.md`
§8, `implementation.md` §6–7). Reduced motion and pointer coarseness gate the
*light*, not the tier.

Nothing here authorizes building either outside its phase.

## 7. Why the declaration was postponed — and why it will be *declared*, not sampled

Two separate decisions, often confused.

### Why postponed

Phase 17.2 had to choose between *coherence* and *reach*, and chose coherence:

1. **The app was running two glass materials at once**, and the forbidden one
   (an `rgba` fill plus `backdrop-filter: blur()` — plain glassmorphism) had
   *wider reach* than the real one. Adding adaptivity to the correct material
   while the incorrect one spread further would have widened the gap, not closed
   it. Retiring the second material had to come first.
2. **Adaptivity has no floor to stand on without a token layer.** The optical
   dials were magic numbers typed at three call sites. A backdrop contract
   remaps tokens; if there are no tokens, there is nothing to remap. Phase 17.2
   built that floor (`--material-*` in `tokens.css`, presets in `glass.css`).
3. **Adaptivity is a reach mechanism, and reach without guards is how the
   material got fragmented the first time.** The budget test, the token test,
   and one primitive had to be load-bearing *before* the material was allowed to
   travel.
4. **It is a design decision with a rank problem attached** (§5), not an
   implementation task. Presets, ranks, and the Clear/Regular rule had to be
   named and shipped first — they were, in Phase 17.2 — so that adaptation has
   something well-formed to adapt.

The order was: one material → tokens → presets and variants → *then* adaptivity.
Each step is a precondition of the next.

### Why declared, not sampled

`docs/liquid-glass-analysis.md` §7.5 rejects automatic luminance sampling, and
`docs/liquid-material-system.md` §7 restates it: **do not sample backdrop
luminance — the view knows whether it is bright; let it say so.**

The reasons compound:

- **The view already has the answer.** Sampling spends real work recovering a
  fact the code above it knew at author time. That is the definition of a
  guess replacing a declaration.
- **Sampling is per-frame work for a near-static property.** A backdrop's
  brightness changes on navigation, not on scroll. Reading pixels every frame to
  learn something that changes once per route violates the performance budget
  (`constitution.md` §4.6) for no gain.
- **The tools are unreliable.** There is no cheap, correct way to read the
  composited backdrop of an element in a browser. Every available approach —
  canvas readback, a hidden duplicate render, sampling a source image — is
  either expensive, wrong at the edges, or blocked outright.
- **It oscillates.** A material that reads its own backdrop while the backdrop
  scrolls will hunt: denser, lighter, denser. Any damping added to stop the
  hunting is a slow lag the user perceives as the UI being unsure.
- **It is untestable.** A declared token can be asserted in a unit test. A
  sampled value can only be verified by looking at it.
- **It hides authorial mistakes.** If a surface is placed on a stage where it
  cannot work, a declaration makes that visible in the source. Sampling papers
  over it with a value nobody chose.

The rule to carry forward: **the stage declares its backdrop; the surface
obeys.** Detection is the fallback of last resort, and it is not needed here.

### Why the shell's backdrop turned out to be knowable

The deferral assumed no authenticated stage had a known backdrop. The shell
does: it paints `--color-bg` behind every route, and `--color-bg` is a function
of the theme the app already writes to `<html>`. The declaration therefore costs
one attribute derived from state that exists, and P1 stopped being a phase and
became a token block (B4). The *landing* is the opposite case — its backdrop is
authored per gallery and does not follow the theme — which is why the
declaration is stage-owned rather than theme-owned.

## 8. Anti-patterns

- ❌ **Sampling backdrop luminance at runtime** to pick a material appearance.
- ❌ **Whitening for legibility.** Density darkens. There is no white-fill dial.
- ❌ **Per-surface backdrop opinions.** A stage declares once; every surface on
  it agrees. Two surfaces in one view disagreeing about the backdrop is two
  light sources by another name.
- ❌ **Adaptation that reorders rank** (§5).
- ❌ **Treating reduced transparency as a bug state.** It is tier C, designed
  (`materials.md` §8).
- ❌ **Confusing theme with backdrop.** A black stage in the light theme is a
  dark backdrop; the declaration follows the stage.
- ❌ **Three independent per-instance capability probes.** One boot-resolved
  decision, reflected once on the root.
- ❌ **Building any of §6 outside a phase that owns it.** This document is
  knowledge, not authorization.
