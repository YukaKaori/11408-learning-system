import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'
import { TextSelection } from '@tiptap/pm/state'
import type { EditorState } from '@tiptap/pm/state'

export interface EditorSelection {
  from: number
  to: number
  /** Plain text of the range; atoms (wiki links) contribute nothing. */
  text: string
  /** Viewport coordinates of the selection's start and end. */
  left: number
  right: number
  top: number
  bottom: number
}

/**
 * Live, *valid* text selection inside the editor (Phase 16 Step 5) — the
 * trigger for the floating glass toolbar.
 *
 * "Valid" is deliberately narrow, because the toolbar is a transient surface
 * and must never appear where it has nothing to act on:
 *
 * - a non-empty {@link TextSelection} (a `NodeSelection` — clicking a wiki-link
 *   atom — is not a text selection and must not summon AI actions),
 * - whose text is not blank,
 * - in an editable editor.
 *
 * Callers add their own conditions (focus, no autocomplete open); this
 * composable only reports what the document says.
 */
export function useEditorSelection(editor: Ref<Editor | null | undefined>) {
  const selection = ref<EditorSelection | null>(null)
  let bound: Editor | null = null

  function recompute() {
    selection.value = bound ? readSelection(bound) : null
  }

  function unbind() {
    bound?.off('selectionUpdate', recompute)
    bound?.off('transaction', recompute)
    bound = null
  }

  watch(
    editor,
    (next) => {
      unbind()
      bound = next ?? null
      // `transaction` too: typing above the selection shifts its coordinates
      // without changing the selection itself.
      bound?.on('selectionUpdate', recompute)
      bound?.on('transaction', recompute)
      recompute()
    },
    { immediate: true },
  )

  onBeforeUnmount(unbind)

  return { selection }
}

function readSelection(editor: Editor): EditorSelection | null {
  if (!editor.isEditable) return null
  const state: EditorState = editor.state
  const { selection } = state
  if (!(selection instanceof TextSelection) || selection.empty) return null

  const { from, to } = selection
  const text = state.doc.textBetween(from, to, '\n')
  if (!text.trim()) return null

  const start = editor.view.coordsAtPos(from)
  const end = editor.view.coordsAtPos(to)
  return {
    from,
    to,
    text,
    left: Math.min(start.left, end.left),
    right: Math.max(start.right, end.right),
    top: Math.min(start.top, end.top),
    bottom: Math.max(start.bottom, end.bottom),
  }
}

/**
 * Keeps a captured range valid while the document changes underneath it — an
 * AI action can be in flight for seconds and the user is free to keep typing.
 * Positions are mapped through the transaction, ProseMirror's own answer to
 * "where did my range go", rather than being re-read from a stale snapshot.
 */
export function mapRange(
  range: { from: number; to: number },
  mapping: { map: (pos: number) => number },
): { from: number; to: number } {
  return { from: mapping.map(range.from), to: mapping.map(range.to) }
}
