# M2 — Question Bank & Timed Papers (plan of record)

Written 2026-10-03 at the start of M2. **Shipped 2026-10-16**: the binding
parts are in [`architecture.md`](architecture.md) § M2, the state and open
items in [`m2-handoff.md`](m2-handoff.md); this file keeps the reasoning.
Step 4 ("content at scale") shipped as the pipeline plus one whole paper per
paper (original samples for 政治、英语一、数学一); bank depth is content work that
continues through the pipeline.

## The loop M2 closes

```
syllabus ─► question bank ─► practice ─► timed paper ─► result ─► diagnosis ─► mistakes / mastery ─► plan
   ▲            ▲  (packs, versioned,         │ (sat in the app,      │  (sitting record,        │
   │            │   validated offline)        │  no verdicts until    │   attempts, mistakes,    │
   │            └─ assembled from slots ◄─────┘  submission)          │   study time)            │
   └────────────────────────────────────────────────────────────────── the plan re-divides the day
```

M1 made the paper the unit of evidence, but every sitting was recorded after
it was sat on paper, and the bank (110 questions) could not fill one paper.
M2 makes the bank deep enough to sit papers *inside* the product and makes a
paper sat there an ordinary sitting — so the estimate, the plan, mastery and
the mistake book all move from one submission.

## Domain

| Concept | What it is | Where |
| --- | --- | --- |
| **Content pack** (v2) | A versioned JSON file of groups, questions and papers. Ships on the classpath or in a configured directory | `content` package (new) |
| **Question group** | A shared stimulus (a reading passage, a cloze text) and its ordered questions | `question_groups` |
| **Paper** (试卷) | A blueprint-structured, ordered composition of questions: sections in printed order, each question with its score in this paper. Library papers come from packs; a candidate's own papers are assembled (组卷) | `papers`, `paper` package (new) |
| **Paper session** | A paper being sat in the app: deadline, answers, flags, time per question, then marks and self-scores | `paper_sessions` |
| **Sitting** (M1) | The record. A paper session writes one — an ordinary sitting, tagged with its session | `paper_sittings.session_id` |

### The blueprint grows slots and minutes (content)

Each printed section in `exam/syllabus/<paper>.json` gains

- `minutes` — the suggested time; the sections of a paper sum to its duration
  (validated). A section paper's time limit is the sum of its sections'.
- `slots` — how the real paper distributes the section over modules:
  `{scope, count, total?, group?}`. Uniform-score sections take
  `count × score`; variable-score sections (408 综合应用题, 数学解答题) declare a
  `total` per slot and the slots sum to the section. `group` is the size of a
  question group the slot takes whole (英语: a cloze text of 20, reading texts
  of 5). Validated at boot: counts sum to the section, totals to its worth,
  scopes are modules of the paper.

Slots are the real structure since 2021: 408 选择 11/11/10/8 (数据结构, 组成,
操作系统, 网络), 综合 23/23/15/9 points over 2/2/2/1 questions; 数学一 6/2/2,
4/1/1, 4/1/1 with 46/12/12 points of 解答; 政治 per module 4/6/2/2/2 单选,
5/7/1/2/2 多选, one 分析题 each — which reproduces the syllabus's module
scores exactly.

### Assembly (组卷) — `PaperAssembler`, pure

For each requested section, for each slot, from the questions the candidate
may practise in that section whose **primary 考点** (`points[0]`) lies in the
slot's scope:

1. order by preference — never attempted and never in one of the
   candidate's papers, then never attempted, then attempted longest ago;
   random within a band;
2. uniform slot: take `count` (or `count / group` whole groups of exactly
   that size); variable slot: the first combination of `count` questions
   whose scores sum to the slot's `total` (bounded search over the 40 most
   preferred);
3. order the slot's questions by syllabus position, groups kept whole.

A slot that cannot be filled makes its section unavailable, and the catalog
says which slot is short and by how much — the bank's gaps are visible, never
papered over. A whole paper is available when every section is.

### Sitting a paper — `PaperSessionService`

| State | What the candidate sees | What the server allows |
| --- | --- | --- |
| `in_progress` | questions, own answers, flags, the countdown — **no verdicts, answers or 解析** | full-state autosave until the deadline + 2 min grace; submit; abandon |
| `scoring` | auto-graded verdicts, every reference answer and scoring point, own answers | self-scores for what the grader could not judge; complete |
| `completed` | the report | read |

- **Time is the server's.** `deadline_at = started_at + limit`. A save after
  the grace is refused (`PAPER_TIME_UP`); a session found past its grace is
  submitted with its last saved answers on the next read. The client counts
  down against the server clock (`serverNow` in every response) and submits
  at zero.
- **One paper at a time** (`PAPER_SESSION_ACTIVE`). Starting saves any running
  study timer (a switch) so time is never counted twice.
- **Grading at submission** — the practice grader, no second judge. Choice: an
  answer is right or wrong (政治多选 all-or-nothing, as printed). Fill-blank:
  an accepted form is right, anything else is judged by the candidate (right
  or wrong — a blank is worth all or nothing). Open: judged by the candidate
  in points against the scoring points (0 … worth, steps of ½).
- **A blank is not evidence.** An unanswered question scores 0 on the paper
  and writes no attempt — running out of time is not "不会". The report lists
  the blanks.
- **Evidence is written when it exists.** At submission: one attempt per
  auto-graded answer (session id = the paper session), each through the
  mistake book; the sitting record with every section already fully graded;
  one study session for the time sat. At completion: the self-scored attempts
  (full marks → correct, 0 → wrong, otherwise partial), their mistakes, and
  the remaining sections — the record becomes a whole paper and starts
  counting towards the estimate.
- **An app-scored record is read-only.** Its scores are the sum of its
  answers; `PUT` is refused (`SITTING_READ_ONLY`), the 复盘 note stays
  editable (`PATCH /sittings/{id}/note`), and deleting it withdraws it from
  the estimate without touching the answer log.

### Content pipeline

- **Format v2**: `{pack, title, version, description?, generator?, groups[],
  questions[], papers[]}`. Keys are global and permanent; a grouped question
  carries `group` and no passage of its own; papers list question keys per
  printed section.
- **Validation is one pure function** (`PackValidator`): `QuestionRules` per
  item plus the pack rules (unique keys, groups whole and consistent, a score
  that matches a uniform section, a score for every variable-section question,
  papers that match the blueprint). It runs at boot (fail fast), in the test
  suite, and offline: `./mvnw -q compile exec:java` prints errors and a coverage
  report (per paper, section, module, 考点 against depth targets; how many
  disjoint whole papers the library can assemble).
- **Versioning**: every pack declares an integer `version`; `content_packs`
  records what is live (version, hash, counts, source). Per pack id the highest
  available version wins; a lower version than the live one is refused (logged,
  its content kept); a change without a bump is imported and logged. Rows carry
  `pack_id`. Packs load from the classpath and, when `app.content.packs-dir` is
  set, from that directory — content ships without a code release.
- **Generated families**: computational question families (page replacement,
  subnetting, IEEE 754, determinants, distributions …) are produced by seeded
  generators in `content/generators/` whose answers are computed, not typed;
  the JSON they emit is committed and reviewed like any pack.

## Surfaces

- **模考** (`/sittings`): an 在线模考 panel per paper — library papers with
  their history, 组卷 (whole paper or one section, availability shown), the
  candidate's own papers; a banner for a paper in progress or awaiting
  scoring; app-scored records open their result.
- **The exam stage** (`/exam/:id`, full-bleed): countdown, section navigator,
  flags, the group's passage beside its questions, autosave with a visible
  state, a submit dialog that counts blanks and flags, auto-submit at zero.
- **Result** (`/sittings/papers/:id`, in the shell): scoring, then the report —
  score by section against the suggested time, every question with verdict,
  answer and 解析 (AI walk-through available), 考点 diagnosis with one-tap
  practice, mistakes filed, the estimate against the target.
- **Today**: a paper in progress or awaiting scoring is a `paper` row in the
  plan (`now`) — it has the hardest time contract there is.

## Error codes

`paper` 260000–269999: `PAPER_NOT_FOUND`, `PAPER_ACCESS_DENIED`,
`PAPER_SECTION_INVALID`, `PAPER_CONTENT_SHORT`, `PAPER_SESSION_NOT_FOUND`,
`PAPER_SESSION_ACCESS_DENIED`, `PAPER_SESSION_ACTIVE`, `PAPER_SESSION_STATE`,
`PAPER_TIME_UP`, `PAPER_ANSWER_INVALID`, `PAPER_SCORE_INVALID`,
`PAPER_SCORING_INCOMPLETE`. `sitting` adds `SITTING_READ_ONLY` (250007).

## Order of work

1. Syllabus slots/minutes · V11 (+ H2 mirror) · content package (format v2,
   validator, importer, versioning, CLI) · groups in the question API.
2. `paper` package: assembler, catalog, sessions, grading, completion →
   attempts, mistakes, sitting, study time · sitting read-only rule · Today row.
3. Web: API modules, 模考 panel, exam stage, scoring, report, Today row, i18n.
4. Content at scale: v2 migration of the core packs, generators, hand-authored
   packs for all four papers, library mock papers, coverage report.
5. Live verification (API + Playwright, both themes, phone width), docs,
   handoff.
