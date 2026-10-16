/**
 * Seeded randomness for generators. A generator is a pure function of its
 * seed, so regenerating a pack reproduces it byte for byte — the keys stay
 * attached to the same questions, and a diff shows only real changes.
 */

/** mulberry32 — small, fast, good enough for choosing exercise parameters. */
export function rng(seed) {
  let state = seed >>> 0
  const next = () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
  return {
    next,
    /** An integer in [lo, hi]. */
    int(lo, hi) {
      return lo + Math.floor(next() * (hi - lo + 1))
    },
    pick(list) {
      return list[Math.floor(next() * list.length)]
    },
    /** A shuffled copy. */
    shuffle(list) {
      const copy = [...list]
      for (let i = copy.length - 1; i > 0; i--) {
        const j = Math.floor(next() * (i + 1))
        ;[copy[i], copy[j]] = [copy[j], copy[i]]
      }
      return copy
    },
    /** `n` distinct integers from [lo, hi]. */
    distinct(n, lo, hi) {
      const seen = new Set()
      while (seen.size < n) seen.add(lo + Math.floor(next() * (hi - lo + 1)))
      return [...seen]
    },
    chance(p) {
      return next() < p
    },
  }
}

/** A stable seed from a family name, so families do not share a stream. */
export function seedOf(name) {
  let hash = 2166136261
  for (const char of name) {
    hash ^= char.codePointAt(0)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}
