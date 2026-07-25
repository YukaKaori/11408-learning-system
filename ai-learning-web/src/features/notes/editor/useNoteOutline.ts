import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'

export interface OutlineHeading {
  /** Heading depth 1–3 (the v1 schema's cap). */
  level: number
  text: string
  /** Document position of the heading node — what makes an item clickable. */
  pos: number
}

/** Every heading in a document, in reading order. */
export function headingsOf(doc: ProseMirrorNode): OutlineHeading[] {
  const headings: OutlineHeading[] = []
  doc.descendants((node, pos) => {
    if (node.type.name !== 'heading') return
    const text = node.textContent.trim()
    if (text) headings.push({ level: Number(node.attrs.level ?? 1), text, pos })
  })
  return headings
}

/**
 * The Outline rail's data source (Phase 16 Step 4).
 *
 * Derived from the **ProseMirror document**, not a regex over markdown: that
 * gives every entry an exact position (so clicking one can focus the heading)
 * and stops `#` inside a fenced code block from being mistaken for a heading.
 *
 * Listens to `transaction` rather than `update` on purpose — switching notes
 * calls `setContent(…, { emitUpdate: false })`, which fires no `update` but does
 * fire a transaction, so the rail would otherwise show the previous note.
 */
export function useNoteOutline(editor: Ref<Editor | null | undefined>) {
  const headings = ref<OutlineHeading[]>([])
  let bound: Editor | null = null

  function recompute() {
    headings.value = bound ? headingsOf(bound.state.doc) : []
  }

  function onTransaction({ transaction }: { transaction: { docChanged: boolean } }) {
    if (transaction.docChanged) recompute()
  }

  function unbind() {
    bound?.off('transaction', onTransaction)
    bound = null
  }

  watch(
    editor,
    (next) => {
      unbind()
      bound = next ?? null
      bound?.on('transaction', onTransaction)
      recompute()
    },
    { immediate: true },
  )

  onBeforeUnmount(unbind)

  return { headings }
}
