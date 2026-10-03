# Roadmap — 11408 Learning System

**Canonical as of 2026-10-03.** This replaces the general-purpose "AI Learning
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

## M2 — Content at scale, and papers sat in the app (next)

**Why next:** M1 made the paper the unit of evidence; the bank is still too
thin to sit papers inside the product, and M0 ships 110 original questions
(408: 53 · 数学一: 24 · 政治: 18 · 英语一: 15) — enough to prove the loop, far too
few for a year: sets repeat within days and mastery rests on a handful of
answers per 考点.

- A content pipeline: the pack authoring format documented, a validation
  command that runs `QuestionRules` and the syllabus checks offline, and pack
  versioning so content ships independently of code releases.
- Bank depth: at least 30–50 questions per high-yield (weight 3) 考点 in
  数学一 and 408, English reading passages grouped as sets, politics sets per
  module — authored or licensed, never scraped.
- Papers sat inside the app: an exam stage built from the blueprint's
  sections — a countdown, a section navigator, flags, no verdicts until
  submission, self-grading of open questions against scoring points — whose
  result is an ordinary sitting. The first sections the bank can already fill:
  408 单项选择题 (40) and 数学一 选择 + 填空 (16).
- Past papers as ordered sets where licensing allows: a paper-year pack
  format that preserves the printed order and sections.
- Images in questions (figures for 408 and 数学) — needs object storage (M4)
  or packs that embed vetted assets.

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
- Liquid Material: B5.2/B5.3 and Gecko/WebKit checks remain open
  (`docs/liquid-material-global-reassessment.md`).
- The local folder rename to `11408-Learning-System` (it cannot be renamed
  while an editor or Claude Code holds it open) and the hosted repository
  renames by the account owner — see README § Repository.
- Accounts that saved preferences before V10 keep their stored daily goal
  (the old default was 60 minutes); the plan page edits it in one click.
