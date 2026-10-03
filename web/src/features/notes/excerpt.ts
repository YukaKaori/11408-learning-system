/**
 * Note excerpts — the plain-text preview shown in the capture rail and on a
 * subject's note cards.
 *
 * Notes are stored as markdown, so the raw source carries syntax the reader
 * never asked to see: `## `, `**bold**`, `[label](url)`, `[[Wiki Link]]`, task
 * checkboxes, fenced code. Before Phase 16 Step 6 the excerpt was the first
 * non-heading line verbatim, so all of that leaked into the list. Now the
 * excerpt reads like what the *editor* renders, flattened to one line:
 * formatting is removed, the words survive.
 *
 * This is a preview, not a parser — it deliberately shares nothing with the
 * editor's ProseMirror pipeline, because building a document per list row to
 * draw two lines of grey text would be absurd.
 */

/** Roughly two clamped lines at the rail's width. */
const MAX_LENGTH = 160

/** `---`, `***`, `___` — a rule carries no text. */
function isHorizontalRule(line: string): boolean {
  return /^\s{0,3}([-*_])\s*(?:\1\s*){2,}$/.test(line)
}

function isFence(line: string): boolean {
  return /^\s{0,3}(?:```|~~~)/.test(line)
}

function isHeading(line: string): boolean {
  return /^\s{0,3}#{1,6}\s+/.test(line)
}

/** Strip the block marker(s) a line opens with. */
function stripBlockMarkers(line: string): string {
  let text = line
  // Blockquotes nest: `> > quoted`.
  while (/^\s{0,3}>\s?/.test(text)) text = text.replace(/^\s{0,3}>\s?/, '')
  text = text.replace(/^\s{0,3}#{1,6}\s+/, '')
  text = text.replace(/^\s*(?:[-*+]|\d+[.)])\s+/, '')
  // Task checkbox, which sits after the (now removed) list marker.
  text = text.replace(/^\[[ xX]\]\s+/, '')
  return text
}

/**
 * Remove inline markdown, outermost constructs first so a link inside emphasis
 * still resolves to its label.
 */
function stripInlineMarkdown(text: string): string {
  return (
    text
      // Images have no readable text in a one-line preview.
      .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
      // `[[Wiki Link]]` → `Wiki Link`, before the link rule so it isn't eaten.
      .replace(/\[\[([^\]]+)\]\]/g, '$1')
      .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
      .replace(/`+([^`]*)`+/g, '$1')
      .replace(/(\*\*|__|~~)([\s\S]*?)\1/g, '$2')
      // Single-marker emphasis: `*em*` anywhere, `_em_` only when word-flanked,
      // so `snake_case_names` survive intact.
      .replace(/\*([^*\n]+)\*/g, '$1')
      .replace(/(^|[\s([{])_([^_\n]+)_(?=$|[\s)\].,;:!?])/g, '$1$2')
      // Markdown escapes: `\[` → `[`.
      .replace(/\\([\\`*_{}[\]()#+\-.!~>])/g, '$1')
  )
}

function clean(line: string): string {
  return stripInlineMarkdown(stripBlockMarkers(line)).replace(/\s+/g, ' ').trim()
}

function truncate(text: string): string {
  if (text.length <= MAX_LENGTH) return text
  const cut = text.slice(0, MAX_LENGTH)
  const lastSpace = cut.lastIndexOf(' ')
  // CJK has no spaces, so only break on one when there is a plausible one.
  return `${(lastSpace > MAX_LENGTH * 0.6 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`
}

/**
 * A formatting-free preview of a note's body.
 *
 * Headings are skipped — the note's title already sits directly above the
 * excerpt, so repeating its first heading says nothing. A note that is *only*
 * headings falls back to the first of them rather than rendering blank.
 */
export function excerptOf(note: { content: string }): string {
  const parts: string[] = []
  let headingFallback = ''
  let inFence = false
  let length = 0

  for (const line of note.content.split('\n')) {
    if (isFence(line)) {
      inFence = !inFence
      continue
    }
    if (inFence || isHorizontalRule(line)) continue

    const heading = isHeading(line)
    const text = clean(line)
    if (!text) continue

    if (heading) {
      if (!headingFallback) headingFallback = text
      continue
    }
    parts.push(text)
    length += text.length + 1
    if (length >= MAX_LENGTH) break
  }

  return truncate(parts.join(' ') || headingFallback)
}
