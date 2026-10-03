import { Extension } from '@tiptap/core'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'

/**
 * The empty-note placeholder (Phase 16 Step 6).
 *
 * Hand-rolled rather than installing `@tiptap/extension-placeholder`, for the
 * same reason Step 4 hand-rolled the `[[` suggestion plugin instead of pulling
 * in `@tiptap/suggestion`: this is thirty lines of decoration, and the era's
 * dependency budget was spent on the editor itself.
 *
 * The text is read through a getter on every decoration pass, so a locale
 * switch is picked up by the next transaction (`NoteEditor` dispatches an empty
 * one when the translation changes) instead of being baked in at editor
 * construction.
 */
export interface NotePlaceholderOptions {
  text: () => string
}

export const notePlaceholderKey = new PluginKey('notePlaceholder')

/** A pristine note: one empty top-level text block and nothing else. */
function isDocEmpty(doc: ProseMirrorNode): boolean {
  const first = doc.firstChild
  return doc.childCount === 1 && first !== null && first.isTextblock && first.content.size === 0
}

export const NotePlaceholder = Extension.create<NotePlaceholderOptions>({
  name: 'notePlaceholder',

  addOptions() {
    return { text: () => '' }
  },

  addProseMirrorPlugins() {
    const options = this.options

    return [
      new Plugin({
        key: notePlaceholderKey,
        props: {
          decorations(state) {
            const text = options.text()
            const first = state.doc.firstChild
            if (!text || !first || !isDocEmpty(state.doc)) return null
            // A node decoration, not a widget: the placeholder is drawn by CSS
            // (`::before`) so it never enters the document or the selection.
            return DecorationSet.create(state.doc, [
              Decoration.node(0, first.nodeSize, {
                class: 'is-empty',
                'data-placeholder': text,
              }),
            ])
          },
        },
      }),
    ]
  },
})
