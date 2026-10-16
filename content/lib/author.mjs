/**
 * Authoring helpers for hand-written packs (`content/packs/*.mjs`).
 *
 * `md` is a template tag for question text: the raw template is kept as
 * written — LaTeX backslashes need no doubling, line breaks are real line
 * breaks — and the common indentation of the template is removed before the
 * interpolated values are inserted, so a multi-line value (a shared block of
 * instructions) lands at the column it is placed at. Inside it, write \` for a
 * backtick and \$ for a dollar sign that must not open an interpolation.
 */

const HOLE = (i) => `\u0000${i}\u0000`

export function md(strings, ...values) {
  let text = strings.raw.map((part, i) => (i < values.length ? part + HOLE(i) : part)).join('')
  text = text.replace(/\\([`$])/g, '$1')
  const lines = text.replace(/^\n/, '').replace(/\n[ \t]*$/, '').split('\n')
  const indents = lines.filter((line) => line.trim().length > 0).map((line) => line.match(/^ */)[0].length)
  const indent = indents.length > 0 ? Math.min(...indents) : 0
  text = lines.map((line) => line.slice(indent)).join('\n')
  return text.replace(/\u0000(\d+)\u0000/g, (_, i) => String(values[Number(i)]))
}

export const CORE_SOURCE = '原创 · 核心题库'

/**
 * Sample questions: original items written so that every printed section of
 * a paper can be sat, graded and recorded in the app — a check of the formats,
 * not a bank. The label travels with the question (it is shown wherever the
 * question is), and a sample is never a 真题 nor stands in for a licensed one.
 */
export const SAMPLE_SOURCE = '原创样题 · 非真题'

/** A question with the shared defaults of its paper section filled in. */
function question(defaults, spec) {
  const out = { ...defaults, ...spec }
  if (!out.points || out.points.length === 0) throw new Error(`${spec.key}: no 考点`)
  return out
}

export function cs408Choice(key, points, difficulty, stem, options, answer, analysis) {
  return question(
    { subject: 'cs408', section: 'cs408.choice', type: 'single_choice', score: 2, source: CORE_SOURCE },
    { key, points, difficulty, stem, options, answer, analysis },
  )
}

export function cs408Open(key, points, difficulty, score, stem, answer, analysis) {
  return question(
    { subject: 'cs408', section: 'cs408.comprehensive', type: 'open', source: CORE_SOURCE },
    { key, points, difficulty, score, stem, answer, analysis },
  )
}
