# Product Domain — 11408 Learning System

The product domain as of the 11408 transformation (2026-09). Binding
engineering rules live in [`architecture.md`](architecture.md) § 11408; the
AI grounding in [`ai-engine.md`](ai-engine.md). The previous domain model (the
general "AI Learning Platform" with free-form subjects) is archived at
[`archive/product-domain-ai-learning-platform.md`](archive/product-domain-ai-learning-platform.md).

## Who it is for

A candidate preparing for China's national postgraduate entrance exam in the
**11408** combination — the most common computer-science track:

| Paper | Code | Full score | Shape of the paper |
| --- | --- | --- | --- |
| 政治 (思想政治理论) | 101 | 100 | 16 single choice · 17 multiple choice · 5 analysis questions |
| 英语一 | 201 | 100 | cloze · reading A/B · translation · two essays |
| 数学一 | 301 | 150 | 10 choice · 6 fill-in · 6 worked solutions |
| 408 (计算机学科专业基础) | 408 | 150 | 40 single choice · 7 comprehensive questions |

The candidate studies for most of a year, alone, against a fixed date. What
they need is not a place to store things; it is an answer, every day, to
*what should I do now, and am I getting closer?*

That question has two levels, and the product answers both:

- **The 考点 level** — what do I know, point by point? Practice, the mistake
  book, spaced repetition and the mastery model (M0).
- **The paper level** — what would I score, and where does my time go? Whole
  papers sat under time, hours measured per paper, and a plan that divides
  each day by the gap between estimate and target (M1, "the exam year") —
  and, since M2, papers sat *inside* the app on the server's clock, whose
  result is the same kind of record.

## The loop

Everything in the product serves one loop, run daily:

```
learn a 考点 ──► practise it ──► diagnose what went wrong ──► redo it on schedule
     ▲                                                              │
     │                                                              ▼
improve ◄── see readiness rise ◄── plan the day ◄── remember (spaced repetition)
```

| Step | Where it happens | What makes it real |
| --- | --- | --- |
| Learn | 考纲 (syllabus map), 考点 pages, notes, materials, AI 讲解 | every artifact anchored to the same syllabus tree |
| Practise | 练习 (practice) | server-drawn sets from the question bank; honest grading |
| Diagnose | 错题本 (mistake book) | wrong answers filed automatically; cause + reflection; AI 诊断 |
| Redo | Today's plan, the mistake book | FSRS-spaced redos; three correct due-day redos resolve a mistake |
| Remember | 记忆卡片 (flashcards) | FSRS-6 review; decks anchored on 考点; cards generated from note selections by AI |
| Plan | 规划 (Plan), 今日 (Today) | the day divided among the papers by phase and by the gap to target; whole papers per week; a server-ranked, capped plan of due work plus one suggested 考点 per paper |
| Sit | 模考 (mock exams & past papers), the exam stage | a paper sat in the app — a library paper, or one assembled from the bank (组卷, whole or one section) — on the server's clock with autosave and resume, nothing revealed before the hand-in, graded at once where the grader can judge and scored by the candidate where it cannot; or a paper sat on paper and recorded after. Either way: the paper estimate, where the points go, the 真题 shelf |
| Measure | the focus timer (sidebar, Today, every 考点 page) | study time recorded per paper as it happens — one tap, server-side, a switch saves the running block |
| Improve | 学习分析 (analytics), the syllabus map, 模考 | score-weighted readiness per paper, weakest 考点, mistake causes; the estimate against the target per paper |

## Domain model

```
The exam (content, versioned with the code — no tables)
  Blueprint 11408
  └─ Paper ×4 ─ Module ─ Chapter ─ 考点 (point, weight 1–3)
     │  every node addressed by a hierarchical code: cs408 › cs408.os › cs408.os.process › cs408.os.process.sync
     └─ printed Section ×n   type, worth, suggested minutes, and slots: how the real paper
                             spreads it over modules ("11 数据结构", "2 组成原理 worth 23", "texts of 5")

The library (content packs: versioned JSON, validated, imported idempotently at boot)
  ContentPack (live version per pack: content_packs)
  ├─ QuestionGroup        a shared text — a reading passage, a cloze — and its ordered questions
  ├─ Question             library questions (user_id NULL), each with its primary 考点 first
  └─ Paper                a library paper: printed sections in order, each question with its worth

Candidate (User)
  ├─ ExamProfile          target 考研年份, confirmed exam date (else estimated), target score per paper
  ├─ Question             own questions (captured from paper)
  │    └─ QuestionPoint   1–6 考点 of one paper
  ├─ QuestionAttempt      the answer log — immutable, the evidence behind every figure
  ├─ PracticeSession      a drawn set (topic / weakness / mistakes / random) and its progress
  ├─ Mistake              one per question: status, cause, reflection, FSRS redo state
  ├─ Paper                the candidate's own papers, assembled from the bank (组卷) — kept, so they can be sat again
  ├─ PaperSession         a paper being sat in the app: the server's deadline, the answer sheet, then the marks
  ├─ PaperSitting         a whole paper (or part) sat under time: 真题 year or mock name, score per section;
  │                       written by a PaperSession (scores read-only) or entered after a paper sat on paper
  ├─ FocusTimer           the study timer running now — at most one; stopping it writes a StudySession
  ├─ FlashcardDeck → Flashcard (FSRS-6)
  ├─ Note (wiki links, backlinks)   ├─ LearningMaterial   ├─ LearningTask
  ├─ StudySession                   ├─ AiConversation → AiMessage
  └─ Preferences
     every artifact above may carry a node_code: the one anchor

Read models (own no tables, derived per request)
  Mastery          per 考点 mastery + level; per aggregate readiness + coverage
  Recommendations  prioritised 考点 (importance × need × freshness, phase-aware)
  Today            plan (a paper in progress or awaiting scores, reviews, due mistakes, tasks, sessions)
                   + focus + progress + exam countdown
  PaperAssembler   组卷: a paper from the bank, slot by slot as the real paper is built (pure)
  Coverage         how deep the bank is per 考点, and how many disjoint whole papers it can assemble
  Analytics        readiness per paper, practice trend, mistake causes, weakest 考点, time shares
  Workspace        the Today ledger: practice in flight, recent notes and conversations, the week
  ScoreEstimate    per paper: recency-weighted whole-paper estimate, section profile, trajectory, 真题 shelf
  Plan             the day split per paper (phase base × gap to target), weekly cadence, phase timeline, exam timetable
```

**The syllabus is the anchor.** It replaced per-user subjects: in an 11408
system the subjects are fixed and global, and they are only the roots of a
deeper structure. A note on all of 操作系统, a deck on 进程同步, a textbook
filed under 408, a question tagged with two 考点 — all are addressed the same
way, so any 考点 page can gather everything the candidate has there.

**Evidence lives in one place.** Mastery, recommendations, readiness, the
AI's diagnosis and the analytics are all computed from the answer log
(`question_attempts`) and the mistake book, per request. Nothing derived is
stored, so no two screens can disagree.

## Modules

| Module | Owns | Surface |
| --- | --- | --- |
| `exam` | exam profile; the syllabus content | 考纲 map and 考点 pages; Settings › 考试 |
| `question` | questions, tags, question groups, the answer log | question drawer on 考点 pages; capture dialog |
| `content` | content packs: loading, validation, versioning, import, coverage | `GET /content/packs`; the offline check (`ContentCheck`) — see `content/README.md` |
| `paper` | papers (library and 组卷), paper sessions | the 在线模考 panel on 模考; the exam stage (`/exam/:id`); scoring and the report (`/sittings/papers/:id`); Today's paper row |
| `practice` | practice sessions | 练习 (launcher, recent sets), the practice stage, the report |
| `mistake` | the mistake book | 错题本 (overview, filters, drawer, capture) |
| `mastery` | — (read model) | meters on the map, Today's focus, recommendations |
| `srs` | — (pure scheduling) | FSRS-6 for cards and mistake redos |
| `flashcard` | decks, cards, reviews | 记忆卡片; the review stage (also mounted on Today) |
| `note` | notes, derived link index | 笔记 (TipTap workspace, AI selection toolbar) |
| `material` | reference materials | the materials shelf on every 考点 page |
| `task`, `calendar` | tasks, study sessions, the focus timer | 日历 (under 规划); the timer in the sidebar, the mobile header, Today and every 考点 page |
| `sitting` | paper sittings | 模考 (paper cards, estimate, trajectory, section profile, 真题 shelf, records) |
| `plan` | — (read model) | 规划 (timeline, the day's split and its reasons, the week, the exam timetable); Today's time band |
| `ai` | conversations and messages | AI 导师 (chat, scoped to a node); AI panels on questions, mistakes and 考点 |
| `workspace`, `analytics` | — (read models) | 今日; 学习分析 |
| `auth`, `preference` | users, tokens, preferences | login, profile, settings |

## Information architecture

Navigation follows the loop. The sidebar lists, in order: **今日 · 考纲 · 练习 ·
模考 · 错题本 · 记忆卡片 · 笔记 · AI 导师 · 规划 · 学习分析**, then 设置 and 个人主页;
日历 is the plan's second view (规划 | 日历), and the study timer sits between
the destinations and the footer.
On compact screens the dock carries the practice loop — **今日 · 考纲 · 练习 ·
错题 · 导师** — and More opens the full list.

| Screen | The question it answers |
| --- | --- |
| 今日 | What should I do now, and how many days are left? |
| 考纲 | Where does each paper stand, and what is worth practising next? |
| 考点 page | What do I have on this point — standing, questions, notes, materials — and can the tutor explain it? |
| 练习 | How do I want to draw today's set? |
| Practice stage | One question at a time: answer, see the verdict, understand it. |
| 错题本 | What is due, where do my mistakes cluster, and why did each one happen? |
| 模考 | What do whole papers yield per paper, where are the points lost, which 真题 are done — and which paper do I sit next? |
| Exam stage | The paper, the clock and the answer sheet — nothing else, until the hand-in. |
| Paper result | What could the grader not judge (I score it), and then: what did this paper yield, section by section and 考点 by 考点? |
| 规划 | How does my day divide among the papers, why, and how is this week going? |
| 学习分析 | How ready am I per paper, and where is the evidence? |
| AI 导师 | Anything else — scoped to a 考点 when that helps. |

## Rules the product keeps

- **Honest grading.** The system grades what it can judge and asks the
  candidate to grade the rest against the reference answer. It never marks
  an answer wrong that it could not judge. On a timed paper a blank scores
  nothing and is not evidence — running out of time is not "不会".
- **The clock is the server's.** A paper's deadline is fixed when it starts;
  closing the tab does not stop it, and a paper found past its time is handed
  in with its last saved answers.
- **Content says what it is.** Every question shows its provenance; original
  samples are labelled 原创样题 · 非真题, and nothing is presented as a past-exam
  question unless it is one and licensed.
- **Honest numbers.** Untested is "—", never 0%. Readiness converted to points
  is labelled a conversion, never a predicted score. The paper estimate comes
  only from whole papers sat in the last 90 days and says so. An estimated
  exam date is labelled "预计".
- **Time is what was recorded.** Study hours are recorded sessions (timer or
  calendar); the plan cannot see unrecorded study and does not pretend to.
  A day *studied* (the streak) is any study activity.
- **Today is a plan, not a dashboard.** The server decides rank, cap and
  whether the day is complete; suggestions travel beside the plan and never
  decide it.
- **Content is never glass.** Question stems, explanations and plans are solid
  surfaces; the Liquid Material budget is unchanged by the transformation.
- **Every string is localized** (zh-CN primary, en-US mirrored key for key).

## Not built yet

A question bank at real scale for 政治、英语一 and 数学一 (they have exactly one
whole paper of content each — original samples that prove the formats; 408 has
five), licensed past papers, figures in questions, retrieval-grounded AI with
citations, AI-assisted scoring of essays, translations and analysis answers,
file upload, registration and password reset. Order and rationale:
[`roadmap.md`](roadmap.md).
