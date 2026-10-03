/**
 * Displacement-map generation for `GlassSurface` (Phase B1 — `materials.md` §2,
 * `implementation.md` §4).
 *
 * The map is the optical prescription: a procedural SVG (two gradient plates
 * blended into a flat, blurred core) rasterised by the browser and fed to the
 * primitive's `feImage`. Every new data URI forces the filter to re-decode, so
 * two disciplines live here rather than in the component:
 *
 *   1. `buildDisplacementMap` is memoised by its full parameter set. A surface
 *      that toggles between two sizes (the note toolbar's 320 ↔ 420 px bar)
 *      never rebuilds a map it has already built.
 *   2. `createSettledMeasure` coalesces a burst of size observations into one
 *      settled value: the first observation lands immediately (the map must be
 *      right on first paint), every later one is trailing-debounced, and
 *      sub-pixel jitter is rounded away before it can count as a change.
 *
 * Pointer movement never reaches this module — the light is CSS variables, the
 * map is geometry.
 */

export interface DisplacementMapParams {
  width: number
  height: number
  borderRadius: number
  borderWidth: number
  brightness: number
  opacity: number
  blur: number
  mixBlendMode: string
  redGradId: string
  blueGradId: string
}

const MAP_CACHE_LIMIT = 24

/** Per-instance memo. Bounded so a resized surface cannot grow it forever. */
export function createDisplacementMapCache() {
  const cache = new Map<string, string>()
  return function build(params: DisplacementMapParams): string {
    const key = [
      params.width,
      params.height,
      params.borderRadius,
      params.borderWidth,
      params.brightness,
      params.opacity,
      params.blur,
      params.mixBlendMode,
      params.redGradId,
    ].join('|')
    const hit = cache.get(key)
    if (hit) return hit
    const uri = buildDisplacementMap(params)
    if (cache.size >= MAP_CACHE_LIMIT) {
      const oldest = cache.keys().next().value
      if (oldest !== undefined) cache.delete(oldest)
    }
    cache.set(key, uri)
    return uri
  }
}

/** The procedural map, unchanged from the Phase 9 primitive. */
export function buildDisplacementMap(p: DisplacementMapParams): string {
  const edgeSize = Math.min(p.width, p.height) * (p.borderWidth * 0.5)
  const svgContent = `
    <svg viewBox="0 0 ${p.width} ${p.height}" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="${p.redGradId}" x1="100%" y1="0%" x2="0%" y2="0%">
          <stop offset="0%" stop-color="#0000"/>
          <stop offset="100%" stop-color="red"/>
        </linearGradient>
        <linearGradient id="${p.blueGradId}" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stop-color="#0000"/>
          <stop offset="100%" stop-color="blue"/>
        </linearGradient>
      </defs>
      <rect x="0" y="0" width="${p.width}" height="${p.height}" fill="black"></rect>
      <rect x="0" y="0" width="${p.width}" height="${p.height}" rx="${p.borderRadius}" fill="url(#${p.redGradId})" />
      <rect x="0" y="0" width="${p.width}" height="${p.height}" rx="${p.borderRadius}" fill="url(#${p.blueGradId})" style="mix-blend-mode: ${p.mixBlendMode}" />
      <rect x="${edgeSize}" y="${edgeSize}" width="${p.width - edgeSize * 2}" height="${p.height - edgeSize * 2}" rx="${p.borderRadius}" fill="hsl(0 0% ${p.brightness}% / ${p.opacity})" style="filter:blur(${p.blur}px)" />
    </svg>
  `
  return `data:image/svg+xml,${encodeURIComponent(svgContent)}`
}

export interface MeasuredSize {
  width: number
  height: number
}

/** Trailing debounce window for size observations, ms (`implementation.md` §4). */
export const MAP_SETTLE_DELAY = 120

/**
 * Coalesces size observations. `observe()` is called from a `ResizeObserver`
 * (any number of times per frame during a width transition or a window drag);
 * `onSettle` runs once with the first size and then once per quiet period, and
 * only when the rounded size actually changed.
 */
export function createSettledMeasure(
  onSettle: (size: MeasuredSize) => void,
  delay: number = MAP_SETTLE_DELAY,
) {
  let timer: ReturnType<typeof setTimeout> | null = null
  let pending: MeasuredSize | null = null
  let last: MeasuredSize | null = null

  function commit(size: MeasuredSize) {
    if (last && last.width === size.width && last.height === size.height) return
    last = size
    onSettle(size)
  }

  return {
    observe(width: number, height: number) {
      if (!(width > 0 && height > 0)) return
      const size = { width: Math.round(width), height: Math.round(height) }
      if (last === null) {
        commit(size)
        return
      }
      pending = size
      if (timer !== null) clearTimeout(timer)
      timer = setTimeout(() => {
        timer = null
        if (pending) commit(pending)
        pending = null
      }, delay)
    },
    cancel() {
      if (timer !== null) clearTimeout(timer)
      timer = null
      pending = null
    },
  }
}
