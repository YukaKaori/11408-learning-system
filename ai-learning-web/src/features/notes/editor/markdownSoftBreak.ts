import { Extension } from '@tiptap/core'

/**
 * Soft-break repair for the markdown parse path (Phase 16 Step 7).
 *
 * ## The defect
 *
 * `tiptap-markdown`'s `MarkdownParser.normalizeDOM` removes a leading `\n` from
 * **every** text node that follows **any** element:
 *
 * ```js
 * node.querySelectorAll('*').forEach(el => {
 *   if (el.nextSibling?.nodeType === Node.TEXT_NODE && !el.closest('pre')) {
 *     el.nextSibling.textContent = el.nextSibling.textContent.replace(/^\n/, '')
 *   }
 * })
 * ```
 *
 * The intent is to drop the newlines markdown-it emits *between block elements*
 * (`</p>\n<p>`). But inside a paragraph that same `\n` is a **soft break**,
 * which CommonMark renders as a space. So a wrapped line whose first half ends
 * in an inline construct lost its word boundary on load, and the damage was
 * then written back by autosave:
 *
 * ```
 * "a [label](url)\nand more"  →  "a [label](url)and more"
 * ```
 *
 * Links, bold, italic, strikethrough, inline code and `[[wiki links]]` were all
 * affected; a line ending in plain text was not, because there is no preceding
 * element for the rule to fire on.
 *
 * ## The repair
 *
 * `parse.updateDOM` runs on the rendered HTML *before* `normalizeDOM`, so this
 * extension converts genuine soft breaks into the space they mean while the
 * evidence still exists. `normalizeDOM` then finds no leading `\n` to strip, and
 * the newlines between blocks — which it is right about — are left untouched.
 *
 * Scope is deliberately narrow: only a leading `\n` in a text node whose
 * previous sibling is one of the **inline** elements the v1 schema can produce.
 * The schema is closed (see `extensions.ts`), so an explicit tag list is exact
 * rather than a guess, and nothing between block elements is touched.
 *
 * ## Round-trip consequence
 *
 * The break reflows to a space, so the first save of an affected note rewrites
 * `"…url)\nand more"` as `"…url) and more"`. That is the *same markdown* — a
 * soft break and a space are identical in CommonMark — and it is a fixed point:
 * every subsequent save is byte-stable, which is what the Phase 16 round-trip
 * gate requires. The previous behaviour was not a reflow but a deletion.
 */

/** Inline elements the v1 schema's markdown can render. */
const INLINE_TAGS = new Set(['A', 'STRONG', 'B', 'EM', 'I', 'S', 'DEL', 'CODE', 'SPAN', 'IMG'])

/**
 * Runs after every other `parse.updateDOM` hook.
 *
 * TipTap orders extensions by descending priority, and `MarkdownParser` walks
 * them in that order — so a priority below the default 100 guarantees this runs
 * after `WikiLink`, whose hook is what turns a trailing `[[Target]]` into the
 * inline `<span>` that triggers the defect in the first place.
 */
const RUN_LAST = 50

export function repairSoftBreaks(element: HTMLElement): void {
  const walker = element.ownerDocument.createTreeWalker(element, 4 /* SHOW_TEXT */)

  while (walker.nextNode()) {
    const text = walker.currentNode as Text
    if (!text.data.startsWith('\n')) continue
    // Code keeps its newlines verbatim — they are content, not formatting.
    if (text.parentElement?.closest('pre, code')) continue

    const previous = text.previousSibling
    if (previous?.nodeType !== 1 /* ELEMENT_NODE */) continue
    if (!INLINE_TAGS.has((previous as Element).tagName)) continue

    text.data = ` ${text.data.slice(1)}`
  }
}

/**
 * tiptap-markdown exports `MarkdownNodeSpec`/`MarkdownMarkSpec`, both of which
 * require a `serialize`. A plain extension contributes only a parse hook — the
 * parser reads `extension.storage.markdown.parse.updateDOM` and nothing else —
 * so the parse-only slice is declared here rather than faking a serializer.
 */
export interface MarkdownSoftBreakStorage {
  markdown: {
    parse: { updateDOM(element: HTMLElement): void }
  }
}

export const MarkdownSoftBreak = Extension.create<
  Record<string, never>,
  MarkdownSoftBreakStorage
>({
  name: 'markdownSoftBreak',
  priority: RUN_LAST,

  addStorage() {
    return {
      markdown: {
        parse: {
          updateDOM(element: HTMLElement) {
            repairSoftBreaks(element)
          },
        },
      },
    }
  },
})
