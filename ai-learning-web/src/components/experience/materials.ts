/**
 * Liquid Material presets — the named slabs (Phase 17.2).
 *
 * A surface declares WHAT IT IS and inherits the whole optical prescription
 * from `styles/glass.css`; it never hand-types dials. The preset maps the
 * canonical `--material-*` tokens onto the `--glass-*` runtime contract that
 * `GlassSurface` reads, so the primitive's own API never moves.
 *
 * Rank → Apple variant, and the rule behind it — Clear may only be used where
 * the stage supplies its own dimming and the backdrop is meant to be seen
 * through; Regular floats over arbitrary content and carries legibility in its
 * own body:
 *
 *   chrome    Clear    permanent navigation over a stage that dims for it
 *   hero      Clear    the flagship slab, stage-tuned denser for brighter art
 *   floating  Regular  transient surfaces over arbitrary user content
 *
 * See `docs/liquid-material-system.md`. Adding a preset is a design-system
 * decision, not an implementation detail: it needs a rank, a variant, and a
 * documented reason a surface cannot use an existing one.
 */
export const MATERIAL_PRESETS = ['chrome', 'hero', 'floating'] as const

export type MaterialPreset = (typeof MATERIAL_PRESETS)[number]

/**
 * The declared backdrop (Phase B4, `environment.md` E5) — how bright the scene
 * behind the glass is, stated by the stage that owns it as
 * `data-material-backdrop` and never sampled.
 *
 * Theme ≠ backdrop. The theme styles the app and the environment; the
 * declaration styles the material. The Login stage is `dark` in both themes
 * (its dusk dims where the glass stands) except the Product room, which is
 * `light` in both; the authenticated shell derives it from the theme because
 * there the content is the backdrop. A stage that declares nothing falls back
 * to the theme (`tokens.css`).
 */
export const MATERIAL_BACKDROPS = ['dark', 'light'] as const

export type MaterialBackdrop = (typeof MATERIAL_BACKDROPS)[number]
