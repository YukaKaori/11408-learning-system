import { mergeAttributes, Node } from '@tiptap/core'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import type MarkdownIt from 'markdown-it'
import type { MarkdownNodeSpec } from 'tiptap-markdown'

export const IMAGE_NAME = 'image'

/**
 * Carries the **raw** alt source from markdown-it's token to the parsed node.
 *
 * markdown-it renders an image's alt through `renderInlineAsText`, which strips
 * the markup it just parsed (`![a *b*](u)` → `alt="a b"`) and — because the
 * result then has to survive a markdown serializer — would need escaping on the
 * way back out (`![screenshot_1](u)` → `![screenshot\_1](u)`). Both are silent
 * rewrites of the user's file. The raw label text is still on the token, so it
 * is copied onto the element and read back as the node's `alt`.
 */
const RAW_ALT_ATTR = 'data-md-alt'

export interface ImageStorage {
  markdown: MarkdownNodeSpec
}

/**
 * The external `![alt](src "title")` inline node (Phase 17 Step 1).
 *
 * ## Why this exists
 *
 * Phase 16 shipped a closed editor schema with no image node, so markdown-it's
 * `<img>` matched nothing during the HTML → ProseMirror parse and was dropped —
 * and autosave then wrote the loss back. `docs/phase16-handoff.md` §4.1 records
 * it as declared v1 scope that did not ship; this node closes it.
 *
 * ## Shape
 *
 * An **inline atom**, exactly like {@link WikiLink}: one cursor unit, so an
 * image can never be half-deleted into a broken `![alt](` state, and one click
 * target. It is inline rather than block because CommonMark images *are*
 * inline — `![a](u)` alone on a line is an image inside a paragraph, and making
 * it a block would change how the surrounding markdown serializes.
 *
 * ## The storage rule it inherits from {@link WikiLink}
 *
 * **The node stores the raw markdown source, not a normalized projection.**
 * `title` on a wiki link keeps its inner whitespace so `[[ Foo ]]` survives a
 * save byte-for-byte; `alt` here keeps the raw label for the same reason (see
 * {@link RAW_ALT_ATTR}). Rendering may normalize; storage never does.
 *
 * ## Fidelity boundary (stated, not hidden)
 *
 * `src` is whatever markdown-it's `normalizeLink` produced — i.e. **exactly the
 * fidelity the `link` mark has had since Phase 16**, since both run through the
 * same parser rule. Images are not given a private, better-than-links URL path;
 * that would be a new serialization contract, and there is only one to keep.
 *
 * No dependency, no HTML fallback (`html: false` stays), no schema change on the
 * server: markdown remains the persistence format and this node is only the
 * editing view over `![…](…)`.
 */
export const NoteImage = Node.create<Record<string, never>, ImageStorage>({
  name: IMAGE_NAME,

  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  draggable: false,

  addAttributes() {
    return {
      src: {
        default: '',
        parseHTML: (element) => element.getAttribute('src') ?? '',
        renderHTML: (attributes) => ({ src: attributes.src as string }),
      },
      alt: {
        default: '',
        // Raw label first; the rendered `alt` is the fallback for an `<img>`
        // that did not come through the markdown parser (an internal copy of a
        // rendered node, which already carries the raw text in `alt`).
        parseHTML: (element) =>
          element.getAttribute(RAW_ALT_ATTR) ?? element.getAttribute('alt') ?? '',
        renderHTML: (attributes) => ({ alt: attributes.alt as string }),
      },
      title: {
        default: '',
        parseHTML: (element) => element.getAttribute('title') ?? '',
        // Absent, not empty: an empty `title=""` would re-parse to `''` all the
        // same, but it would dirty the editor DOM for every untitled image.
        renderHTML: (attributes) => (attributes.title ? { title: attributes.title as string } : {}),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'img[src]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['img', mergeAttributes(HTMLAttributes, { class: 'note-image' })]
  },

  /** Plain-text projection (copy, AI payloads) stays markdown-true. */
  renderText({ node }) {
    return imageMarkdown(node)
  },

  addStorage() {
    return {
      markdown: {
        serialize(state, node) {
          state.write(imageMarkdown(node))
        },
        parse: {
          setup(markdownit) {
            preserveRawAlt(markdownit)
          },
        },
      },
    }
  },
})

/**
 * The single serialization point — used by both the markdown serializer and the
 * plain-text projection, so the two can never disagree about what an image is.
 *
 * `alt` is written verbatim (it *is* raw markdown source, exactly as
 * `WikiLink` writes its raw title); `src` is escaped only when it has to be
 * (see {@link escapeSrc}), and `title` escapes the quote that would close the
 * title early. Nothing else is escaped, because nothing else can change how the
 * result re-parses.
 */
function imageMarkdown(node: ProseMirrorNode): string {
  const alt = String(node.attrs.alt ?? '')
  const src = escapeSrc(String(node.attrs.src ?? ''))
  const title = String(node.attrs.title ?? '')
  const suffix = title ? ` "${title.replace(/"/g, '\\"')}"` : ''
  return `![${alt}](${src}${suffix})`
}

/**
 * Parentheses in a destination, escaped **only when they would break the
 * re-parse**.
 *
 * CommonMark allows balanced parens inside a bare destination, and a URL like
 * `…/screenshot (1).png` is ordinary. Escaping unconditionally (what
 * prosemirror-markdown does) would therefore rewrite `a(1).png` to
 * `a\(1\).png` the first time a note is opened — a silent edit of the user's
 * file, which is the exact defect this node exists to remove. An *unbalanced*
 * paren really would close the destination early, so that case is escaped.
 */
function escapeSrc(src: string): string {
  let depth = 0
  for (const character of src) {
    if (character === '(') depth += 1
    else if (character === ')') depth -= 1
    if (depth < 0) break
  }
  return depth === 0 ? src : src.replace(/[()]/g, '\\$&')
}

/**
 * Copies each image token's raw label onto the rendered `<img>`.
 *
 * Wraps markdown-it's own `image` rule rather than replacing it, so attribute
 * rendering, entity handling and the `alt` computation stay the library's.
 */
function preserveRawAlt(markdownit: MarkdownIt): void {
  const render = markdownit.renderer.rules.image
  if (!render) return

  markdownit.renderer.rules.image = (tokens, index, options, env, self) => {
    tokens[index]?.attrSet(RAW_ALT_ATTR, tokens[index]?.content ?? '')
    return render(tokens, index, options, env, self)
  }
}
