/**
 * Notes — markdown-first knowledge capture. Mirrors the backend `notes`
 * table; outline and excerpt are derived from content, never stored.
 *
 * The derivations live next door, not here: the outline comes from the editor's
 * ProseMirror document (`editor/useNoteOutline.ts`, so each heading carries a
 * position to jump to and `#` inside a code block is not a heading) and the
 * list preview from `excerpt.ts`.
 */

export interface Note {
  id: string
  subjectId?: string
  title: string
  /** Raw markdown source. */
  content: string
  pinned: boolean
  /** Epoch ms. */
  updatedAt: number
}
