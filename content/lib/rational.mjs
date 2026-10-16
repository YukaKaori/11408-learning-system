/**
 * Exact rational arithmetic (BigInt) — mathematics answers are computed, never
 * rounded, so a generated 数学 question's key is right by construction.
 */

function gcd(a, b) {
  a = a < 0n ? -a : a
  b = b < 0n ? -b : b
  while (b) [a, b] = [b, a % b]
  return a
}

export class Q {
  constructor(num, den = 1n) {
    num = BigInt(num)
    den = BigInt(den)
    if (den === 0n) throw new Error('division by zero')
    if (den < 0n) {
      num = -num
      den = -den
    }
    const g = gcd(num, den) || 1n
    this.n = num / g
    this.d = den / g
  }

  static of(value) {
    if (value instanceof Q) return value
    if (typeof value === 'number') {
      if (!Number.isInteger(value)) throw new Error(`non-integer ${value}`)
      return new Q(BigInt(value))
    }
    return new Q(value)
  }

  add(o) {
    o = Q.of(o)
    return new Q(this.n * o.d + o.n * this.d, this.d * o.d)
  }

  sub(o) {
    o = Q.of(o)
    return new Q(this.n * o.d - o.n * this.d, this.d * o.d)
  }

  mul(o) {
    o = Q.of(o)
    return new Q(this.n * o.n, this.d * o.d)
  }

  div(o) {
    o = Q.of(o)
    return new Q(this.n * o.d, this.d * o.n)
  }

  neg() {
    return new Q(-this.n, this.d)
  }

  pow(k) {
    let result = new Q(1n)
    for (let i = 0; i < k; i++) result = result.mul(this)
    return result
  }

  isZero() {
    return this.n === 0n
  }

  isInt() {
    return this.d === 1n
  }

  eq(o) {
    o = Q.of(o)
    return this.n === o.n && this.d === o.d
  }

  sign() {
    return this.n === 0n ? 0 : this.n > 0n ? 1 : -1
  }

  toNumber() {
    return Number(this.n) / Number(this.d)
  }

  /** `3`, `-1/2` — the plain form, and the one a fill-blank grader compares. */
  toString() {
    return this.d === 1n ? `${this.n}` : `${this.n}/${this.d}`
  }

  /** `3`, `-\frac{1}{2}` — for display in LaTeX. */
  tex() {
    if (this.d === 1n) return `${this.n}`
    const sign = this.n < 0n ? '-' : ''
    const n = this.n < 0n ? -this.n : this.n
    return `${sign}\\frac{${n}}{${this.d}}`
  }
}

export const q = (n, d = 1) => new Q(BigInt(n), BigInt(d))

/** Determinant by fraction-exact elimination. */
export function det(matrix) {
  const n = matrix.length
  const m = matrix.map((row) => row.map((value) => Q.of(value)))
  let result = q(1)
  for (let col = 0; col < n; col++) {
    let pivot = col
    while (pivot < n && m[pivot][col].isZero()) pivot++
    if (pivot === n) return q(0)
    if (pivot !== col) {
      ;[m[pivot], m[col]] = [m[col], m[pivot]]
      result = result.neg()
    }
    result = result.mul(m[col][col])
    for (let row = col + 1; row < n; row++) {
      const factor = m[row][col].div(m[col][col])
      for (let k = col; k < n; k++) m[row][k] = m[row][k].sub(factor.mul(m[col][k]))
    }
  }
  return result
}

/** Rank by fraction-exact elimination. */
export function rank(matrix) {
  const m = matrix.map((row) => row.map((value) => Q.of(value)))
  const rows = m.length
  const cols = m[0].length
  let r = 0
  for (let col = 0; col < cols && r < rows; col++) {
    let pivot = r
    while (pivot < rows && m[pivot][col].isZero()) pivot++
    if (pivot === rows) continue
    ;[m[pivot], m[r]] = [m[r], m[pivot]]
    for (let row = 0; row < rows; row++) {
      if (row === r || m[row][col].isZero()) continue
      const factor = m[row][col].div(m[r][col])
      for (let k = col; k < cols; k++) m[row][k] = m[row][k].sub(factor.mul(m[r][k]))
    }
    r++
  }
  return r
}

/** Inverse (fraction-exact Gauss–Jordan); null when singular. */
export function inverse(matrix) {
  const n = matrix.length
  const m = matrix.map((row, i) => [
    ...row.map((value) => Q.of(value)),
    ...Array.from({ length: n }, (_, j) => q(i === j ? 1 : 0)),
  ])
  for (let col = 0; col < n; col++) {
    let pivot = col
    while (pivot < n && m[pivot][col].isZero()) pivot++
    if (pivot === n) return null
    ;[m[pivot], m[col]] = [m[col], m[pivot]]
    const lead = m[col][col]
    for (let k = 0; k < 2 * n; k++) m[col][k] = m[col][k].div(lead)
    for (let row = 0; row < n; row++) {
      if (row === col || m[row][col].isZero()) continue
      const factor = m[row][col]
      for (let k = 0; k < 2 * n; k++) m[row][k] = m[row][k].sub(factor.mul(m[col][k]))
    }
  }
  return m.map((row) => row.slice(n))
}

export function matMul(a, b) {
  return a.map((row) => b[0].map((_, j) => row.reduce((sum, value, k) => sum.add(Q.of(value).mul(Q.of(b[k][j]))), q(0))))
}

/** A matrix as LaTeX `\begin{pmatrix}…\end{pmatrix}`. */
export function texMatrix(matrix) {
  return `\\begin{pmatrix}${matrix.map((row) => row.map((value) => Q.of(value).tex()).join(' & ')).join(' \\\\ ')}\\end{pmatrix}`
}

export function binom(n, k) {
  if (k < 0 || k > n) return 0n
  let result = 1n
  for (let i = 0; i < k; i++) result = (result * BigInt(n - i)) / BigInt(i + 1)
  return result
}
