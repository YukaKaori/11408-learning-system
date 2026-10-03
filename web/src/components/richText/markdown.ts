import MarkdownIt from 'markdown-it'
import type StateInline from 'markdown-it/lib/rules_inline/state_inline.mjs'
import katex from 'katex'

/**
 * Markdown + LaTeX → HTML for exam content: question stems, options, reference
 * answers, 解析, AI explanations. The one place in the app that produces HTML
 * from text, and therefore the one place whose safety has to be argued:
 *
 * - **Raw HTML is off** (`html: false`): any tag in the source is escaped and
 *   shown as text, so content — library, candidate-written or AI-generated —
 *   can never inject markup or script.
 * - **Links are validated** by markdown-it's default `validateLink`
 *   (`javascript:`, `vbscript:`, `file:` and non-image `data:` are refused).
 * - **Math is rendered by KaTeX** with `trust: false` (its default), which
 *   disables `\href`, `\url`, `\includegraphics` and every other command that
 *   could emit an attribute an author controls. KaTeX escapes everything else.
 *
 * Math syntax is the de-facto one of every Chinese exam-prep source: `$…$`
 * inline, `$$…$$` display. A single `$` must hug its content (`$x$`, not
 * `$ x $`), so prices like "$5 and $6" stay text.
 */

const md = new MarkdownIt({ html: false, linkify: true, breaks: true, typographer: false })

function renderMath(tex: string, displayMode: boolean): string {
  return katex.renderToString(tex, {
    displayMode,
    throwOnError: false,
    output: 'htmlAndMathml',
    strict: 'ignore',
    trust: false,
  })
}

/** `$…$` and `$$…$$` as one inline token each (display math may span lines within a paragraph). */
function mathRule(state: StateInline, silent: boolean): boolean {
  const start = state.pos
  if (state.src.charCodeAt(start) !== 0x24 /* $ */) return false
  const display = state.src.charCodeAt(start + 1) === 0x24
  const delimiter = display ? '$$' : '$'

  let end = start + delimiter.length
  for (;;) {
    end = state.src.indexOf(delimiter, end)
    if (end === -1) return false
    if (state.src.charCodeAt(end - 1) !== 0x5c /* \ */) break
    end += 1
  }
  const content = state.src.slice(start + delimiter.length, end)
  if (!content.trim()) return false
  if (!display && (/^\s/.test(content) || /\s$/.test(content))) return false

  if (!silent) {
    const token = state.push(display ? 'math_display' : 'math_inline', 'math', 0)
    token.content = content
  }
  state.pos = end + delimiter.length
  return true
}

md.inline.ruler.before('escape', 'math', mathRule)
md.renderer.rules.math_inline = (tokens, idx) => renderMath(tokens[idx]!.content, false)
md.renderer.rules.math_display = (tokens, idx) => renderMath(tokens[idx]!.content, true)

// Links in exam content open in a new tab and never hand over `window.opener`.
const defaultLinkOpen =
  md.renderer.rules.link_open ?? ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx]!.attrSet('target', '_blank')
  tokens[idx]!.attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

/** A block of content: paragraphs, lists, code, tables, display math. */
export function renderMarkdown(source: string | null | undefined): string {
  return source ? md.render(source) : ''
}

/** A single line (an option, a chip): inline markup and math only, no paragraph wrapper. */
export function renderMarkdownInline(source: string | null | undefined): string {
  return source ? md.renderInline(source) : ''
}
