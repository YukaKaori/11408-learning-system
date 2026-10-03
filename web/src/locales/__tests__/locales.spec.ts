import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { KNOWN_ERROR_CODES } from '../../api/errorKeys'
import zhCN from '../zh-CN'
import enUS from '../en-US'

function flattenKeys(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null) {
    return [prefix]
  }
  return Object.entries(value).flatMap(([key, child]) =>
    flattenKeys(child, prefix ? `${prefix}.${key}` : key),
  )
}

function resolve(messages: unknown, key: string): unknown {
  return key
    .split('.')
    .reduce<unknown>(
      (node, part) =>
        node !== null && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined,
      messages,
    )
}

const SRC = fileURLToPath(new URL('../..', import.meta.url))

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) return entry === '__tests__' ? [] : sourceFiles(path)
    return entry.endsWith('.vue') || entry.endsWith('.ts') ? [path] : []
  })
}

describe('locale messages', () => {
  it('en-US covers exactly the same keys as zh-CN', () => {
    expect(flattenKeys(enUS).sort()).toEqual(flattenKeys(zhCN).sort())
  })

  it('has no empty translations', () => {
    for (const messages of [zhCN, enUS]) {
      const leaves = JSON.stringify(messages)
      expect(leaves).not.toContain('""')
    }
  })

  /**
   * Every business code the HTTP layer maps to its own message
   * (`api/errorKeys.ts`) must have that message, or the candidate would see
   * the raw key instead of "这个范围暂时没有可练的题".
   */
  it('has a message for every known business error code', () => {
    for (const code of KNOWN_ERROR_CODES) {
      for (const [name, messages] of [
        ['zh-CN', zhCN],
        ['en-US', enUS],
      ] as const) {
        expect(typeof resolve(messages, `error.codes.${code}`), `${name} error.codes.${code}`).toBe('string')
      }
    }
  })

  /**
   * A key the source asks for must exist — a typo or a key left behind by a
   * rename (the retired `subjects.*`, say) would otherwise render as its own
   * path in the UI. Literal keys must resolve to a message; the static prefix
   * of a template key (`` t(`exam.papers.${code}`) ``) must resolve to a group.
   */
  it('resolves every key the source uses', () => {
    const missing: string[] = []
    for (const file of sourceFiles(SRC)) {
      const source = readFileSync(file, 'utf8')
      const where = file.slice(SRC.length).replace(/\\/g, '/')
      for (const match of source.matchAll(/\bt\(\s*'([\w.-]+)'/g)) {
        if (typeof resolve(zhCN, match[1]!) !== 'string') missing.push(`${where}: ${match[1]}`)
      }
      for (const match of source.matchAll(/\bt\(\s*`([\w.-]+)\.\$\{/g)) {
        const group = resolve(zhCN, match[1]!)
        if (group === null || typeof group !== 'object') missing.push(`${where}: ${match[1]}.*`)
      }
    }
    expect(missing).toEqual([])
  })

  /**
   * vue-i18n compiles messages: `{…}` is a placeholder, `|` separates plural
   * forms and `@:` links another message. A literal brace, pipe or at-sign in
   * copy (a LaTeX example, an e-mail address) silently breaks the message.
   */
  it('uses no message-syntax characters as literal text', () => {
    for (const [name, messages] of [
      ['zh-CN', zhCN],
      ['en-US', enUS],
    ] as const) {
      for (const key of flattenKeys(messages)) {
        const message = resolve(messages, key) as string
        expect(message, `${name} ${key}`).not.toMatch(/[|@]/)
        // Only simple `{name}` placeholders.
        expect(message.replace(/\{\w+\}/g, ''), `${name} ${key}`).not.toMatch(/[{}]/)
      }
    }
  })
})
