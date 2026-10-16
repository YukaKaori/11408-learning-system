/**
 * Writing content packs in the house style — the shape `PackValidator` reads
 * and reviewers diff. Objects are expanded one field per line, absent fields
 * are omitted, and short string/number arrays (tags, short options) stay on
 * one line so a pack reads like the hand-written ones.
 *
 * Every generator and migration under content/ writes through `writePack`, so
 * regenerating a pack never produces formatting noise in a diff.
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))

/** The server's pack directory — where every pack the importer reads lives. */
export const PACKS_DIR = resolve(HERE, '../../server/src/main/resources/exam/questions')

const INLINE_LIMIT = 100

/** The record order of `ContentPack` — fields are written in it, whatever order they were built in. */
const ORDER = {
  pack: ['pack', 'title', 'version', 'description', 'generator', 'groups', 'questions', 'papers'],
  group: ['key', 'subject', 'section', 'title', 'passage', 'source'],
  question: [
    'key', 'subject', 'section', 'type', 'group', 'points', 'difficulty', 'score', 'source', 'sourceYear',
    'stem', 'passage', 'options', 'answer', 'accept', 'analysis',
  ],
  paper: ['key', 'subject', 'kind', 'title', 'year', 'sections'],
  paperSection: ['code', 'questions'],
}

function ordered(object, order) {
  const known = order.filter((key) => key in object)
  const rest = Object.keys(object).filter((key) => !order.includes(key))
  if (rest.length > 0) throw new Error(`Unknown field(s) ${rest.join(', ')} in ${JSON.stringify(object).slice(0, 80)}`)
  return Object.fromEntries(known.map((key) => [key, object[key]]))
}

/** Puts every record of a pack in its canonical field order (unknown fields are an error). */
export function canonical(pack) {
  const out = ordered(pack, ORDER.pack)
  if (out.groups) out.groups = out.groups.map((group) => ordered(group, ORDER.group))
  if (out.questions) out.questions = out.questions.map((question) => ordered(question, ORDER.question))
  if (out.papers) {
    out.papers = out.papers.map((paper) => {
      const p = ordered(paper, ORDER.paper)
      p.sections = p.sections.map((section) => ordered(section, ORDER.paperSection))
      return p
    })
  }
  return out
}

/** JSON in the house style. */
export function format(value, indent = 0) {
  const pad = '  '.repeat(indent)
  if (Array.isArray(value)) {
    if (value.length === 0) return '[]'
    if (value.every((v) => typeof v === 'string' || typeof v === 'number')) {
      const inline = `[${value.map((v) => JSON.stringify(v)).join(', ')}]`
      if (inline.length <= INLINE_LIMIT && !inline.includes('\\n')) return inline
    }
    return `[\n${value.map((v) => `${pad}  ${format(v, indent + 1)}`).join(',\n')}\n${pad}]`
  }
  if (value !== null && typeof value === 'object') {
    const entries = Object.entries(value).filter(([, v]) => v !== undefined && v !== null)
    if (entries.length === 0) return '{}'
    return `{\n${entries.map(([k, v]) => `${pad}  ${JSON.stringify(k)}: ${format(v, indent + 1)}`).join(',\n')}\n${pad}}`
  }
  return JSON.stringify(value)
}

/** Writes a pack to `<PACKS_DIR>/<relative>` in canonical order and house style. */
export function writePack(relative, pack) {
  const path = resolve(PACKS_DIR, relative)
  mkdirSync(dirname(path), { recursive: true })
  writeFileSync(path, `${format(canonical(pack))}\n`, 'utf8')
  return path
}
