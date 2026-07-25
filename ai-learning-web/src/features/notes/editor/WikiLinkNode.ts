import { InputRule, mergeAttributes, Node } from '@tiptap/core'
import { NodeSelection, Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import type { EditorView } from '@tiptap/pm/view'
import type { MarkdownNodeSpec } from 'tiptap-markdown'
import { WIKI_LINK_INPUT_PATTERN, wikiLinkPattern } from './wikiLink'

export const WIKI_LINK_NAME = 'wikiLink'

export interface WikiLinkOptions {
  /** Whether a title currently resolves to one of the user's notes. */
  isResolved: (title: string) => boolean
  /** Follow a link — resolved or dangling; the view decides what that means. */
  onNavigate: (title: string) => void
}

export interface WikiLinkStorage {
  markdown: MarkdownNodeSpec<WikiLinkOptions>
}

const decorationKey = new PluginKey('wikiLinkDecoration')

/**
 * The `[[Wiki Link]]` inline node (Phase 16 Step 4).
 *
 * An **inline atom**: one cursor unit, so a link can never be half-deleted into
 * a broken `[[Foo]` state, and one click target for navigation. Its only
 * attribute is `title`, holding the **raw** inner text — normalization is a
 * lookup concern (see {@link normalizeWikiTitle}), never a storage one, so
 * `[[ Foo ]]` survives a save byte-for-byte.
 *
 * Markdown remains the persistence format: this node serializes back to the
 * exact `[[…]]` characters it was parsed from. It stores no note id — the
 * server's `note_links` index is derived from the same characters, and a
 * markdown file that embedded snowflake ids would stop being portable.
 */
export const WikiLink = Node.create<WikiLinkOptions, WikiLinkStorage>({
  name: WIKI_LINK_NAME,

  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  draggable: false,

  addOptions() {
    return {
      isResolved: () => false,
      onNavigate: () => {},
    }
  },

  addAttributes() {
    return {
      title: {
        default: '',
        parseHTML: (element) => element.getAttribute('data-wiki-link') ?? element.textContent ?? '',
        renderHTML: (attributes) => ({ 'data-wiki-link': attributes.title as string }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-wiki-link]' }]
  },

  renderHTML({ node, HTMLAttributes }) {
    const title = String(node.attrs.title ?? '')
    return ['span', mergeAttributes(HTMLAttributes, { class: 'wiki-link' }), title.trim() || title]
  },

  /** Plain-text projection (copy, AI payloads) stays markdown-true. */
  renderText({ node }) {
    return `[[${String(node.attrs.title ?? '')}]]`
  },

  addStorage() {
    return {
      markdown: {
        serialize(state, node) {
          state.write(`[[${String(node.attrs.title ?? '')}]]`)
        },
        parse: {
          // markdown-it emits `[[Title]]` as plain text; promote those runs to
          // link spans before TipTap parses the HTML. Skipped inside code, so
          // `` `[[x]]` `` and fenced blocks stay literal (and round-trip as
          // literal, since prosemirror-markdown never escapes code content).
          updateDOM(element) {
            promoteWikiLinkText(element)
          },
        },
      },
    }
  },

  addInputRules() {
    return [
      new InputRule({
        find: WIKI_LINK_INPUT_PATTERN,
        handler: ({ state, range, match }) => {
          const title = match[1] ?? ''
          if (!title.trim()) return
          state.tr.replaceWith(range.from, range.to, this.type.create({ title }))
        },
      }),
    ]
  },

  addKeyboardShortcuts() {
    return {
      // Keyboard path for following a link: select the node (arrow keys land on
      // an atom as a NodeSelection), press Enter.
      Enter: () => {
        const { selection } = this.editor.state
        if (!(selection instanceof NodeSelection) || selection.node.type.name !== WIKI_LINK_NAME) {
          return false
        }
        this.options.onNavigate(String(selection.node.attrs.title ?? ''))
        return true
      },
    }
  },

  addProseMirrorPlugins() {
    const options = this.options
    return [
      new Plugin({
        key: decorationKey,
        props: {
          /**
           * Resolved vs dangling is a decoration, not a node view: no per-link
           * Vue component, no re-render storm — one doc-sized walk per state
           * change. The view forces a recompute (an empty transaction) when the
           * *note list* changes without the document changing.
           */
          decorations(state) {
            const decorations: Decoration[] = []
            state.doc.descendants((node, pos) => {
              if (node.type.name !== WIKI_LINK_NAME) return
              const resolved = options.isResolved(String(node.attrs.title ?? ''))
              decorations.push(
                Decoration.inline(pos, pos + node.nodeSize, {
                  class: resolved ? 'is-resolved' : 'is-dangling',
                }),
              )
            })
            return DecorationSet.create(state.doc, decorations)
          },
          handleClickOn(_view, _pos, node, _nodePos, _event, direct) {
            if (!direct || node.type.name !== WIKI_LINK_NAME) return false
            options.onNavigate(String(node.attrs.title ?? ''))
            return true
          },
        },
      }),
    ]
  },
})

/** Replaces `[[Title]]` text runs with link spans, in place, outside code. */
function promoteWikiLinkText(element: HTMLElement): void {
  const doc = element.ownerDocument
  const walker = doc.createTreeWalker(element, 4 /* NodeFilter.SHOW_TEXT */)
  const candidates: Text[] = []

  while (walker.nextNode()) {
    const text = walker.currentNode as Text
    if (text.parentElement?.closest('code, pre')) continue
    if (wikiLinkPattern().test(text.data)) candidates.push(text)
  }

  for (const text of candidates) {
    const pattern = wikiLinkPattern()
    const fragment = doc.createDocumentFragment()
    let cursor = 0
    let match: RegExpExecArray | null

    while ((match = pattern.exec(text.data)) !== null) {
      if (match.index > cursor) {
        fragment.append(doc.createTextNode(text.data.slice(cursor, match.index)))
      }
      const title = match[1] ?? ''
      const span = doc.createElement('span')
      span.setAttribute('data-wiki-link', title)
      span.textContent = title.trim() || title
      fragment.append(span)
      cursor = match.index + match[0].length
    }

    if (cursor < text.data.length) {
      fragment.append(doc.createTextNode(text.data.slice(cursor)))
    }
    text.replaceWith(fragment)
  }
}

/** Replaces `from..to` with a wiki-link node — the autocomplete's apply step. */
export function insertWikiLinkAt(view: EditorView, from: number, to: number, title: string): void {
  const type = view.state.schema.nodes[WIKI_LINK_NAME]
  if (!type) return
  view.dispatch(view.state.tr.replaceWith(from, to, type.create({ title })).scrollIntoView())
  view.focus()
}
