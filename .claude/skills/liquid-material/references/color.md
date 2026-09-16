# Color — the OKLCH law

How color is *derived* in this product. Adopted in Phase 18 from the Ein UI
Claude skill (`github.com/unobtuse/einui-claude-skill`), whose one genuinely
transferable idea is its color methodology. Its material was rejected — see §7.

The constitution's principle 15: **color is derived, never invented.** This file
is the derivation.

---

## 1. Scope — read this first

The rules below govern **new** color work. They deliberately do **not** rewrite
what already ships.

| Situation | Rule |
|---|---|
| Adding a new color token | OKLCH, derived from a declared anchor (§3) |
| Adding a new theme, brand skin, or subject/category color | OKLCH ladder (§3–4) |
| Touching an existing `--color-*` / `--accent-*` / `--scene-*` token | Stays in its current hex/rgba form; change the value, not the format |
| Adding an environment token (`--environment-*`: atmosphere band, ambient pool colours, wake mask) or a material stage/recipe token (`--material-backdrop`, `--material-radius-*`, `--material-inset`) | New tokens → OKLCH-derived where they are colours; non-colour tokens are plain numbers/lengths. B1 moved the four material literals into tokens: `--on-glass-halo`/`-active` and `--on-glass-inset-bg`/`-lip` (frozen dusk, `glass.css`), `--environment-stage-text` (frozen dusk, `tokens.css`), and the primitive's focus ring now uses `--color-focus-ring` |
| Wholesale migration of `tokens.css` to OKLCH | **Not authorized.** It is a pixel-moving change and needs its own phase with equivalence verification |

`styles/tokens.css` currently holds ~52 hex and ~42 rgba literals across two
hand-picked themes. That is the pre-OKLCH world and it is *frozen*, not endorsed.
Never leave a token family half-migrated — mixed formats inside one ramp are
worse than either format alone.

**Frozen by physics, not just by policy** — these are never palette-derived, in
any migration:

- **On-glass text** — a fixed dusk palette. The slab is smoked in both themes, so
  on-glass text cannot be theme-relative or brand-relative (constitution §2.7).
- **`--material-tint-*`** — the ND smoke body. Theme-invariant by design.
- **The five categorical accents** (`--accent-indigo|teal|amber|rose|violet`) —
  each step was validated *per mode* against its surface for lightness band,
  chroma, CVD separation and contrast (the dataviz six checks). A generated
  ladder is a *proposal* for these, never a substitute for re-validation.

## 2. Why OKLCH

OKLCH is perceptually uniform: `L` means the same apparent brightness across
every hue. `oklch(0.60 0.15 30)` and `oklch(0.60 0.15 260)` genuinely look
equally bright — the HSL equivalents do not (HSL yellow at 50% lightness blazes;
HSL blue at 50% sinks).

That is what makes color *derivable*. With a perceptually uniform space you can:

- Fix a lightness ladder once and reuse it for every hue — contrast behavior
  stays predictable across brand skins and subject colors.
- Move a hue without re-tuning brightness for every step.
- Compute derived tokens (glows, tints, hovers) from a base color instead of
  hand-picking a second literal that "looks right."

Browser support is current-baseline; `light-dark()` — already the app's theme
mechanism — composes with it fine.

## 3. The anchor and the ladder

**Ask for the anchor before designing.** When work needs a new palette (a brand
skin, a themed surface, a category set), the anchor color is an input, not
something to invent. If the user hasn't given one, ask — one question, then
proceed. Do not silently ship a default violet.

A palette is **one anchor** → **seven stops** at fixed lightness:

```
L:  0.95   0.85   0.73   0.60   0.48   0.35   0.20
    ─────────────────────────────────────────────
     1      2      3      4      5      6      7
   lightest              anchor              darkest
```

The hue `h` is constant across all seven. Only `L` and `C` move.

### Chroma tapering — the part that matters

Constant chroma across a ladder is the tell of a machine-generated palette: the
light steps read neon, the dark steps read muddy. Real palettes peak in
saturation near mid-lightness and fade toward both extremes, because that is
where the gamut actually lives.

Chroma peaks at the anchor's lightness and tapers to a floor (~30% of anchor
chroma) at both ends:

```
c(L) = c_anchor × ( floor + (1 − floor) × (1 − t)^γ )

  t = (L − L_anchor) / (0.95 − L_anchor)     when L > L_anchor
  t = (L_anchor − L) / (L_anchor − 0.20)     when L < L_anchor
  floor = 0.30      γ = 1.2
```

Ein UI documents the *behavior* (peak at anchor, ~30% at the extremes) rather
than a curve; the formula above is our formulation of it, and it reproduces the
documented endpoints exactly. Tune `γ` if a ladder feels wrong — never tune
individual steps by hand, or the ladder stops being derivable.

Worked example, anchor `oklch(0.60 0.15 280)`:

| Stop | L | C | Value |
|---|---|---|---|
| 1 | 0.95 | 0.045 | `oklch(0.95 0.045 280)` |
| 2 | 0.85 | 0.068 | `oklch(0.85 0.068 280)` |
| 3 | 0.73 | 0.105 | `oklch(0.73 0.105 280)` |
| 4 | 0.60 | 0.150 | `oklch(0.60 0.150 280)` ← anchor |
| 5 | 0.48 | 0.113 | `oklch(0.48 0.113 280)` |
| 6 | 0.35 | 0.077 | `oklch(0.35 0.077 280)` |
| 7 | 0.20 | 0.045 | `oklch(0.20 0.045 280)` |

Subtle at the top, rich at the bottom, saturated in the middle. That asymmetry
around the anchor is the point — it is not a bug to "even out."

## 4. Semantic mapping

The ladder is raw material. Nothing in the app references a stop directly; every
consumer reads a **semantic** token, and semantic tokens reference *only* the
ladder — they never define new color values.

```css
/* the ladder — the only place literal color values exist */
--palette-1: oklch(0.95 0.045 280);
/* … --palette-7 */

/* semantics — references only */
--color-primary: var(--palette-4);
--color-text:    light-dark(var(--palette-7), var(--palette-1));
--color-surface: light-dark(var(--palette-1), var(--palette-7));
```

Theme flipping is a *remap of semantics*, not a second set of literals. This is
the discipline the current `:root` / `html.dark` split lacks — the dark theme
re-picks every value by hand, which is why every accent needed independent
re-validation.

### Derived tokens use relative color syntax

Alphas, hovers, and soft variants are computed from the token they belong to,
never typed as a fresh `rgba()`:

```css
/* ✅ derived — one source of truth */
--color-primary-soft: oklch(from var(--color-primary) l c h / 0.12);
--color-primary-hover: oklch(from var(--color-primary) calc(l + 0.06) c h);

/* ❌ a second literal that must be kept in sync by hand */
--color-primary-soft: rgba(94, 106, 210, 0.1);
```

Guard relative color syntax with `@supports (color: oklch(from red l c h))` when
the token is load-bearing, falling back to the frozen literal.

## 5. Hard rules

1. **No new hex or `rgba()` literals** outside a declared ladder. A raw color in
   a component's `<style>` block is a defect.
2. **No named framework colors** — no Tailwind-style `bg-cyan-500`, no Element
   Plus palette names leaking into our surfaces.
3. **Semantic tokens reference the ladder only.** If a semantic token contains a
   literal, the ladder is incomplete — fix the ladder.
4. **Derived variants use relative color syntax**, not a second literal.
5. **One hue per ladder.** A palette that wanders hue across its stops is two
   palettes; declare two.
6. **Generated ≠ validated.** Any ladder used for categorical data or as
   text/surface pairs is re-checked for contrast and CVD separation before it
   ships (the `dataviz` skill's six checks). The math proposes; validation
   disposes.
7. **Gradients are lights, not decoration** (constitution §7). A gradient's
   colors come from the ladder and its direction agrees with the scene's one
   light source.
8. **The material's optics are not colors.** Density, depth, and edge energy are
   `--material-*` dials; do not "theme" a slab by tinting it with a brand hue.
   The house material is smoked in both themes.

## 6. Applying this to a new surface

1. Is a new color actually needed? Usually not — the semantic tokens exist.
2. If yes: get the anchor (ask the user if it isn't given).
3. Generate the seven stops with §3.
4. Map semantics (§4); derive variants with relative color syntax.
5. Validate: contrast for every text/surface pair, CVD separation if categorical.
6. Verify at the real surface in **both themes** (`verify` skill) — OKLCH math is
   correct, but "correct" and "right" are different words.

## 7. What was rejected from Ein UI

Recorded so nobody re-imports it later. The skill's material is straightforward
glassmorphism, which this project's constitution forbids:

```css
/* Ein UI's "liquid glass" — this is the forbidden material */
--glass-bg: oklch(from var(--color-7) l c h / 0.4);
--glass-border: oklch(from var(--color-3) l c h / 0.15);
--glass-blur: 16px;
```

A translucent fill, a uniform border, and a blur radius. No refraction, no
displacement map, no Fresnel directionality, no depth layering, no mass. It
hides its backdrop rather than transmitting it — the exact failure of the one
test in `SKILL.md` §2.

Also not applicable: its component registry (`npx shadcn add …`), its stack
(React/Next.js/shadcn/Radix/Tailwind v4 — we are Vue 3 + TS + Element Plus), and
its `--color-1..7` global naming, which would collide with our existing
`--color-*` semantic namespace. Hence `--palette-N` above.

Taken: the anchor-driven ladder, chroma tapering, semantics-reference-only,
relative color syntax for derived tokens, and asking for the anchor before
designing.
