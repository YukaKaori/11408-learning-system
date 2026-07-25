import { describe, expect, it } from 'vitest'
import {
  INLINE_AI_ACTIONS,
  inlineAiAction,
  isApplicable,
  normalizeAiResult,
} from '../inlineAi'

/**
 * The inline-AI action model (Phase 16 Step 5). These pin the *contract* the
 * glass toolbar applies — which actions exist, and where an accepted result is
 * allowed to land — so a future step cannot quietly make an action destructive.
 */
describe('inline AI action model', () => {
  it('exposes exactly the Step 5 cut-list, in toolbar order', () => {
    expect(INLINE_AI_ACTIONS.map((action) => action.key)).toEqual([
      'rewrite',
      'explain',
      'summarize',
      'flashcards',
    ])
  })

  it('only rewrite may replace the selection', () => {
    const replacing = INLINE_AI_ACTIONS.filter((action) => action.apply === 'replace')
    expect(replacing.map((action) => action.key)).toEqual(['rewrite'])
  })

  it('explain and summarize insert below, never overwrite', () => {
    expect(inlineAiAction('explain').apply).toBe('insertBelow')
    expect(inlineAiAction('summarize').apply).toBe('insertBelow')
  })

  it('flashcards is not a document edit at all', () => {
    const flashcards = inlineAiAction('flashcards')
    expect(flashcards.apply).toBe('none')
    expect(flashcards.request).toBeUndefined()
  })

  it('maps every text action onto an existing server NOTE_* action', () => {
    const requests = INLINE_AI_ACTIONS.map((action) => action.request).filter(Boolean)
    expect(requests).toEqual(['rewrite', 'explain', 'summarize'])
  })

  it('rejects an unknown action key', () => {
    // @ts-expect-error — guarding the runtime path, not the type
    expect(() => inlineAiAction('translate')).toThrow()
  })
})

describe('normalizeAiResult', () => {
  it('trims surrounding whitespace', () => {
    expect(normalizeAiResult('\n\n  hello  \n')).toBe('hello')
  })

  it('unwraps a fenced block the provider added around prose', () => {
    expect(normalizeAiResult('```\nrewritten text\n```')).toBe('rewritten text')
    expect(normalizeAiResult('```markdown\n# Title\n\nBody\n```')).toBe('# Title\n\nBody')
  })

  it('keeps a genuine code block that is only part of the answer', () => {
    const mixed = 'Here is the fix:\n\n```js\nconst x = 1\n```'
    expect(normalizeAiResult(mixed)).toBe(mixed)
  })

  it('leaves an unterminated fence alone rather than truncating', () => {
    expect(normalizeAiResult('```js\nconst x = 1')).toBe('```js\nconst x = 1')
  })
})

describe('isApplicable', () => {
  it('is false for a blank or whitespace-only result', () => {
    expect(isApplicable(inlineAiAction('rewrite'), '')).toBe(false)
    expect(isApplicable(inlineAiAction('rewrite'), '   \n ')).toBe(false)
  })

  it('is false for flashcards even with text (no document edit exists)', () => {
    expect(isApplicable(inlineAiAction('flashcards'), 'anything')).toBe(false)
  })

  it('is true for a text action with a real result', () => {
    expect(isApplicable(inlineAiAction('summarize'), 'A summary.')).toBe(true)
  })
})
