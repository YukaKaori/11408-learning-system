# Liquid Glass — Material Analysis & Adaptation Study

**Subject:** [`rdev/liquid-glass-react`](https://github.com/rdev/liquid-glass-react) (5.7k ★, `master`)
**Read against:** this project's `GlassSurface.vue` + `glass.css` + `useGlassSpotlight.ts`
**Purpose:** extract the *material system* behind Apple's Liquid Glass, not the React code.
**Status:** analysis only. No code changed. Awaiting instruction.

> Governing document: `.claude/skills/liquid-material/references/constitution.md`. Where this
> analysis and the design system disagree, the design system wins. Several of
> the reference library's most-copied features are **rejected below** precisely
> because they contradict it.

---

## 0. Executive summary

The reference library is worth studying for exactly **three** ideas:

1. **Edge-only displacement.** The centre of the slab passes through
   *undistorted*; refraction is masked to the rim. This is the single most
   important structural insight, and it is what separates "lens" from "smear".
2. **A signed-distance-field displacement map.** The map is derived from a
   rounded-rect SDF, so the refraction knows where the corners are. Corner-aware
   refraction is what makes glass read as a *shape* rather than a rectangle.
3. **Per-channel displacement at different scales.** Chromatic dispersion is
   produced by displacing R, G, B by slightly different amounts and
   screen-blending them back — not by drawing coloured borders.

Everything else in that repository is either (a) already done better here, or
(b) actively contradicts our material philosophy. In particular the headline
feature — **`elasticity`, the "liquid" stretch toward the cursor — must not be
adopted.** It is a *liquid blob* metaphor. Ours is *heavy optical glass*. A slab
of glass does not lean toward your finger.

Our `GlassSurface` is, on balance, the more advanced implementation. It has
depth layering, directional Fresnel, ND smoke tint, an unwrapped light angle,
rAF-interpolated lighting with asymmetric attack/release, and reduced-motion
correctness — none of which the reference has. What we can genuinely take from
it is **map quality** (SDF, corner-awareness) and **filter-space discipline**
(explicit edge mask).

One concrete finding on our own code is recorded in §6.4: our default
`yChannel: 'G'` selects a channel our displacement map does not meaningfully
encode. Worth verifying before Phase 18 work.

---

## 1. Liquid Glass rendering principles

### 1.1 What refraction actually is, in a browser

There is no ray tracing. Every "refraction" on the web is one operation:

> **For each output pixel, sample the backdrop from a *different* pixel.**

`feDisplacementMap` is that operation. It reads a second image (the
*displacement map*) and uses two of its colour channels as a per-pixel offset
vector:

```
P'(x, y) = P( x + scale × (mapR(x,y)/255 − 0.5),
              y + scale × (mapB(x,y)/255 − 0.5) )
```

So a map value of `128` = "sample from here" (no displacement), `0` =
"sample from `−scale/2` away", `255` = "sample from `+scale/2` away".
**Mid-grey is the neutral element.** Every displacement map is therefore a
grey field with deviations near the edges.

This single fact drives the entire design: *refraction strength is authored as
an image*, and that image is the material's optical prescription.

### 1.2 How the reference generates distortion

Four modes, only one of which is interesting.

| Mode | Source | Verdict |
|---|---|---|
| `standard`, `polar`, `prominent` | Three hard-coded base64 **JPEG** blobs in `src/utils.ts` | Reject (§5.6) |
| `shader` | Runtime-generated, per-pixel, on a 2D canvas | **Study this one** |

The `shader` mode (`src/shader-utils.ts`, adapted from `shuding/liquid-glass`)
is *not* WebGL despite the name. It is a CPU double loop evaluating a fragment
function per pixel:

```ts
liquidGlass: (uv: Vec2): Vec2 => {
  const ix = uv.x - 0.5
  const iy = uv.y - 0.5
  const distanceToEdge = roundedRectSDF(ix, iy, 0.3, 0.2, 0.6)
  const displacement  = smoothStep(0.8, 0, distanceToEdge - 0.15)
  const scaled        = smoothStep(0, 1, displacement)
  return texture(ix * scaled + 0.5, iy * scaled + 0.5)
}
```

Read optically, this says:

- `roundedRectSDF` → signed distance to the rounded-rect boundary. Negative
  inside, zero on the edge, positive outside. **The map is corner-aware.**
- `smoothStep(0.8, 0, d − 0.15)` → a smooth falloff that is ~0 deep inside the
  shape and rises toward the rim. This is the **thickness profile** of a lens.
- `ix * scaled` → the sample point is pulled **toward the centre**, scaled by
  that profile. Deep inside (`scaled → 1`) the sample is unmoved. Near the rim
  (`scaled → 0`) the sample collapses inward.

That last line is the whole trick: **a lens magnifies by sampling from nearer
the optical axis, and it does so hardest where the glass is thickest — at the
curved rim.** It is a genuine (if crude) plano-convex lens model, evaluated once
into an image.

The generator then encodes it (`shader-utils.ts:112-119`):

```
R = dx/maxScale + 0.5     // X displacement
G = dy/maxScale + 0.5     // Y displacement
B = dy/maxScale + 0.5     // Y again — for SVG filter compatibility
A = 255
```

Two details worth stealing:

- **`maxScale` normalisation** with a floor of 1, so a nearly-flat map is not
  amplified into noise.
- **A 2px edge feather** (`edgeFactor = min(1, edgeDistance / 2)`) that ramps
  displacement to zero at the map's own boundary. Without it the map's outer
  pixels create a hard tearing ring. This is a real artifact fix.

### 1.3 How background displacement is applied

`backdrop-filter` is the mechanism (`src/index.tsx:190-193`):

```ts
const backdropStyle = {
  filter: isFirefox ? null : `url(#${filterId})`,
  backdropFilter: `blur(${(overLight ? 12 : 4) + blurAmount * 32}px) saturate(${saturation}%)`,
}
```

The distorted layer is a dedicated `<span class="glass__warp">` at `inset: 0`,
**separate from the content layer**, which stays at `z-index: 1` and sharp. This
separation is non-negotiable and matches our design system rule *"the material
sits behind content, never on top of it."* Our `.glass-surface__content` does
the same thing.

### 1.4 The edge mask — the most valuable idea in the repository

The reference's SVG filter does something our implementation does not do
explicitly. It builds a mask **from the displacement map itself** and uses it to
composite displaced edges over an *undisplaced* centre
(`src/index.tsx:55-124`):

```xml
<!-- 1. luminance of the map -->
<feColorMatrix in="DISPLACEMENT_MAP" type="matrix"
  values="0.3 0.3 0.3 0 0  0.3 0.3 0.3 0 0  0.3 0.3 0.3 0 0  0 0 0 1 0"
  result="EDGE_INTENSITY"/>

<!-- 2. quantise it into a hard 3-step edge mask -->
<feComponentTransfer in="EDGE_INTENSITY" result="EDGE_MASK">
  <feFuncA type="discrete" tableValues="0 {aberrationIntensity*0.05} 1"/>
</feComponentTransfer>

<!-- 3. keep a pristine copy of the backdrop -->
<feOffset in="SourceGraphic" dx="0" dy="0" result="CENTER_ORIGINAL"/>

<!-- … three displaced channels, blended … -->

<!-- 4. aberration only where the mask says "edge" -->
<feComposite in="ABERRATED_BLURRED" in2="EDGE_MASK" operator="in" result="EDGE_ABERRATION"/>

<!-- 5. invert the mask, keep the centre clean -->
<feComponentTransfer in="EDGE_MASK" result="INVERTED_MASK">
  <feFuncA type="table" tableValues="1 0"/>
</feComponentTransfer>
<feComposite in="CENTER_ORIGINAL" in2="INVERTED_MASK" operator="in" result="CENTER_CLEAN"/>

<!-- 6. edge over centre -->
<feComposite in="EDGE_ABERRATION" in2="CENTER_CLEAN" operator="over"/>
```

**Why this matters to us:** it decouples two dials that are currently fused in
our implementation — *how hard the rim bends* and *how clear the centre stays*.
Today we get centre clarity implicitly, because the blurred mid-grey core rect
in our generated map flattens the middle. That works, but it means raising
`distortionScale` for more edge energy also softens the centre. An explicit mask
lets Edge Energy rise without spending Transmission.

This directly serves design principle 3 (*"refraction is strongest at edges and
corners where a real lens distorts most"*) and principle 11 (*"glass reveals
content; it never hides it"*).

### 1.5 How highlights are generated

Chromatic dispersion, first — three displacements at slightly different scales,
isolated to one channel each, screen-blended:

```xml
<feDisplacementMap scale={s * 1}                      result="RED_DISPLACED"/>
<feDisplacementMap scale={s * (1 - aberration*0.05)}  result="GREEN_DISPLACED"/>
<feDisplacementMap scale={s * (1 - aberration*0.10)}  result="BLUE_DISPLACED"/>
<feBlend in="GREEN_CHANNEL" in2="BLUE_CHANNEL" mode="screen" result="GB_COMBINED"/>
<feBlend in="RED_CHANNEL"   in2="GB_COMBINED"  mode="screen" result="RGB_COMBINED"/>
<feGaussianBlur stdDeviation={max(0.1, 0.5 - aberration*0.1)}/>
```

This is physically motivated: different wavelengths refract by different
amounts, so red/green/blue land at slightly different places, and the split is
naturally largest where displacement is largest — the rim. **We already do
exactly this** via `redOffset` / `greenOffset` / `blueOffset` (`GlassSurface.vue:220-275`).
Our formulation is additive (`distortionScale + offset`), theirs multiplicative;
ours is easier to reason about. No change needed.

Specular/rim highlights, second — two stacked ring layers using the
padding + `mask-composite: exclude` ring trick (`src/index.tsx:509-559`):

```ts
padding: "1.5px",
WebkitMask: "linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)",
maskComposite: "exclude",
boxShadow: "0 0 0 0.5px rgba(255,255,255,.5) inset, 0 1px 3px rgba(255,255,255,.25) inset, 0 1px 4px rgba(0,0,0,.35)",
background: `linear-gradient(${135 + mouseOffset.x * 1.2}deg, …)`,
```

One layer at `mix-blend-mode: screen` (opacity .2), one at `overlay`. The
gradient *angle and stop positions* track the mouse.

Assessment: the ring construction technique is sound and we already use it
(`.glass-surface__fresnel` uses the identical `mask-composite: exclude` idiom,
with a `@supports not` bail-out they lack). But their light model is fake — a
`linear-gradient` whose angle is a linear function of cursor X. Ours computes a
**real** light bearing from the card centre with `atan2`, unwrapped across ±180°
so the arc glides rather than snapping (`useGlassSpotlight.ts:173-186`), and
feeds it to a `conic-gradient`. Ours is correct physics; theirs is a heuristic.
**Keep ours.**

### 1.6 How depth perception is created

The reference creates depth from four cues:

1. A double inset `box-shadow` on the ring (bright 0.5px line + 1px inner
   bright + 1px outer dark) → a two-sided bevel.
2. Two ring layers in different blend modes → the rim reads as having thickness.
3. A drop shadow, boosted when `overLight` (`0px 16px 70px rgba(0,0,0,.75)` vs
   `0px 12px 40px rgba(0,0,0,.25)`).
4. `text-shadow` on the content so it appears to float above the slab.

This is a **thin** depth model, and cue 3 is one our design system explicitly
forbids (*"❌ heavy drop shadows — depth comes from internal optics, not from
40px-blur black halos"*). Their `70px/0.75` shadow is precisely the pattern we
ruled out.

Our depth model is materially richer — front rim, inner back rim offset 1px
downward, back-face reflection, internal scattering gated on light presence, ND
smoke body (`GlassSurface.vue:378-441`). **We are ahead here. Nothing to adopt.**

The one idea worth keeping from cue 3's *neighbourhood* is `overLight` as a
**concept**: the material knows whether it sits over a bright or dark backdrop
and re-tunes itself. Their implementation is crude (two black overlay divs, one
at `opacity-20`, one at `mix-blend-overlay`, plus halving `displacementScale`).
Our ND smoke tint (`--glass-density`) already solves the legibility half. What we
lack is the *automatic* half — see §7.5.

---

## 2. Technical implementation review

### 2.1 CSS techniques inventory

| Technique | Reference | Us | Note |
|---|---|---|---|
| `backdrop-filter: url(#filter)` | ✅ | ✅ | Chromium-only in practice |
| `backdrop-filter: blur() saturate()` | ✅ | ✅ | |
| Ring via `padding` + `mask-composite: exclude` | ✅ | ✅ | We add `@supports not` bail-out |
| `mix-blend-mode: screen / overlay` | ✅ heavy | ❌ | See §5.7 |
| `color-mix()` gated alphas | ❌ | ✅ | Lets layers cost nothing when off |
| `light-dark()` | ❌ | ✅ | |
| CSS custom properties as the state channel | ❌ | ✅ | Their state lives in React |
| `conic-gradient` Fresnel arc | ❌ | ✅ | |
| `@supports` feature detection | ❌ | ⚠️ partial | We also UA-sniff — §6.5 |

### 2.2 SVG filter usage

Both implementations converge on the same primitive chain. The material
difference is **where the edge weighting lives**:

- **Reference:** map is a lens profile; edge weighting is done in *filter space*
  (`feColorMatrix` → `feComponentTransfer` → `feComposite`).
- **Ours:** edge weighting is baked into the *map* — a blurred, bright,
  inset rounded rect flattens the middle to neutral grey
  (`GlassSurface.vue:140`).

Ours is cheaper (fewer filter primitives → fewer full-canvas passes). Theirs is
more controllable. A hybrid is recommended in §7.2.

Filter region also differs: theirs is `x="-35%" y="-35%" width="170%" height="170%"`
(room for the displacement to sample outside the box); ours is `0%/0%/100%/100%`.
A tight region is faster and avoids halo bleed; it also clips extreme
displacement. Ours is the right default given `distortionScale: -180`.

### 2.3 Canvas / WebGL / shader usage

**There is no WebGL in this repository.** "Shader mode" is a marketing name for
a JS function evaluated in a nested loop:

```ts
for (let y = 0; y < h; y++)
  for (let x = 0; x < w; x++) {
    const pos = this.options.fragment({ x: x/w, y: y/h })
    …
  }
this.context.putImageData(imageData, 0, 0)
return this.canvas.toDataURL()
```

Cost profile: for a 400×200 surface that is **80,000 iterations**, each doing
`roundedRectSDF` + two `smoothStep`s, then a second 80,000-iteration encode
pass, then a synchronous `toDataURL()` PNG encode — **on the main thread, at
mount, and again on every window resize** (`src/index.tsx:183-188` re-runs on
`glassSize` change; `glassSize` is set from a `resize` listener with no
debounce). On a large hero surface during a window drag this is a guaranteed
frame-rate collapse.

Our approach — build an SVG string and `encodeURIComponent` it
(`GlassSurface.vue:121-145`) — is orders of magnitude cheaper because the
*browser's own rasteriser* draws the gradients. That is the right instinct and
should be preserved even if we adopt SDF math (§7.2).

**Conclusion for our design system rule 4 ("no WebGL unless absolutely
necessary"): fully vindicated.** The most-starred Liquid Glass implementation on
GitHub achieves its look with zero GPU shader code.

### 2.4 Animation system

The reference has no animation system. It has React state and CSS transitions:

```ts
transition: "all ease-out 0.2s"   // on the outer transform
transition: "all 0.2s ease-in-out" // on the glass body
```

`transition: all` on an element whose `transform`, `boxShadow`, `background`
and `opacity` all change is a compositor hazard — it invites the browser to
animate properties that require paint.

Every pointer move calls `setInternalMouseOffset()` and
`setInternalGlobalMousePos()` — **two React state updates per `mousemove`
event**, each re-rendering a component that computes five inline style objects
containing template-literal gradients. There is no rAF batching, no throttle, no
settle detection.

Ours (`useGlassSpotlight.ts`): pointer events only move goalposts; a rAF loop
interpolates; the loop **stops itself when everything settles** (line 229-234);
rects are cached and re-measured at most once per frame behind a dirty flag;
only CSS custom properties are written. Vue never re-renders. This is the
correct architecture and is explicitly required by design system rule 5.

### 2.5 Interaction response

| Behaviour | Reference | Design system verdict |
|---|---|---|
| Rim gradient angle follows cursor | `135 + mouseOffset.x * 1.2` deg | Right idea, fake math — we do it properly |
| Hover light pool | 3 stacked `radial-gradient(circle at 50% 0%)` at `mix-blend-overlay`, opacity 0→.4/.5/.8 | Right idea, wrong mechanism |
| Press | `scale(0.96)` | ❌ 4% shrink is not "mass"; ours is sub-pixel settle |
| **Elastic stretch toward cursor** | `scaleX/scaleY` ±30%, 200px activation zone, `translate` toward pointer | ❌ **Reject — see §5.1** |
| Reduced motion | none | ❌ Ours is zero-by-construction |
| Touch / coarse pointer | none (`mousemove` only) | ❌ Ours disables the light |

The elasticity math, for the record (`src/index.tsx:341-428`):

```ts
const edgeDistance = Math.sqrt(edgeDistanceX² + edgeDistanceY²)
const activationZone = 200
const fadeInFactor = 1 - edgeDistance / activationZone
const stretchIntensity = Math.min(centerDistance / 300, 1) * elasticity * fadeInFactor
const scaleX = 1 + |normalizedX| * stretchIntensity * 0.3 - |normalizedY| * stretchIntensity * 0.15
const scaleY = 1 + |normalizedY| * stretchIntensity * 0.3 - |normalizedX| * stretchIntensity * 0.15
// plus: translate toward the cursor by (delta * elasticity * 0.1 * fadeInFactor)
```

The *distance-to-nearest-edge* + activation-zone envelope is a genuinely good
proximity model — and it is conceptually the same thing our
`distanceToRect()` / `influence` proximity does (`useGlassSpotlight.ts:147-151, 213-217`).
The difference is what the envelope drives: **they drive geometry, we drive
light.** That is the entire philosophical split between the two systems.

---

## 3. Design principles

### 3.1 Traditional glassmorphism vs. Liquid Glass

| | Glassmorphism (2020) | Liquid Glass (Apple, 2025) |
|---|---|---|
| Backdrop | `blur()` only | `blur()` + **displacement** |
| Metaphor | frosted acrylic sheet | polished optical element |
| Thickness | implied by shadow | implied by **refraction + double rim** |
| Edges | uniform 1px white border | **directional, dispersive, Fresnel-weighted** |
| Legibility | opaque white fill | **neutral-density tint** (darken, don't whiten) |
| Background | hidden | **transmitted and bent** |
| Light source | none | **one, with a direction** |
| Colour at rim | none | **chromatic dispersion** |
| Response | static | reflections move; the object does not |

The compressed statement: **glassmorphism hides what is behind it; Liquid Glass
transmits what is behind it, altered.** A frosted rectangle can be faked with
one CSS declaration. A refracting slab cannot, and that is the point.

### 3.2 Material layers

The layer stack both systems converge on, back to front:

```
  ┌───────────────────────────────────────────────┐
  │ 6  CONTENT              sharp, never filtered │  z:1
  ├───────────────────────────────────────────────┤
  │ 5  SPECULAR / FRESNEL   directional rim arc   │
  │ 4  DEPTH                double rim, back-face │
  │ 3  BODY / ND TINT       legibility by density │
  │ 2  DIFFUSION            blur + saturate       │
  │ 1  REFRACTION           feDisplacementMap     │
  └───────────────────────────────────────────────┘
              ↑ scene behind the glass
```

Reference implements 1, 2, 5, 6 (and 4 weakly). We implement all six. The rule
that keeps this coherent: **layers 1–5 are `pointer-events: none`,
`aria-hidden`, and gated by a custom property that defaults to inert.** A
surface that opts into nothing must render as the calm baseline. Our
`color-mix(… calc(var(--glass-depth, 0) * 34%) …)` idiom achieves this exactly
— when the variable is 0, the layer paints literally nothing.

### 3.3 Lighting behaviour

Three rules, in priority order:

1. **One scene, one light.** Every highlight, Fresnel arc, and sheen in a view
   agrees on direction. The reference violates this by hardcoding
   `circle at 50% 0%` for hover pools (always top-centre) while the rim gradient
   tracks the cursor — two lights, contradicting each other.
2. **Fresnel: reflection rises at grazing angles.** Only the rim facing the
   light brightens. A uniform 1px border on all four sides is the tell-tale of
   glassmorphism.
3. **Dispersion is a whisper.** Visible when sought, invisible otherwise. The
   reference's default `aberrationIntensity: 2` is well judged; louder values
   read as a rendering bug.

### 3.4 Spatial depth

Depth is **layered internal cues, not blur radius, and not drop shadow.**
Ranked by how much depth they buy per unit of cost:

1. Double rim (bright front lip + dimmer inner contour offset ~1px down) —
   the strongest single cue, nearly free.
2. Edge-weighted refraction — the backdrop visibly bending only near the rim.
3. Back-face reflection (a soft bloom rising from the lower interior).
4. Internal scattering, gated on an actual nearby light.
5. Drop shadow — **last resort, kept small.**

### 3.5 Motion behaviour

The reference's motion philosophy is *liquid*: the object deforms, stretches,
and follows the pointer. Ours is *optical glass*: the object is planted and only
the light moves across it.

Both are legitimate design languages. They cannot be mixed — a surface that
stretches like mercury but is lit like a lens reads as broken. **This project
has already chosen, in writing, and shipped four phases on that choice.** The
choice stands.

---

## 4. What should be adopted

Ordered by value-to-risk. None of these are implemented yet; they are candidates
for a future phase.

### A-1 — Explicit edge mask (high value, low risk)
Add the `feColorMatrix → feComponentTransfer(discrete) → feComposite(in/over)`
sub-chain from §1.4 as an **opt-in prop** (`centerClarity?: number`, default
`0` = today's behaviour). Buys independent control of Edge Energy vs.
Transmission. Cost: ~5 extra filter primitives on surfaces that opt in.

### A-2 — SDF-derived displacement map (high value, medium risk)
Replace the linear-gradient plates with a rounded-rect SDF profile so refraction
follows the actual corner radius. **But generate it as SVG, not canvas** — see
§7.2 for how to get SDF behaviour without the 80,000-iteration loop.

### A-3 — Edge feather on the map (high value, trivial)
Ramp displacement to zero within ~2px of the map's own boundary
(`shader-utils.ts:106-110`). Prevents the hard tearing ring at the surface
border. Applies whichever map generator we use.

### A-4 — Displacement normalisation floor (medium value, trivial)
`maxScale = Math.max(maxScale, 1)` — never amplify a near-flat map into noise.
Relevant only if we adopt a computed map.

### A-5 — "Over light" awareness as a token (medium value, medium risk)
Not their implementation (§1.6), but the *concept*: a surface should be able to
know it sits over a bright backdrop and raise `--glass-density` accordingly.
Fits our existing token model as a new dial. See §7.5.

### A-6 — Proximity envelope shape (already have it — confirm it)
Their *distance-to-nearest-edge* + activation-zone model is the same as our
`distanceToRect` + `influence`. Independent convergence on the same solution is
a good sign our envelope is right. No action; recorded as validation.

---

## 5. What should **not** be copied

### 5.1 ❌ `elasticity` — the liquid stretch
The library's signature feature. It scales the element up to ±30% and translates
it toward the cursor. This violates, directly and by name:

- Design principle 12 — *"Subtle motion, meaningful motion"*
- Design principle 13 — *"Glass has mass. Nothing springs, bounces, or overshoots."*
- Motion principle 1 — *"Reflections move; objects don't. Cursor influences light, not geometry."*
- Forbidden pattern — *"❌ Constant floating / hover-bobbing — surfaces have mass; they don't levitate."*

It is also the feature users will most likely ask for, because it is what the
demo GIF shows. **The answer is no, and the reason is that we are rendering a
different material.** If a "responds to me" quality is wanted, it should be
spent on light — raising `--glass-proximity` earlier and further — not geometry.

### 5.2 ❌ Layout coupling
`positionStyles` forces `position: relative; top: 50%; left: 50%` with
`translate(-50%, -50%)` baked into the transform string
(`src/index.tsx:444-456`). This makes the component unusable in flow layout, in
grid, or inside a flex row without fighting it. Our surface is layout-neutral
(`width`/`height`/`borderRadius` props, `class`/`style` fall-through) and must
stay that way.

### 5.3 ❌ Sibling layer duplication
The reference renders **five to seven absolutely-positioned sibling elements
outside the component root**, each re-declaring `height`, `width`,
`borderRadius`, `transform` and `transition` from the same state
(`src/index.tsx:458-609`). Every geometry change must be mirrored across all of
them. In Vue this belongs inside the root as pseudo-elements and child divs
inheriting `border-radius: inherit` — which is what we already do.

### 5.4 ❌ State-per-mousemove
Two `setState` calls per pointer move, five inline style objects rebuilt per
render, template-literal gradients re-parsed each time. In Vue the equivalent
sin is binding light position to reactive refs used in `:style`. Our
composable's contract already forbids it, in a comment
(`useGlassSpotlight.ts:63-65`): *"The returned refs … exist for logic and tests
— binding them in templates would re-render Vue at 60fps."* Hold that line.

### 5.5 ❌ UA sniffing
```ts
const isFirefox = navigator.userAgent.toLowerCase().includes("firefox")
```
Design system rule 6: *"Feature-detect; never UA-gate features."* (We are not
entirely clean here either — §6.5.)

### 5.6 ❌ Base64 JPEG displacement maps
`src/utils.ts` ships three displacement maps as inline base64 **JPEG**. Two
problems: the file is enormous (a single map is tens of thousands of tokens of
base64, dwarfing the actual logic), and JPEG is a *lossy, chroma-subsampled*
format. Chroma subsampling damages exactly the R and B channels that encode the
displacement vector, and DCT ringing near the rim becomes visible banding in the
refraction. **A displacement map must be lossless or procedural.** Our
generated-SVG data URI is both, and weighs ~1KB.

### 5.7 ❌ Stacked `mix-blend-mode: screen` / `overlay`
The reference layers `screen` and `overlay` blends for rim and hover light.
These are backdrop-dependent: `overlay` over a light backdrop darkens, over a
dark backdrop lightens, and the result is unpredictable across our light/dark
themes and the colourful Product page. We achieve rim light with explicit
`color-mix()` alphas whose value is knowable at author time. Keep that.

### 5.8 ❌ `transition: all`
Invites transitions on paint-triggering properties. Enumerate the properties.

### 5.9 ❌ `scale(0.96)` on press
A 4% shrink reads as rubber. Heavy glass settles by a fraction of a pixel.

### 5.10 ❌ Default `cornerRadius: 999`
Pill-shaped-by-default is a button idiom, not a material default. Our
`borderRadius: 20` is the correct kind of default.

### 5.11 ❌ Calling CPU loops "shaders"
Naming matters for architectural decisions. If we ever add SDF generation it is
a *map generator*, not a shader, and it must not be used to argue for a WebGL
dependency later.

---

## 6. Cross-reading against our implementation

### 6.1 Where we are ahead
Depth layering, directional Fresnel with unwrapped angle, ND smoke body,
`color-mix()` inert-by-default gating, rAF interpolation with asymmetric
attack/release and self-settling, reduced-motion and coarse-pointer handling by
construction, layout neutrality, `@supports not (mask-composite)` fallback,
lossless procedural map, and an enforced one-primitive budget
(`__tests__/glassBudget.spec.ts`). None of this exists in the reference.

### 6.2 Where they are ahead
Corner-aware (SDF) refraction profile; explicit filter-space edge mask; the 2px
map-boundary feather; a generous filter region that allows sampling outside the
box.

### 6.3 Where we independently converged
Per-channel chromatic dispersion; separate warp layer vs. sharp content layer;
`mask-composite: exclude` rings; edge-distance proximity envelopes; treating the
displacement map as a build-once artifact keyed to element size.

### 6.4 ⚠️ Finding: `yChannel` may be reading an unencoded channel

Our map is painted as (`GlassSurface.vue:125-142`):

- a red `linearGradient` running **horizontally** (`x1:100% → x2:0%`) — encodes **X in R**
- a blue `linearGradient` running **vertically** (`y1:0% → y2:100%`) — encodes **Y in B**
- composited with `mix-blend-mode: difference`, then a blurred mid-grey
  (`hsl(0 0% 50% / .93)`) inset rect flattening the core to neutral

Our defaults are `xChannel: 'R'`, **`yChannel: 'G'`**. But nothing in that map
paints a vertical gradient into **G** — G is only lifted where the blurred grey
core rect covers, i.e. in the middle. Outside the core, G ≈ 0, which
`feDisplacementMap` reads as a **constant** `−0.5 × scale` vertical offset
rather than a gradient. With `distortionScale: -180` that is a uniform ~90px
downward pull at the rim.

The reference avoids this by writing `dy` into **both G and B** and selecting
`xChannelSelector="R" yChannelSelector="B"` — explicitly commented *"Y
displacement for SVG filter compatibility"* (`shader-utils.ts:118`).

Two possible readings:
1. It is unintended, and switching to `yChannel: 'B'` would give true 2D
   refraction where vertical bend varies with vertical position.
2. It is empirically tuned — the constant rim bias is part of the look we
   shipped and liked across Phases 9–16.

**This is an inference from reading the map generation, not a rendered
comparison.** It should be verified visually (render the same surface with
`yChannel="B"` side by side) before anything is changed. If reading 1 holds, it
is a one-word fix that measurably improves corner refraction; if reading 2
holds, the default deserves a comment explaining the bias so it is never
"fixed" by accident. Either way it is worth resolving before we invest in map
quality (A-2).

### 6.5 ⚠️ Finding: we UA-gate too

`supportsSVGFilters()` (`GlassSurface.vue:158-166`) runs a UA check **before**
the feature test:

```ts
const isWebkit = /Safari/.test(navigator.userAgent) && !/Chrome/.test(navigator.userAgent)
const isFirefox = /Firefox/.test(navigator.userAgent)
if (isWebkit || isFirefox) return false
```

The comment justifies it honestly — those engines *parse* `backdrop-filter: url()`
successfully but render nothing, so the feature test alone returns a false
positive. That is a real constraint, and the UA check is currently load-bearing.
But it means design system rule 6 (*"never UA-gate"*) is not actually satisfied,
and the check will silently mis-classify Safari the day WebKit ships support.
Recommendation: keep the gate, but restate the rule as *"never UA-gate without a
documented render-level probe attempt"*, and add a TODO to replace it with a
one-time offscreen render probe when one becomes practical.

---

## 7. Vue 3 implementation recommendations

For Vue 3 + TypeScript + Vite + Pinia, macOS-style desktop web / iOS-style
mobile web, AI-native product design.

### 7.1 Do not add a component — add props

The budget test (`__tests__/glassBudget.spec.ts`) enforces exactly one
refracting primitive and a hard cap of three instantiations. Everything below is
therefore an **extension of `GlassSurface`**, never a sibling component. A
fourth surface, or a second primitive, requires deliberately renegotiating the
budget and updating `ALLOWED`.

Proposed additive props, all defaulting to today's behaviour:

```ts
interface GlassSurfaceProps {
  // … existing …
  /** Displacement map generator. 'gradient' = today. */
  profile?: 'gradient' | 'lens'
  /** 0 = today. >0 masks refraction away from the centre (§1.4). */
  centerClarity?: number
  /** px of feather at the map boundary, kills the rim tear (§A-3). */
  mapFeather?: number
}
```

### 7.2 Get SDF behaviour without the CPU loop

The reference's lens profile is valuable; its generation method is not. Three
options, best first:

**Option 1 — SVG radial/rounded gradients approximating the SDF (recommended).**
The profile `smoothStep(0.8, 0, d − 0.15)` over a rounded-rect SDF is closely
approximated by an inset rounded `<rect>` with a large `feGaussianBlur`, which
is *already the shape of our existing map*. The missing piece is corner
awareness in the **direction** channels, not the magnitude channel. Paint the R
and B gradients as four directional wedges clipped to the rounded rect rather
than two full-width linear gradients. Still a string, still rasterised by the
browser, still ~1KB, still zero main-thread pixel work.

**Option 2 — Compute once, cache aggressively.** If true SDF fidelity is needed,
compute at **reduced resolution** (cap the longest side to ~128px; `feImage`
with `preserveAspectRatio="none"` stretches it, and a displacement map is
low-frequency data so downsampling is nearly free visually), and memoise at
module scope:

```ts
const mapCache = new Map<string, string>()   // `${w}x${h}r${radius}f${feather}`
```

At 128×64 that is 8,192 iterations instead of 80,000 — and cache hits make
resize free.

**Option 3 — `OffscreenCanvas` in a worker.** Only if 1 and 2 both fail. Adds a
build-config surface and an async path through a currently synchronous
`computed`. Not recommended.

**In all cases: debounce map regeneration.** Today the map is a `computed` on
`measured`, which a `ResizeObserver` writes on every observed frame. The string
build is cheap, but each new data URI forces the browser to **re-decode the
`feImage` and re-render the filter**. During a window drag that is a filter
rebuild per frame — the one thing design system rule 5 forbids
(*"expensive work happens on mount/resize, never per frame"*). A ~120ms trailing
debounce on `measured`, with the last value held, removes it.

### 7.3 Type the custom-property contract

The variable contract is currently documented in a 40-line docblock and
enforced by nothing. Make it a typed surface so consumers cannot typo
`--glass-fresnal`:

```ts
// styles/glass-vars.ts
export interface GlassVars {
  '--glass-depth'?: number
  '--glass-density'?: number
  '--glass-tint'?: string
  '--glass-fresnel'?: number
  '--glass-light-angle'?: `${number}deg`
  '--glass-flow-opacity'?: number
}
export const glassVars = (v: GlassVars): Record<string, string> =>
  Object.fromEntries(Object.entries(v).map(([k, val]) => [k, String(val)]))
```

Usage stays declarative: `:style="glassVars({ '--glass-depth': 1, '--glass-density': .8 })"`.
Zero runtime cost, full autocomplete, and it makes the token dials discoverable
without reading the component.

### 7.4 A Pinia store for **material quality**, not material state

Pinia should hold exactly one glass-related thing: the **global quality tier**.
Per-surface optical state stays in CSS variables — putting it in a store would
mean reactive writes at 60fps, which is §5.4 with extra steps.

```ts
// stores/material.ts
export type GlassTier = 'full' | 'reduced' | 'flat'

export const useMaterialStore = defineStore('material', () => {
  const tier = ref<GlassTier>('full')
  // resolved once at boot from: SVG-backdrop support, prefers-reduced-motion,
  // prefers-reduced-transparency, coarse pointer, saveData, deviceMemory
  const refracts = computed(() => tier.value === 'full')
  return { tier, refracts }
})
```

Why a store earns its place here: the tier must be **one decision, applied
everywhere at once**. Today each `GlassSurface` independently runs
`supportsSVGFilters()` on mount — three surfaces, three probes, three
independent verdicts, and no way for the app to say "we're on a low-end device,
everything goes flat." A single boot-time resolution also gives a natural home
for the §6.5 probe when we replace the UA gate, and a `data-glass-tier`
attribute on `<html>` lets `glass.css` respond without any component knowing.

### 7.5 `overLight` as a token, resolved by the stage

Add `--glass-backdrop: dark | light` as a **stage-level** declaration (set by the
view, not sniffed by the surface), remapping density and rim polarity inside
`glass.css`:

```css
.glass-surface { --glass-density: .55; }
[data-glass-backdrop='light'] .glass-surface { --glass-density: .78; }
```

Do **not** attempt automatic backdrop luminance detection (sampling the backdrop
requires a canvas readback per frame). The view knows whether it is bright. Let
it say so. This keeps §1.6's useful idea and discards its implementation.

### 7.6 Desktop vs. mobile: two motion budgets, one material

- **macOS-style desktop.** Pointer exists → `useGlassSpotlight` runs. Light
  tracks the cursor, proximity lifts the rim, the Fresnel arc rotates with the
  real bearing. This is the full expression.
- **iOS-style mobile.** No pointer → per motion principle 7 and the existing
  `(hover: hover) and (pointer: fine)` gate, light-strength stays 0 **by
  construction**. Mobile glass must therefore stand up *statically*: a fixed
  implied light from above, `--glass-fresnel` resting as a top highlight
  (which our `conic-gradient` already does when `--glass-light-angle` is
  undriven), depth and density carrying the whole material impression.
  **Test the mobile surfaces with the spotlight disabled** — that is the real
  mobile appearance, and it is easy to never look at it on a desktop dev machine.
- Do **not** substitute device orientation or scroll position as a pseudo-cursor
  on mobile. That is a second light source (violates principle 9) and a battery
  cost for decoration (violates motion principle 3).

### 7.7 AI-native surfaces: light as the AI's presence

The design system already stakes this out — *"AI presence is expressed as light
inside glass."* Concretely, and consistent with everything above:

| AI state | Optical expression | Mechanism |
|---|---|---|
| Idle | baseline material | nothing driven |
| Thinking | slow internal sheen | `--glass-flow-opacity` raised; the existing 20–40s loop is the right speed — do not accelerate it into a spinner |
| Streaming | faint rising back-face bloom | `--glass-depth` back-face gradient, opacity only |
| Complete | one-shot rim brighten, settling | `--glass-fresnel` pulse, `transition`, no loop |
| Error | density up, light down | glass gets heavier and darker; never red-glow |

Critically: **AI chrome is glass; AI output is solid.** A streamed answer must
render on an opaque surface. Text a user is reading never sits on a refracting
backdrop — principle 11, and it is the difference between a premium product and
an unreadable one.

### 7.8 Verification checklist for any future glass work

- [ ] Extends `GlassSurface`; no second primitive; `glassBudget.spec.ts` updated if a 4th instance is genuinely needed
- [ ] New optical layers gated by a custom property that defaults to inert (`color-mix(… calc(var(--x, 0) * N%) …)`)
- [ ] Map regeneration debounced; nothing in the filter chain touched per frame
- [ ] Only opacity / transform / gradient positions animate
- [ ] Reduced-motion and coarse-pointer paths verified **by disabling the spotlight**, not by trusting the gate
- [ ] Safari/Firefox fallback legible and functional
- [ ] One light direction across the whole view
- [ ] On-glass text uses the fixed dusk palette; contrast measured, not eyeballed
- [ ] No `mix-blend-mode` whose result depends on an unknown backdrop
- [ ] No geometry deformation on hover, ever

---

## 8. Verdict

`liquid-glass-react` is a well-observed *visual* study and a poor *architectural*
one. It reverse-engineers Apple's optics competently — edge-only displacement,
SDF lens profile, per-channel dispersion — and then wraps them in per-frame
React state, forced positioning, seven duplicated sibling layers, lossy JPEG
maps, UA sniffing, and a deformation model borrowed from a different material
entirely.

We should take its **optics** (§4: A-1 through A-4) and take nothing else.

The most useful outcome of this study is negative confirmation: the most popular
Liquid Glass implementation on GitHub uses **no WebGL, no shaders, and no
canvas** in its recommended path. Our design system's rule 4 was the right call,
and our existing `GlassSurface` — layered, gated, self-settling, layout-neutral
— is the better foundation. The gap to close is map quality, not architecture.

Two items in §6 warrant attention independent of any adoption work: the
`yChannel` question (§6.4) and the UA-gate honesty problem (§6.5).

---

*Analysis complete. No source files were modified.*
