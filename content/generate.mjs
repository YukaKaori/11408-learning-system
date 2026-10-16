/**
 * Writes the content packs that have a source module in content/:
 *
 *   generators/*.mjs  computed families (answers produced by code)
 *   packs/*.mjs       hand-written packs authored in JS (template literals keep
 *                     LaTeX and code blocks readable)
 *
 *   node content/generate.mjs              # every source
 *   node content/generate.mjs gen-cs408-os # one (by id)
 *   node content/generate.mjs --check      # fail if any committed pack differs from its source
 *
 * Every source is a pure function (generators are seeded), so an unchanged
 * source rewrites its pack byte for byte. Packs without a module here (the
 * core-*.json packs) are edited as JSON directly.
 *
 * After writing, validate exactly as the server will:
 *   cd server && ./mvnw -q compile exec:java
 *
 * Changing what a source produces is a content change: bump its pack
 * `version` in the same commit.
 */
import { readdirSync, readFileSync, existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { PACKS_DIR, canonical, format, writePack } from './lib/pack.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))

async function sources() {
  const found = {}
  for (const folder of ['generators', 'packs']) {
    const dir = join(HERE, folder)
    if (!existsSync(dir)) continue
    for (const file of readdirSync(dir).filter((name) => name.endsWith('.mjs')).sort()) {
      const { default: source } = await import(pathToFileURL(join(dir, file)).href)
      if (found[source.id]) throw new Error(`Duplicate source id ${source.id}`)
      found[source.id] = source
    }
  }
  return found
}

/** Cheap structural checks before the real validation: keys, letters, distinct options. */
function check(id, questions) {
  const keys = new Set()
  for (const question of questions) {
    if (keys.has(question.key)) throw new Error(`${id}: duplicate key ${question.key}`)
    keys.add(question.key)
    if (question.options) {
      if (new Set(question.options).size !== question.options.length) {
        throw new Error(`${id}: ${question.key} has duplicate options ${JSON.stringify(question.options)}`)
      }
      for (const letter of question.answer) {
        const position = letter.charCodeAt(0) - 65
        if (position < 0 || position >= question.options.length) {
          throw new Error(`${id}: ${question.key} answer ${question.answer} names no option`)
        }
      }
    }
  }
}

function render(source) {
  const built = source.build()
  const pack = Array.isArray(built) ? { ...source.pack, questions: built } : { ...source.pack, ...built }
  check(source.id, pack.questions ?? [])
  return pack
}

const args = process.argv.slice(2)
const checking = args.includes('--check')
const wanted = args.filter((arg) => !arg.startsWith('--'))
const all = await sources()
const ids = wanted.length > 0 ? wanted : Object.keys(all)
let drift = 0
for (const id of ids) {
  const source = all[id]
  if (!source) throw new Error(`Unknown source ${id}; known: ${Object.keys(all).join(', ')}`)
  const pack = render(source)
  if (checking) {
    const path = resolve(PACKS_DIR, source.file)
    const expected = `${format(canonical(pack))}\n`
    const actual = existsSync(path) ? readFileSync(path, 'utf8').replace(/\r\n/g, '\n') : ''
    if (actual !== expected) {
      drift++
      console.error(`✗ ${source.file} differs from ${id} — run: node content/generate.mjs ${id}`)
    } else {
      console.log(`✓ ${source.file}`)
    }
    continue
  }
  const path = writePack(source.file, pack)
  console.log(`${id}: ${(pack.questions ?? []).length} questions, ${(pack.groups ?? []).length} groups, ${(pack.papers ?? []).length} papers → ${path}`)
}
if (drift > 0) process.exit(1)
