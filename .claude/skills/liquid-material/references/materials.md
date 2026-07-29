# Materials — the optics of Liquid Glass

How the material is physically constructed. Governed by `constitution.md`.
Source research: `docs/liquid-glass-analysis.md`; living reference
implementation: `ai-learning-web/src/components/experience/GlassSurface.vue`.

---

## 1. The layer stack

Every Liquid Glass surface is six layers, back to front. Layers 1–5 are
`pointer-events: none` and `aria-hidden`; layer 6 is never filtered.

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

Rule: each optical layer is gated by its own custom property with an **inert
default** — with nothing driven, the layer paints nothing. The
`color-mix(in srgb, <color> calc(var(--gate, 0) * N%), transparent)` idiom
achieves this exactly.

## 2. Refraction (layer 1)

There is no ray tracing on the web. All refraction is one operation:

> For each output pixel, sample the backdrop from a *different* pixel.

`feDisplacementMap` does this, reading a second image (the **displacement
map**) whose channels encode a per-pixel offset vector:

```
P'(x,y) = P( x + scale × (mapX(x,y)/255 − 0.5),
             y + scale × (mapY(x,y)/255 − 0.5) )
```

Consequences you must design with:

- **Mid-grey (128) is the neutral element.** A displacement map is a grey
  field with deviations near the edges.
- **The map IS the optical prescription.** Author refraction as an image; the
  filter merely applies it.
- Applied via `backdrop-filter: url(#filter-id)` on a dedicated warp layer —
  Chromium-only in practice; see fallbacks (§8).

### Edge-only refraction — the defining rule

A real lens distorts hardest where the glass is thickest: the curved rim. The
centre passes through nearly straight. Therefore:

- The map's core is flattened to neutral grey (in `GlassSurface`, a blurred
  bright inset rounded-rect does this).
- Edge energy (how hard the rim bends) and centre clarity (transmission) are
  separate dials. If raising one degrades the other, the map needs work — the
  explicit filter-space edge mask from the analysis (§1.4) is the upgrade path.
- **Corner-aware maps beat linear-gradient maps.** A rounded-rect SDF profile
  makes refraction follow the actual corner radius, so the glass reads as a
  *shape*, not a rectangle. When improving map quality, approximate the SDF
  with SVG gradients (browser-rasterised, ~1KB) — never a CPU per-pixel loop.

### Map hygiene

- Procedural SVG data URI, lossless. Never JPEG (chroma subsampling corrupts
  the offset channels; DCT ringing becomes banding in the refraction).
- Feather displacement to zero within ~2px of the map's own boundary or the
  outer pixels produce a hard tearing ring.
- If computing values, floor the normaliser (`maxScale = max(maxScale, 1)`) so
  a near-flat map isn't amplified into noise.
- Regenerate only on mount and debounced resize. Never per frame.

## 3. Distortion character

Distortion parameters shape the material's personality — set once per surface:

| Dial | Mechanism | Effect |
|---|---|---|
| `distortionScale` | `feDisplacementMap scale` | Overall bend strength (house default −180) |
| `borderWidth` | inset of the flat core in the map | How wide the bending rim band is |
| `blur` (map-space) | blur on the core rect | How softly rim bend fades into clear centre |
| `displace` | output `feGaussianBlur` | Softens the displaced result itself |

Character guide: **hero slabs** (login card, command palette) take strong scale
and a wide rim band; **utility glass** (toolbar, toasts) takes gentle scale and
a narrow band. Distortion never animates — it is the slab's ground truth.

## 4. Highlights (layer 5)

### Chromatic dispersion

Different wavelengths refract differently. Implemented as three displacements
at slightly different scales, each isolated to one channel, screen-blended:

```
scale + redOffset   → keep R    ┐
scale + greenOffset → keep G    ├─ feBlend mode="screen" ×2 → tiny feGaussianBlur
scale + blueOffset  → keep B    ┘
```

The split is automatically largest where displacement is largest — the rim.
House defaults: `redOffset 0 / greenOffset 10 / blueOffset 20`. Keep it a
whisper; loud aberration reads as a rendering bug.

### Fresnel rim arc

Glass reflects most at grazing angles, so only the light-facing rim glows:

- A `conic-gradient` arc masked to a 1.5px ring via the
  `padding` + `mask-composite: exclude` idiom.
- Aimed by `--glass-light-angle`, computed from the **real** light bearing
  (`atan2` from the surface centre, unwrapped across ±180° so the arc glides
  instead of snapping). Never fake it with a linear function of cursor X.
- Undriven, the arc rests as a gentle top highlight — this is the mobile/static
  expression.
- Always guard with `@supports not (mask-composite: exclude)` → hide the ring
  entirely rather than let the conic flood the face.

### Sheens and pools

- Light-tracking sheen: a radial gradient at `--glass-light-x/y`, opacity =
  `strength × (0.2 + proximity × 0.8)`. Dormant at 0 until a composable feeds it.
- Hover pooling: brightening of existing light layers — never a hardcoded
  second light position, never `mix-blend-mode` stacks (screen/overlay results
  depend on unknown backdrops; use explicit `color-mix()` alphas whose value is
  knowable at author time).

## 5. Shadows

Depth comes from internal optics; shadow is the *last* cue, kept small.

- ✅ Small, soft, layered low-alpha shadows (the house stack uses several
  `rgba(17,17,26,0.05)` layers).
- ✅ Inset micro-shadows as part of the rim bevel.
- ❌ `0 16px 70px rgba(0,0,0,.75)` — the forbidden "40px black halo". If a
  surface needs that to separate, its depth layers are failing; fix those.
- Text on glass may carry a faint `text-shadow` so it floats above the slab —
  content lighting, not surface shadow.

## 6. Depth (layers 3–4)

Ranked by depth-per-cost:

1. **Double rim** — bright front lip (1px inset ring) + dimmer inner contour
   ~3px inside, biased 1px downward: the far edge of a thick slab seen through
   its own body. The strongest single "heavy glass" cue, nearly free.
2. **Edge-weighted refraction** — §2.
3. **Back-face reflection** — a soft bloom rising from the lower interior
   (`radial-gradient` at `50% 110%`), light re-emerging from the slab's rear.
4. **Internal scattering** — a diffuse pool inside the body, gated on
   `depth × light-strength × proximity`, so it only exists when a real light
   is actually near.
5. **ND body (layer 3)** — the smoked neutral-density tint
   (`--glass-density` × `--glass-tint`, default near-black smoke), reading
   slightly thicker toward the base. This is the legibility mechanism —
   darkening, never whitening. On-glass text uses the fixed dusk palette
   regardless of theme.

## 7. The variable contract

The `--glass-*` **runtime** contract that `GlassSurface` reads, with inert
defaults. Call sites never set these by hand: the named presets in `glass.css`
map the canonical `--material-*` tokens (`tokens.css`) onto this contract, and
`useGlassSpotlight` writes the per-frame members (`x/y/angle/strength`,
`proximity`) directly. Read a value here to understand what a dial *does*; change
it in `tokens.css` or the preset, never at a component.

| Variable | Default | Meaning |
|---|---|---|
| `--glass-depth` | 0 | Presence of thickness cues (rims, back-face, scatter) |
| `--glass-density` | 0 | ND smoke strength — the legibility dial |
| `--glass-tint` | `rgb(10 12 18)` | Color of the ND body |
| `--glass-fresnel` | 0 | Presence of the directional rim arc |
| `--glass-inner-glow` | 0.55 | Base opacity of the permanent inner glow |
| `--glass-edge-glow` | 0.5 | Base opacity of the permanent edge highlight |
| `--glass-proximity` | 0 | 0..1 nearby-light factor — lifts glows, thins frost |
| `--glass-light-x/y` | 50% | Surface-local light position |
| `--glass-light-radius` | 360px | Light radius |
| `--glass-light-strength` | 0 | 0..1 presence of the travelling light |
| `--glass-light-angle` | 0deg | Light bearing (0 = above) aiming the Fresnel arc |
| `--glass-flow-opacity` | 0.6 | Presence of the ambient surface-flow layer |

The conceptual dials in `constitution.md` §5 map onto these: Density,
Temperature, Edge Energy, Reflection Strength, Optical Depth, Transmission,
Surface Flow, Material Weight. Adjust dials; don't invent parallel mechanisms.

`--glass-light-radius` is the one member with a configurable base in
`tokens.css` — stages may override it, and the spotlight reads it once then
swells it slightly near a surface. The rest of the `--glass-light-*` family is
per-frame state and must stay undriven at author time.

## 8. Fallback material

Browsers that can't render SVG backdrop filters get **one** graceful step down
per capability lost:

1. **Full** (Chromium): refraction + all light layers.
2. **Frosted** (Safari/Firefox): `blur(12px) saturate(1.8)` + border + inset
   highlights — a dignified frosted material, fully legible, keyed to
   `html.dark` for theme.
3. **Solid-ish** (`@supports not (backdrop-filter)`): raised background alpha,
   no filter at all.

The fallback is a *material tier*, not a bug state — design it, test it, keep
every control functional in it.
