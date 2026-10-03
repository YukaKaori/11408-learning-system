// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/vue-3'
import { TextSelection } from '@tiptap/pm/state'
import { getEditorMarkdown, noteEditorExtensions } from '../extensions'
import { applyInlineAiResult, inlineAiAction } from '../inlineAi'

/**
 * Applying an accepted AI proposal to a real document (Phase 16 Step 5).
 *
 * The three properties that matter, all exercised against a live editor:
 * the edit lands where the action says it lands, markdown still round-trips
 * afterwards, and **one** Ctrl+Z restores the exact pre-AI note.
 */
function editorFor(md: string): Editor {
  return new Editor({ extensions: noteEditorExtensions(), content: md })
}

/** Document range of the first occurrence of `needle`. */
function rangeOf(editor: Editor, needle: string): { from: number; to: number } {
  let found: { from: number; to: number } | null = null
  editor.state.doc.descendants((node, pos) => {
    if (found || !node.isText || !node.text) return
    const index = node.text.indexOf(needle)
    if (index >= 0) found = { from: pos + index, to: pos + index + needle.length }
  })
  if (!found) throw new Error(`"${needle}" not found in document`)
  return found
}

function select(editor: Editor, range: { from: number; to: number }) {
  const selection = TextSelection.create(editor.state.doc, range.from, range.to)
  editor.view.dispatch(editor.state.tr.setSelection(selection))
}

describe('applying an inline AI result', () => {
  it('replaces the selection for rewrite, and round-trips', () => {
    const editor = editorFor('The old sentence stays here.')
    try {
      const range = rangeOf(editor, 'old sentence')
      select(editor, range)
      expect(applyInlineAiResult(editor, inlineAiAction('rewrite'), range, 'new wording')).toBe(true)
      const md = getEditorMarkdown(editor)
      expect(md).toBe('The new wording stays here.')
    } finally {
      editor.destroy()
    }
  })

  it('inserts below the block for explain, leaving the original intact', () => {
    const editor = editorFor('# Title\n\nA claim worth explaining.\n\nAnother paragraph.')
    try {
      const range = rangeOf(editor, 'A claim')
      select(editor, range)
      applyInlineAiResult(editor, inlineAiAction('explain'), range, 'Because of X and Y.')
      const md = getEditorMarkdown(editor)
      expect(md).toBe(
        '# Title\n\nA claim worth explaining.\n\nBecause of X and Y.\n\nAnother paragraph.',
      )
    } finally {
      editor.destroy()
    }
  })

  it('keeps a multi-block result as blocks when inserting below', () => {
    const editor = editorFor('Source paragraph.')
    try {
      const range = rangeOf(editor, 'Source')
      select(editor, range)
      applyInlineAiResult(editor, inlineAiAction('summarize'), range, '- point one\n- point two')
      expect(getEditorMarkdown(editor)).toBe('Source paragraph.\n\n- point one\n- point two')
    } finally {
      editor.destroy()
    }
  })

  it('is a single undo step — one Ctrl+Z restores the pre-AI note', () => {
    const original = 'The old sentence stays here.'
    const editor = editorFor(original)
    try {
      const range = rangeOf(editor, 'old sentence')
      select(editor, range)
      applyInlineAiResult(editor, inlineAiAction('rewrite'), range, 'new wording')
      expect(getEditorMarkdown(editor)).not.toBe(original)
      editor.commands.undo()
      expect(getEditorMarkdown(editor)).toBe(original)
    } finally {
      editor.destroy()
    }
  })

  it('undoes an insert-below in one step too', () => {
    const original = 'A claim worth explaining.'
    const editor = editorFor(original)
    try {
      const range = rangeOf(editor, 'A claim')
      select(editor, range)
      applyInlineAiResult(editor, inlineAiAction('explain'), range, 'Because of X.')
      editor.commands.undo()
      expect(getEditorMarkdown(editor)).toBe(original)
    } finally {
      editor.destroy()
    }
  })

  it('writes nothing for a blank result or for flashcards', () => {
    const original = 'Untouched paragraph.'
    const editor = editorFor(original)
    try {
      const range = rangeOf(editor, 'Untouched')
      select(editor, range)
      expect(applyInlineAiResult(editor, inlineAiAction('rewrite'), range, '   ')).toBe(false)
      expect(applyInlineAiResult(editor, inlineAiAction('flashcards'), range, 'deck made')).toBe(
        false,
      )
      expect(getEditorMarkdown(editor)).toBe(original)
    } finally {
      editor.destroy()
    }
  })

  it('preserves wiki links around an accepted edit', () => {
    const editor = editorFor('See [[Attention]] and the old wording here.')
    try {
      const range = rangeOf(editor, 'old wording')
      select(editor, range)
      applyInlineAiResult(editor, inlineAiAction('rewrite'), range, 'new wording')
      expect(getEditorMarkdown(editor)).toBe('See [[Attention]] and the new wording here.')
    } finally {
      editor.destroy()
    }
  })
})
