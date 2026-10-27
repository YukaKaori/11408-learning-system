# Roadmap — 11408 Learning System

**Canonical as of 2026-10-03; M2 updated 2026-10-16; carry-forward 2026-10-23, verified 2026-10-27 (M3 not started).** This replaces the general-purpose "AI Learning
Platform" roadmap (Phases 15–28), archived at
[`archive/roadmap-ai-learning-platform.md`](archive/roadmap-ai-learning-platform.md).
Historical documents that cite "`docs/roadmap.md` § Phase N" refer to that
archived file.

## North star

Every day a candidate spends in the product should leave them measurably
closer to their target score — and the product should be able to show it,
paper by paper, from their own evidence.

Three consequences shape the order below:

1. **The exam is sat as papers, not as points.** Point-level practice builds
   knowledge; only full, timed papers build the exam skill and give an honest
   score estimate.
2. **Time is the scarce resource.** A candidate has a fixed number of hours
   before a fixed date; a plan that does not say where they go is a wish list.
3. **The loop is only as good as its content.** Practice, the mistake book,
   mastery and recommendations all run on the question bank; a thin bank
   makes every downstream number thin.

## M0 — The 11408 foundation ✅ (2026-09)

The general learning workspace became an exam-preparation system without
rebuilding what already worked. The loop at the **考点 level**.

- The 11408 syllabus as versioned content: four papers, their modules,
  chapters and 考点, score weights and paper sections, validated at boot.
- Every artifact re-anchored from free-form subjects to syllabus nodes
  (expand/contract, V8); the subject module retired.
- Question bank (V9): content packs imported idempotently, candidate-captured
  questions, shared validation, honest grading with a self-grade protocol.
- Practice: topic, weak-point, mistake-redo and random sets; per-question
  verdicts, 解析 and AI walk-through; a per-考点 report.
- Mistake book: automatic filing, FSRS-spaced redos, 3-in-a-row resolution,
  causes and reflections, capture from paper, AI diagnosis.
- Mastery model and explainable, phase-aware recommendations.
- Today: exam countdown, due mistakes on the plan, one suggested 考点 per paper.
- Analytics: score-weighted readiness per paper, practice trend, mistake
  causes, weakest 考点, time by paper.
- AI grounded in the exam, the scope, the diagnosis and the candidate's corpus;
  three streaming tutoring actions; LaTeX rendered everywhere.
- Exam profile: target year, confirmed-or-estimated date, target scores.
- Product identity renamed to 11408 Learning System (code, artifacts, UI,
  docs).

## M1 — The exam year ✅ (2026-10)

The loop at the **paper level**: what a whole paper yields, where the hours
go, and a plan that reaches the exam date.

- **模考 (sittings, V10):** 真题 by year and 模拟卷 by name, recorded with a
  score per printed section (or a total). A recency-weighted estimate per
  paper from whole papers only, the section profile ("where the points go"),
  the score trajectory against the target, and the 真题 shelf (which years
  are done, 二刷 included).
- **The study timer:** one server-side timer per candidate, in the sidebar,
  the mobile header, Today and on every 考点 page; a start while running is a
  switch; stopping writes an ordinary study session; a sitting-length block
  offers to record its score.
- **规划 (the plan, a read model):** the phase timeline, the study day divided
  among the four papers (phase base × gap to target, every share explained),
  this week's hours and whole papers against the plan, and the two-day exam
  timetable. The calendar became its second view.
- **Today:** a time band — the day in hours per paper, one tap to time one —
  above the server-ranked plan; the goal ring in hours with the running timer
  counted live.
- The streak counts any study day; the daily goal became the study day
  (default 8 h, edited in hours); the AI tutor knows the paper estimates and,
  for general questions, the day's plan.

## M2 — Question bank & timed papers ✅ (2026-10)

The system for content at scale, and every paper sat in the app. Plan:
[`m2-plan.md`](m2-plan.md); binding decisions: `architecture.md` § M2; handoff:
[`m2-handoff.md`](m2-handoff.md).

- **The content pipeline.** Packs format v2 (groups, questions, library
  papers), one pure validator behind the boot, the tests and an offline check
  that also prints coverage; integer pack versions with the live version
  recorded and older content refused; content shipped in the jar or from a
  packs directory without a code release; seeded generators for computed
  408 families; the authoring guide (`content/README.md`).
- **The blueprint as structure.** Suggested minutes per printed section and
  the real paper's slots, so 组卷 assembles a paper — whole or one section —
  module by module as it is printed, preferring what the candidate has not
  seen, and says plainly which slot the bank cannot fill.
- **Papers sat in the app**, all four, every printed section: library papers
  and assembled ones; the server's clock with autosave, resume after a closed
  tab and hand-in at zero; flags and a section navigator; English texts beside
  their questions with the open blank lit; no verdicts before the hand-in;
  automatic grading where the grader can judge (多选 all or nothing, fill-blanks
  by their accepted forms) and the candidate's own scoring of the rest against
  the reference and its 得分点; a report by section, by time and by 考点; an
  ordinary, read-only 模考 record that feeds the estimate, the plan, mastery,
  analytics and the mistake book; Today's paper row.
- **Content today: 716 questions.** 408 has 586 (five disjoint whole papers);
  政治、英语一 and 数学一 have exactly one whole paper each, completed with
  **original samples labelled 原创样题 · 非真题** that exist to prove every format
  works end to end, plus one sample library paper each.

Not met, carried forward:

- **Bank depth.** The targets stand — 30–50 questions per weight-3 考点 in
  数学一 and 408, several papers' worth of English texts and politics sets —
  and are now content work: the content owner writes or licenses packs and
  publishes them through the pipeline (`content/COVERAGE.md` shows the gaps).
- **Licensed past papers.** The library-paper format preserves printed order
  and sections and takes `kind: past_paper` with its year; no 真题 is shipped.
- **Figures in questions** (408, 数学) — still needs object storage (M4) or
  packs that embed vetted assets.

## M3 — Grounded AI, the next step

- Retrieval over the candidate's notes, cards and the bank's 解析, with
  citations the UI can open (the Phase 18 plan, re-scoped to 11408).
- AI-assisted scoring of English writing and translation and of politics
  analysis answers against published rubrics — always shown as advice beside
  the candidate's own grade, never replacing it.
- A weekly AI 复盘 written from M1's numbers: hours per paper against the
  plan, sittings and their sections, mistakes filed and resolved.
- Variant questions generated for a 考点 and verified before they enter
  practice.

## M4 — Productization

- Registration, email verification, password reset.
- Object storage for materials and question figures (`StorageService`).
- Contract migration: drop the retired `subjects` table and `subject_id`
  columns after one release on V8.
- Deployment (Docker, CI/CD), observability, backups, a content-admin role.
- Mobile polish of the practice stage (it already works at phone width).

## Carry-forward

- Live verification with a real DeepSeek key of the three tutoring streams and
  of the two new context lines.
- Two unexplained native crashes of the dev backend (`0xC0000005`, no JVM
  crash report) under `mvnw spring-boot:run` during M2 verification; not
  reproduced afterwards — see `m2-handoff.md` § Open.
- Liquid Material: B5.2 (the scroll edge under the app dock) and B5.3 (the
  light-backdrop optics) landed in the 2026-10-17 visual pass, with the
  English-locale follow-up: the note toolbar sizes to its actions and goes
  icon-only where they cannot fit, and the dock's stacked labels use their
  whole cell. The 2026-10-21 pass made the sign-in room a daylight room on the
  supplied wallpaper, gave the hero slab its
  light-backdrop optics, made the note editor's `[[` list the first desktop
  glass beyond the toolbar, fixed the toolbar's lost refraction, added a
  forced-colours state, 44px touch reach on glass controls and English dock
  labels at 320px (§25 of `docs/liquid-material-global-reassessment.md`).
  The 2026-10-23 pass (committed 2026-10-27 on branch `welcome-room-crown-verification`) moved the welcome
  hero into the same daylight room, gave phone-shaped stages a composition of
  their own (the crown), raised the Regular rank's body to .78 with one-pass
  refraction, kept the note toolbar's backdrop on phones, declared keyboard
  rings on each slab, made a production build honour the declared backdrop
  (its CSS minifier had made the light-stage slabs follow the theme) and
  deepened the sign-in error ink; no surface was added (§26). Its verification
  finished on 2026-10-27 on production builds, in Chrome only: 463 unit tests,
  type-check, lint and build, 564 interaction checks, the contrast tables of
  §26.4. Still open: the Product room's scroll edge; the Gecko/WebKit, Windows
  High Contrast and device checks; the landing dock's current label on upright
  tablets (3.4–4.3 : 1 bare, 4.6–6.4 with its halo); the note toolbar, its
  proposal panel and the `[[` list clipping inside a scrolled editor pane
  (pre-existing positioning); the app-wide dark-theme primary button (a white
  label on `#7b86e8`, 3.27 : 1 — pre-existing, an app-wide restyle); tier C
  (reduced transparency) focus rings — the docks' 1.3–3.1 : 1 and, at desktop
  widths, three of the slab's 2.6–3.0 — and its desktop error line, 4.2–4.3
  (pre-existing; better than before the pass in every compared figure but one
  dock ring; deferred — a two-tone ring is the likely fix); and the root-token
  smoke tints a build still resolves by the theme.
- The local folder rename to `11408-Learning-System` (it cannot be renamed
  while an editor or Claude Code holds it open) and the hosted repository
  renames by the account owner — see README § Repository.
- Accounts that saved preferences before V10 keep their stored daily goal
  (the old default was 60 minutes); the plan page edits it in one click.
