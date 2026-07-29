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
