/**
 * Notes — markdown-first knowledge capture. Mirrors the backend `notes`
 * table; outline and excerpt are derived from content, never stored.
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

/**
 * First non-heading, non-empty line — the list-row preview.
 *
 * (The outline is no longer derived here: since Phase 16 Step 4 it comes from
 * the editor's ProseMirror document — see `editor/useNoteOutline.ts` — which
 * gives each heading a position to jump to and ignores `#` inside code blocks.)
 */
export function excerptOf(note: { content: string }): string {
  for (const line of note.content.split('\n')) {
    const text = line.trim()
    if (text && !text.startsWith('#')) return text
  }
  return ''
}
