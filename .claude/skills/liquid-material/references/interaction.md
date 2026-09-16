# Interaction — how the material answers the user

Principles only. No mechanisms, no code, no variable names beyond the ones the
constitution already owns. How a given response is *built* belongs to
`implementation.md`; whether it may exist at all belongs here and to
`constitution.md`.

The governing sentence: **the material acknowledges the user with light, and
with almost nothing else.**

---

## 1. The first principle: reflections move, objects don't

Every legitimate interaction response in this system is a change in *how the
surface is lit*, not a change in where the surface is or what shape it has.

This is not a stylistic preference. It is the honest consequence of the
material: a slab of glass on a desk does not move when you touch it. What
changes is the light on it and through it. A surface that leans toward your
cursor, stretches, bounces, or inflates is claiming to be something soft, and
this material is not soft.

The single mechanical exception is the **settle** (§4) — a sub-pixel
displacement that reads as mass yielding, not as the object animating.

## 2. Damped, never springy

Springs describe elastic objects: overshoot, oscillate, come to rest. Glass has
mass and no elasticity. Its interactions are **damped** — they approach their
target and stop.

- No overshoot. A response that goes past its destination and comes back is
  claiming stored elastic energy the material does not have.
- No bounce, no oscillation, no rubber-banding.
- Slightly heavier than the app's default timing. Weight is expressed as
  *duration and curve*, never as distance.
- Asymmetry is legitimate and desirable: light arrives faster than it leaves. A
  reveal, not a switch. The material remembers a moment longer than it notices.

Damping is what makes restraint read as confidence rather than as absence.

## 3. Hover — light pools, geometry stays

Hover means "a light source has come near." The surface answers by *gathering*
light: existing glow layers brighten, the light-facing rim strengthens,
proximity to the pointer lifts the interior.

Rules:

- **Hover never moves or resizes anything.** No lift, no scale, no bob.
- **Hover is pointer-only, and therefore optional information.** Anything a
  touch user must know cannot be expressed by hover. Hover ranks fourth in the
  navigation interaction hierarchy (`navigation.md` §6) precisely because a
  large fraction of users never see it.
- **Hover pooling is a change in strength, not a new light.** One implied light
  per scene includes the one following the pointer. Adding a second, fixed
  highlight at the hover point puts two suns in one sky.
- **Proximity precedes contact.** The material may respond *before* the pointer
  is on it, falling off with distance. This is the most convincing single
  behavior the material has, and it costs nothing at rest.
- **The spotlight is the light; the wake is the reveal.** On a stage with an
  environment (`environment.md`), the pointer does two things at once: it
  steers reflections across the material (the spotlight) and it wakes the
  wallpaper beneath (the reveal wake). Both read the **same eased cursor**.
  Neither is a second light: the wake opens the atmosphere, it does not emit.
  A stage-scale radial that follows the pointer is neither — it is a
  spotlight-as-reveal, and it is the pattern retired in B3.

## 4. Press — settle plus illumination

Press is the one interaction where the object itself may respond, and it must be
almost imperceptible.

**Settle.** The surface yields by a fraction of a pixel — never more than one —
with a slightly heavy curve, and returns without overshoot. This is mass
absorbing a force. Anything larger becomes a button animation and destroys the
reading of a thick, heavy slab.

Explicitly forbidden: proportional scaling on press, spring return, any
displacement large enough that a user could measure it by eye.

**Press illumination.** The optical half, and currently the *missing* half in
this codebase (`docs/liquid-glass-apple-audit.md` §2.3): the rim brightens under
the press and light gathers at the contact point. Apple's glass "gels" under a
touch — the material acknowledges contact optically rather than kinetically.

This is the correct direction for this system because it is entirely light: it
works identically on touch and pointer, it needs no geometry, it composes with
the existing hierarchy, and it is the cheapest real gain in tactile quality
available. **Shipped (B2)**, mechanism in `implementation.md` §3: a
registered `--glass-press` property set by `.glass-surface:has(:active)`,
tweened over the fast duration, consumed opacity-only by the edge glow (+0.3)
and the Fresnel arc (+0.2); on the dock a pool of light also gathers under the
pressed label (`.dock-item:active::before`, opacity only). The 0.5 px settle
stays as the kinetic half.

**Press must be perceivable without light.** On the frosted and solid tiers, and
under reduced motion, the settle and the state change carry the feedback alone.
Illumination is the enrichment, never the whole signal.

## 5. Focus — an edge lifts, and the ring never dies

Focus is the keyboard user's cursor. It ranks above hover, always.

- **The focus ring is non-negotiable.** It survives every treatment, every tier,
  every backdrop. No optical effect may replace it, dim it, or clip it.
- **The material may add to it**: the light-facing edge of the focused surface
  lifts, as if the surface tilted a degree toward the light. Additive only.
- **Focus is not hover.** They must be visually distinguishable, because they
  mean different things to different users and can occur simultaneously.
- **Focus is persistent; hover is momentary.** The expressions should differ in
  character accordingly — focus holds steady, hover breathes.

Focus illumination is **Shipped (B2)** alongside press illumination (§4): a
registered `--glass-focus` property set by `.glass-surface:has(:focus-visible)`
(keyboard focus, not every click into an input), tweened over the base
duration, lifting the light-facing Fresnel arc (+0.4) — additive to the focus
ring, which every item and the slab keep as `outline: var(--border-width-md)
solid var(--color-focus-ring)`.

## 6. Selection — state, not feedback

Selection is the odd one out: it is not a response to an action, it is a
*standing fact*. The distinction changes everything about how it may be
expressed.

- **Selection must be correct on first paint.** The user who arrives at a view
  and looks at it once must know what is selected. Anything communicated only
  during a transition is invisible to them.
- **Selection may not rely on the travelling light.** On touch, on reduced
  motion, and on every fallback tier the light is off by construction. A
  selection cue built from it does not exist there.
- **Selection needs a non-optical carrier** — weight, a mark, a persistent
  indicator — plus optional light as enrichment. Color alone is insufficient.
- **A moving selection indicator moves once, and lands correctly.** It travels
  between items with a damped settle; it never overshoots, and it never appears
  in the wrong place first and corrects itself. Measure, then place.
- **The indicator is a light, not a slab**, and its width may interpolate with
  the item it marks — the full contract is `navigation.md` §4 ("The indicator")
  and the mechanism `implementation.md` §15. **Shipped (B2)** on the landing
  dock; the desktop rail still marks its row with the solid brand-soft fill.
- **Selection outranks hover and press.** An item that is selected *and* hovered
  reads as selected.

## 7. Depth response

The surface may express *how close* something is, not just whether it is
touched. Proximity is a continuous quantity, and the material's interior
responds to it: internal scattering appears only when a real light is actually
near, edge glow rises as the light approaches, the body's frost thins.

Two constraints:

- **Depth response is multiplicative with the surface's rank.** A `chrome`
  surface responds less than a `hero` one, under identical input. Interaction
  never lets a low-rank surface out-shout a high-rank one — hierarchy survives
  interaction (`adaptive-material.md` §5).
- **Depth response is zero at rest and returns to zero.** A surface with no
  light near it renders as the calm baseline. Nothing accumulates.

## 8. Edge highlight

The rim is the material's most expressive region and its most abused one.

- Highlights are **directional**. The edge facing the light glows; the far edge
  falls dark. Uniform brightness on all four edges is a border, not a Fresnel
  reflection, and it is forbidden.
- The rim's polarity depends on the backdrop: on bright stages, dark ink is the
  visible highlight (`adaptive-material.md` §3).
- Under interaction the rim **strengthens and rotates** — it strengthens with
  proximity and press, and its bearing follows the actual light. It never
  changes hue for emphasis, and never becomes a brand color.
- Undriven, the rim rests as a gentle highlight from above. That resting state
  is the mobile appearance, the reduced-motion appearance, and the appearance
  under which every surface must be reviewed.

## 9. Morph — named, distinguished, and not adopted

Two behaviors get confused under one word. They deserve opposite verdicts.

**Hover-follow deformation** — the surface continuously stretches or leans
toward the pointer while hovered. **Rejected permanently.** It is a liquid-blob
metaphor from a different material; a slab of glass does not lean toward your
finger. This was evaluated and rejected in `docs/liquid-glass-analysis.md` §5.1
and the rejection stands with no expiry.

**Transition-time morphing** — a control that grows into the panel it opened,
or two controls that merge into one as a context changes. Different behavior,
different justification:

- It is **causal**, not decorative: it says *this came from that*, which is
  information the user cannot get any other way.
- It is **bounded in time**: it happens once, on a state change, and ends.
- It is **compatible with mass**: weight constrains the easing curve, not the
  existence of a transition. A heavy thing can still change shape slowly.

The constitution's current text ("reflections move; objects don't") forbids
both, and the audit found that the second was rejected **by association with the
first** rather than on its own merits (`docs/liquid-glass-apple-audit.md` §2.2).

**Status: not adopted.** Adopting transition morphing is a *constitution
amendment* (deferred item P3), not an implementation task, and not something any
phase may introduce by building it first. Until such an amendment exists, the
constitution's text governs and morphing does not ship. This section exists so
the distinction is recorded and the decision, when it is taken, is taken
deliberately.

**What is *not* a morph:** the navigation indicator changing width as it
travels between items of different size (`navigation.md` §4). The indicator
is a light on the slab; the slab keeps its shape. Morph is about the *object*
changing form; a light changing extent is the ordinary behaviour of light.

## 10. Reduced motion

Reduced motion is **zero-by-construction, not a patch**. The light variables
default to zero and the gates never enable them. There is no code path that
first animates and then gets suppressed.

What this means for design:

- **Every state in this document must be fully expressible with all motion
  removed.** Selection, focus, current location, and enabled/disabled must be
  readable in a still screenshot. If a state is only legible in motion, it is
  not designed yet.
- **Reduced motion is not reduced information.** Nothing is dropped; only the
  animation of the transition between states is.
- **Ambient loops freeze.** The one permitted ambient layer stops entirely.
- **Coarse pointer is the same discipline for a different reason.** With no fine
  pointer there is no travelling light, so the mobile appearance is the same
  still expression. Never substitute device orientation or scroll position as a
  pseudo-cursor — that is a second light source and a battery cost for
  decoration.
- **Review with the light off.** The static expression is the *real* expression
  for a large share of users; the travelling light is the enrichment on top.

## 11. Anti-patterns

- ❌ Springs, bounces, overshoot, rubber-banding
- ❌ `scale()` on press; any press displacement large enough to measure by eye
- ❌ Hover lift, hover bob, hover-follow deformation
- ❌ A second light appearing at the pointer instead of the one light brightening
- ❌ Any state whose only expression is motion
- ❌ Selection or focus cues that depend on the travelling light
- ❌ An indicator that lands wrong and then corrects itself
- ❌ Interaction that lets a low-rank surface out-shout a high-rank one
- ❌ Optical feedback replacing, rather than supplementing, the focus ring
- ❌ Introducing morphing without the constitution amendment that permits it
