# 11408 Learning System · 11408 学习系统

An enterprise-grade, AI-native learning system for China's postgraduate
entrance examination (考研) — built for the **11408** combination:
**政治 · 英语一 · 数学一 · 408**.

![Java 22](https://img.shields.io/badge/Java-22-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Vue 3.5](https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vuedotjs&logoColor=white)
![TypeScript 6](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![MySQL 8+](https://img.shields.io/badge/MySQL-8%2B-4479A1?logo=mysql&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-blue)

A candidate studies for most of a year, alone, against a fixed date. What they
need is not a place to store things; it is an answer, every day, to *what
should I do now, and am I getting closer?* This system answers it at two
levels, from the candidate's own evidence:

- **The 考点 level** — learn a point, practise it, have every mistake filed and
  redone on a spaced schedule, remember with FSRS, and watch mastery rise.
- **The paper level** — sit whole papers under time, see what each paper
  actually yields against the target and where the points go, measure the
  hours each paper gets, and follow a plan that divides every day by the gap.

Every account is isolated, every number on screen is derived from the
candidate's own answers, sittings and timed hours, and nothing is mocked.

## What it does

| Area | What the candidate gets |
| --- | --- |
| **今日 Today** | The exam countdown; today's hours per paper with one tap to start timing; a server-ranked, completable plan of due cards, due mistake redos, tasks and sessions; one suggested 考点 per paper. |
| **考纲 Syllabus** | The 11408 syllabus as a map — four papers, modules, chapters and 考点 weighted by exam score, each coloured by mastery, any node practisable in one click; a page per 考点 with its questions, notes, materials, an AI explanation and a focus timer. |
| **练习 Practice** | Topic, weak-point, mistake-redo and random sets; honest grading (what the system cannot judge, the candidate grades against the reference answer); 解析 and an AI walk-through per question; a per-考点 report. |
| **模考 Mock exams** | Papers sat **in the app** — library papers or 组卷 from the bank, whole or one section — on the server's clock: autosave, resume after a closed tab, flags, a section navigator, passages beside their questions, hand-in at zero; nothing revealed before the hand-in, then instant grading of what can be judged and self-scoring of the rest against the reference and its 得分点. Papers sat on paper are recorded by section. Either way: a recency-weighted estimate per paper (whole papers only, never a "prediction"), the trajectory against the target, where the points are lost, and the 真题 shelf. |
| **错题本 Mistake book** | Wrong answers filed automatically, redos spaced by FSRS, resolved after three correct due-day redos; causes, reflections, AI diagnosis, capture of mistakes made on paper. |
| **记忆卡片 Flashcards** | FSRS-6 spaced repetition; decks anchored on 考点; AI-generated cards from notes. |
| **笔记 Notes** | A markdown knowledge workspace with wiki links and backlinks, anchored on the syllabus, with an AI selection toolbar. |
| **AI 导师 Tutor** | Streaming chat (DeepSeek) that knows the exam date, the targets, what whole papers have yielded, today's plan, the weak points and the notes; replies render markdown and LaTeX. |
| **规划 Plan** | The phases to the exam, the study day divided among the papers with every share explained, this week's hours and whole papers against the plan, the two-day exam timetable — and the calendar. |
| **学习分析 Analytics** | Score-weighted readiness per paper, practice trend, mistake causes, weakest 考点, time by paper, a study heatmap, AI insights. |
| **专注 Study timer** | One timer per candidate, on every screen; switching papers saves the running block; a sitting-length block offers to record its score. |

Domain model: [`docs/product-domain.md`](docs/product-domain.md) ·
engineering constitution: [`docs/architecture.md`](docs/architecture.md) ·
AI engine: [`docs/ai-engine.md`](docs/ai-engine.md) ·
roadmap: [`docs/roadmap.md`](docs/roadmap.md).

## Repository layout

```
11408-learning-system/
├── server/     # Backend  · Spring Boot 4 · Java 22 · MyBatis-Plus · MySQL · Flyway
│   └── src/main/resources/exam/   # the syllabus and question packs (versioned content)
├── content/    # Pack sources: hand-written packs, computed generators, the authoring guide
├── web/        # Frontend · Vue 3 · TypeScript · Vite · Pinia · Element Plus · KaTeX
├── docs/       # Architecture, domain, roadmap, design system
├── database/   # Schema conventions (migrations live in the server module)
├── docker/     # Containerization (not yet built — see docker/README.md)
└── README.md
```

## Prerequisites

- JDK 22
- Node.js ≥ 22.18
- MySQL 8+ (local database `ai_learning` — the schema name predates the rename
  and is kept so existing data carries over)

## Quick start

Backend (<http://localhost:8080>):

```bash
cd server
# dev profile is the default; override credentials via DB_USERNAME / DB_PASSWORD
./mvnw spring-boot:run
```

On start the server validates the syllabus content, applies migrations and
imports the question packs (idempotent — unchanged questions are skipped).

Frontend (<http://localhost:5173>, proxies `/api` to the backend):

```bash
cd web
npm install
npm run dev
```

Create the dev database once:

```sql
CREATE DATABASE IF NOT EXISTS ai_learning
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

On the dev profile a seed account is created automatically:
**`demo` / `Demo123456`**.

## Configuration

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database connection |
| `JWT_SECRET` | HMAC-SHA256 signing key, **at least 32 bytes** (required in prod) |
| `DEEPSEEK_API_KEY` | Enables the AI tutor; without it AI actions report "not configured" and everything else works |
| `CONTENT_PACKS_DIR` | Optional directory of question packs read at startup beside the bundled ones (per pack the higher version wins) — new content without a code release |

Authentication is stateless JWT (access + rotating refresh token); the
security architecture is documented in
[`docs/architecture.md`](docs/architecture.md) § Identity & security.

## Verification

| Check | Command |
| --- | --- |
| Backend tests | `cd server && ./mvnw clean test` (H2 in-memory, no MySQL needed) |
| Frontend lint | `cd web && npm run lint` |
| Frontend type-check + build | `cd web && npm run build` |
| Frontend unit tests | `cd web && npm run test:unit` |
| Content packs (offline) | `cd server && ./mvnw -q compile exec:java` — validates every pack as the boot does and prints coverage |
| Generated packs current | `node content/generate.mjs --check` |

## Content

The syllabus (`server/src/main/resources/exam/syllabus/*.json`) and the
question packs (`…/exam/questions/<paper>/*.json`) are content, versioned with
the code and validated at boot — a malformed 考点 code, an answer that names a
missing option, a reading text whose blanks are misnumbered, or a library paper
that does not match the printed paper stops the server rather than reaching a
candidate. Each pack carries a version; the live versions are recorded, an
older pack never replaces a newer one, and content that leaves every pack is
retired, never deleted. The authoring guide is
[`content/README.md`](content/README.md); the bank's depth is in
[`content/COVERAGE.md`](content/COVERAGE.md).

The bundled questions are original items written for this project, labelled
with their provenance: the core bank, computed families (408), and — for
政治、英语一 and 数学一 — **original samples (原创样题 · 非真题)** that give each of
those papers exactly one whole paper's worth of content, enough to sit and
score every printed section in the app. No past-exam question is shipped;
past papers sat on paper are recorded by the candidate.

## Repository

The project was renamed from **AI Learning Platform** to **11408 Learning
System** with its full Git history; the code, artifacts
(`11408-learning-system-server`, `11408-learning-system-web`) and
documentation carry the new name. The hosted repositories are renamed by the
account owner; once they are, point the local remotes at the new addresses:

```bash
git remote set-url origin git@github.com:YukaKaori/11408-learning-system.git
git remote set-url gitee https://gitee.com/yuka-kaori/11408-learning-system.git
```

GitHub redirects the old URL after a rename, so existing clones keep working
until their remote is updated.

Suggested repository description (GitHub / Gitee):

> 11408 学习系统 — An enterprise-grade, AI-native learning system for China's
> postgraduate entrance exam (政治 · 英语一 · 数学一 · 408). Spring Boot 4 + Vue 3.

Suggested topics: `kaoyan` `postgraduate-entrance-exam` `11408` `spaced-repetition`
`fsrs` `learning-system` `spring-boot` `vue3` `typescript` `ai-tutor`

## Engineering standards

Architecture decisions, module conventions and error-code ranges are in
[`docs/architecture.md`](docs/architecture.md) — read it before adding a
module. Database changes are **migration-only** via Flyway
(`server/src/main/resources/db/migration`) — see
[`database/README.md`](database/README.md).
