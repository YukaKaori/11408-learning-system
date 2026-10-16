# Content packs — the question bank's authoring guide

The library is content, versioned like code: every question, shared passage
and library paper ships in a **content pack** (a JSON file), is validated by
one function before anything is written, and is imported idempotently at
startup. Nothing in the bank is entered by hand into the database.

| Where | What |
| --- | --- |
| `server/src/main/resources/exam/questions/<paper>/*.json` | The packs shipped in the jar — what the importer reads |
| `content/packs/*.mjs` | Hand-written packs authored in JavaScript (template literals keep LaTeX and code readable) |
| `content/generators/*.mjs` | Computed families: seeded scripts whose answers are produced by code, never typed |
| `content/lib/` | The house-style writer, authoring helpers, seeded randomness, exact arithmetic |
| `content/COVERAGE.md` | The coverage report — generated, never edited |
| `CONTENT_PACKS_DIR` (`app.content.packs-dir`) | Optional directory of packs read beside the jar's — content without a code release |

A pack may be authored as JSON directly (the `core-*.json` packs are) or as a
module under `content/`; a module's JSON is generated and committed, and the
committed JSON is what ships.

## The workflow

```bash
node content/generate.mjs                 # write every pack that has a source module
node content/generate.mjs sample-english1 # … or one, by id
node content/generate.mjs --check         # fail if a committed pack differs from its source

cd server
./mvnw -q compile exec:java               # validate every pack exactly as the boot does, print coverage
./mvnw -q compile exec:java -Dexec.args="--dir /path/to/packs"            # plus a packs directory
./mvnw -q compile exec:java -Dexec.args="--markdown ../content/COVERAGE.md" # refresh the report

cd ../web
npx vitest run src/components/richText   # every field rendered: no KaTeX error, no stray $
```

`./mvnw test` runs the same validation over the shipped packs
(`PackValidatorTest`), and the server refuses to start with an invalid pack —
listing every problem, not the first.

Then **bump the pack's `version`** in the same change (see Versioning) and
ship: either in the jar (commit the JSON) or by copying the pack into the
directory named by `CONTENT_PACKS_DIR` and restarting the server.

## Format (v2)

```jsonc
{
  "pack": "sample-english1",          // id: [a-z0-9-], ≤ 64, unique across packs
  "title": "英语（一）原创样题 · 题型验证",
  "version": 1,                        // positive integer; bump on every content change
  "description": "…",                  // optional, for reviewers
  "generator": "content/packs/sample-english1.mjs", // optional: the source of a generated pack
  "groups": [ … ],                     // shared material
  "questions": [ … ],
  "papers": [ … ]                      // library papers
}
```

Unknown fields fail the pack (a typo such as `anwser` is an error, not a
silently dropped answer). Fields are written in the order above; `writePack`
puts generated packs in house style so a regeneration never produces noise.

### Questions

| Field | Rule |
| --- | --- |
| `key` | Permanent, globally unique id (`[a-z0-9-]`, ≤ 64). It is the row's identity across imports — never reuse a retired key |
| `subject` | `politics` · `english1` · `math1` · `cs408` |
| `section` | A printed section of that paper (`math1.fill`), or omitted for a practice-only question. The section fixes the type, and — where it prints one value per question — the score |
| `type` | `single_choice` · `multi_choice` · `fill_blank` · `open`; must be the section's type |
| `group` | The key of the group whose passage the question reads (then it has no `passage` of its own) |
| `points` | 1–6 考点 (leaf codes of the paper's syllabus). The **first** is the primary 考点 — the one a paper's slot is filled by |
| `difficulty` | 1–5, default 3 |
| `score` | Required in a variable-score section (408 综合应用题, 数学解答题); must equal the printed value elsewhere (it may be omitted there) |
| `source` | Provenance shown with the question everywhere (≤ 128). See Labels |
| `sourceYear` | Only for a licensed past-paper question |
| `stem`, `passage`, `analysis` | Markdown + LaTeX, ≤ 20 000 characters each |
| `options` | Choice questions only: 2–8 option texts in A, B, C… order |
| `answer` | Letters for choice (`"B"`, `"ACD"`), the value for fill-blank, the reference answer with its scoring points for open questions |
| `accept` | Fill-blank only: every accepted form, if more than `answer` |

**Grading follows the paper.** Choice questions are marked right or wrong
(多选 all or nothing). A fill-blank is right when the response matches an
accepted form after notation-only normalization — full-width to half-width,
whitespace and `$` removed, lower case, a trailing full stop dropped; no
algebra — and is otherwise scored by the candidate, all or nothing. Open
answers are scored by the candidate against the reference, in half points.
So an open answer should list its **得分点 with their points**, adding up to
the question's worth.

### Groups — shared material

```json
{ "key": "english1-sample-reading-g2", "subject": "english1", "section": "english1.reading-a",
  "title": "In defense of boredom", "passage": "…", "source": "原创样题 · 非真题" }
```

A group is read by at least two questions of its own section, which list it
in `group`; their order in the pack is their order in the text. Its questions
share the module of their primary 考点 (a paper's slot takes a text whole).

**In-place numbering.** A text that numbers its questions — a cloze's blanks,
a 七选五 text's gaps, a translation's sentences — marks them **relative to the
text** as `**[1]**` … or `**(1)**` …, and question *n* names its marker in its
stem (`Blank **[3]**`, `将第 **(2)** 句译成中文：…`). The validator checks that
every marker appears exactly once, in order, with none past the last question.
The paper numbers questions as printed (the cloze's `[3]` is question 3 of a
whole paper, the translation's `(1)` is question 46), and the exam stage
lights the open question's marker in the passage.

### Library papers

```json
{ "key": "math1-sample-paper-01", "subject": "math1", "kind": "mock",
  "title": "数学（一）原创样卷 · 题型验证（非真题）",
  "sections": [ { "code": "math1.choice", "questions": ["math1-limit-002", "…"] }, … ] }
```

Sections appear once each, in printed order (a subset is a section paper);
each lists **exactly** the printed number of questions (any pack's), every one
a question of that section, groups whole and in order, worth exactly the
section's total. `kind` is `mock` (no year) or `past_paper` (a 考研年份 from the
paper's first past-paper year to last year). A whole paper is timed by the
paper's duration, a section paper by its sections' suggested minutes.

## Labels — what a question claims to be

| `source` | Meaning |
| --- | --- |
| `原创 · 核心题库` | Original core questions |
| `原创 · 计算专项` | Generated: the answer is computed by the generator named in the pack |
| `原创样题 · 非真题` | Original **samples** written to exercise every format of a paper — not a bank, never a 真题 |

A question is presented as a past-exam question only when it is one and its
use is licensed: `kind: "past_paper"` with its `year`, and `sourceYear` on its
questions. Nothing generated or written here may carry either.

## Versioning

- Per pack id, the **highest `version`** among the jar and `CONTENT_PACKS_DIR`
  is used. The same version with different content in two places is an error.
- `content_packs` records the live version, hash and counts
  (`GET /api/v1/content/packs`). A pack **older** than the live one fails the
  boot — an old jar never silently reverts newer content.
- A content change without a version bump is imported, with a warning in the
  log. Don't rely on it: bump.
- Rows keep their ids across imports (keyed by `key`), so the answer log,
  mistakes and records keep pointing at them. A question, group or paper that
  leaves every pack is **retired** (no longer drawn or listed), never deleted.
  Re-adding the key reactivates the same row.
- A library paper is rewritten when its questions, their worth or its time
  limit change; a paper being sat keeps the layout it started with.

## Coverage

`ContentCheck` prints, per paper: questions per 考点 against their syllabus
weight, the thinnest weight-3 考点, and per printed section and slot how many
questions (or texts) are eligible and how many **disjoint** fills they make —
so "whole papers" is how many papers the bank can assemble before repeating a
question. The numbers are the assembler's own (`Combination`, the same
eligibility rules). See `COVERAGE.md` for the current bank.

## The bank today

The sample packs (`sample-politics`, `sample-english1`, `sample-math1`) add
exactly what one whole paper of each needed beyond the core packs, plus one
sample library paper each; 408 has computed families and hand-written packs
for five disjoint whole papers. Replacing the samples later is an ordinary
content change: drop their keys from the packs (they retire) or edit them in
place and bump the version.
