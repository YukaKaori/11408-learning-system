import { describe, expect, it, vi } from 'vitest'
import {
  EDGE_BOUND,
  WAKE,
  WAKE_MAX_RADIUS,
  WAKE_QUERIES,
  WAKE_TILE,
  blendOperation,
  buildPlate,
  createFieldScratch,
  createWake,
  dropAlive,
  dropEdge,
  dropPresence,
  dropRadius,
  lensField,
  lensRegions,
  parseTransform,
  refractRegion,
  shelterFactor,
  signedDistance,
  wakeQualifies,
  workingDpr,
  type Lens,
  type Plate,
  type WakeDrop,
} from '../useRefractionField'

/**
 * The refraction wake (`environment.md` §1 E4, Login rework round 3).
 *
 * Behaviour under test: the head lens sits on the raw pointer — exactly, with
 * no easing — while residue is left along the travelled path and only relaxes;
 * lenses come only from real travel; a resting hand's lens relaxes and the loop
 * stops by itself; the optics only move wallpaper pixels (never light, dim or
 * reveal), bend strongest toward the rim and not at all outside it, and never
 * compound where lenses overlap; lenses are local and irregular; the painted
 * region follows the wake's area; the plate is composed the way CSS composes
 * the wallpaper; and the whole layer is gated to nothing on coarse pointers
 * and under reduced motion.
 */

/** A deterministic clock + frame scheduler standing in for rAF. */
function harness() {
  let now = 0
  const queue = new Map<number, () => void>()
  let next = 1
  const paints: Array<{ count: number; now: number }> = []
  const wake = createWake({
    now: () => now,
    requestFrame: (cb) => {
      const id = next++
      queue.set(id, cb)
      return id
    },
    cancelFrame: (id) => {
      queue.delete(id)
    },
    paint: (drops, t) => paints.push({ count: drops.length, now: t }),
  })
  return {
    wake,
    paints,
    now: () => now,
    pending: () => queue.size,
    advance(ms: number) {
      now += ms
    },
    /** run the frames queued at this instant */
    frame() {
      const due = [...queue.entries()]
      queue.clear()
      for (const [, cb] of due) cb()
    },
    /** advance time in 16ms frames until the loop stops (or a cap) */
    runOut(capMs = 5000) {
      let spent = 0
      while (queue.size > 0 && spent < capMs) {
        now += 16
        spent += 16
        const due = [...queue.entries()]
        queue.clear()
        for (const [, cb] of due) cb()
      }
      return spent
    },
  }
}

const drop = (overrides: Partial<WakeDrop> = {}): WakeDrop => ({
  x: 0,
  y: 0,
  born: 0,
  moved: 0,
  scale: 0.7,
  weight: 0.6,
  phases: [0.3, 1.7],
  head: false,
  ...overrides,
})

const lens = (overrides: Partial<Lens> = {}): Lens => ({
  x: 100,
  y: 100,
  r: 60,
  strength: 1,
  c2: 1,
  s2: 0,
  c3: 1,
  s3: 0,
  ...overrides,
})

const residueOf = (h: ReturnType<typeof harness>) => h.wake.drops.filter((d) => !d.head)

describe('wake — lenses come from travel, never from presence', () => {
  it('a stationary pointer makes no lens and schedules no frame', () => {
    const h = harness()
    h.wake.track(400, 300)
    for (let i = 0; i < 60; i++) {
      h.advance(16)
      h.wake.track(400, 300)
    }
    expect(h.wake.drops).toHaveLength(0)
    expect(h.pending()).toBe(0)
    expect(h.wake.running()).toBe(false)
  })

  it('leaves one residue lens per spacing of travel, behind the head', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(WAKE.spacing - 1, 0)
    // the head only: travel, but not yet a spacing of it
    expect(h.wake.drops).toEqual([h.wake.head()])
    h.wake.track(WAKE.spacing * 3 + 2, 0)
    expect(residueOf(h).map((d) => d.x)).toEqual([WAKE.spacing, WAKE.spacing * 2, WAKE.spacing * 3])
    expect(h.wake.running()).toBe(true)
  })

  it('slow, halting travel still accumulates — a pause never forfeits distance', () => {
    const h = harness()
    h.wake.track(100, 100)
    for (let i = 1; i <= 6; i++) {
      h.advance(40)
      h.wake.track(100 + i * 7, 100)
    }
    expect(residueOf(h)).toHaveLength(Math.floor(42 / WAKE.spacing))
  })

  it('jitter below minMove is not travel', () => {
    const h = harness()
    h.wake.track(50, 50)
    h.wake.track(50 + WAKE.minMove / 2, 50)
    expect(h.wake.drops).toHaveLength(0)
    expect(h.pending()).toBe(0)
  })

  it('a jump (re-entry, gallery return) re-anchors without a streak', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(WAKE.teleport + 50, 0)
    expect(h.wake.drops).toHaveLength(0)
    h.wake.reset()
    h.wake.track(900, 900)
    expect(h.wake.drops).toHaveLength(0)
  })

  it('a flick is spread over at most maxStampsPerUpdate residue lenses', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(WAKE.teleport - 1, 0)
    expect(residueOf(h).length).toBeLessThanOrEqual(WAKE.maxStampsPerUpdate)
  })

  it('never keeps more than maxDrops alive, and the head survives the cap', () => {
    const h = harness()
    let x = 0
    h.wake.track(x, 0)
    for (let i = 0; i < 200; i++) {
      x += WAKE.spacing * 4
      h.wake.track(x, 0)
    }
    expect(h.wake.drops.length).toBeLessThanOrEqual(WAKE.maxDrops)
    expect(h.wake.drops).toContain(h.wake.head())
  })
})

describe('wake — the head is on the pointer, the residue keeps time', () => {
  it('the head is at the raw input position on every update — never eased toward it', () => {
    const h = harness()
    h.wake.track(0, 0)
    const path: Array<[number, number]> = [
      [37, 11],
      [120, 64],
      [121.5, 64],
      [260, 140],
    ]
    for (const [x, y] of path) {
      h.advance(16)
      h.wake.track(x, y)
      expect(h.wake.head()).toMatchObject({ x, y })
    }
  })

  it('a moving head is there in its first frame, forms within headAttack and stays formed', () => {
    const h = harness()
    h.wake.track(0, 0)
    for (let i = 1; i <= 40; i++) {
      h.advance(16)
      h.wake.track(i * 10, 0)
      h.frame()
      const presence = dropPresence(h.wake.head()!, h.now())
      if (16 * (i - 1) >= WAKE.headAttack) expect(presence).toBe(1)
      // no frame of lag: the lens that appears under the hand is already most of a lens
      else expect(presence).toBeGreaterThan(0.5)
    }
  })

  it('a resting hand’s lens relaxes and dies within hold + release; the loop stops', () => {
    const h = harness()
    h.wake.track(0, 0)
    for (let i = 1; i <= 10; i++) {
      h.advance(16)
      h.wake.track(i * 10, 0)
    }
    const head = h.wake.head()!
    const spent = h.runOut()
    expect(spent).toBeLessThanOrEqual(Math.max(WAKE.headHold + WAKE.headRelease, WAKE.life) + 32)
    expect(dropAlive(head, h.now())).toBe(false)
    expect(h.wake.head()).toBeNull()
    expect(h.wake.running()).toBe(false)
  })

  it('a relaxing lens picks up where it is when the hand moves again — it never pops back to full', () => {
    const h = harness()
    h.wake.track(0, 0)
    for (let i = 1; i <= 10; i++) {
      h.advance(16)
      h.wake.track(i * 10, 0)
    }
    h.advance(WAKE.headHold + WAKE.headRelease / 2)
    const relaxed = dropPresence(h.wake.head()!, h.now())
    expect(relaxed).toBeGreaterThan(0)
    expect(relaxed).toBeLessThan(1)
    h.wake.track(120, 0)
    expect(dropPresence(h.wake.head()!, h.now())).toBeCloseTo(relaxed, 1)
  })

  it('residue is born when the hand passed it: earlier along the path is older', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.advance(16)
    h.wake.track(WAKE.spacing * 3, 0)
    const births = residueOf(h).map((d) => d.born)
    expect(births).toEqual([...births].sort((a, b) => a - b))
    expect(births[0]).toBeLessThan(births.at(-1)!)
    expect(births.at(-1)).toBeLessThanOrEqual(16)
  })

  it('residue only relaxes: presence falls monotonically to zero over its life', () => {
    const d = drop()
    let previous = Infinity
    for (let t = 0; t <= WAKE.life; t += 20) {
      const p = dropPresence(d, t)
      expect(p).toBeLessThanOrEqual(previous)
      previous = p
    }
    expect(dropPresence(d, 0)).toBeLessThan(1)
    expect(dropPresence(d, WAKE.life)).toBe(0)
  })
})

describe('wake — decays and stops by itself', () => {
  it('every lens dies; the last paint is empty and the loop stops', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(60, 0)
    expect(h.wake.drops.length).toBeGreaterThan(0)
    h.runOut()
    expect(h.wake.drops).toHaveLength(0)
    expect(h.paints.at(-1)?.count).toBe(0)
    expect(h.wake.running()).toBe(false)
  })

  it('idle after decay: time passing schedules nothing and paints nothing', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(40, 0)
    h.runOut()
    const paints = h.paints.length
    for (let i = 0; i < 120; i++) {
      h.advance(16)
      h.frame()
    }
    expect(h.pending()).toBe(0)
    expect(h.paints.length).toBe(paints)
  })

  it('dispose cancels a running loop', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(50, 0)
    expect(h.pending()).toBe(1)
    h.wake.dispose()
    expect(h.pending()).toBe(0)
    expect(h.wake.drops).toHaveLength(0)
  })
})

describe('wake — local, organic, bounded: not a spotlight', () => {
  it('no lens, at any age or bearing, reaches past the local ceiling', () => {
    // the retired spotlight was 576px; a lens is a fifth of that at most
    expect(WAKE_MAX_RADIUS).toBeLessThanOrEqual(576 / 5)
    const largest = drop({ scale: WAKE.residueScale[1], weight: WAKE.residueWeight[1] })
    for (const d of [drop(), largest, drop({ head: true, scale: 1, weight: 1 })]) {
      for (let t = 0; t <= WAKE.life; t += 40) {
        for (let k = 0; k < 64; k++) {
          expect(dropEdge(d, t, (k / 64) * Math.PI * 2)).toBeLessThanOrEqual(WAKE_MAX_RADIUS + 1e-9)
        }
      }
    }
  })

  it('has an irregular outline — never a perfect circle', () => {
    const d = drop({ head: true, scale: 1, weight: 1 })
    const radii = Array.from({ length: 48 }, (_, k) => dropEdge(d, 100, (k / 48) * Math.PI * 2))
    const spread = (Math.max(...radii) - Math.min(...radii)) / dropRadius(d, 100)
    expect(spread).toBeGreaterThan(0.1)
  })

  it('residue is smaller than the head and widens a little as it relaxes', () => {
    const d = drop({ scale: WAKE.residueScale[1] })
    expect(dropRadius(d, 0)).toBeLessThan(WAKE.headRadius)
    expect(dropRadius(d, WAKE.life)).toBeGreaterThan(dropRadius(d, 0))
  })

  it('drops differ from one another (seeded)', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(WAKE.spacing * 4, 0)
    const [a, b] = h.wake.drops
    expect(a!.phases).not.toEqual(b!.phases)
  })
})

describe('optics — the lens bends, it never lights', () => {
  const out = new Float32Array(4)
  const at = (lenses: Lens[], x: number, y: number) => {
    lensField(lenses, x, y, out)
    return { dx: out[0]!, dy: out[1]!, cover: out[2]!, rim: out[3]! }
  }

  it('nothing moves outside a lens, at its centre, or at its rim', () => {
    const l = lens()
    expect(at([l], 100 + l.r * EDGE_BOUND + 1, 100)).toMatchObject({ dx: 0, dy: 0, cover: 0 })
    const centre = at([l], 100, 100)
    expect(Math.hypot(centre.dx, centre.dy)).toBeLessThan(1e-6)
    // just inside the (circular, for these phases) rim the bend has fallen away
    const rim = at([lens({ c2: 0, s2: 0, c3: 0, s3: 0 })], 100 + 59.9, 100)
    expect(Math.abs(rim.dx)).toBeLessThan(0.5)
  })

  it('samples from nearer the centre (a magnifying lens), strongest toward the rim', () => {
    const l = lens({ c2: 0, s2: 0, c3: 0, s3: 0 })
    const samples = [10, 20, 30, 40, 45, 50].map((d) => at([l], 100 + d, 100).dx)
    // displacement points away from the centre, so the sample comes from inside
    for (const dx of samples) expect(dx).toBeGreaterThan(0)
    expect(samples[4]!).toBeGreaterThan(samples[0]!)
    // bounded: never more than the lens's refraction
    for (const dx of samples) expect(dx).toBeLessThanOrEqual(WAKE.refraction * l.r + 1e-6)
  })

  it('overlapping lenses never bend further than one lens does', () => {
    const one = lens({ c2: 0, s2: 0, c3: 0, s3: 0 })
    const peak = Math.max(
      ...Array.from({ length: 60 }, (_, d) => Math.abs(at([one], 100 + d, 100).dx)),
    )
    const trail = Array.from({ length: 8 }, (_, i) =>
      lens({ x: 100 + i * 6, c2: 0, s2: 0, c3: 0, s3: 0 }),
    )
    for (let x = 30; x < 220; x += 3) {
      for (let y = 40; y < 160; y += 3) {
        const f = at(trail, x, y)
        expect(Math.hypot(f.dx, f.dy)).toBeLessThanOrEqual(peak + 1e-6)
      }
    }
  })

  it('a relaxed lens bends nothing and draws nothing', () => {
    const f = at([lens({ strength: 0 })], 130, 100)
    expect(f.dx).toBe(0)
    expect(f.cover).toBe(0)
  })

  it('colours part only in the rim band, never in the clear interior', () => {
    const l = lens({ c2: 0, s2: 0, c3: 0, s3: 0 })
    expect(at([l], 100 + 20, 100).rim).toBe(0)
    expect(at([l], 100 + 52, 100).rim).toBeGreaterThan(0.5)
  })

  /** A plate whose every pixel is one colour. */
  function flatPlate(width: number, height: number, rgb: [number, number, number]): Plate {
    const data = new Uint8ClampedArray(width * height * 4)
    for (let i = 0; i < data.length; i += 4) data.set([...rgb, 255], i)
    return { data, width, height }
  }

  it('every pixel it draws is a wallpaper pixel: over one colour, it draws exactly that colour', () => {
    const plate = flatPlate(200, 200, [68, 37, 50])
    const box = { x: 0, y: 0, width: 200, height: 200 }
    const target = { data: new Uint8ClampedArray(200 * 200 * 4), width: 200 }
    const lenses = [lens(), lens({ x: 130, y: 90, r: 40, strength: 0.6 })]
    refractRegion(plate, box, lenses, target, createFieldScratch(), 2)
    let drawn = 0
    for (let i = 0; i < target.data.length; i += 4) {
      if (target.data[i + 3] === 0) continue
      drawn++
      expect([target.data[i], target.data[i + 1], target.data[i + 2]]).toEqual([68, 37, 50])
    }
    expect(drawn).toBeGreaterThan(1000)
  })

  it('never brighter or darker than the wallpaper it bends: every channel stays inside the plate’s range', () => {
    // vertical stripes 40..200
    const width = 160
    const plate = flatPlate(width, 160, [0, 0, 0])
    for (let y = 0; y < 160; y++)
      for (let x = 0; x < width; x++) {
        const v = x % 16 < 8 ? 40 : 200
        plate.data.set([v, v, v], (y * width + x) * 4)
      }
    const target = { data: new Uint8ClampedArray(width * 160 * 4), width }
    refractRegion(
      plate,
      { x: 0, y: 0, width, height: 160 },
      [lens({ x: 80, y: 80 })],
      target,
      createFieldScratch(),
      2,
    )
    let bent = 0
    for (let i = 0; i < target.data.length; i += 4) {
      if (target.data[i + 3] === 0) continue
      for (let c = 0; c < 3; c++) {
        expect(target.data[i + c]).toBeGreaterThanOrEqual(40)
        expect(target.data[i + c]).toBeLessThanOrEqual(200)
      }
      const x = (i / 4) % width
      if (target.data[i + 1] !== plate.data[i + 1] && x > 0) bent++
    }
    expect(bent).toBeGreaterThan(500)
  })

  it('outside every lens it draws nothing — the real wallpaper shows through', () => {
    const plate = flatPlate(300, 120, [10, 20, 30])
    const target = { data: new Uint8ClampedArray(300 * 120 * 4).fill(255), width: 300 }
    refractRegion(
      plate,
      { x: 0, y: 0, width: 300, height: 120 },
      [lens({ x: 60, y: 60 })],
      target,
      createFieldScratch(),
      2,
    )
    for (let y = 0; y < 120; y += 5) {
      for (let x = 140; x < 300; x += 5) expect(target.data[(y * 300 + x) * 4 + 3]).toBe(0)
    }
  })
})

describe('painter — cost follows the wake', () => {
  const STAGE = { x: 0, y: 0, width: 1440, height: 900 }
  const area = (boxes: ReadonlyArray<{ width: number; height: number }>) =>
    boxes.reduce((sum, b) => sum + b.width * b.height, 0)
  const covers = (boxes: ReturnType<typeof lensRegions>, x: number, y: number) =>
    boxes.some((b) => x >= b.x && x < b.x + b.width && y >= b.y && y < b.y + b.height)

  it('the painted region covers every lens and stays local', () => {
    const lenses = [lens({ x: 100, y: 100 }), lens({ x: 180, y: 120, r: 40 })]
    const regions = lensRegions(lenses, WAKE_TILE, STAGE)
    for (const l of lenses) {
      const reach = l.r * EDGE_BOUND - 1
      for (const [dx, dy] of [
        [0, 0],
        [-reach, 0],
        [reach, 0],
        [0, -reach],
        [0, reach],
      ] as const) {
        expect(covers(regions, l.x + dx, l.y + dy)).toBe(true)
      }
    }
    expect(area(regions)).toBeLessThan(STAGE.width * STAGE.height * 0.05)
    expect(lensRegions([], WAKE_TILE, STAGE)).toEqual([])
  })

  it('two far-apart ends of a wake cost their own area, never the span between them', () => {
    const one = lensRegions([lens({ x: 80, y: 80 })], WAKE_TILE, STAGE)
    const both = lensRegions([lens({ x: 80, y: 80 }), lens({ x: 1360, y: 820 })], WAKE_TILE, STAGE)
    expect(area(both)).toBeLessThanOrEqual(2 * area(one))
    expect(covers(both, 720, 450)).toBe(false)
  })

  it('regions never overlap and stay inside the canvas', () => {
    const lenses = Array.from({ length: 30 }, (_, i) =>
      lens({ x: 20 + i * 70, y: 30 + i * 43, r: 50 }),
    )
    const limit = { x: 0, y: 0, width: 2160, height: 1350 }
    const regions = lensRegions(lenses, Math.round(WAKE_TILE * 1.5), limit)
    expect(regions.length).toBeGreaterThan(1)
    for (const b of regions) {
      expect(b.x).toBeGreaterThanOrEqual(0)
      expect(b.y).toBeGreaterThanOrEqual(0)
      expect(b.x + b.width).toBeLessThanOrEqual(limit.width)
      expect(b.y + b.height).toBeLessThanOrEqual(limit.height)
    }
    for (let i = 0; i < regions.length; i++) {
      for (let j = i + 1; j < regions.length; j++) {
        const a = regions[i]!
        const b = regions[j]!
        const apart =
          a.x + a.width <= b.x ||
          b.x + b.width <= a.x ||
          a.y + a.height <= b.y ||
          b.y + b.height <= a.y
        expect(apart).toBe(true)
      }
    }
  })

  it('the working resolution is the device’s, capped, and steps down for huge stages', () => {
    expect(workingDpr(1, 1440, 900)).toBe(1)
    expect(workingDpr(3, 390, 844)).toBe(WAKE.dprCap)
    const big = workingDpr(2, 2560, 1440)
    expect(big).toBeLessThan(2)
    expect(2560 * 1440 * big * big).toBeLessThanOrEqual(WAKE.maxPlatePixels + 1)
  })
})

describe('shelters — the wake belongs to the wallpaper, not the glass', () => {
  const slab = { x: 460, y: 132, width: 520, height: 516 }

  it('signed distance: negative inside, positive outside', () => {
    expect(signedDistance(slab, 720, 400)).toBeLessThan(0)
    expect(signedDistance(slab, 400, 400)).toBeCloseTo(60)
    expect(signedDistance(slab, 460, 400)).toBe(-0)
  })

  it('a lens is whole away from a slab, gone well under it, and fades across the edge', () => {
    expect(shelterFactor([slab], 300, 400)).toBe(1)
    expect(shelterFactor([slab], 720, 400)).toBe(0)
    let previous = 1
    for (let x = 400; x <= 520; x += 8) {
      const f = shelterFactor([slab], x, 400)
      expect(f).toBeLessThanOrEqual(previous)
      previous = f
    }
  })
})

describe('the plate registers with the CSS wallpaper', () => {
  it('parses computed transforms and blend modes', () => {
    expect(parseTransform('none')).toEqual([1, 0, 0, 1, 0, 0])
    expect(parseTransform('matrix(-0.99, 0.1, 0.1, 0.99, 0, 0)')).toEqual([
      -0.99, 0.1, 0.1, 0.99, 0, 0,
    ])
    expect(parseTransform('matrix3d(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1)')).toEqual([
      1, 0, 0, 1, 0, 0,
    ])
    expect(blendOperation('screen')).toBe('screen')
    expect(blendOperation('normal')).toBe('source-over')
  })

  it('draws the field, then each layer in its box, under its transform about its origin, with its blend', () => {
    const calls: Array<[string, ...unknown[]]> = []
    const record =
      (name: string) =>
      (...args: unknown[]) =>
        calls.push([name, ...args])
    const ctx = {
      setTransform: record('setTransform'),
      fillRect: record('fillRect'),
      save: record('save'),
      restore: record('restore'),
      translate: record('translate'),
      transform: record('transform'),
      drawImage: record('drawImage'),
      getImageData: (_x: number, _y: number, width: number, height: number) => ({
        data: new Uint8ClampedArray(width * height * 4),
        width,
        height,
      }),
      set fillStyle(value: string) {
        calls.push(['fillStyle', value])
      },
      set globalCompositeOperation(value: string) {
        calls.push(['globalCompositeOperation', value])
      },
      set globalAlpha(value: number) {
        calls.push(['globalAlpha', value])
      },
      imageSmoothingEnabled: false,
      imageSmoothingQuality: 'low',
    }
    const canvas = {
      width: 0,
      height: 0,
      getContext: vi.fn(() => ctx),
    } as unknown as HTMLCanvasElement
    const image = {} as CanvasImageSource
    const plate = buildPlate(canvas, {
      width: 100,
      height: 50,
      dpr: 2,
      field: 'oklch(0.31 0.05 354)',
      layers: [
        {
          image,
          box: { x: 10, y: 5, width: 40, height: 46 },
          matrix: [-1, 0, 0, 1, 0, 0],
          origin: { x: 16, y: 14 },
          blend: 'screen',
          opacity: 0.8,
        },
      ],
    })
    expect(canvas.width).toBe(200)
    expect(canvas.height).toBe(100)
    expect(plate).toMatchObject({ width: 200, height: 100 })
    const names = calls.map((c) => c[0])
    // the field first, then the layer
    expect(names.indexOf('fillRect')).toBeLessThan(names.indexOf('drawImage'))
    expect(calls).toContainEqual(['fillStyle', 'oklch(0.31 0.05 354)'])
    expect(calls).toContainEqual(['globalCompositeOperation', 'screen'])
    expect(calls).toContainEqual(['globalAlpha', 0.8])
    // about the transform origin: in, transform, out, then the image in its own box
    const layer = calls.slice(names.indexOf('save'))
    expect(layer.filter((c) => c[0] === 'translate')).toEqual([
      ['translate', 26, 19],
      ['translate', -16, -14],
    ])
    expect(layer).toContainEqual(['transform', -1, 0, 0, 1, 0, 0])
    expect(layer).toContainEqual(['drawImage', image, 0, 0, 40, 46])
    expect(ctx.imageSmoothingQuality).toBe('high')
  })
})

describe('wake — gated to nothing', () => {
  const env = (fine: boolean, reduced: boolean) => (query: string) =>
    query === WAKE_QUERIES.pointer ? fine : query === WAKE_QUERIES.reducedMotion ? reduced : false

  it('mounts only for a fine hovering pointer with motion allowed', () => {
    expect(wakeQualifies(env(true, false))).toBe(true)
  })

  it('never on touch / coarse pointers', () => {
    expect(wakeQualifies(env(false, false))).toBe(false)
  })

  it('never under reduced motion', () => {
    expect(wakeQualifies(env(true, true))).toBe(false)
  })

  it('asks the same two questions as the spotlight', () => {
    expect(WAKE_QUERIES.pointer).toBe('(hover: hover) and (pointer: fine)')
    expect(WAKE_QUERIES.reducedMotion).toBe('(prefers-reduced-motion: reduce)')
    const matches = vi.fn(() => false)
    wakeQualifies(matches)
    expect(matches).toHaveBeenCalledWith(WAKE_QUERIES.pointer)
  })
})
