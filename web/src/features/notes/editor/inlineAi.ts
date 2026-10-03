import type { Editor } from '@tiptap/core'
import type { IconName } from '@/components'
import type { NoteAiAction } from '@/api/modules/ai'

/**
 * The inline-AI action model (Phase 16 Step 5) — pure data + pure text helpers,
 * so *what an action does to the document* is unit-testable without an editor.
 *
 * The governing rule from the Phase 16 plan: **AI proposes; the user disposes.**
 * Nothing here writes; it only describes how an accepted proposal would be
 * placed, and the toolbar applies that in a single transaction.
 */

/** Where an accepted result goes. */
export type ApplyMode =
  | 'replace' // swap the selected text for the result
  | 'insertBelow' // leave the selection intact, add the result after its block
  | 'none' // not a document edit at all (flashcards create a deck instead)

export interface InlineAiAction {
  /** Stable key; also the i18n suffix under `notes.ai.*`. */
  key: InlineAiActionKey
  icon: IconName
  /** Server action for the text actions; absent for `flashcards`. */
  request?: NoteAiAction
  apply: ApplyMode
}

export type InlineAiActionKey = 'rewrite' | 'explain' | 'summarize' | 'flashcards'

/**
 * The Step 5 cut-list, in toolbar order.
 *
 * `rewrite` replaces because the user asked for *this text, better*. `explain`
 * and `summarize` insert below because they produce commentary *about* the
 * selection — silently swallowing the original would be destructive, and the
 * plan forbids AI edits the user did not visibly choose.
 *
 * The other `NOTE_*` server actions (continue / simplify / expand / translate)
 * remain implemented server-side and are deliberately not surfaced this step.
 */
export const INLINE_AI_ACTIONS: readonly InlineAiAction[] = [
  { key: 'rewrite', icon: 'pencil', request: 'rewrite', apply: 'replace' },
  { key: 'explain', icon: 'info', request: 'explain', apply: 'insertBelow' },
  { key: 'summarize', icon: 'file-text', request: 'summarize', apply: 'insertBelow' },
  { key: 'flashcards', icon: 'layers', apply: 'none' },
]

export function inlineAiAction(key: InlineAiActionKey): InlineAiAction {
  const action = INLINE_AI_ACTIONS.find((candidate) => candidate.key === key)
  if (!action) throw new Error(`Unknown inline AI action: ${key}`)
  return action
}

/**
 * Normalizes a streamed result before it may enter the document.
 *
 * Providers like to wrap prose in a fence or pad it with blank lines; neither
 * belongs in the middle of a note. Trimming here (rather than at apply time)
 * keeps the preview the user accepts byte-identical to what gets inserted.
 */
export function normalizeAiResult(raw: string): string {
  const trimmed = raw.trim()
  if (!trimmed.startsWith('```')) return trimmed
  const firstNewline = trimmed.indexOf('\n')
  const lastFence = trimmed.lastIndexOf('```')
  if (firstNewline === -1 || lastFence <= firstNewline) return trimmed
  return trimmed.slice(firstNewline + 1, lastFence).trim()
}

/** A proposal is applicable only if it produced something to apply. */
export function isApplicable(action: InlineAiAction, result: string): boolean {
  return action.apply !== 'none' && normalizeAiResult(result).length > 0
}

/**
 * The one and only place an AI proposal is written into a note.
 *
 * Two invariants this function exists to guarantee:
 *
 * 1. **One transaction.** A single chain means a single undo step, so Ctrl+Z
 *    restores the exact pre-AI document.
 * 2. **Commands, never `setContent`.** `setContent` would replace the document
 *    wholesale and blow away the history stack — undo would have nothing to
 *    return to.
 *
 * Markdown stays the format: `insertContentAt` is the markdown-aware command
 * `tiptap-markdown` installs, so the result is parsed, not injected as HTML.
 *
 * @returns whether anything was written
 */
export function applyInlineAiResult(
  editor: Editor,
  action: InlineAiAction,
  range: { from: number; to: number },
  rawResult: string,
): boolean {
  if (!isApplicable(action, rawResult)) return false
  const result = normalizeAiResult(rawResult)

  if (action.apply === 'replace') {
    editor.chain().focus().insertContentAt(range, result).run()
    return true
  }

  const $to = editor.state.doc.resolve(range.to)
  const after = $to.after(Math.max($to.depth, 1))
  // The leading blank line stops tiptap-markdown unwrapping the result into
  // inline content — an insert-below must land as its own block.
  editor.chain().focus().insertContentAt(after, `\n\n${result}`).run()
  return true
}
