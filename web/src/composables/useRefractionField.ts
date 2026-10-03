import { onBeforeUnmount, watch, type Ref } from 'vue'
import { MAP_SETTLE_DELAY } from '@/components/experience/displacementMap'

/**
 * The refraction wake — environment layer E4 of the Login stage
 * (`environment.md` §1).
 *
 * The pointer carries no light. It disturbs the wallpaper the way a drop of
 * liquid glass would, crossing it: under the hand a small lens bends what is
 * beneath — the picture a little magnified toward its middle and pulled tight
 * at its rim, the rim's colours parting slightly (red bends least, blue most,
 * as in the sign-in slab's own dispersion). Behind a moving hand the liquid
 * does not close at once: smaller lenses left along the path relax over about
 * half a second, and when the hand rests the lens under it relaxes too.
 *
 * Nothing is lit, brightened, dimmed or revealed: every pixel this layer draws
 * is a wallpaper pixel taken from somewhere nearby. Over the open field (one
 * flat colour) a lens has nothing to bend and is next to invisible; over a
 * lotus the petals' hairlines visibly bend. That is the whole effect.
 *
 * Environment, never material: no GlassSurface, no backdrop-filter, no SVG
 * filter, no budget instance, no pointer listener of its own. The optics are
 * the material's ideas re-derived on the CPU for the wallpaper alone — the
 * slab's refraction is strongest at its rim and clear at its centre, and so is
 * a lens here (`materials.md` §2).
 *
 * Two clocks, kept apart (`implementation.md` §12, the hybrid class):
 *   position  the RAW pointer (`useGlassSpotlight().cursor`), never the eased
 *             light. The head lens is moved onto it on every pointer event and
 *             painted in the next frame.
 *   time      presence. The head builds in `headAttack` ms and relaxes over
 *             `headRelease` ms once the hand rests; residue lenses only relax.
 * A resting pointer → no travel → the head relaxes → the loop stops.
 *
 * Cost: the painter works per pixel on the CPU, but only inside the 64 px tiles
 * a living lens touches; the displacement field is evaluated on a 2-device-px
 * grid and interpolated, and each pixel costs one to three bilinear samples of
 * the plate (the composed wallpaper, rendered once per settled size).
 */

/** The wake's parameters. Lengths are CSS px. */
export const WAKE = {
  /** the lens on the pointer: its nominal radius */
  headRadius: 60,
  /** ms — a stroke's lens forms this quickly (a lens forming, not a switch) */
  headAttack: 70,
  /** a new lens starts this far into its forming, so it is there in its very first frame */
  headOnset: 0.3,
  /** ms — pointer events are not every frame; the lens holds this long between them */
  headHold: 50,
  /** ms — once the hand rests, the lens relaxes over this long */
  headRelease: 460,
  /** px of travel between two residue lenses */
  spacing: 20,
  /** px — movement below this is jitter, not travel */
  minMove: 1,
  /** a jump this large in one update is a re-entry, not travel */
  teleport: 280,
  /** ms — the longest stretch of time one path segment is spread over */
  pathWindow: 48,
  /** residue lenses per pointer update (a flick is spread, never a solid tube) */
  maxStampsPerUpdate: 8,
  /** ms a residue lens lives */
  life: 380,
  /** residue radius as a fraction of the head's: seeded in [min, max] */
  residueScale: [0.52, 0.76] as const,
  /** residue presence at birth: seeded in [min, max] */
  residueWeight: [0.5, 0.72] as const,
  /** a residue lens widens by this fraction of its radius as it relaxes */
  spread: 0.2,
  /** presence falls as (1 − age)^decay */
  decay: 1.6,
  /** living lenses, worst case (the head is never dropped) */
  maxDrops: 32,

  /** peak displacement, as a fraction of a lens's radius */
  refraction: 0.3,
  /** displacement profile ρ^rise · (1 − ρ^fall), normalised to a peak of 1 */
  rise: 2,
  fall: 5,
  /** in the rim band, red bends (1 − d) and blue (1 + d) times as far as green */
  dispersion: 0.07,

  /** the organic edge: radius × (1 + Σ a·cos(kθ + φ)), k = 2 and 3 */
  edgeAmplitudes: [0.07, 0.045] as const,
  /** rad/ms on the page clock — the outline drifts slowly, never spins */
  edgeDrift: 0.0011,

  /** px — a lens fades out over this much of a glass slab's edge */
  shelterFeather: 48,

  /** device px per CSS px, at most (refraction is resolution-sensitive) */
  dprCap: 2,
  /** plate pixels, at most — above this the working resolution steps down */
  maxPlatePixels: 6_000_000,
  /** CSS px between two samples of the displacement field (it is smooth; pixels interpolate) */
  grid: 2,
} as const

/** A lens's outermost reach as a multiple of its nominal radius. */
export const EDGE_BOUND = 1 + WAKE.edgeAmplitudes[0] + WAKE.edgeAmplitudes[1]

/** Upper bound on any lens's reach, at any age and bearing. */
export const WAKE_MAX_RADIUS =
  WAKE.headRadius * Math.max(1, WAKE.residueScale[1] * (1 + WAKE.spread)) * EDGE_BOUND

export interface Point {
  x: number
  y: number
}

export interface Box {
  x: number
  y: number
  width: number
  height: number
}

/** One drop of the wake, in stage CSS px. */
export interface WakeDrop {
  x: number
  y: number
  /** residue: when the hand passed this spot. head: when its presence began to build */
  born: number
  /** head: when the hand last moved it (residue: its birth) */
  moved: number
  /** radius seed (1 for the head) */
  scale: number
  /** presence seed (1 for the head) */
  weight: number
  /** phases of the edge's two harmonics */
  phases: [number, number]
  head: boolean
}

const clamp01 = (t: number) => Math.min(1, Math.max(0, t))
const easeOutCubic = (t: number) => 1 - (1 - t) ** 3
/** The inverse of the head's forming curve: how far into forming gives `presence`. */
const formingFor = (presence: number) => 1 - Math.cbrt(1 - clamp01(presence))
const smoothstep = (e0: number, e1: number, x: number) => {
  const t = clamp01((x - e0) / (e1 - e0))
  return t * t * (3 - 2 * t)
}

/** Normalised age of a residue lens: 0 at birth, 1 at death. */
export const dropAge = (drop: WakeDrop, now: number) => clamp01((now - drop.born) / WAKE.life)

/** 0..1 — how present the lens is: how far it bends, and how much of it is drawn. */
export function dropPresence(drop: WakeDrop, now: number): number {
  if (drop.head) {
    const build = easeOutCubic(clamp01((now - drop.born) / WAKE.headAttack))
    const rest = now - drop.moved - WAKE.headHold
    const relax = rest <= 0 ? 1 : (1 - clamp01(rest / WAKE.headRelease)) ** WAKE.decay
    return build * relax
  }
  return drop.weight * (1 - dropAge(drop, now)) ** WAKE.decay
}

/** The lens's nominal radius: the head is constant, residue widens a little as it relaxes. */
export function dropRadius(drop: WakeDrop, now: number): number {
  if (drop.head) return WAKE.headRadius
  return WAKE.headRadius * drop.scale * (1 + WAKE.spread * easeOutCubic(dropAge(drop, now)))
}

export function dropAlive(drop: WakeDrop, now: number): boolean {
  return drop.head
    ? now - drop.moved < WAKE.headHold + WAKE.headRelease
    : now - drop.born < WAKE.life
}

/** The organic outline: the lens's reach at bearing `theta`. Never above radius × EDGE_BOUND. */
export function dropEdge(drop: WakeDrop, now: number, theta: number): number {
  const drift = now * WAKE.edgeDrift
  const [a2, a3] = WAKE.edgeAmplitudes
  return (
    dropRadius(drop, now) *
    (1 +
      a2 * Math.cos(2 * theta + drop.phases[0] + drift) +
      a3 * Math.cos(3 * theta + drop.phases[1] - drift))
  )
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
  /** Called once per frame while a drop lives, and once with [] when the last one dies. */
  paint: (drops: readonly WakeDrop[], now: number) => void
  seed?: number
}

/**
 * The wake model — no DOM. `track()` is fed the raw pointer in stage
 * coordinates; the model keeps a head lens on the pointer, drops residue along
 * the travelled path and runs one self-settling frame loop while any drop lives.
 */
export function createWake(options: WakeOptions) {
  const random = seededRandom(options.seed ?? 0x5eed)
  const drops: WakeDrop[] = []
  /** where the last residue drop was left (the path is measured from here) */
  let anchor: Point | null = null
  /** the previous accepted pointer sample */
  let last: (Point & { t: number }) | null = null
  /** the lens on the pointer, while it lives */
  let head: WakeDrop | null = null
  let frame = 0

  const between = (range: readonly [number, number]) => range[0] + (range[1] - range[0]) * random()
  const phases = (): [number, number] => [random() * Math.PI * 2, random() * Math.PI * 2]

  function add(drop: WakeDrop) {
    drops.push(drop)
    // over the cap: the oldest residue goes first; the head never does
    for (let i = 0; drops.length > WAKE.maxDrops && i < drops.length; ) {
      if (drops[i] !== head && drops[i] !== drop) drops.splice(i, 1)
      else i++
    }
    return drop
  }

  function tick() {
    frame = 0
    const now = options.now()
    let alive = 0
    for (const drop of drops) {
      if (dropAlive(drop, now)) drops[alive++] = drop
      else if (drop === head) head = null
    }
    drops.length = alive
    options.paint(drops, now)
    if (drops.length > 0) frame = options.requestFrame(tick)
  }

  function schedule() {
    if (frame === 0) frame = options.requestFrame(tick)
  }

  /**
   * Feed one raw pointer position (stage coordinates). The head moves onto it
   * immediately; residue is dropped every `spacing` px of path however slowly
   * or haltingly the hand moves — a pause never forfeits distance; only a jump
   * (re-entry, gallery return) re-anchors.
   */
  function track(x: number, y: number) {
    const now = options.now()
    const previous = last
    if (!previous) {
      anchor = { x, y }
      last = { x, y, t: now }
      return
    }
    const distance = Math.hypot(x - previous.x, y - previous.y)
    if (distance > WAKE.teleport) {
      anchor = { x, y }
      last = { x, y, t: now }
      head = null
      return
    }
    if (distance < WAKE.minMove) return

    // position: the head is on the pointer, this frame
    if (head && dropAlive(head, now)) {
      // a lens still relaxing picks up where it is — it never pops back to
      // full: its forming restarts from the point that gives its presence now
      if (now - head.moved > WAKE.headHold)
        head.born = now - WAKE.headAttack * formingFor(dropPresence(head, now))
      head.x = x
      head.y = y
      head.moved = now
    } else {
      const born = now - WAKE.headAttack * WAKE.headOnset
      head = add({ x, y, born, moved: now, scale: 1, weight: 1, phases: phases(), head: true })
    }

    // time: residue along the path since the last drop, each born when the hand
    // passed it (spread over the time this segment took, capped)
    const from = anchor ?? previous
    const dx = x - from.x
    const dy = y - from.y
    const along = Math.hypot(dx, dy)
    const steps = Math.floor(along / WAKE.spacing)
    if (steps > 0) {
      const count = Math.min(steps, WAKE.maxStampsPerUpdate)
      const span = Math.min(now - previous.t, WAKE.pathWindow)
      for (let i = 1; i <= count; i++) {
        const f = (WAKE.spacing * ((i * steps) / count)) / along
        const born = now - (1 - f) * span
        add({
          x: from.x + dx * f,
          y: from.y + dy * f,
          born,
          moved: born,
          scale: between(WAKE.residueScale),
          weight: between(WAKE.residueWeight),
          phases: phases(),
          head: false,
        })
      }
      const f = (WAKE.spacing * steps) / along
      anchor = { x: from.x + dx * f, y: from.y + dy * f }
    }
    last = { x, y, t: now }
    schedule()
  }

  /** Forget the path (the stage stopped being awake: gallery change, disable). */
  function reset() {
    anchor = null
    last = null
    head = null
  }

  function dispose() {
    if (frame !== 0) options.cancelFrame(frame)
    frame = 0
    drops.length = 0
    reset()
  }

  return {
    track,
    reset,
    dispose,
    drops: drops as readonly WakeDrop[],
    /** the lens on the pointer, while it lives */
    head: () => head,
    running: () => frame !== 0,
  }
}

// ---------------------------------------------------------------------------
// Optics — the displacement field and the refracted region (pure, typed arrays)
// ---------------------------------------------------------------------------

/** One lens as the painter sees it this frame, in device px. */
export interface Lens {
  x: number
  y: number
  /** nominal radius */
  r: number
  /** 0..1 presence (already sheltered) */
  strength: number
  /** cos / sin of the two edge harmonics' phases this frame */
  c2: number
  s2: number
  c3: number
  s3: number
}

const LUT_SIZE = 1024
/** displacement shape along the radius, peak 1: 0 at the centre and at the rim */
const PROFILE = new Float32Array(LUT_SIZE + 1)
/** how much a lens counts where lenses overlap: 1 at its centre, 0 at its rim */
const WEIGHT = new Float32Array(LUT_SIZE + 1)
/** how much of the lens is drawn: all of its interior, feathered at the rim */
const COVER = new Float32Array(LUT_SIZE + 1)
/** where colours part: only in the rim band, as at the edge of the slab — the interior stays clear */
const RIM = new Float32Array(LUT_SIZE + 1)
{
  let peak = 0
  for (let i = 0; i <= LUT_SIZE; i++) {
    const rho = i / LUT_SIZE
    PROFILE[i] = rho ** WAKE.rise * (1 - rho ** WAKE.fall)
    peak = Math.max(peak, PROFILE[i]!)
    WEIGHT[i] = (1 - rho * rho) ** 2
    COVER[i] = 1 - smoothstep(0.7, 1, rho)
    RIM[i] = smoothstep(0.7, 0.92, rho)
  }
  for (let i = 0; i <= LUT_SIZE; i++) PROFILE[i] = PROFILE[i]! / peak
}

/** How opaque the drawn lens is per unit of summed cover (a faint residue still draws faintly). */
const COVER_GAIN = 2

/**
 * The displacement field at one point: `out[0..1]` is the displacement vector
 * (device px, pointing away from the lens centres — the plate is sampled at
 * `point − displacement`, i.e. from nearer the centre: magnified), `out[2]` the
 * drawn coverage 0..1, `out[3]` how much of the point is rim (0..1: where the
 * colours part). Where lenses overlap they are blended by weight, never summed,
 * so a trail bends no further than one lens does.
 */
export function lensField(lenses: readonly Lens[], x: number, y: number, out: Float32Array) {
  const [a2, a3] = WAKE.edgeAmplitudes
  let sx = 0
  let sy = 0
  let sw = 0
  let cover = 0
  let rim = 0
  for (let i = 0; i < lenses.length; i++) {
    const lens = lenses[i]!
    const dx = x - lens.x
    const dy = y - lens.y
    const bound = lens.r * EDGE_BOUND
    if (dx >= bound || dx <= -bound || dy >= bound || dy <= -bound) continue
    const r2 = dx * dx + dy * dy
    if (r2 >= bound * bound) continue
    const r = Math.sqrt(r2)
    let c = 0
    let s = 0
    let edge = 1
    if (r > 1e-6) {
      c = dx / r
      s = dy / r
      const cos2 = c * c - s * s
      const sin2 = 2 * c * s
      const cos3 = c * (4 * c * c - 3)
      const sin3 = s * (3 - 4 * s * s)
      edge += a2 * (cos2 * lens.c2 - sin2 * lens.s2) + a3 * (cos3 * lens.c3 - sin3 * lens.s3)
    }
    const rho = r / (lens.r * edge)
    if (rho >= 1) continue
    const k = (rho * LUT_SIZE + 0.5) | 0
    const d = lens.strength * WAKE.refraction * lens.r * PROFILE[k]!
    const w = lens.strength * WEIGHT[k]! + 1e-6
    sx += w * c * d
    sy += w * s * d
    rim += w * RIM[k]!
    sw += w
    cover += lens.strength * COVER[k]!
  }
  out[0] = sw > 0 ? sx / sw : 0
  out[1] = sw > 0 ? sy / sw : 0
  out[2] = Math.min(1, cover * COVER_GAIN)
  out[3] = sw > 0 ? rim / sw : 0
}

/** The composed wallpaper, RGBA, at the painter's working resolution. */
export interface Plate {
  data: Uint8ClampedArray
  width: number
  height: number
}

/** Grow-only scratch for the field grid of one region. */
export function createFieldScratch() {
  const scratch = {
    x: new Float32Array(0),
    y: new Float32Array(0),
    a: new Float32Array(0),
    rim: new Float32Array(0),
    point: new Float32Array(4),
    ensure(size: number) {
      if (scratch.x.length >= size) return
      scratch.x = new Float32Array(size)
      scratch.y = new Float32Array(size)
      scratch.a = new Float32Array(size)
      scratch.rim = new Float32Array(size)
    },
  }
  return scratch
}

function lensTouches(lens: Lens, box: Box) {
  const reach = lens.r * EDGE_BOUND
  return (
    lens.x + reach > box.x &&
    lens.x - reach < box.x + box.width &&
    lens.y + reach > box.y &&
    lens.y - reach < box.y + box.height
  )
}

/** One channel of the plate, bilinear, at a continuous pixel-index position (centres on integers). */
function sample(
  p: Uint8ClampedArray,
  width: number,
  height: number,
  x: number,
  y: number,
  channel: number,
) {
  const xm = width - 1
  const ym = height - 1
  const sx = x < 0 ? 0 : x > xm ? xm : x
  const sy = y < 0 ? 0 : y > ym ? ym : y
  const x0 = sx | 0
  const y0 = sy | 0
  const tx = sx - x0
  const ty = sy - y0
  const x1 = x0 < xm ? x0 + 1 : x0
  const row0 = y0 * width
  const row1 = (y0 < ym ? y0 + 1 : y0) * width
  const a = p[(row0 + x0) * 4 + channel]!
  const b = p[(row0 + x1) * 4 + channel]!
  const c = p[(row1 + x0) * 4 + channel]!
  const d = p[(row1 + x1) * 4 + channel]!
  const top = a + (b - a) * tx
  return top + (c + (d - c) * tx - top) * ty
}

/** Device px — a displacement below this is not a visible bend. */
const STILL = 0.25

const still = (dx: number, dy: number) => (dx < 0 ? -dx : dx) + (dy < 0 ? -dy : dy) < STILL

/** All three channels of the plate at one position, bilinear, into `out[o..o+2]`. */
function sampleRGB(
  p: Uint8ClampedArray,
  width: number,
  height: number,
  x: number,
  y: number,
  out: Uint8ClampedArray,
  o: number,
) {
  const xm = width - 1
  const ym = height - 1
  const sx = x < 0 ? 0 : x > xm ? xm : x
  const sy = y < 0 ? 0 : y > ym ? ym : y
  const x0 = sx | 0
  const y0 = sy | 0
  const tx = sx - x0
  const ty = sy - y0
  const i00 = (y0 * width + x0) * 4
  const i10 = x0 < xm ? i00 + 4 : i00
  const i01 = y0 < ym ? i00 + width * 4 : i00
  const i11 = x0 < xm ? i01 + 4 : i01
  for (let c = 0; c < 3; c++) {
    const top = p[i00 + c]! + (p[i10 + c]! - p[i00 + c]!) * tx
    out[o + c] = top + (p[i01 + c]! + (p[i11 + c]! - p[i01 + c]!) * tx - top) * ty
  }
}

/**
 * Refract one region of the plate into `out` (row stride `out.width`): each
 * pixel is the plate sampled at its displaced position — in the rim band, red
 * a little short of it and blue a little past it — and drawn with the lenses'
 * coverage. Pixels no lens covers are left fully transparent, so the real
 * wallpaper shows through them untouched.
 *
 * The field is evaluated every `grid` device px and interpolated; a grid cell
 * whose four corners are all uncovered is skipped whole.
 */
export function refractRegion(
  plate: Plate,
  box: Box,
  lenses: readonly Lens[],
  out: { data: Uint8ClampedArray; width: number },
  field: ReturnType<typeof createFieldScratch>,
  grid: number = 2,
) {
  const G = Math.max(1, Math.round(grid))
  const inv = 1 / G
  const gw = Math.floor((box.width - 1) / G) + 2
  const gh = Math.floor((box.height - 1) / G) + 2
  field.ensure(gw * gh)
  const local = lenses.filter((lens) => lensTouches(lens, box))
  const row: Lens[] = []
  const point = field.point
  for (let j = 0; j < gh; j++) {
    const y = box.y + j * G + 0.5
    // only the lenses this row of the grid can reach
    row.length = 0
    for (const lens of local) {
      const reach = lens.r * EDGE_BOUND
      if (y > lens.y - reach && y < lens.y + reach) row.push(lens)
    }
    for (let i = 0; i < gw; i++) {
      const n = j * gw + i
      if (row.length === 0) {
        field.x[n] = field.y[n] = field.a[n] = field.rim[n] = 0
        continue
      }
      lensField(row, box.x + i * G + 0.5, y, point)
      field.x[n] = point[0]!
      field.y[n] = point[1]!
      field.a[n] = point[2]!
      field.rim[n] = point[3]!
    }
  }

  const P = plate.data
  const W = plate.width
  const H = plate.height
  const D = out.data
  const stride = out.width * 4
  const fx = field.x
  const fy = field.y
  const fa = field.a
  const fr = field.rim
  const dispersion = WAKE.dispersion
  for (let y = 0; y < box.height; y++) {
    const v = y * inv
    const j = v | 0
    const ty = v - j
    const Y = box.y + y
    const rowStart = y * stride
    for (let i = 0; i * G < box.width; i++) {
      const n00 = j * gw + i
      const n01 = n00 + gw
      const x0 = i * G
      const xEnd = Math.min(box.width, x0 + G)
      // the cell's left and right edges at this row; pixels step linearly between
      const aL = fa[n00]! + (fa[n01]! - fa[n00]!) * ty
      const aR = fa[n00 + 1]! + (fa[n01 + 1]! - fa[n00 + 1]!) * ty
      const xL = fx[n00]! + (fx[n01]! - fx[n00]!) * ty
      const xR = fx[n00 + 1]! + (fx[n01 + 1]! - fx[n00 + 1]!) * ty
      const yL = fy[n00]! + (fy[n01]! - fy[n00]!) * ty
      const yR = fy[n00 + 1]! + (fy[n01 + 1]! - fy[n00 + 1]!) * ty
      if ((aL < 0.004 && aR < 0.004) || (still(xL, yL) && still(xR, yR))) {
        // nothing covers this stretch, or nothing in it moves by a visible
        // amount (a lens's centre, a relaxed residue): the wallpaper beneath is
        // already exactly this, so leave it transparent
        for (let x = x0, o = rowStart + x * 4; x < xEnd; x++, o += 4) D[o + 3] = 0
        continue
      }
      const rL = fr[n00]! + (fr[n01]! - fr[n00]!) * ty
      const rR = fr[n00 + 1]! + (fr[n01 + 1]! - fr[n00 + 1]!) * ty
      const aStep = (aR - aL) * inv
      const xStep = (xR - xL) * inv
      const yStep = (yR - yL) * inv
      const rStep = (rR - rL) * inv
      let alpha = aL
      let dx = xL
      let dy = yL
      let rim = rL
      for (let x = x0, o = rowStart + x * 4; x < xEnd; x++, o += 4) {
        if (alpha < 0.004 || still(dx, dy)) {
          D[o + 3] = 0
        } else {
          const X = box.x + x
          const spread = dispersion * rim
          if (((dx < 0 ? -dx : dx) + (dy < 0 ? -dy : dy)) * spread < 0.2) {
            // a fraction of a pixel of dispersion: one position for all three
            sampleRGB(P, W, H, X - dx, Y - dy, D, o)
          } else {
            D[o] = sample(P, W, H, X - dx * (1 - spread), Y - dy * (1 - spread), 0)
            D[o + 1] = sample(P, W, H, X - dx, Y - dy, 1)
            D[o + 2] = sample(P, W, H, X - dx * (1 + spread), Y - dy * (1 + spread), 2)
          }
          D[o + 3] = alpha * 255 + 0.5
        }
        alpha += aStep
        dx += xStep
        dy += yStep
        rim += rStep
      }
    }
  }
}

/** CSS px — the painter's tile: only tiles a lens touches are drawn. */
export const WAKE_TILE = 32

/**
 * The device-pixel rectangles the living lenses occupy: the tiles any lens
 * reaches, merged into horizontal runs and then into vertical stacks of equal
 * runs. A single union box would span the stage whenever the wake has two far
 * ends (a long flick, a re-entry while the old trail still relaxes), and the
 * frame's cost would follow the box instead of the wake.
 */
export function lensRegions(lenses: readonly Lens[], tile: number, limit: Box): Box[] {
  if (lenses.length === 0) return []
  const cols = Math.max(1, Math.ceil(limit.width / tile))
  const rows = Math.max(1, Math.ceil(limit.height / tile))
  const marked = new Uint8Array(cols * rows)
  for (const lens of lenses) {
    const reach = lens.r * EDGE_BOUND
    const c0 = Math.max(0, Math.floor((lens.x - reach - limit.x) / tile))
    const c1 = Math.min(cols - 1, Math.floor((lens.x + reach - limit.x) / tile))
    const r0 = Math.max(0, Math.floor((lens.y - reach - limit.y) / tile))
    const r1 = Math.min(rows - 1, Math.floor((lens.y + reach - limit.y) / tile))
    for (let row = r0; row <= r1; row++)
      for (let col = c0; col <= c1; col++) marked[row * cols + col] = 1
  }
  const regions: Box[] = []
  // runs of the previous row, keyed by their column span, so equal runs stack
  let open = new Map<string, Box>()
  for (let row = 0; row < rows; row++) {
    const next = new Map<string, Box>()
    for (let col = 0; col < cols; col++) {
      if (!marked[row * cols + col]) continue
      const start = col
      while (col + 1 < cols && marked[row * cols + col + 1]) col++
      const key = start + ':' + col
      const x = limit.x + start * tile
      const y = limit.y + row * tile
      const box = open.get(key)
      if (box) {
        box.height = Math.min(limit.y + limit.height, y + tile) - box.y
        next.set(key, box)
      } else {
        const fresh = {
          x,
          y,
          width: Math.min(limit.x + limit.width, limit.x + (col + 1) * tile) - x,
          height: Math.min(limit.y + limit.height, y + tile) - y,
        }
        regions.push(fresh)
        next.set(key, fresh)
      }
    }
    open = next
  }
  return regions
}

/** Distance from a point to a box's edge: negative inside, positive outside. */
export function signedDistance(box: Box, x: number, y: number): number {
  const dx = Math.max(box.x - x, 0, x - (box.x + box.width))
  const dy = Math.max(box.y - y, 0, y - (box.y + box.height))
  if (dx > 0 || dy > 0) return Math.hypot(dx, dy)
  return -Math.min(x - box.x, box.x + box.width - x, y - box.y, box.y + box.height - y)
}

/**
 * How much of a lens survives at (x, y) near the glass slabs: the wake belongs
 * to the wallpaper, so a lens fades out as it slides under a slab — its labels
 * keep a calm backdrop, and the slab's refraction is not re-run for a
 * disturbance nobody could see through smoked glass.
 */
export function shelterFactor(shelters: readonly Box[], x: number, y: number): number {
  let factor = 1
  const half = WAKE.shelterFeather / 2
  for (const box of shelters) factor *= smoothstep(-half, half, signedDistance(box, x, y))
  return factor
}

/** This frame's lenses, in device px. */
export function frameLenses(
  drops: readonly WakeDrop[],
  now: number,
  dpr: number,
  shelters: readonly Box[],
): Lens[] {
  const drift = now * WAKE.edgeDrift
  const lenses: Lens[] = []
  for (const drop of drops) {
    const strength = dropPresence(drop, now) * shelterFactor(shelters, drop.x, drop.y)
    if (strength < 0.004) continue
    const p2 = drop.phases[0] + drift
    const p3 = drop.phases[1] - drift
    lenses.push({
      x: drop.x * dpr,
      y: drop.y * dpr,
      r: dropRadius(drop, now) * dpr,
      strength,
      c2: Math.cos(p2),
      s2: Math.sin(p2),
      c3: Math.cos(p3),
      s3: Math.sin(p3),
    })
  }
  return lenses
}

/**
 * The wake's renderer. `setPlate` hands it the composed wallpaper (and sizes
 * the canvas to it); `paint` clears the tiles drawn last frame and refracts the
 * tiles this frame's lenses touch — nothing else on the canvas is touched.
 */
export function createRefractionPainter(canvas: HTMLCanvasElement) {
  const ctx = canvas.getContext('2d')
  const field = createFieldScratch()
  let plate: Plate | null = null
  let dpr = 1
  let shelters: Box[] = []
  let painted: Box[] = []
  let scratch: ImageData | null = null

  function clear() {
    if (ctx) for (const box of painted) ctx.clearRect(box.x, box.y, box.width, box.height)
    painted = []
  }

  function setPlate(next: Plate | null, nextDpr: number, nextShelters: Box[]) {
    clear()
    plate = next
    dpr = nextDpr
    shelters = nextShelters
    if (next && (canvas.width !== next.width || canvas.height !== next.height)) {
      canvas.width = next.width
      canvas.height = next.height
    }
  }

  function paint(drops: readonly WakeDrop[], now: number) {
    if (!ctx) return
    clear()
    if (!plate) return
    const lenses = frameLenses(drops, now, dpr, shelters)
    if (lenses.length === 0) return
    const tile = Math.max(8, Math.round(WAKE_TILE * dpr))
    const grid = Math.max(1, Math.round(WAKE.grid * dpr))
    const regions = lensRegions(lenses, tile, {
      x: 0,
      y: 0,
      width: plate.width,
      height: plate.height,
    })
    for (const box of regions) {
      if (!scratch || scratch.width < box.width || scratch.height < box.height) {
        scratch = new ImageData(
          Math.max(box.width, scratch?.width ?? 0),
          Math.max(box.height, scratch?.height ?? 0),
        )
      }
      refractRegion(plate, box, lenses, scratch, field, grid)
      ctx.putImageData(scratch, box.x, box.y, 0, 0, box.width, box.height)
    }
    painted = regions
  }

  function dispose() {
    clear()
    plate = null
    scratch = null
  }

  return { setPlate, paint, clear, dispose }
}

// ---------------------------------------------------------------------------
// The plate — the wallpaper as the stage composes it, re-drawn once per settle
// ---------------------------------------------------------------------------

/** One image layer of the wallpaper as CSS lays it out. */
export interface PlateLayer {
  image: CanvasImageSource
  /** the untransformed layout box, stage px */
  box: Box
  /** the CSS transform as a 2D matrix [a, b, c, d, e, f], about `origin` */
  matrix: readonly [number, number, number, number, number, number]
  /** transform-origin, px within the box */
  origin: Point
  /** the canvas composite operation matching the layer's mix-blend-mode */
  blend: GlobalCompositeOperation
  opacity: number
}

export interface PlateInput {
  /** stage CSS px */
  width: number
  height: number
  dpr: number
  /** the stage's own colour — the field the layers are blended onto */
  field: string
  layers: readonly PlateLayer[]
}

/**
 * Compose the wallpaper into `canvas` exactly as CSS composes it — the field,
 * then each layer at its layout box under its transform and blend — and return
 * its pixels. Built once per settled size, image load or theme change; never
 * per frame.
 */
export function buildPlate(canvas: HTMLCanvasElement, input: PlateInput): Plate | null {
  canvas.width = Math.max(1, Math.round(input.width * input.dpr))
  canvas.height = Math.max(1, Math.round(input.height * input.dpr))
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return null
  ctx.setTransform(input.dpr, 0, 0, input.dpr, 0, 0)
  ctx.globalCompositeOperation = 'source-over'
  ctx.fillStyle = input.field
  ctx.fillRect(0, 0, input.width, input.height)
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  for (const layer of input.layers) {
    const [a, b, c, d, e, f] = layer.matrix
    ctx.save()
    ctx.globalCompositeOperation = layer.blend
    ctx.globalAlpha = layer.opacity
    ctx.translate(layer.box.x + layer.origin.x, layer.box.y + layer.origin.y)
    ctx.transform(a, b, c, d, e, f)
    ctx.translate(-layer.origin.x, -layer.origin.y)
    ctx.drawImage(layer.image, 0, 0, layer.box.width, layer.box.height)
    ctx.restore()
  }
  const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height)
  return { data: pixels.data, width: pixels.width, height: pixels.height }
}

/** `matrix(a, b, c, d, e, f)` / `none` → the six numbers (anything else is identity). */
export function parseTransform(value: string): [number, number, number, number, number, number] {
  const match = value.match(/^matrix\(([^)]+)\)$/)
  if (!match) return [1, 0, 0, 1, 0, 0]
  const n = match[1]!.split(',').map((part) => parseFloat(part))
  if (n.length !== 6 || n.some((v) => !Number.isFinite(v))) return [1, 0, 0, 1, 0, 0]
  return n as [number, number, number, number, number, number]
}

/** CSS `mix-blend-mode` → canvas composite operation. */
export function blendOperation(mode: string): GlobalCompositeOperation {
  return (!mode || mode === 'normal' ? 'source-over' : mode) as GlobalCompositeOperation
}

/** The working resolution: the device's, capped, and stepped down for very large stages. */
export function workingDpr(deviceRatio: number, width: number, height: number): number {
  const capped = Math.min(deviceRatio || 1, WAKE.dprCap)
  const area = Math.max(1, width * height)
  return Math.min(capped, Math.sqrt(WAKE.maxPlatePixels / area))
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

export interface RefractionFieldSources {
  /**
   * The stage's raw pointer (viewport px) — `useGlassSpotlight().cursor`, set
   * synchronously on every pointer event. Never the eased light.
   */
  cursor: Readonly<Ref<Point>>
  stage: () => HTMLElement | null
  /**
   * The wallpaper's image layers. Each image fills an untransformed frame (its
   * parent, which carries the layer's blend and opacity) and may carry its own
   * transform; the plate re-draws them the same way.
   */
  layers: () => ReadonlyArray<HTMLImageElement | null>
  /** Glass slabs a lens fades out under. */
  shelters: () => ReadonlyArray<HTMLElement | null>
  /** False while another gallery covers the stage — the wake stops tracking. */
  active: () => boolean
}

function relativeBox(el: Element, stageRect: DOMRect): Box {
  const r = el.getBoundingClientRect()
  return { x: r.left - stageRect.left, y: r.top - stageRect.top, width: r.width, height: r.height }
}

/** A loaded wallpaper image as the plate sees it (null until it has pixels). */
function plateLayer(img: HTMLImageElement | null, stageRect: DOMRect): PlateLayer | null {
  const frame = img?.parentElement
  if (!img || !frame || !img.complete || img.naturalWidth === 0) return null
  const style = getComputedStyle(img)
  const frameStyle = getComputedStyle(frame)
  const [ox, oy] = style.transformOrigin.split(/\s+/).map((part) => parseFloat(part))
  return {
    image: img,
    box: relativeBox(frame, stageRect),
    matrix: parseTransform(style.transform),
    origin: { x: ox ?? 0, y: oy ?? 0 },
    blend: blendOperation(frameStyle.mixBlendMode),
    opacity: (parseFloat(frameStyle.opacity) || 0) * (parseFloat(style.opacity) || 0),
  }
}

/**
 * Binds the wake to a canvas that exists only while the gate holds (the
 * component renders it under `v-if`). One ResizeObserver, no pointer listener,
 * no per-frame layout read (the stage rect is re-read only after a scroll or
 * resize marked it dirty), and the plate rebuilt only when sizes settle, an
 * image (re)loads or the theme changes the field.
 */
export function useRefractionField(
  canvas: Ref<HTMLCanvasElement | null>,
  sources: RefractionFieldSources,
) {
  let painter: ReturnType<typeof createRefractionPainter> | null = null
  let workbench: HTMLCanvasElement | null = null
  let stageRect: DOMRect | null = null
  let stageRectDirty = true
  let observer: ResizeObserver | null = null
  let themeObserver: MutationObserver | null = null
  let stopCursor: (() => void) | null = null
  let stopActive: (() => void) | null = null
  const loaders: Array<[HTMLImageElement, () => void]> = []

  const wake = createWake({
    now: () => performance.now(),
    requestFrame: (cb) => requestAnimationFrame(cb),
    cancelFrame: (id) => cancelAnimationFrame(id),
    paint: (drops, now) => painter?.paint(drops, now),
  })

  function rebuild() {
    const stage = sources.stage()
    if (!stage || !painter || !workbench) return
    const rect = stage.getBoundingClientRect()
    const layers = sources.layers().map((img) => plateLayer(img, rect))
    // every layer must have pixels, or the plate would misregister the stage
    if (layers.length === 0 || layers.some((layer) => layer === null)) return
    stageRect = rect
    stageRectDirty = false
    const dpr = workingDpr(window.devicePixelRatio, rect.width, rect.height)
    const shelters = sources
      .shelters()
      .filter((s): s is HTMLElement => !!s)
      .map((s) => relativeBox(s, rect))
    const plate = buildPlate(workbench, {
      width: rect.width,
      height: rect.height,
      dpr,
      field: getComputedStyle(stage).backgroundColor,
      layers: layers as PlateLayer[],
    })
    // the workbench's pixels now live in the plate; release its backing store
    workbench.width = 0
    workbench.height = 0
    painter.setPlate(plate, dpr, shelters)
  }

  // Settled geometry, not per-tick geometry: the first observation rebuilds at
  // once, a burst (window drag, the card's entrance) rebuilds once after it
  // goes quiet — the displacement map's settle window, reused. While a burst
  // lasts the old plate no longer registers, so nothing is drawn.
  let settleTimer: ReturnType<typeof setTimeout> | null = null
  let built = false

  function onObserved() {
    stageRectDirty = true
    if (!built) {
      built = true
      rebuild()
      return
    }
    painter?.setPlate(null, 1, [])
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
    painter = createRefractionPainter(el)
    workbench = document.createElement('canvas')
    const stage = sources.stage()
    observer = new ResizeObserver(onObserved)
    if (stage) {
      observer.observe(stage)
      stage.addEventListener('animationend', onAnimationEnd)
    }
    for (const s of sources.shelters()) if (s) observer.observe(s)
    for (const img of sources.layers()) {
      if (!img) continue
      const onLoad = () => onObserved()
      img.addEventListener('load', onLoad)
      loaders.push([img, onLoad])
    }
    // the field is a theme token: a theme flip changes the wallpaper's colour
    themeObserver = new MutationObserver(onObserved)
    themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class'],
    })
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
    themeObserver?.disconnect()
    themeObserver = null
    const stage = sources.stage()
    stage?.removeEventListener('animationend', onAnimationEnd)
    for (const [img, onLoad] of loaders) img.removeEventListener('load', onLoad)
    loaders.length = 0
    window.removeEventListener('resize', markDirty)
    window.removeEventListener('scroll', markDirty, { capture: true })
    if (workbench) {
      workbench.width = 0
      workbench.height = 0
    }
    workbench = null
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
