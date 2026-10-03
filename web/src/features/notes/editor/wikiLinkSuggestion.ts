import { Extension } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import type { EditorState, Transaction } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import { findWikiLinkQuery, type WikiLinkQuery } from './wikiLink'

export interface WikiLinkSuggestionState extends WikiLinkQuery {
  /** Viewport coordinates of the `[[` trigger, for popup placement. */
  left: number
  top: number
  bottom: number
}

export interface WikiLinkSuggestionOptions {
  /** Called whenever the trigger opens, changes, or closes (`null`). */
  onUpdate: (state: WikiLinkSuggestionState | null) => void
  /** Keys are offered to the view *only while the popup is open*. */
  onKeyDown: (event: KeyboardEvent) => boolean
}

/** One row of the popup: an existing note, or "create this note". */
export interface SuggestionRow {
  kind: 'note' | 'create'
  /** Note id for `note` rows; absent for the create row. */
  id?: string
  title: string
}

interface PluginState {
  active: WikiLinkQuery | null
  dismissed: boolean
}

interface SuggestionMeta {
  dismiss: boolean
}

export const wikiLinkSuggestionKey = new PluginKey<PluginState>('wikiLinkSuggestion')

/** Closes the popup until the caret leaves the current trigger (Escape). */
export function dismissWikiLinkSuggestion(state: EditorState): Transaction {
  return state.tr.setMeta(wikiLinkSuggestionKey, { dismiss: true } satisfies SuggestionMeta)
}

/**
 * The `[[` autocomplete (Phase 16 Step 4) — a small ProseMirror plugin rather
 * than `@tiptap/suggestion`, which Step 0's locked dependency list does not
 * include and this step is not allowed to expand.
 *
 * Division of labour: the plugin owns *detection* (where the trigger is, what
 * has been typed) and hands it to the view; the Vue layer owns the list, the
 * highlighted index, and rendering — so the popup can be a plain solid surface
 * with no ProseMirror knowledge, and no glass touches the canvas.
 */
export function WikiLinkSuggestion(options: WikiLinkSuggestionOptions): Extension {
  return Extension.create({
    name: 'wikiLinkSuggestion',

    addProseMirrorPlugins() {
      let emitted: WikiLinkSuggestionState | null = null

      return [
        new Plugin<PluginState>({
          key: wikiLinkSuggestionKey,

          state: {
            init: () => ({ active: null, dismissed: false }),
            apply(tr, previous, _oldState, newState) {
              const meta = tr.getMeta(wikiLinkSuggestionKey) as SuggestionMeta | undefined
              if (meta?.dismiss) {
                return { active: detectQuery(newState), dismissed: true }
              }
              const active = detectQuery(newState)
              // A dismissal survives only while the same trigger is open.
              const dismissed =
                previous.dismissed && active !== null && previous.active?.from === active.from
              return { active, dismissed }
            },
          },

          props: {
            decorations(state) {
              const plugin = wikiLinkSuggestionKey.getState(state)
              if (!plugin?.active || plugin.dismissed) return null
              return DecorationSet.create(state.doc, [
                Decoration.inline(plugin.active.from, plugin.active.to, {
                  class: 'wiki-link-typing',
                }),
              ])
            },
            handleKeyDown(view, event) {
              const plugin = wikiLinkSuggestionKey.getState(view.state)
              if (!plugin?.active || plugin.dismissed) return false
              return options.onKeyDown(event)
            },
          },

          view() {
            return {
              update(view) {
                const plugin = wikiLinkSuggestionKey.getState(view.state)
                const active = plugin?.dismissed ? null : (plugin?.active ?? null)
                if (!active) {
                  if (emitted) {
                    emitted = null
                    options.onUpdate(null)
                  }
                  return
                }
                const coords = view.coordsAtPos(active.from)
                const next: WikiLinkSuggestionState = {
                  ...active,
                  left: coords.left,
                  top: coords.top,
                  bottom: coords.bottom,
                }
                if (
                  emitted?.from === next.from &&
                  emitted.to === next.to &&
                  emitted.query === next.query &&
                  emitted.left === next.left &&
                  emitted.bottom === next.bottom
                ) {
                  return
                }
                emitted = next
                options.onUpdate(next)
              },
              destroy() {
                if (emitted) {
                  emitted = null
                  options.onUpdate(null)
                }
              },
            }
          },
        }),
      ]
    },
  })
}

/** The open `[[` trigger at the caret, if any. Never fires inside code. */
function detectQuery(state: EditorState): WikiLinkQuery | null {
  const { selection } = state
  if (!selection.empty) return null
  const $from = selection.$from
  if (!$from.parent.isTextblock || $from.parent.type.spec.code) return null
  // One char per leaf keeps `textBefore` offsets aligned with document positions.
  const textBefore = state.doc.textBetween($from.start(), selection.from, '\n', '\0')
  return findWikiLinkQuery(textBefore, selection.from)
}
