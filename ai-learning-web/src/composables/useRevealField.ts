import { onBeforeUnmount, watch, type Ref } from 'vue'
import { MAP_SETTLE_DELAY } from '@/components/experience/displacementMap'

/**
 * The reveal wake (Phase B3 — `environment.md` §1 E4).
 *
 * The pointer does not carry a lamp; it wakes the wallpaper. Every ≥12px of
 * travel of the stage's ONE eased cursor stamps a small opening. Each opening
 * grows over its own short life, decays on its own clock and has a seeded,
 * irregular edge. The canvas paints the openings as alpha, then keeps only the
 * awake plate (the wallpaper at full daylight + the luminous secondary layer)
 * inside them. When no opening lives the canvas is cleared and the loop stops.
 *
 * Environment, never material: no GlassSurface, no backdrop-filter, no budget
 * instance, no pointer listener of its own (the cursor comes from
 * `useGlassSpotlight().smoothedCursor`).
 *
 * Two clocks, kept apart (`implementation.md` §12, the hybrid class):
 *   position  — the eased cursor supplies where openings are stamped;
 *   time      — each opening's growth and decay.
 * Stationary pointer → no travel → no stamps → no frames.
 */

/** The wake's parameters — tuned in B3 from the reassessment's §10.3 starting point. */
export const WAKE = {
  /** px of eased-cursor travel between two openings */
  spacing: 12,
  /** ms an opening lives: grow, then decay to nothing */
  life: 760,
  /** px radius at birth */
  radiusBirth: 10,
  /** px radius ceiling — the local opening; the retired spotlight reached 576px */
  radiusMax: 112,
  /** each opening's size seed lies in [seedFloor, 1] */
  seedFloor: 0.55,
  /** the edge: radius × (edgeBase + Σ three seeded sines, each ≤ its amplitude) */
  edgeBase: 0.78,
  edgeAmplitudes: [0.1, 0.07, 0.05] as const,
  edgeFrequencies: [3, 5, 7] as const,
  /** rad/ms — the edge drifts a little as the opening grows, never spins */
  edgeDrift: 0.0011,
  /** polygon resolution of one opening */
  edgeSegments: 28,
  /** living openings, worst case */
  maxOpenings: 64,
  /** stamps per cursor update (a flick is spread, never a solid tube) */
  maxStampsPerUpdate: 6,
  /** a jump this large in one update is a re-entry, not travel */
  teleport: 280,
  /** the opening never fully clears the atmosphere */
  peakAlpha: 0.88,
  /** the inner plateau of an opening's soft ramp (fraction of its radius) */
  plateau: 0.34,
  /** canvas cost ∝ pixels */
  dprCap: 1.5,
  /** how much of the wake the glass shelters keep out (0 = none, 1 = all) */
  shelter: 0.6,
  /** px feather of a shelter's edge */
  shelterFeather: 44,
} as const

/** Upper bound on any opening's radius at any age, at any angle. */
export const WAKE_MAX_RADIUS =
  WAKE.radiusMax * (WAKE.edgeBase + WAKE.edgeAmplitudes.reduce((sum, a) => sum + a, 0))

export interface Point {
  x: number
  y: number
}

export interface WakeOpening {
  x: number
  y: number
  born: number
  /** size seed in [seedFloor, 1] */
  scale: number
  phases: [number, number, number]
}

const easeOutCubic = (t: number) => 1 - (1 - t) ** 3
const clamp01 = (t: number) => Math.min(1, Math.max(0, t))

/** Normalised age of an opening, 0 at birth, 1 at death. */
export const openingProgress = (opening: WakeOpening, now: number) =>
  clamp01((now - opening.born) / WAKE.life)

/** The opening's nominal radius — grows fast, then holds (easeOutCubic). */
export function openingRadius(opening: WakeOpening, now: number): number {
  const t = openingProgress(opening, now)
  const ceiling = WAKE.radiusMax * opening.scale
  return WAKE.radiusBirth + (ceiling - WAKE.radiusBirth) * easeOutCubic(t)
}

/** Presence of the opening — strongest at birth, gone at the end of its life. */
export function openingAlpha(opening: WakeOpening, now: number): number {
  const t = openingProgress(opening, now)
  return WAKE.peakAlpha * (1 - t * t)
}

/** The seeded, irregular edge: the radius at bearing `theta`. Never above the nominal radius. */
export function openingEdge(opening: WakeOpening, now: number, theta: number): number {
  const drift = (now - opening.born) * WAKE.edgeDrift
  let factor = WAKE.edgeBase
  for (let i = 0; i < 3; i++) {
    factor +=
      WAKE.edgeAmplitudes[i]! * Math.sin(WAKE.edgeFrequencies[i]! * theta + opening.phases[i]! + drift)
  }
  return openingRadius(opening, now) * factor
}

/** Small deterministic PRNG so a wake is reproducible in tests. */
export function seededRandom(seed: number) {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export interface WakeOptions {
  now: () => number
  requestFrame: (callback: () => void) => number
  cancelFrame: (id: number) => void
  /** Called once per frame while openings live, and once with [] when the last one dies. */
  paint: (openings: readonly WakeOpening[], now: number) => void
  seed?: number
}

/**
 * The wake model — no DOM. `track()` is fed the eased cursor in stage
 * coordinates; the model stamps openings along real travel and runs one
 * self-settling frame loop while any opening lives.
 */
export function createWake(options: WakeOptions) {
  const random = seededRandom(options.seed ?? 0x5eed)
  const openings: WakeOpening[] = []
  let anchor: Point | null = null
  let frame = 0

  function spawn(x: number, y: number, now: number) {
    openings.push({
      x,
      y,
      born: now,
      scale: WAKE.seedFloor + (1 - WAKE.seedFloor) * random(),
      phases: [random() * Math.PI * 2, random() * Math.PI * 2, random() * Math.PI * 2],
    })
    if (openings.length > WAKE.maxOpenings) openings.splice(0, openings.length - WAKE.maxOpenings)
  }

  function tick() {
    frame = 0
    const now = options.now()
    let alive = 0
    for (const opening of openings) {
      if (now - opening.born < WAKE.life) openings[alive++] = opening
    }
    openings.length = alive
    options.paint(openings, now)
    if (openings.length > 0) frame = options.requestFrame(tick)
  }

  function schedule() {
    if (frame === 0) frame = options.requestFrame(tick)
  }

  /**
   * Feed one eased-cursor position (stage coordinates). Travel accumulates
   * from the last stamp however slowly or haltingly the hand moves — a pause
   * never forfeits distance; only a jump (re-entry, gallery return) re-anchors.
   */
  function track(x: number, y: number) {
    const now = options.now()
    const previous = anchor
    if (!previous) {
      anchor = { x, y }
      return
    }
    const dx = x - previous.x
    const dy = y - previous.y
    const distance = Math.hypot(dx, dy)
    if (distance > WAKE.teleport) {
      anchor = { x, y }
      return
    }
    if (distance < WAKE.spacing) return

    const steps = Math.floor(distance / WAKE.spacing)
    if (steps <= WAKE.maxStampsPerUpdate) {
      for (let i = 1; i <= steps; i++) {
        const along = (WAKE.spacing * i) / distance
        spawn(previous.x + dx * along, previous.y + dy * along, now)
      }
      const along = (WAKE.spacing * steps) / distance
      anchor = { x: previous.x + dx * along, y: previous.y + dy * along }
    } else {
      for (let i = 1; i <= WAKE.maxStampsPerUpdate; i++) {
        const along = i / WAKE.maxStampsPerUpdate
        spawn(previous.x + dx * along, previous.y + dy * along, now)
      }
      anchor = { x, y }
    }
    schedule()
  }

  /** Forget the path (the stage stopped being awake: gallery change, disable). */
  function reset() {
    anchor = null
  }

  function dispose() {
    if (frame !== 0) options.cancelFrame(frame)
    frame = 0
    openings.length = 0
    reset()
  }

  return {
    track,
    reset,
    dispose,
    openings: openings as readonly WakeOpening[],
    running: () => frame !== 0,
  }
}

// ---------------------------------------------------------------------------
// Canvas — the wake's mask and the plate it reveals
// ---------------------------------------------------------------------------

export interface Box {
  x: number
  y: number
  width: number
  height: number
}

/** `object-fit: cover` geometry — the same crop the CSS wallpaper renders. */
export function coverRect(
  box: Box,
  natural: { width: number; height: number },
  position: Point = { x: 0.5, y: 0.5 },
): Box {
  const scale = Math.max(box.width / natural.width, box.height / natural.height)
  const width = natural.width * scale
  const height = natural.height * scale
  return {
    x: box.x + (box.width - width) * position.x,
    y: box.y + (box.height - height) * position.y,
    width,
    height,
  }
}

/** `object-position` percentages → fractions (anything unparseable is centred). */
export function parseObjectPosition(value: string): Point {
  const parts = value.trim().split(/\s+/)
  const fraction = (part: string | undefined) =>
    part && part.endsWith('%') && Number.isFinite(parseFloat(part)) ? parseFloat(part) / 100 : 0.5
  return { x: fraction(parts[0]), y: fraction(parts[1]) }
}

/** The smallest device-pixel box that holds every living opening. */
export function wakeBounds(openings: readonly WakeOpening[], now: number, dpr: number, limit: Box): Box | null {
  if (openings.length === 0) return null
  let left = Infinity
  let top = Infinity
  let right = -Infinity
  let bottom = -Infinity
  for (const opening of openings) {
    const r = openingRadius(opening, now)
    left = Math.min(left, opening.x - r)
    top = Math.min(top, opening.y - r)
    right = Math.max(right, opening.x + r)
    bottom = Math.max(bottom, opening.y + r)
  }
  const x = Math.max(limit.x, Math.floor(left * dpr))
  const y = Math.max(limit.y, Math.floor(top * dpr))
  const x2 = Math.min(limit.x + limit.width, Math.ceil(right * dpr))
  const y2 = Math.min(limit.y + limit.height, Math.ceil(bottom * dpr))
  if (x2 <= x || y2 <= y) return null
  return { x, y, width: x2 - x, height: y2 - y }
}

/** CSS px → mask px. Openings are painted into a quarter-resolution mask. */
export const WAKE_MASK_SCALE = 0.25

/**
 * The wake's renderer. Two rules keep a frame cheap enough to never matter:
 *
 *   1. Openings are painted as alpha into a small mask (a quarter of the stage
 *      in CSS px). Their soft ramps and seeded edges cost a few thousand pixels,
 *      and scaling the mask up is what gives the edge its softness — no filter.
 *   2. The plate is composited only inside the living openings' bounding box
 *      (a clip), and only the box painted last frame is cleared. Nothing
 *      outside the wake is touched, so cost follows the wake, not the stage.
 *
 * Canvas colour values below are alpha carriers for the mask, not colours of
 * the scene.
 */
export function createWakePainter(canvas: HTMLCanvasElement, makeCanvas: () => HTMLCanvasElement = () => document.createElement('canvas')) {
  const ctx = canvas.getContext('2d')
  const mask = makeCanvas()
  const maskCtx = mask.getContext('2d')
  let painted: Box | null = null

  function resize(width: number, height: number, dpr: number) {
    canvas.width = Math.max(1, Math.round(width * dpr))
    canvas.height = Math.max(1, Math.round(height * dpr))
    mask.width = Math.max(1, Math.ceil(width * WAKE_MASK_SCALE))
    mask.height = Math.max(1, Math.ceil(height * WAKE_MASK_SCALE))
    painted = null
  }

  function paintMask(openings: readonly WakeOpening[], now: number) {
    if (!maskCtx) return
    maskCtx.setTransform(1, 0, 0, 1, 0, 0)
    maskCtx.clearRect(0, 0, mask.width, mask.height)
    maskCtx.setTransform(WAKE_MASK_SCALE, 0, 0, WAKE_MASK_SCALE, 0, 0)
    const step = (Math.PI * 2) / WAKE.edgeSegments
    for (const opening of openings) {
      const alpha = openingAlpha(opening, now)
      if (alpha <= 0.004) continue
      const r = openingRadius(opening, now)
      const ramp = maskCtx.createRadialGradient(opening.x, opening.y, 0, opening.x, opening.y, r)
      ramp.addColorStop(0, `rgba(0, 0, 0, ${alpha.toFixed(3)})`)
      ramp.addColorStop(WAKE.plateau, `rgba(0, 0, 0, ${alpha.toFixed(3)})`)
      ramp.addColorStop(1, 'rgba(0, 0, 0, 0)')
      maskCtx.fillStyle = ramp
      maskCtx.beginPath()
      for (let i = 0; i < WAKE.edgeSegments; i++) {
        const theta = i * step
        const edge = openingEdge(opening, now, theta)
        const px = opening.x + Math.cos(theta) * edge
        const py = opening.y + Math.sin(theta) * edge
        if (i === 0) maskCtx.moveTo(px, py)
        else maskCtx.lineTo(px, py)
      }
      maskCtx.closePath()
      maskCtx.fill()
    }
  }

  function paint(plate: CanvasImageSource | null, openings: readonly WakeOpening[], now: number, dpr: number) {
    if (!ctx) return
    ctx.setTransform(1, 0, 0, 1, 0, 0)
    ctx.globalCompositeOperation = 'source-over'
    if (painted) {
      ctx.clearRect(painted.x, painted.y, painted.width, painted.height)
      painted = null
    }
    if (!plate) return
    const bounds = wakeBounds(openings, now, dpr, { x: 0, y: 0, width: canvas.width, height: canvas.height })
    if (!bounds) return

    paintMask(openings, now)
    const k = WAKE_MASK_SCALE / dpr
    ctx.save()
    ctx.beginPath()
    ctx.rect(bounds.x, bounds.y, bounds.width, bounds.height)
    ctx.clip()
    ctx.drawImage(plate, bounds.x, bounds.y, bounds.width, bounds.height, bounds.x, bounds.y, bounds.width, bounds.height)
    ctx.globalCompositeOperation = 'destination-in'
    ctx.drawImage(mask, bounds.x * k, bounds.y * k, bounds.width * k, bounds.height * k, bounds.x, bounds.y, bounds.width, bounds.height)
    ctx.restore()
    painted = bounds
  }

  function dispose() {
    mask.width = 0
    mask.height = 0
    painted = null
  }

  return { resize, paint, dispose }
}

export interface PlateInput {
  width: number
  height: number
  dpr: number
  wallpaper: { image: CanvasImageSource; natural: { width: number; height: number }; box: Box; position: Point }
  /** the daylight exposure of the awake room — a CSS colour read from the stage's token, screen-applied */
  daylight: string | null
  secondary: { image: CanvasImageSource; box: Box } | null
  /** glass slabs the wake stays mostly out of, each with its corner radius */
  shelters: Array<Box & { radius: number }>
}

/**
 * The awake plate — built once per settled size / image load, never per frame:
 * the room at daylight exposure (the wallpaper without its atmosphere, lifted by
 * the stage's `--environment-wake-daylight`), the secondary layer at full light
 * (screen, so its black field adds nothing), and a feathered reduction over the
 * glass shelters so the wake never lifts the dimming the slabs' labels rely on.
 */
export function buildPlate(canvas: HTMLCanvasElement, input: PlateInput) {
  canvas.width = Math.max(1, Math.round(input.width * input.dpr))
  canvas.height = Math.max(1, Math.round(input.height * input.dpr))
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(input.dpr, 0, 0, input.dpr, 0, 0)
  ctx.globalCompositeOperation = 'source-over'

  const cover = coverRect(input.wallpaper.box, input.wallpaper.natural, input.wallpaper.position)
  ctx.save()
  ctx.beginPath()
  ctx.rect(input.wallpaper.box.x, input.wallpaper.box.y, input.wallpaper.box.width, input.wallpaper.box.height)
  ctx.clip()
  ctx.drawImage(input.wallpaper.image, cover.x, cover.y, cover.width, cover.height)
  ctx.restore()

  if (input.daylight) {
    ctx.globalCompositeOperation = 'screen'
    ctx.fillStyle = input.daylight
    ctx.fillRect(0, 0, input.width, input.height)
  }

  if (input.secondary) {
    const { box, image } = input.secondary
    ctx.globalCompositeOperation = 'screen'
    ctx.drawImage(image, box.x, box.y, box.width, box.height)
  }

  // Feathered shelters: the shape is drawn far off-canvas and only its shadow
  // lands on the shelter (shadow offsets and blur are device-space).
  ctx.globalCompositeOperation = 'destination-out'
  const away = input.width + WAKE.shelterFeather * 4
  for (const box of input.shelters) {
    ctx.save()
    ctx.shadowColor = `rgba(0, 0, 0, ${WAKE.shelter})`
    ctx.shadowBlur = WAKE.shelterFeather * input.dpr
    ctx.shadowOffsetX = away * input.dpr
    ctx.fillStyle = 'rgba(0, 0, 0, 1)'
    ctx.beginPath()
    const x = box.x - away
    if (typeof ctx.roundRect === 'function') ctx.roundRect(x, box.y, box.width, box.height, box.radius)
    else ctx.rect(x, box.y, box.width, box.height)
    ctx.fill()
    ctx.restore()
  }
  ctx.globalCompositeOperation = 'source-over'
}

// ---------------------------------------------------------------------------
// Lifecycle — the component-facing composable
// ---------------------------------------------------------------------------

/** The gate: a fine hovering pointer and no reduced-motion preference. */
export const WAKE_QUERIES = {
  pointer: '(hover: hover) and (pointer: fine)',
  reducedMotion: '(prefers-reduced-motion: reduce)',
} as const

export function wakeQualifies(matches: (query: string) => boolean): boolean {
  return matches(WAKE_QUERIES.pointer) && !matches(WAKE_QUERIES.reducedMotion)
}

export interface RevealFieldSources {
  /** The stage's one eased cursor (viewport px) — `useGlassSpotlight().smoothedCursor`. */
  cursor: Readonly<Ref<Point>>
  stage: () => HTMLElement | null
  wallpaper: () => HTMLImageElement | null
  secondary: () => HTMLImageElement | null
  shelters: () => ReadonlyArray<HTMLElement | null>
  /** False while another gallery covers the stage — the wake stops stamping. */
  active: () => boolean
}

function relativeBox(el: Element, stageRect: DOMRect): Box {
  const r = el.getBoundingClientRect()
  return { x: r.left - stageRect.left, y: r.top - stageRect.top, width: r.width, height: r.height }
}

/**
 * Binds the wake to a canvas that exists only while the gate holds (the
 * component renders it under `v-if`). One ResizeObserver, no pointer listener,
 * no per-frame layout read (the stage rect is re-read only after a scroll or
 * resize marked it dirty), and the plate rebuilt only when sizes settle or an
 * image (re)loads.
 */
export function useRevealField(canvas: Ref<HTMLCanvasElement | null>, sources: RevealFieldSources) {
  let painter: ReturnType<typeof createWakePainter> | null = null
  let plate: HTMLCanvasElement | null = null
  let plateReady = false
  let dpr = 1
  let stageRect: DOMRect | null = null
  let stageRectDirty = true
  let observer: ResizeObserver | null = null
  let stopCursor: (() => void) | null = null
  let stopActive: (() => void) | null = null
  const loaders: Array<[HTMLImageElement, () => void]> = []

  const wake = createWake({
    now: () => performance.now(),
    requestFrame: (cb) => requestAnimationFrame(cb),
    cancelFrame: (id) => cancelAnimationFrame(id),
    paint: (openings, now) => {
      painter?.paint(plateReady ? plate : null, openings, now, dpr)
    },
  })

  function rebuild() {
    const stage = sources.stage()
    const wallpaper = sources.wallpaper()
    const el = canvas.value
    if (!stage || !wallpaper || !el || !plate) return
    if (!wallpaper.complete || wallpaper.naturalWidth === 0) return
    const rect = stage.getBoundingClientRect()
    stageRect = rect
    stageRectDirty = false
    dpr = Math.min(window.devicePixelRatio || 1, WAKE.dprCap)
    const width = rect.width
    const height = rect.height
    painter?.resize(width, height, dpr)

    const secondary = sources.secondary()
    const shelters = sources
      .shelters()
      .filter((s): s is HTMLElement => !!s)
      .map((s) => ({
        ...relativeBox(s, rect),
        radius: parseFloat(getComputedStyle(s).borderRadius) || 0,
      }))
    buildPlate(plate, {
      width,
      height,
      dpr,
      wallpaper: {
        image: wallpaper,
        natural: { width: wallpaper.naturalWidth, height: wallpaper.naturalHeight },
        box: relativeBox(wallpaper, rect),
        position: parseObjectPosition(getComputedStyle(wallpaper).objectPosition),
      },
      daylight: getComputedStyle(stage).getPropertyValue('--environment-wake-daylight').trim() || null,
      secondary:
        secondary && secondary.complete && secondary.naturalWidth > 0
          ? { image: secondary, box: relativeBox(secondary, rect) }
          : null,
      shelters,
    })
    plateReady = true
  }

  // Settled geometry, not per-tick geometry: the first observation rebuilds at
  // once, a burst (window drag, the card's entrance) rebuilds once after it
  // goes quiet — the displacement map's settle window, reused.
  let settleTimer: ReturnType<typeof setTimeout> | null = null
  let built = false

  function onObserved() {
    stageRectDirty = true
    if (!built) {
      built = true
      rebuild()
      return
    }
    if (settleTimer !== null) clearTimeout(settleTimer)
    settleTimer = setTimeout(() => {
      settleTimer = null
      rebuild()
    }, MAP_SETTLE_DELAY)
  }

  function markDirty() {
    stageRectDirty = true
  }

  function onAnimationEnd(event: Event) {
    if (sources.shelters().includes(event.target as HTMLElement)) onObserved()
  }

  function onCursor(point: Point) {
    if (!sources.active()) return
    const stage = sources.stage()
    if (!stage) return
    if (stageRectDirty || !stageRect) {
      stageRect = stage.getBoundingClientRect()
      stageRectDirty = false
    }
    wake.track(point.x - stageRect.left, point.y - stageRect.top)
  }

  function start(el: HTMLCanvasElement) {
    painter = createWakePainter(el)
    plate = document.createElement('canvas')
    plateReady = false
    const stage = sources.stage()
    observer = new ResizeObserver(onObserved)
    if (stage) {
      observer.observe(stage)
      stage.addEventListener('animationend', onAnimationEnd)
    }
    for (const s of sources.shelters()) if (s) observer.observe(s)
    for (const img of [sources.wallpaper(), sources.secondary()]) {
      if (!img) continue
      const onLoad = () => onObserved()
      img.addEventListener('load', onLoad)
      loaders.push([img, onLoad])
    }
    window.addEventListener('resize', markDirty)
    window.addEventListener('scroll', markDirty, { passive: true, capture: true })
    stopCursor = watch(sources.cursor, onCursor, { flush: 'sync' })
    stopActive = watch(sources.active, (on) => {
      wake.reset()
      // returning to the stage: the slabs may have moved while it was covered
      if (on) onObserved()
    })
    onObserved()
  }

  function stop() {
    stopCursor?.()
    stopActive?.()
    stopCursor = stopActive = null
    wake.dispose()
    if (settleTimer !== null) clearTimeout(settleTimer)
    settleTimer = null
    built = false
    observer?.disconnect()
    observer = null
    const stage = sources.stage()
    stage?.removeEventListener('animationend', onAnimationEnd)
    for (const [img, onLoad] of loaders) img.removeEventListener('load', onLoad)
    loaders.length = 0
    window.removeEventListener('resize', markDirty)
    window.removeEventListener('scroll', markDirty, { capture: true })
    if (plate) {
      plate.width = 0
      plate.height = 0
    }
    plate = null
    plateReady = false
    painter?.dispose()
    painter = null
  }

  watch(
    canvas,
    (el, previous) => {
      if (previous) stop()
      if (el) start(el)
    },
    { flush: 'post' },
  )

  onBeforeUnmount(stop)

  return { wake }
}
