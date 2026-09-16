/**
 * The material tier resolver (Phase B1 — `materials.md` §8, `implementation.md` §6–7).
 *
 * One decision, taken once at boot, written once onto `<html>` as
 * `data-glass-tier`, and obeyed by every surface through the CSS cascade:
 *
 *   refract  — SVG-in-`backdrop-filter` renders (Blink): the full optical path
 *   diffuse  — the backdrop can only be blurred, not refracted (WebKit, Gecko):
 *              the same slab, same preset dials, minus the refraction layer
 *   dense    — no `backdrop-filter`, or the user asked for reduced transparency
 *              / more contrast: transmission down, density up, rims kept
 *
 * Capability, never brand. Every Blink browser — Chrome, Edge, Brave, Arc,
 * Opera, Vivaldi — reports a literal `"Chromium"` brand in
 * `navigator.userAgentData.brands`, which is an *engine* signal; WebKit and
 * Gecko do not implement the API at all. That is the sufficient test. The
 * necessary test is `CSS.supports` on an SVG `url()` value for the backdrop
 * filter property, which every engine passes because WebKit and Gecko *parse*
 * SVG backdrop filters but render nothing (`docs/liquid-glass-analysis.md`
 * §6.5) — a pure feature probe false-positives there, which is why the engine
 * signal exists.
 *
 * `legacyUaIsBlink` is the single named technical-debt exception in the
 * codebase: a UA-string check consulted only when `userAgentData` is absent.
 * Nothing else under `src/` may name a browser (a test enforces it).
 *
 * Reduced motion and pointer coarseness are deliberately NOT inputs — they gate
 * the travelling light (`useGlassSpotlight`), not the material.
 *
 * SSR / hydration: no browser global is touched at module scope; the tier is
 * an attribute on the root, markup is identical in every tier, and the CSS
 * base (no attribute) is the diffuse tier — the legible inert baseline.
 */
export const GLASS_TIERS = ['refract', 'diffuse', 'dense'] as const

export type GlassTier = (typeof GLASS_TIERS)[number]

export const GLASS_TIER_ATTRIBUTE = 'data-glass-tier'

/** Everything the decision reads, injectable so the resolution is unit-testable. */
export interface GlassTierEnvironment {
  /** `CSS.supports(property, value)` */
  supports: (property: string, value: string) => boolean
  /** `matchMedia(query).matches` */
  matches: (query: string) => boolean
  /** `navigator.userAgentData?.brands`, or `undefined` where the API is absent. */
  brands: ReadonlyArray<{ brand: string }> | undefined
  /** `navigator.userAgent` — read only when `brands` is undefined. */
  userAgent: string
}

const REDUCED_TRANSPARENCY = '(prefers-reduced-transparency: reduce)'
const MORE_CONTRAST = '(prefers-contrast: more)'

/** The media queries whose change re-runs the decision. */
export const GLASS_TIER_MEDIA_QUERIES = [REDUCED_TRANSPARENCY, MORE_CONTRAST] as const

/**
 * The one place a browser-ish string may exist. Every Blink build carries
 * `Chrome/` in its UA string (Edge, Opera, Brave and Arc included), and no
 * WebKit or Gecko build does — so this is still an engine test, not a brand
 * test. Consulted only when `userAgentData` is unavailable.
 */
function legacyUaIsBlink(userAgent: string): boolean {
  return /Chrome\//.test(userAgent)
}

function isBlink(env: GlassTierEnvironment): boolean {
  if (env.brands) return env.brands.some((entry) => entry.brand === 'Chromium')
  return legacyUaIsBlink(env.userAgent)
}

/** Pure: capability in, tier out. */
export function resolveGlassTier(env: GlassTierEnvironment): GlassTier {
  if (env.matches(REDUCED_TRANSPARENCY) || env.matches(MORE_CONTRAST)) return 'dense'
  if (!env.supports('backdrop-filter', 'blur(1px)')) return 'dense'

  const parsesSvgBackdrop = env.supports('backdrop-filter', 'url(#p)')
  return parsesSvgBackdrop && isBlink(env) ? 'refract' : 'diffuse'
}

/** Reads the live browser into a `GlassTierEnvironment`. Client-only. */
export function readGlassTierEnvironment(): GlassTierEnvironment {
  const nav = navigator as Navigator & {
    userAgentData?: { brands?: ReadonlyArray<{ brand: string }> }
  }
  return {
    supports: (property, value) =>
      typeof CSS !== 'undefined' && typeof CSS.supports === 'function'
        ? CSS.supports(property, value)
        : false,
    matches: (query) => window.matchMedia(query).matches,
    brands: nav.userAgentData?.brands,
    userAgent: nav.userAgent,
  }
}

/**
 * Resolves the tier and writes it onto the document root. Call once from
 * `main.ts` before mounting; the two accessibility media queries re-run the
 * decision when they change. Returns the tier that was applied.
 */
export function applyGlassTier(root: HTMLElement = document.documentElement): GlassTier {
  const apply = (): GlassTier => {
    const tier = resolveGlassTier(readGlassTierEnvironment())
    root.setAttribute(GLASS_TIER_ATTRIBUTE, tier)
    return tier
  }
  const tier = apply()
  for (const query of GLASS_TIER_MEDIA_QUERIES) {
    window.matchMedia(query).addEventListener('change', apply)
  }
  return tier
}
