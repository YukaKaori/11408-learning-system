/** Shared prop vocabulary for the AppX component library. */

export type Size = 'sm' | 'md' | 'lg'

export type Tone = 'primary' | 'secondary' | 'success' | 'warning' | 'danger' | 'info'

export type ButtonVariant = 'solid' | 'soft' | 'outline' | 'ghost' | 'plain'

/**
 * Cards are content containers, so there is no `glass` member: content is never
 * glass. Elevated surfaces that genuinely qualify compose `GlassSurface` with a
 * declared material — see `docs/liquid-material-system.md`.
 */
export type CardVariant = 'flat' | 'elevated'
