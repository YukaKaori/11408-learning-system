import type { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import type { Extensions } from '@tiptap/vue-3'
import { Markdown, type MarkdownStorage } from 'tiptap-markdown'
import { NoteImage } from './ImageNode'
import { MarkdownSoftBreak } from './markdownSoftBreak'
import { NotePlaceholder } from './placeholder'
import { WikiLink, type WikiLinkOptions } from './WikiLinkNode'
import { WikiLinkSuggestion, type WikiLinkSuggestionOptions } from './wikiLinkSuggestion'

export interface NoteEditorSchemaOptions {
  /** Wiki-link resolution + navigation; omitted in tests and read-only uses. */
  wikiLink?: Partial<WikiLinkOptions>
  /** `[[` autocomplete wiring; omitted where there is no popup to drive. */
  suggestion?: WikiLinkSuggestionOptions
  /**
   * Empty-document prompt, read as a getter so it follows the active locale.
   * Omitted where there is no reader (tests, serialization).
   */
  placeholder?: () => string
}

/**
 * The Notes 2.0 editor schema — the single source of truth for the closed,
 * losslessly-serializable node/mark set (Phase 16 Steps 3–4). Both the live
 * editor ({@link NoteEditor}) and the round-trip property tests build from this
 * exact function, so "what the user can write" and "what round-trips" can never
 * drift.
 *
 * v1 schema: headings h1–h3, paragraph, bold / italic / inline-code /
 * strikethrough, link, bullet + ordered list, blockquote, fenced code block,
 * horizontal rule — plus the custom `[[wiki-link]]` inline node (Step 4), the
 * external `![image](…)` inline node (Phase 17 Step 1) and undo/redo history.
 *
 * Step 6 adds the empty-note placeholder — hand-rolled (`./placeholder`), so it
 * still costs no dependency.
 *
 * Still deferred (a separate, deliberate step — not smuggled in here): task
 * lists (checkboxes). Markdown remains the persistence format; TipTap is only
 * the editing view over it.
 */
export function noteEditorExtensions(options: NoteEditorSchemaOptions = {}): Extensions {
  const extensions: Extensions = [
    StarterKit.configure({
      // The outline rail and the v1 schema cap headings at three levels.
      heading: { levels: [1, 2, 3] },
      // Underline has no markdown representation; excluding it keeps the
      // markdown round-trip closed (no lossy `<u>` HTML on save).
      underline: false,
    }),
    WikiLink.configure(options.wikiLink),
    // Closes the Phase 16 §4.1 data loss: without a node matching markdown-it's
    // `<img>`, every `![alt](url)` was dropped on parse and the loss written
    // back by autosave. Unconditional — it is a persistence-path correctness
    // fix, so it must hold everywhere the schema does, tests included.
    NoteImage,
    // Repairs soft breaks the markdown parser would otherwise delete. Always
    // on: it is a correctness fix for the persistence path, not a feature, so
    // it must apply everywhere the schema does — including the round-trip tests.
    MarkdownSoftBreak,
    Markdown.configure({
      // Closed schema: never parse or emit raw HTML — markdown in, markdown out.
      html: false,
      tightLists: true,
      bulletListMarker: '-',
      linkify: false,
      breaks: false,
      transformPastedText: true,
      transformCopiedText: true,
    }),
  ]

  if (options.placeholder) {
    extensions.push(NotePlaceholder.configure({ text: options.placeholder }))
  }

  if (options.suggestion) {
    extensions.push(WikiLinkSuggestion(options.suggestion))
  }

  return extensions
}

/**
 * Serialize the editor's current document to markdown. Wraps the untyped
 * {@code editor.storage.markdown} the Markdown extension attaches, so callers
 * get a typed markdown accessor rather than reaching into storage.
 */
export function getEditorMarkdown(editor: Editor): string {
  return (editor.storage as unknown as { markdown: MarkdownStorage }).markdown.getMarkdown()
}
