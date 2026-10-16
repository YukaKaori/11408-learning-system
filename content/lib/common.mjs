/**
 * Shared helpers for generated packs: the question base, number formatting,
 * markdown tables. Every generated question says where it came from — the
 * `source` is "原创 · 计算专项", and the pack names the generator script.
 */

export const GENERATED_SOURCE = '原创 · 计算专项'

/** A number as an exam prints it: integers bare, otherwise up to `digits` decimals, trailing zeros dropped. */
export function fmt(value, digits = 2) {
  if (Number.isInteger(value)) return String(value)
  return String(Number(value.toFixed(digits)))
}

/** A markdown table. */
export function table(header, rows) {
  const line = (cells) => `| ${cells.join(' | ')} |`
  return [line(header), line(header.map(() => '---')), ...rows.map(line)].join('\n')
}

/** Two-digit index for keys: 1 → "01". */
export function nn(index) {
  return String(index).padStart(2, '0')
}

/** Builds `count` questions from `make(random, index)`, skipping (and retrying) ones that throw `Retry`. */
export class Retry extends Error {}

export function family(random, count, make, attemptsPerQuestion = 50) {
  const questions = []
  for (let index = 1; index <= count; index++) {
    let question = null
    for (let attempt = 0; attempt < attemptsPerQuestion && question === null; attempt++) {
      try {
        question = make(random, index)
      } catch (error) {
        if (!(error instanceof Retry)) throw error
      }
    }
    if (question === null) throw new Error(`Could not generate question ${index}`)
    questions.push(question)
  }
  return questions
}

/** A 408 single-choice base. */
export function cs408(key, points, difficulty) {
  return {
    key,
    subject: 'cs408',
    section: 'cs408.choice',
    points,
    difficulty,
    score: 2,
    source: GENERATED_SOURCE,
  }
}
