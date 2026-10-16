/**
 * Building questions from computed answers.
 *
 * A generated choice question is its correct value plus distractors — each a
 * mistake a candidate actually makes (an off-by-one, the wrong algorithm, a
 * forgotten term) — shuffled with the family's seeded randomness. Everything
 * here refuses to produce an ambiguous question: too few distinct options is
 * a `Retry` (the family draws new parameters), never a silently weaker question.
 */

import { Retry } from './common.mjs'

const LETTERS = 'ABCDEFGH'

/**
 * @param correct     the right option text
 * @param distractors wrong option texts (at least three are used)
 * @returns `{ options, answer }` with the right option at a seeded position
 */
export function shuffleOptions(random, correct, distractors, count = 4) {
  const wrong = [...new Set(distractors.map(String))].filter((option) => option !== String(correct))
  if (wrong.length < count - 1) {
    // Too few distinct wrong answers for these parameters: the family draws new ones.
    throw new Retry(`Not enough distinct distractors for "${correct}": ${JSON.stringify(distractors)}`)
  }
  const options = random.shuffle([String(correct), ...random.shuffle(wrong).slice(0, count - 1)])
  return { options, answer: LETTERS[options.indexOf(String(correct))] }
}

/** Distinct numeric distractors near a numeric answer, from the family's own mistake list first. */
export function numericDistractors(random, correct, mistakes, spread = 3) {
  const out = []
  for (const value of mistakes) {
    if (value !== correct && Number.isFinite(value) && !out.includes(value)) out.push(value)
  }
  let guard = 0
  while (out.length < 6 && guard++ < 200) {
    const delta = random.int(1, spread) * (random.chance(0.5) ? 1 : -1)
    const value = correct + delta
    if (value !== correct && value >= 0 && !out.includes(value)) out.push(value)
  }
  return out
}

/** A single-choice question of the given pack shape. */
export function choiceQuestion(base, random, correct, distractors) {
  const { options, answer } = shuffleOptions(random, correct, distractors)
  return { ...base, type: 'single_choice', options, answer }
}

export function letters() {
  return LETTERS
}
