/**
 * Shared helpers for the 数学一 generators: the question base, accepted
 * fill-blank forms for exact values, and LaTeX snippets.
 *
 * A fill-blank answer that matches no accepted form is not marked wrong — the
 * candidate compares it with the reference — so `accept` lists the forms a
 * candidate commonly writes (1/6, \frac{1}{6}, \dfrac16, 0.25 …), not every
 * algebraically equal expression.
 */
import { GENERATED_SOURCE } from './common.mjs'
import { Q } from './rational.mjs'

/** Strings with LaTeX: backslashes stay as written, `${}` still interpolates. */
export const R = String.raw

/** A 数学一 question base (5 points: every choice and fill-blank question). */
export function math1(key, section, points, difficulty) {
  return { key, subject: 'math1', section, points, difficulty, score: 5, source: GENERATED_SOURCE }
}

/** The exact decimal of a fraction whose denominator has no prime factors but 2 and 5, else null. */
export function decimal(value) {
  const v = Q.of(value)
  let d = v.d
  let twos = 0
  let fives = 0
  while (d % 2n === 0n) {
    d /= 2n
    twos++
  }
  while (d % 5n === 0n) {
    d /= 5n
    fives++
  }
  if (d !== 1n) return null
  const digits = Math.max(twos, fives)
  if (digits === 0) return v.n.toString()
  const scaled = (v.n * 10n ** BigInt(digits)) / v.d
  const negative = scaled < 0n
  const text = (negative ? -scaled : scaled).toString().padStart(digits + 1, '0')
  const out = `${text.slice(0, -digits)}.${text.slice(-digits)}`.replace(/0+$/, '').replace(/\.$/, '')
  return negative ? `-${out}` : out
}

/** `\dfrac{1}{6}`, `-\dfrac{3}{2}`, `5` — how a reference answer prints a value. */
export function texQ(value) {
  const v = Q.of(value)
  if (v.isInt()) return v.toString()
  const sign = v.n < 0n ? '-' : ''
  const n = v.n < 0n ? -v.n : v.n
  return `${sign}\\dfrac{${n}}{${v.d}}`
}

/** Accepted forms of an exact rational. */
export function rationalForms(value) {
  const v = Q.of(value)
  const forms = [v.toString()]
  if (!v.isInt()) {
    const sign = v.n < 0n ? '-' : ''
    const n = v.n < 0n ? -v.n : v.n
    forms.push(`${sign}\\frac{${n}}{${v.d}}`, `${sign}\\dfrac{${n}}{${v.d}}`, `\\frac{${v.n}}{${v.d}}`)
    if (n < 10n && v.d < 10n) forms.push(`${sign}\\frac${n}${v.d}`, `${sign}\\dfrac${n}${v.d}`)
  }
  const dec = decimal(v)
  if (dec !== null) forms.push(dec)
  return [...new Set(forms)]
}

/** A value times π: `\dfrac{3\pi}{4}`, and its accepted forms. */
export function piValue(coefficient) {
  const c = Q.of(coefficient)
  const sign = c.n < 0n ? '-' : ''
  const n = c.n < 0n ? -c.n : c.n
  const top = n === 1n ? '' : `${n}`
  if (c.isInt()) {
    return {
      tex: `${sign}${top}\\pi`,
      forms: [`${sign}${top}\\pi`, `${sign}${top}π`, `${sign}${top}pi`],
    }
  }
  return {
    tex: `${sign}\\dfrac{${top}\\pi}{${c.d}}`,
    forms: [
      `${sign}\\frac{${top}\\pi}{${c.d}}`, `${sign}\\dfrac{${top}\\pi}{${c.d}}`,
      `${sign}\\frac{${n}}{${c.d}}\\pi`, `${sign}\\dfrac{${n}}{${c.d}}\\pi`,
      `${sign}${top}\\pi/${c.d}`, `${sign}${top}π/${c.d}`, `${sign}${n}/${c.d}π`, `${sign}\\frac{${top}π}{${c.d}}`,
    ],
  }
}

/** `e^{6}`, `e^{-\frac{1}{2}}` and the forms a candidate writes for them. */
export function expValue(exponent) {
  const k = Q.of(exponent)
  if (k.isZero()) return { tex: '1', forms: ['1'] }
  if (k.isInt()) {
    const forms = [`e^{${k}}`, `e^${k}`]
    if (k.eq(1)) forms.push('e')
    if (k.eq(-1)) forms.push('1/e', '\\frac{1}{e}', '\\dfrac{1}{e}')
    return { tex: k.eq(1) ? 'e' : `e^{${k}}`, forms }
  }
  const sign = k.n < 0n ? '-' : ''
  const n = k.n < 0n ? -k.n : k.n
  const forms = [`e^{${k}}`, `e^{${sign}\\frac{${n}}{${k.d}}}`, `e^{${sign}\\dfrac{${n}}{${k.d}}}`]
  const dec = decimal(k)
  if (dec !== null) forms.push(`e^{${dec}}`)
  if (k.eq(Q.of(-1).div(2))) forms.push('\\frac{1}{\\sqrt{e}}', '1/\\sqrt{e}', '\\dfrac{1}{\\sqrt{e}}')
  if (k.eq(Q.of(1).div(2))) forms.push('\\sqrt{e}')
  return { tex: `e^{${sign}\\frac{${n}}{${k.d}}}`, forms }
}

/** A fill-blank question from a base, its stem and an exact value (rational, or prepared `{tex, forms}`). */
export function fillOf(base, stem, value, analysis) {
  const prepared = value && typeof value === 'object' && 'tex' in value && 'forms' in value
  return {
    ...base,
    type: 'fill_blank',
    stem,
    answer: `$${prepared ? value.tex : texQ(value)}$`,
    accept: prepared ? [...new Set(value.forms)] : rationalForms(value),
    analysis,
  }
}

/** `\begin{vmatrix}…\end{vmatrix}` with an optional symbol in place of some entries. */
export function texGrid(matrix, kind = 'pmatrix', symbolAt = () => null) {
  const rows = matrix.map((row, i) =>
    row.map((value, j) => symbolAt(i, j) ?? Q.of(value).tex()).join(' & '))
  return `\\begin{${kind}}${rows.join(' \\\\ ')}\\end{${kind}}`
}

/** A column vector `(1,-2,3)^{T}`. */
export function texVector(values) {
  return `(${values.map((v) => Q.of(v).tex()).join(',')})^{T}`
}

/** A coefficient as printed before a symbol: `` for 1, `\dfrac{1}{2}`, `3`. */
function coefficient(abs, withSymbol) {
  if (withSymbol && abs.eq(1)) return ''
  return abs.isInt() ? abs.toString() : `\\dfrac{${abs.n}}{${abs.d}}`
}

/** `+3x`, `-x`, `` (zero) — a signed term; the first term of an expression carries no `+`. */
export function signed(value, symbol = '', first = false) {
  const v = Q.of(value)
  if (v.isZero()) return ''
  const abs = v.sign() < 0 ? v.neg() : v
  const sign = v.sign() < 0 ? '-' : first ? '' : '+'
  return `${sign}${coefficient(abs, symbol !== '')}${symbol}`
}

/** A polynomial `x^{3}-3x+1` from coefficients, highest degree first. */
export function texPoly(coefficients, variable = 'x') {
  const degree = coefficients.length - 1
  let out = ''
  coefficients.forEach((c, i) => {
    const power = degree - i
    const symbol = power === 0 ? '' : power === 1 ? variable : `${variable}^{${power}}`
    out += signed(c, symbol, out === '')
  })
  return out === '' ? '0' : out
}

export function factorial(n) {
  let result = 1n
  for (let i = 2n; i <= BigInt(n); i++) result *= i
  return result
}
