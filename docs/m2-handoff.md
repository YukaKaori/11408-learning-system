# M2 handoff — Question Bank & Timed Papers

**State on 2026-10-16: complete and verified; nothing committed.** The work
sits in the working tree on top of `006825d` (M0 + M1). Plan of record:
[`m2-plan.md`](m2-plan.md); binding decisions: [`architecture.md`](architecture.md)
§ M2; content authoring: [`content/README.md`](../content/README.md).

## What M2 is

The system for a question bank at scale, and every paper sat inside the app.

- **Content pipeline** — packs format v2 (`server/src/main/resources/exam/questions/<paper>/*.json`):
  groups (shared passages), questions, library papers. One pure validator
  (`PackValidator`) behind the boot import, the test suite and the offline
  `ContentCheck` (`cd server && ./mvnw -q compile exec:java`), which also
  writes the coverage report. Integer pack versions; the live version per pack
  is recorded (`content_packs`, `GET /api/v1/content/packs`); a pack older
  than the live one fails the boot; content leaving every pack is retired.
  Packs can ship without a code release from `CONTENT_PACKS_DIR`. Sources for
  generated and hand-written packs live in `content/` (`node content/generate.mjs`).
- **The blueprint as structure** — every printed section has suggested
  minutes and slots (the real paper's spread over modules), validated at boot.
- **组卷** — `PaperAssembler` builds a whole paper or one section slot by
  slot, preferring unseen questions, and reports what the bank cannot fill.
- **Sitting a paper** (`/exam/:id`) — the server's clock (2-minute grace,
  expiry on the next read), autosave of the whole sheet, resume, flags,
  navigator, passages beside their questions with the open blank or sentence
  lit, keyboard answering, hand-in at zero; nothing revealed before the
  hand-in.
- **Scoring and the report** (`/sittings/papers/:id`) — what the grader can
  judge is marked at once (多选 all or nothing; fill-blanks by accepted forms);
  open answers and unmatched fill-blanks are scored by the candidate against
  the reference and its 得分点; then a report by section, time and 考点, with
  every question reviewable and every weak 考点 one tap from practice.
- **Evidence** — attempts (session id = the paper session), mistakes, a
  read-only 模考 record (only its 复盘 note is editable) that feeds the
  estimate when the paper is whole, the plan, mastery and analytics; the time
  sat as a study session; Today's `paper` row.

## What this session did (continuing the earlier M2 work)

System:

- `PackValidator`: two new group rules — a group's questions share the module
  of their primary 考点, and a text that numbers its questions in place
  (`**[n]**` blanks, `**(n)**` sentences) numbers every one once, in order,
  each named by its question's stem.
- `Coverage` counts a text as eligible exactly as the assembler does (every
  question's primary 考点 inside the slot).
- `ContentImporter` rewrites a library paper when its questions' worth or its
  time limit change, not only its keys.
- Exam stage: the open question's blank or sentence is lit in the passage and
  kept in view (scrolling the passage pane only); the word count shows for
  English writing only (not under a Chinese translation).
- Result page: says "时间到" also when the server handed the paper in while the
  tab was closed (not only when the stage handed it in at zero).
- `content/README.md` (the authoring guide the code referenced but did not
  have) and `content/COVERAGE.md` (generated).

Content (original, labelled, numerically checked where computed):

- `sample-math1` v1 — four 12-point 解答题 (an integral equation → ODE, a
  surface integral by Gauss, a power series and its sum, a 2-D density with
  the density of X+Y and the covariance) and the sample library paper. Every
  answer was verified numerically (quadrature, series sums, Monte Carlo).
- `core-math1` v1 → **v2** — the 线代 解答题 rescored 11 → 12 (the current
  paper's slot) with its rubric rescaled to add up.
- `sample-politics` v1 — 8 单选, 12 多选 (answer sets of 2–4 letters), 4 分析题
  (one per module that lacked one) and the sample library paper. Answers are
  the textbooks' own formulations; 时政 items state their material's date
  (2022 二十大报告, 2023 全球文明倡议, 2024 新质生产力 and 二十届三中全会).
- `sample-english1` v1 — a cloze text (20 blanks), three reading texts, a
  七选五 text and a translation text with 5 sentences and 得分点; answer letters
  balanced per section; the sample library paper.
- `cs408-open-co` / `cs408-open-ds` v1 → **v2** — two 解析 whose formulas
  rendered as raw text (`$k + (n-1) + $` — a space before the closing `$`),
  found by the new render test.

Tests added: `PackValidatorTest` (+3: the shipped bank sits every paper whole;
the module rule; the marker rule), `PaperSubjectsTest` (4: 政治多选 all or
nothing and the analysis scored; 数学 fill-blanks by forms or all-or-nothing;
English texts sat whole with their passage; every subject assembles whole),
`contentPacks.spec.ts` (every shipped field through the real markdown + KaTeX
pipeline: no KaTeX error, no stray `$`), `paperFormat.spec.ts` (+1: markers).

## Verification (2026-10-16)

| Check | Result |
| --- | --- |
| Backend suite `./mvnw test` | **267 passed**, 0 failures, 0 errors (260 before this session) |
| Frontend unit `vitest run` | **407 passed** in 34 files (402 before) |
| Type-check `vue-tsc --build` | clean |
| Lint `oxlint .` / `eslint .` | clean (run without `--fix`) |
| Production build `npm run build` | passes |
| Offline content check | 19 packs valid · 716 questions · 7 groups · 3 library papers |
| `node content/generate.mjs --check` | all 15 generated packs match their sources |
| Browser: 408 whole paper (`e2e-408.cjs`) | **27/27** — the check that failed before read a hidden second `.el-drawer`; the visible review drawer was right all along |
| Browser: 政治 · 英语一 · 数学一 (`e2e-subjects.cjs`) | **84/84** |

The browser runs drove Chromium (Playwright 1.61) against the dev servers:
launcher → confirm → exam stage → answers by click and keyboard, flags,
autosave, reload-resume → hand-in → self-scoring → report → review drawer →
the 模考 record; KaTeX on all 22 math questions (no parse errors); the English
passages beside their questions with the lit blank moving as the candidate
moves; one paper at a time (`260006`); the client hand-in at zero (fake clock
fast-forwarded 3 h 1 min); the server hand-in after the grace (`260008` on a
late save, "时间到" on the result); a save within the grace accepted and
stamped with the deadline; abandoning from the stage; phone width (390 px) in
the dark theme; the estimates, analytics, mistake book and plan reading the
new sittings; no console errors.

## The database (`ai_learning`) as it actually is

The original development database was deleted outside these sessions and
recreated empty on 2026-10-16 (migrations V1–V11 applied at 14:52 MySQL time).
**Earlier development data — Phase 6 notes and conversations, M1 records — is
gone and was not restored.** What is there now is the seeded `demo` account
and test data written by verification runs:

| | |
| --- | --- |
| Accounts | `demo` / `Demo123456` only |
| Library | 19 packs live (`core-math1`, `cs408-open-co`, `cs408-open-ds` at v2; the rest v1) · 716 active questions · 7 groups · 3 library papers |
| Paper sessions | 11: 8 completed (408 组卷 2–4; the three sample papers; two 数学选择题 section papers used for the expiry and grace tests), 3 abandoned |
| 模考 records | 8, all written by sessions: 408 25.5 · 23.5 · 23.5; 英语 13; 数学 29.5; 政治 9; 数学选择题 5 · 5 |
| Evidence | 61 attempts (408 42 · 数学 8 · 英语 7 · 政治 4) · 37 active mistakes · 3 study sessions |
| Estimates | 408 24.2 (3 whole papers) · 英语 13 · 数学 29.5 · 政治 9 |

These are test sittings (fast, mostly blank), not study history; delete any of
them on 模考 (deleting a record withdraws it from the estimate; the answer log
keeps its attempts). Two rows were edited directly for the time-up tests: the
clocks of the two 数学选择题 section sessions this session started were shifted
back (their own columns, relative shifts).

**Time zones on this machine.** Windows runs on US Eastern time, so the JVM
writes Eastern local times; MySQL's `NOW()` reports UTC+8. Manual SQL against
time columns must shift values relative to themselves (`DATE_SUB(col, …)`),
never set them from `NOW()`. Start the backend with the same zone every time.

## Open

- **Bank depth (by design for M2).** 政治、英语一 and 数学一 have exactly one
  whole paper of content each, so a second sitting repeats questions (the
  assembler prefers unseen ones, then repeats). Thin 考点 are listed in
  `content/COVERAGE.md`. More content is published through the pipeline.
- **The samples are samples.** Labelled 原创样题 · 非真题 everywhere they appear.
  Replace or extend them as ordinary content changes (retire keys or edit in
  place and bump the version). The 时政 items are tied to their stated dates
  and need refreshing for each exam year.
- **No licensed past papers** are shipped; the library-paper format takes
  `kind: past_paper` with its year when one is licensed.
- **Figures in questions** are not supported yet (M4: object storage or packs
  that embed vetted assets).
- **Writing, translation and politics analysis are scored by the candidate
  only**; AI-assisted scoring beside the candidate's own grade is M3.
- **Fill-blank matching is notation-only** (full-width → half-width,
  whitespace and `$` removed, case, trailing stop); algebraically equal forms
  match only if listed in `accept`; anything else is scored by the candidate.
- **A question edited while a paper is being sat** shows its new text: a
  session snapshots the layout (which questions, their worth), not the
  questions themselves.
- **Unexplained backend crashes.** The dev backend died twice under
  `./mvnw spring-boot:run` (exit `0xC0000005`, an access violation; no JVM
  crash report even with `-XX:ErrorFile`; no Windows error event), both times
  as a new browser session loaded the app shell. It could not be reproduced
  afterwards: about 75 browser sessions and over 6 000 concurrent API requests
  across the packaged jar (default and C1-only JIT) and `spring-boot:run` ran
  clean. JDK 22.0.2 with virtual threads (enabled since Phase 6) is the most
  likely area. If it recurs, capture the forked JVM with
  `-Dspring-boot.run.jvmArguments="-XX:ErrorFile=… -XX:+CreateCoredumpOnCrash"`
  or run the packaged jar.
- **Browsers**: verified in Chromium only (Gecko/WebKit open, as before).
