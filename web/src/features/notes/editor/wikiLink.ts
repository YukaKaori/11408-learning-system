/**
 * Wiki-link primitives (Phase 16 Step 4) — pure, framework-free, and the single
 * place the `[[…]]` grammar is defined on the client.
 *
 * The pattern is deliberately identical to the backend's `NoteLinkExtractor`
 * (`\[\[([^\]]+)\]\]`, trim + collapse whitespace, case-insensitive matching) so
 * "what the editor renders as a link" and "what `note_links` indexes" can never
 * disagree. Markdown stays the only persistence format: a wiki-link is
 * `[[Title]]` characters in `notes.content`, nothing more.
 */

/** `[[ inner ]]` where inner is any non-empty run without a `]`. Global. */
export function wikiLinkPattern(): RegExp {
  return /\[\[([^\]]+)\]\]/g
}

/** The same grammar anchored at the end of a string — the input-rule form. */
export const WIKI_LINK_INPUT_PATTERN = /\[\[([^\]]+)\]\]$/

/**
 * The comparison form of a link target: trimmed, inner whitespace collapsed,
 * lower-cased. Mirrors `NoteLinkExtractor.extract` + `resolveTitleIndex`.
 *
 * Note the node itself stores the *raw* inner text — normalizing on parse would
 * rewrite `[[ Foo ]]` to `[[Foo]]` on the next autosave. Normalization is a
 * lookup concern only, never a storage one.
 */
export function normalizeWikiTitle(title: string): string {
  return title.trim().replace(/\s+/g, ' ').toLowerCase()
}

export interface LinkTarget {
  id: string
  title: string
  /** Epoch ms — ties are broken the way the backend breaks them. */
  updatedAt: number
}

/**
 * normalized title → note id. Mirrors `NoteService.resolveTitleIndex`:
 * latest-updated wins, id-desc breaking exact ties, so a title typed in the
 * editor resolves to the same note the server's index resolved it to.
 */
export function buildTitleIndex(targets: readonly LinkTarget[]): Map<string, string> {
  const ordered = [...targets].sort((a, b) => b.updatedAt - a.updatedAt || (a.id < b.id ? 1 : -1))
  const index = new Map<string, string>()
  for (const target of ordered) {
    const key = normalizeWikiTitle(target.title)
    if (key && !index.has(key)) index.set(key, target.id)
  }
  return index
}

/** An in-progress `[[query` the user is typing, in absolute document coords. */
export interface WikiLinkQuery {
  /** Position of the opening `[`. */
  from: number
  /** Position of the caret. */
  to: number
  /** Text typed after `[[` (may be empty). */
  query: string
}

/**
 * Detects an open `[[` trigger from the text between the start of the current
 * text block and the caret. A `]` closes the trigger (the completed link is the
 * input rule's job) and a newline can never appear inside one.
 *
 * @param textBefore text of the current block up to the caret
 * @param caret absolute document position of the caret
 */
export function findWikiLinkQuery(textBefore: string, caret: number): WikiLinkQuery | null {
  const match = /\[\[([^\]\n]*)$/.exec(textBefore)
  if (!match) return null
  return { from: caret - match[0].length, to: caret, query: match[1]! }
}

/** Notes whose title matches the typed query, best-first, capped. */
export function matchLinkTargets(
  targets: readonly LinkTarget[],
  query: string,
  limit = 8,
): LinkTarget[] {
  const needle = normalizeWikiTitle(query)
  const ranked = targets
    .map((target) => ({ target, at: normalizeWikiTitle(target.title).indexOf(needle) }))
    .filter((entry) => needle === '' || entry.at >= 0)
    // Prefix matches first, then earlier matches, then most recently touched.
    .sort((a, b) => a.at - b.at || b.target.updatedAt - a.target.updatedAt)
  return ranked.slice(0, limit).map((entry) => entry.target)
}
