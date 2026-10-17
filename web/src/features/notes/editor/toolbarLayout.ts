/**
 * How wide the selection toolbar is, and whether its actions keep their labels
 * — pure geometry, so the decision is unit-testable without a browser.
 *
 * The labels are the bar's only variable part: the Chinese set fits the 320px
 * the bar shipped with, the English set needs about 420. So the collapsed bar is
 * as wide as its labelled actions need, never narrower than its shipped width,
 * and never wider than the editor shell it floats in. Where the labelled bar
 * cannot fit — English on a phone — the actions fall back to icons: square
 * targets at the touch floor, their names kept as the accessible label.
 *
 * The labelled actions' width is measured from the rendered buttons once per
 * locale (`NoteSelectionToolbar`); everything else here is authored.
 */

/** The collapsed bar never narrows below the width it shipped with. */
export const BAR_MIN_WIDTH = 320

/**
 * Room around the actions inside the slab: the margin the shipped Chinese bar
 * already leaves (10px a side — the primitive's inset plus a hair), so a wider
 * English bar reads as the same object.
 */
export const BAR_EDGE = 20

/** An icon-only action: a square at the 44px touch floor (`SKILL.md` §4). */
export const COMPACT_ACTION = 44

/** The space between actions — `.action-bar`'s `gap: var(--space-1)`. */
export const ACTION_GAP = 4

/** The expanded proposal panel's preferred width. */
export const PANEL_WIDTH = 420

export interface BarLayout {
  width: number
  compact: boolean
}

/**
 * The collapsed bar for `actions` buttons whose labelled span is
 * `labelledActions` px (undefined until measured), in a shell `room` px wide.
 * Unmeasured, the bar is its shipped self — the first, measuring render.
 */
export function barLayout(labelledActions: number | undefined, actions: number, room: number): BarLayout {
  const labelled = Math.max(BAR_MIN_WIDTH, (labelledActions ?? 0) + BAR_EDGE)
  if (labelledActions === undefined || labelled <= room) {
    return { width: Math.min(labelled, room), compact: false }
  }
  const compact = actions * COMPACT_ACTION + Math.max(actions - 1, 0) * ACTION_GAP + BAR_EDGE
  return { width: Math.min(compact, room), compact: true }
}

/** The expanded panel: its preferred width, or the shell's, whichever is less. */
export function panelWidth(room: number): number {
  return Math.min(PANEL_WIDTH, room)
}
