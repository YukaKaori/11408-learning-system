# Product Roadmap — Phase 15 → Version 1.0

> **Status:** Planning only. Canonical as of 2026-07-22. Supersedes and replaces
> `docs/archive/roadmap-v1.md` and `docs/archive/roadmap-phase15-35.md` entirely.
> **Binding context:** `docs/architecture.md` (engineering constitution),
> `docs/design-system.md`, and `.claude/skills/liquid-material/SKILL.md`
> (the Liquid Material system is a core architectural constraint of every
> phase below, equal in rank to the constitution — not UI polish).
> **Author lens:** product architect of a commercial AI-native learning platform.

---

## Product Strategy (one page)

**What this is.** A premium operating system for learning. Not a course
platform — we sell no content. Not an ed-admin system. The user brings what
they are learning (lecture notes, textbooks, papers, exam syllabi); the
platform turns it into understanding that lasts, through one continuously
running loop: **Capture → Understand → Retain → Plan → Reflect.**

**Who the users are.** Serious self-directed learners who already pay for
good tools — the Notion / Raycast / Readwise audience applied to learning:
university and graduate students in demanding programs, professionals
preparing for certifications and career switches, and lifelong learners who
treat learning as a practice. Bilingual from day one (zh-CN primary, en-US
mirror) — the exam-and-certification market in China plus the global
prosumer market, one codebase.

**What makes it different.**
1. **The loop lives in one place.** Today this person stitches Anki + Notion +
   ChatGPT + a calendar, and the seams leak: notes never become reviews, chat
   answers evaporate, plans don't react to reality. Here every capture can
   become a review, every AI answer is grounded in *their* corpus and lands as
   an artifact, and the plan reschedules itself around what they actually did.
2. **AI-native, not AI-bolted-on.** The AI reads the user's own notes,
   documents, and review history, answers with citations into that corpus, and
   refuses to hallucinate past it. Every AI interaction ends in something
   durable — a note, a deck, a plan — never just a transcript.
3. **Product quality as moat.** Speed, taste, and the Optical Glass identity.
   The bar is Linear/Raycast/Apple: features are fewer and finished. In a
   market of cluttered ed-tech, feeling like a precision instrument *is*
   positioning.

**Why users pay.** The free tier proves the loop; the paid tier removes its
ceilings: metered grounded AI (real per-token cost, priced honestly as
credits), unlimited documents and decks, and the premium experience. The
value equation is concrete — hours of prep time saved per week and retention
that compounds — not "premium features" for their own sake.

**Why users stay.** Data gravity plus rhythm. The corpus (notes, highlights,
decks, review history, AI memory) compounds in value and cannot be exported
into an equivalent experience; the daily rhythm (due reviews, focus
sessions, streaks, weekly reflection) gives the product a reason to be
opened every day; and the AI gets measurably better the longer you stay,
because it knows more of you.

**Why AI matters.** AI collapses the distance between *"I don't understand
this"* and *"this is now scheduled to become permanent knowledge."* That
conversion — confusion in, scheduled retention out — is the product. Without
AI it's a note app plus a flashcard app; with grounded AI it's a tutor that
read everything you ever captured.

---

## Operating principles for every phase

1. **Product value first.** No phase ships because it is technically
   interesting. Each phase names the user who needs it *daily* and the loop
   stage it strengthens.
2. **Infrastructure is built just-in-time, inside the phase that needs it,
   behind the constitution's interfaces.** No standalone infrastructure
   phases. (This is the largest break from the previous roadmap, which spent
   Phases 15–17 on storage/vectors/jobs with near-zero user-visible value.)
3. **Fewer, finished.** Anything that cannot be excellent by 1.0 is postponed,
   not shrunk into mediocrity. The postponement list at the end is a feature
   of this plan, not an appendix.
4. **The Optical Glass Design System evolves only with function.** Glass marks
   premium, focus, interaction, intelligence, transitions, and elevated
   surfaces — never decoration. Several phases below correctly declare **"no
   visual changes."** The displacement-filter budget (currently 2: login card
   + dock) is tracked phase by phase.
5. **Every phase is independently shippable** and ends verified through the
   `verify` skill (real API + Playwright), type-check/lint/unit/build green,
   locale parity intact, reduced-motion and fallback paths confirmed.

### What changed versus the previous roadmap, and why

- **Infrastructure-first → loop-first.** The old Arc I (storage, vector store,
  job queue) is dissolved into the feature phases that actually need each
  capability. A startup that spends its first month on plumbing has a better
  repo and no more product.
- **21 phases → 14.** Cut from 1.0: teams/orgs/RBAC, community publishing,
  marketplace/plugins, the agent runtime, the browser extension, and the
  knowledge-graph visualization. Reasons are given in the 2.0 section — each
  is a real product reason, not a caveat.
- **Onboarding gets a dedicated phase.** The old roadmap never mentioned the
  first hour of a new user's life. For a commercial product, time-to-value is
  a feature with a number on it.
- **Gamification is re-scoped to taste.** XP, levels, and badge carnivals are
  out. Streaks, momentum, and an AI-written weekly reflection are in — the
  Apple/Linear register, not Duolingo's.
- **Registration moves from Phase 28 to Phase 23.** Real strangers using the
  product is the only source of truth a product decision has; it cannot wait
  until after community and billing were planned.

---

# Era I — The Loop (Phases 15–17)

*The daily learning loop becomes real: things you capture become things you
retain, and the product tells you what to do today. After this era, one user
with one subject has a reason to open the app every single day.*

---

## Phase 15 — The Memory Engine (real spaced repetition)

**Goal —** Flashcards stop being a flip-toy and become a scheduling engine:
a due queue, graded review sessions, and honest retention statistics, on the
`due_at` / `interval_days` / `ease` columns reserved since Phase 5.

**Why now —** It is the single strongest daily-return mechanic in learning
software, the schema has been waiting for it for nine phases, and it requires
zero new infrastructure. Every later era (Today, Momentum, Premium) assumes
reviews exist. Fastest possible ratio of daily user value to architectural risk.

**User value —** Open the app → "23 cards due" → a focused session of
again/hard/good/easy grading → tomorrow the queue is right. Retention stops
being a promise and becomes a visible curve.

**Product impact —** Retention (the habit anchor) and learning efficiency
(the actual science). This is the loop's "Retain" stage; the product's
core promise becomes checkable.

**Technical architecture —** An FSRS-style scheduler as a pure, deterministic
service inside the existing `flashcard` package (no new package); review
history in a new `review_logs` table (V6); "today" computed against an
explicit client-timezone contract. No queue, no jobs, no cache — reviews are
synchronous request/response.

**Backend work —** Scheduler service + exhaustive unit tests on interval/ease
transitions; endpoints: fetch due queue, grade card, session summary; V6
migration; Workspace/Analytics read models extended to real review data
(due counts, retention, daily reviews).

**Frontend work —** A full-screen review session view under
`features/flashcards/` (card, four grade actions, keyboard-first: space/1-4,
progress, session summary); due badges on decks; Workspace due-count becomes
live; Analytics gains the retention panel.

**AI work —** Prompt-quality pass on `FLASHCARD_*` generation so AI-produced
cards are *reviewable* (atomic, one fact per card, answer-side brevity) —
generation that feeds a real scheduler must produce schedulable cards.

**Design work —** **No new glass.** The review session is a solid, quiet,
keyboard-first stage built from existing tokens/components — content is the
work, and per the skill, glass never sits on the work. Discipline here *is*
the design contribution. Filter budget: unchanged (2).

**Risks —** Scheduling-math correctness (mitigate: deterministic unit tests
against published FSRS vectors); timezone "today" bugs; existing decks
migrating into scheduling state gracefully (all cards due → overwhelming
first queue; cap initial daily load).

**Verification —** Unit tests on the scheduler; Playwright: full session
(grade all four ways), due queue matches DB, summary correct; analytics
renders real curve; `verify` skill end-to-end; reduced-motion sweep.

**Completion criteria —** Grading reschedules per algorithm; the queue
respects `due_at` and user timezone; Workspace/Analytics show real review
data; a new user's first deck produces a sane first week of queues.

---

## Phase 16 — Notes 2.0 (a real editor)

**Goal —** Replace the read-only note preview with a real editor: rich
blocks stored as markdown, `[[wiki-links]]` with backlinks, autosave, slash
commands, and the existing AI actions working inline.

**Why now —** Notes is the loop's "Capture" stage and the substrate for
everything Intelligence-era: grounded AI, search, reader highlights, and
"note → cards." A read-only notes module caps the entire product. It is also
the highest-UI-risk phase in the plan — early is when you can afford to
learn from it.

**User value —** Actually write here. Link ideas with `[[...]]`, see what
references what, never press save, and invoke AI on a selection instead of
in a separate room.

**Product impact —** Learning efficiency and ecosystem gravity: notes are
the data that makes every AI feature personal. No corpus, no moat.

**Technical architecture —** TipTap/ProseMirror as the one sanctioned heavy
frontend dependency of this era (justified against the constitution's
no-speculative-dependency rule: focus-trap-grade complexity we must not
hand-roll). **Markdown remains the storage format** — the editor is a view,
not a lock-in. Wiki-link parsing server-side into a `note_links` table (V7)
maintained on save; backlinks are a simple indexed query, not a graph engine.

**Backend work —** V7 `note_links` migration; link extraction on note write;
backlinks endpoint; autosave-friendly update endpoint (idempotent, partial);
`OwnershipGuard` on all of it.

**Frontend work —** The editor experience in `features/notes/` (blocks,
markdown shortcuts, slash menu, `[[` autocomplete, backlinks panel,
autosave indicator); existing `NOTE_*` AI toolbar actions become
selection-scoped inline actions.

**AI work —** Rewire existing note AI actions (summarize, explain, improve,
extract cards) to operate on the current selection/block with results
applied in place (insert below / replace, user-confirmed).

**Design work —** First in-app glass beyond login/dock, and it is earned:
the **floating selection toolbar** is a small glass surface (an allowed
"floating toolbar" — it hovers above the work, marking interaction +
intelligence). Built on GlassSurface, low density, `.glass-material`
controls. The editor canvas itself is emphatically solid. Filter budget:
2 → **3** (the toolbar mounts only while a selection exists).

**Risks —** Editor scope explosion (mitigation: a written v1 cut-list — no
tables, no embeds beyond images, no collaboration); markdown round-trip
fidelity; autosave races (last-write-wins + dirty-state guard on route
leave); bundle size (code-split the editor route).

**Verification —** Round-trip property tests markdown→editor→markdown;
Playwright: write, link, backlink navigation, autosave survives reload,
inline AI action edits in place; locale parity; filter budget assertion (≤3).

**Completion criteria —** A user writes and structures a real note, links
it, sees backlinks, never loses work, and uses AI without leaving the
editor. The old preview UI is gone.

---

## Phase 17 — Today (the daily plan)

> **Status: COMPLETE — 2026-08-01.** Shipped in `4316b16` → `ab27b25` (Steps 1–6)
> plus the 17.2 Liquid Material consolidation. Release gate green: 118/118
> backend tests, 184/184 frontend tests, `vue-tsc` + `eslint` + `oxlint` +
> `vite build` clean, and the four states, both themes, both locales, 375/768/1280,
> keyboard order, a browser accessibility inspection and reduced motion all
> verified live. Displacement budget unchanged at **3**.
> **Handoff:** `docs/phase17-handoff.md` — read §5 before starting Phase 18.
> One completion criterion is only partially met: a plain sign-in still passes
> through the Phase 8/9 `/welcome` bridge before reaching Today (handoff §5.1,
> a one-line change, awaiting a product decision).

**Goal —** Replace the Workspace dashboard's passive statistics with
**Today**: one prioritized answer to "what should I do right now?" — due
reviews, scheduled tasks, calendar sessions, and daily-goal progress,
composed into a single actionable view.

**Why now —** Phases 15–16 created real daily obligations (a due queue) and
real material (notes). Without a "what now" surface, the loop exists but has
no front door. Cheap phase, enormous behavioral leverage: this becomes the
default landing view and the thing users check every morning.

**User value —** Open the app, see today's plan in five seconds, click
straight into a review session or task. Finish, and Today visibly shrinks.
The product now has a beginning, middle, and end to a day.

**Product impact —** Retention (the daily front door) and learning
efficiency (prioritization: reviews before new material, deadlines
surfaced). Also the anchor surface Momentum (P22) will decorate.

**Technical architecture —** A pure read-model extension of the existing
`workspace` façade (still owns zero tables): a `today` endpoint composing
flashcard-due (P15), `task`, `calendar`, and preferences' daily goal.
Column-projected SQL, per-user, computed per request — same contract as
every Phase 7 read model.

**Backend work —** `GET /v1/workspace/today` on the façade; prioritization
rules (overdue > due today > scheduled > suggested) kept server-side so
every client agrees; no migration.

**Frontend work —** `features/workspace/` home becomes Today: a single
ordered plan with direct actions (start review session, open task, start
session), goal-minutes ring, and the existing stats demoted to a secondary
row. Empty-day and all-done states designed deliberately (the "day
complete" moment matters).

**AI work —** None. (The AI-suggested "what to study next" belongs to P18+
when suggestions can be grounded; guessing before that would be noise.)

**Design work —** **No visual changes to the design language.** Today is
composition and hierarchy with existing tokens, `StatTile`, and view-state
patterns. The "day complete" state earns exactly one one-shot reveal
(existing motion tokens) — a settle, not a celebration. Filter budget:
unchanged (3).

**Risks —** Prioritization feeling wrong (keep rules few, visible, and
stable); Today duplicating Calendar/Tasks instead of composing them (it
links into them, never re-implements); overloading the view (hard cap on
items, "more" collapses).

**Verification —** Read-model unit tests on ordering rules; Playwright:
morning state → complete items → day-complete state; empty-account state;
`verify` skill; both themes/locales.

**Completion criteria —** Today is the post-login landing view; every item
on it is directly actionable; finishing everything produces the completed
state; a brand-new account sees a sane, non-fabricated Today.

---

# Era II — Intelligence (Phases 18–20)

*The AI stops being a chatbot with your app's name on it and becomes a tutor
that has read everything you have. Grounding, citations, memory, a command
palette, and documents. This era is why users will pay.*

---

## Phase 18 — Grounded AI (retrieval + memory)

**Goal —** The tutor answers from the user's own corpus — notes, materials,
flashcards — with visible citations, refuses to invent sources, shows
"related notes" in context, and keeps a small, user-editable memory of the
learner (goals, level, preferences) that persists across conversations.

**Why now —** This is the credibility phase: ungrounded AI in a learning
product is a liability, not a feature. P16 just made the corpus real.
Retrieval is built *here*, inside the phase that ships its value —
not as a standalone infra phase (the previous roadmap's central mistake).

**User value —** Ask "explain this the way my notes define it" and watch the
answer cite *your* note. The AI knows you're preparing for an exam in June
and pitches accordingly. Open a note and see the three notes that belong
next to it.

**Product impact —** AI intelligence (the headline), learning efficiency,
and the paid tier's backbone — grounded answers are the thing worth
metering. Every later AI feature (palette ask, document chat, weekly
reflection) reuses this exact plumbing.

**Technical architecture —** `infrastructure/retrieval/RetrievalService`
(embed / upsert / query) per the constitution's external-service rule;
`EmbeddingProvider` beside `AiProvider` (DeepSeek first, provider-agnostic).
Vectors in MySQL (V8) — per-user corpora are thousands of chunks, not
millions; brute-force cosine within one user's partition is honest
engineering at this scale, and the interface is the migration path, not a
premature vector DB. Chunk-and-embed synchronously on note/material write.
`ai_memory` table (V8): explicit, small, user-visible facts — never a
hidden profile. `LearningContextService` gains two layers: retrieved chunks
(with source refs threaded to the frontend for citation chips) and memory.

**Backend work —** Retrieval infrastructure + embedding provider + V8;
write-path hooks in `note`/`material`/`flashcard` services (delete/soft-
delete kept consistent with the index); `related` endpoint; memory CRUD;
context pipeline extension; a bounded backfill endpoint for pre-P18 content.

**Frontend work —** Citation chips on tutor replies (click → source);
"Related notes" panel on note view; AI Memory section in Settings (view,
edit, delete each fact — trust surface, treated with care); "grounded"
indicator in tutor.

**AI work —** The core of the phase: chunking strategy, retrieval-augmented
prompt with strict grounding instructions ("cite or say you don't have it"),
memory extraction proposed by AI but **confirmed by the user** before it
persists, token-budget management across context + retrieval + memory +
history.

**Design work —** Intelligence becomes visible as *light inside glass*, per
the skill: the tutor's thinking state is a slow internal sheen on a low-
density glass status strip (variable-driven, compositor-only); answer
arrival is a one-shot brightening. Citations are solid chips — sources are
content, and content is never glass. Filter budget: unchanged (3) — the
sheen is CSS on an existing surface, not a new displacement filter.
> *Correction (2026-09-16, `docs/phase18-plan.md` §14 and
> `docs/liquid-material-global-reassessment.md` §11):* the tutor has **no
> existing glass surface**, so a glass status strip would be a fourth
> instance, not "CSS on an existing surface". P18 therefore keeps the tutor's
> thinking state **solid** and lands the AI light vocabulary on the one glass
> AI surface that exists, `NoteSelectionToolbar`. Budget stays 3.

**Risks —** Highest-risk phase of the plan. Retrieval quality (curated
golden-set queries in tests from day one); embedding cost (batch, dedup,
hash-skip unchanged chunks); index/soft-delete consistency; memory
privacy (user-visible, user-deletable, never inferred silently into
storage); latency (retrieval budget ~200ms before streaming starts).

**Verification —** Golden-set retrieval tests (known corpus → expected
sources); grounding test: question answerable only from a planted note must
cite it, question outside the corpus must produce the "not in your
materials" behavior; memory round-trip across sessions; ownership tests on
every new endpoint; latency budget measured.

**Completion criteria —** Tutor answers cite real user content; ungrounded
questions degrade honestly; related-notes surfaces genuinely related notes;
the user can read and delete everything the AI remembers about them;
provider and store are swappable behind interfaces.

---

## Phase 19 — The Command Palette (⌘K: navigate, search, ask)

**Goal —** One keystroke from anywhere: fuzzy navigation to any entity or
action, hybrid (lexical + semantic) search across the whole corpus, and an
"ask" mode that streams a cited answer — three modes, one surface.

**Why now —** P18 built retrieval; the palette is its everyday face. This is
the connective tissue every reference product (Linear, Raycast, Superhuman)
is organized around, and the product's speed-and-taste positioning made
tangible. It is also the Optical Glass Design System's flagship moment.

**User value —** Stop navigating. `⌘K` → three letters → you're there.
`⌘K` → a question → a cited answer without leaving the current view. The
product starts feeling like an instrument.

**Product impact —** Delight and efficiency for every user on every day;
the single most demo-able surface for growth; the pattern-setter for
keyboard-first interaction across the app.

**Technical architecture —** A `search` façade endpoint (hybrid: lexical
over titles/content via MySQL fulltext + semantic via P18's
`RetrievalService`, merged and ranked, per-user scoped); ask-mode reuses the
P18 grounded pipeline over `SseRelay`. Frontend: a global `AppCommandBar`
mounted in `AppLayout`, a keyboard-shortcut composable, and a client-side
action registry (navigation + commands like "start review session").

**Backend work —** Hybrid search endpoint with ranked, typed results
(subject/note/deck/material/task/conversation); fulltext indexes where
missing (V9 if needed); ask endpoint is P18 reuse, no new AI surface.

**Frontend work —** The palette: instant open, query-as-you-type, grouped
results, keyboard-complete (arrows/enter/esc/tab between modes), recent
items, action registry; full a11y (focus trap, aria-activedescendant,
screen-reader announcements) — EP offers no palette primitive, so this is a
custom surface and must meet the a11y bar the wrappers normally provide.

**AI work —** Ask-mode UX: streaming cited answers in the palette with
"open as conversation" to continue in the tutor; query-intent detection
stays trivial (explicit mode tabs, no magic misclassification).

**Design work —** **The glass milestone of the roadmap.** The palette is the
skill's canonical heavy slab: GlassSurface at high optical depth and edge
energy, smoked density over a dimmed solid scrim, one-shot settle on open
(no bounce, mass not spring), results as solid rows *inside* the material.
Light behavior follows the scene's single source. This phase sets the
written pattern every future overlay copies. Filter budget: 3 → **4**
(palette mounts only while open; budget documented in the design system).

**Risks —** Palette a11y/focus correctness (the one custom overlay in the
app — test hardest here); hybrid ranking feeling dumb (lexical-first for
short queries, semantic weight grows with query length; log-and-tune);
shortcut collisions (single registry, documented).

**Verification —** Playwright: open from every route, navigate, search
across entity types, run an action, stream an ask answer with citations,
full keyboard round-trip, focus restored on close; axe/a11y pass; filter
budget ≤4 asserted; reduced-motion (instant appear, no settle).

**Completion criteria —** ⌘K works from anywhere; all entity types
searchable with correct scoping; ask-mode cites; 100% keyboard operable;
the glass treatment passes the skill's implementation checklist verbatim.

---

## Phase 20 — The Reader (documents, highlights, document chat)

**Goal —** Documents become first-class learning material: upload PDFs,
read them in a focused reader, highlight passages that become notes and
flashcards, and chat with the document through page-cited answers.

**Why now —** "Explain this PDF" is the highest-intent AI-learning workflow
that exists, and everything it needs is now real: retrieval (P18) for
grounding, the memory engine (P15) for highlight→card, notes (P16) for
highlight→note. Storage infrastructure is built *here*, inside the phase
that needs it, activating the constitution's reserved `StorageService` seam.

**User value —** Drop the lecture PDF into the subject. Read it without
chrome. Highlight the theorem; it's a flashcard due Thursday. Ask "what does
chapter 3 assume I know?" and get an answer with page numbers.

**Product impact —** Monetization (document count + document AI are natural
paid-tier ceilings), learning efficiency (the read→retain pipeline
collapses to one gesture), and corpus gravity (documents are heavy data
users won't re-upload elsewhere).

**Technical architecture —** `infrastructure/storage/StorageService` +
`LocalStorageProvider` (dev) / S3-compatible provider (prod, MinIO/OSS)
behind config, per the constitution; streamed upload/download with
ownership-guarded access; PDFBox text extraction feeding P18's ingestion;
highlights as anchored ranges (`V10`: page + quote + position tolerance);
document-scoped RAG chat reusing the P18 pipeline with page metadata on
chunks.

**Backend work —** Storage infra + provider config + V9/V10 migrations
(storage metadata on `learning_materials`, `highlights` table); upload/
download/extract endpoints on `material`; highlight CRUD; highlight→note
and highlight→flashcard composition endpoints (reuse existing creation
paths); doc-scoped retrieval filter.

**Frontend work —** Upload (drag-drop, progress, type/size errors) on
Subject detail; the reader view (`features/reader/`): paged PDF rendering,
highlight creation/list, chat side-panel, "send to notes/cards" actions.
Reader is route-level code-split (PDF rendering is the second sanctioned
heavy dependency; justified the same way the editor was).

**AI work —** Page-aware chunking; doc-scoped grounding ("answer from this
document; cite pages"); highlight→card generation tuned for atomicity
(P15's standard); extraction-quality fallback messaging for scanned/OCR-less
PDFs (OCR itself is explicitly 2.0).

**Design work —** The reader realizes the skill's *reveal, don't hide*
statement: entering it, app chrome recedes and dims; the page is bright and
solid. The highlight action menu is a small glass **context surface** (an
allowed component; interaction + intelligence). The chat panel reuses P18's
thinking-sheen. Filter budget: 4 → **5** (highlight menu, mounted only
during selection; palette and it are never open simultaneously in practice,
but the budget assumes worst case and stays documented).

**Risks —** PDF rendering weight and fidelity (code-split; test CJK-heavy
PDFs early — zh-CN users are primary); highlight anchor drift (store quote +
context, re-anchor tolerantly, degrade to page-level); large-file limits
(explicit caps, clear errors); storage cost (per-plan quotas land with P26,
caps exist from day one).

**Verification —** Upload→read→highlight→card→due-queue round-trip in
Playwright; page-cited chat answer verified against a known PDF; cross-user
download 403; provider swap config-only; extraction failure degrades with
honest messaging; CJK PDF renders.

**Completion criteria —** A user uploads a real textbook chapter, reads it,
turns a highlight into a scheduled flashcard in two clicks, and gets
page-cited answers about it. Storage is a config-swappable interface with
per-user isolation enforced.

---

# Era III — Rhythm (Phases 21–22)

*The loop gains a heartbeat. The product owns the study session itself and
develops a respectful sense of time — momentum you can feel, reminders you
don't resent. Retention becomes a designed experience, not a hope.*

---

## Phase 21 — Focus (own the session)

**Goal —** A focus mode that owns the act of studying: a session timer
(pomodoro-style but not dogmatic), distraction-free layouts for review and
reading, and automatic session logging into the existing calendar/analytics.

**Why now —** Reviews (P15), Today (P17), and the Reader (P20) created
things worth focusing *on*. Sessions are the data spine of Momentum (P22)
and Analytics; owning them is cheap (the `study_session` module exists) and
the differentiated part — the focus experience itself — is design, which is
this product's strength.

**User value —** One click on Today starts a 25-minute focus block; chrome
falls away; the timer is quiet; finishing logs itself. The streak and
analytics fill without ceremony.

**Product impact —** Retention (sessions are the habit's body) and delight
(the focus experience is a signature moment). Sessions become the honest
denominator for every Momentum and Analytics number.

**Technical architecture —** Frontend-led phase. A `stores/focus.ts` timer
(wall-clock-anchored so tab sleep can't lie), a focus-mode layout variant
in `AppLayout`, auto-creation of `StudySession` on completion via the
existing calendar module. Backend: at most a session-source tag. No new
tables, no jobs.

**Backend work —** Minimal — accept a `source` (focus/manual) on session
creation if not already representable; idempotency guard against
double-logging.

**Frontend work —** Focus session UI (start from Today/deck/reader; timer;
pause/abandon/complete; completion summary), the focus layout (chrome
recession), wall-clock correctness across tab sleep, and integration points
on Today ("start focus block" as a first-class action).

**AI work —** None. Focus is the one place the AI should be silent.

**Design work —** The skill's Focus Mode statement, realized: surrounding
chrome recedes into **dim, low-density glass** while the work stays bright
and solid — the strongest reveal-not-hide moment in the product. Enter/exit
are one-shot transitions with mass (no bounce); the timer is typographic,
not decorative. No new glass primitives; the dock's existing material dims
via variables. Filter budget: unchanged (5).

**Risks —** Timer accuracy across sleep/refresh (anchor to timestamps, not
intervals); double-logged sessions (idempotency key per focus run); scope
creep toward a habit tracker (it is a timer + a layout, full stop).

**Verification —** Session logs exactly once (including after tab sleep and
reload mid-session); appears in calendar/analytics/Today; Playwright focus
enter→complete→summary; reduced-motion enters instantly; both themes.

**Completion criteria —** A focus block started from Today runs, survives a
tab suspension, logs one session on completion, and that session is visible
in calendar and analytics. The focus layout passes the glass skill
checklist.

---

## Phase 22 — Momentum (streaks, reflection, and a respectful pulse)

**Goal —** A tasteful momentum system: streaks that survive real life,
gentle in-app + email reminders, and an AI-written **weekly reflection** —
what you studied, what's slipping, what next week should look like. The
async backbone (scheduler, in-DB jobs, notifications, email) is built here,
because this is the phase whose features demand it.

**Why now —** There is now real daily behavior to sustain (reviews, focus,
Today). Retention mechanics added before this point would have gamified an
empty room; added later, they'd miss the launch. Identity 2.0 (P23) needs
the email channel this phase creates — the dependency order is deliberate.

**User value —** A streak that acknowledges effort without tyranny (streak
freezes exist; missing a day of a 60-day streak is designed for); one
reminder at the right time instead of five at wrong ones; a Sunday
reflection that reads like a coach who actually watched your week.

**Product impact —** Retention, directly — this is the difference between a
tool and a practice. The weekly reflection is also a quiet AI-value
showcase: grounded, personal, recurring, and cheap to produce.

**Technical architecture —** Scheduler (`@Scheduled` + ShedLock) and an
in-DB job table per the constitution's reserved seams (MQ stays reserved —
measured need only); `NotificationService` interface with in-app channel
(table + endpoints + bell) and `EmailService` seam (console/dev provider
now, real provider in P23 when strangers exist); streak computation as a
timezone-correct rollover job; V11 migration (notifications, jobs, streaks).
Weekly reflection = a scheduled job composing P18's grounded pipeline over
the week's session/review/note data.

**Backend work —** Jobs infra + scheduler; notification module (entity
through controller); streak service + rollover job; reflection generation
job + storage; per-user notification preferences (quiet hours, channels,
off-switches that actually turn things off).

**Frontend work —** Notification bell + panel in `AppLayout`; streak and
weekly-goal presence on Today (quiet — a number and a ring, not a firework);
the Weekly Reflection view; notification preferences in Settings.

**AI work —** The reflection prompt: grounded in real week data (sessions,
review accuracy, notes touched), honest about gaps ("you skipped algorithms
this week"), forward-looking, short. Tone calibration is product work, not
an afterthought — this text is the product's voice at its most personal.

**Design work —** The notification panel is a small glass **popover**
(allowed: context surface, transient elevation). Streak/goal on Today use
existing tokens — no celebration carnival; completing a week earns the same
single one-shot settle P17 established. Explicitly rejected: badges, XP,
levels, confetti. Filter budget: 5 → **6** (bell popover; transient).

**Risks —** Notification fatigue is a product-killer (defaults conservative,
every channel owner-switchable, quiet hours honored); timezone streak edge
cases (the P15 timezone contract is reused, tested at DST boundaries);
job semantics (at-least-once + idempotent handlers; dead-letter visibility);
reflection tone (bland = ignored, preachy = resented; iterate on a golden
set of real week-shapes).

**Verification —** Two-instance scheduler test fires once; notification
read/dismiss round-trip; streak rollover unit tests across timezones/DST;
reflection generated from seeded week data and rendered; email lands via
dev provider; all switches verified off = silence.

**Completion criteria —** A week of real usage produces: an accurate streak,
at most the notifications the user asked for, and a Sunday reflection that
cites that week's actual activity. Jobs are observable and idempotent.

---

# Era IV — Opening (Phases 23–25)

*The doors open. Strangers can join, the first hour is engineered rather
than hoped for, and the product meets people on the devices where daily
habits actually live.*

---

## Phase 23 — Doors Open (self-service identity)

**Goal —** Public self-service accounts: registration, email verification,
password reset, and OAuth (Google/GitHub) — on the auth extension points
reserved since Phase 2, with production-grade abuse resistance.

**Why now —** Every product judgment so far has been made with zero
strangers. Real users are the only source of truth, and none of P24–P26
(onboarding, mobile, billing) mean anything without them. P22 built the
email channel this phase requires. This was Phase 28 in the old roadmap —
five phases too late.

**User value —** Sign up in under a minute, with Google or email; recover an
account without a support human; trust that the account is handled
seriously.

**Product impact —** Everything downstream: real usage data, real feedback,
real retention curves, and the top of the funnel that the landing page
(Phases 12–14) was built to feed.

**Technical architecture —** Registration/verification/reset flows in the
`auth` package (existing error-code block; account-state machine already
scaffolded); verification/reset tokens single-use + expiring, stored hashed
(the refresh-token discipline applied again); OAuth as additional issuance
paths behind `TokenService` (the reserved seam — no parallel auth stack);
`app.security.password-policy` activated; rate limiting on all
public-facing auth endpoints; V12 migration.

**Backend work —** The flows + V12; email templates through P22's
`EmailService` (real provider configured for prod); enumeration-proof
responses everywhere (the Phase 2 `INVALID_CREDENTIALS` discipline extended
to registration/reset); audit-relevant events logged.

**Frontend work —** Registration, verify, reset request/complete views;
OAuth buttons + callback handling; router guard updates; error states that
are helpful without leaking account existence.

**AI work —** None.

**Design work —** The smoked-glass auth family grows: register and reset
join the login card as facets of the same slab — same material, same
density, same light. This is the glass system doing exactly what it was
built for (a designed material scales to new members without new design).
One route-level auth surface at a time: filter budget unchanged (**6**).

**Risks —** Account enumeration via timing/response differences (uniform
responses + timing discipline); token leakage (hashed at rest, single-use,
short TTL); OAuth callback/state validation; email deliverability (SPF/
DKIM checklist as a launch item); bot signups (rate limits now, escalate
only if attacked).

**Verification —** Full register→verify→login→reset cycle; reuse/expired
token rejection; OAuth round-trip incl. account-linking to an existing
email; enumeration probes return uniform responses; rate limits trip;
`./mvnw test` security suite green.

**Completion criteria —** A stranger self-registers, verifies, resets, and
signs in via OAuth — all without a human, all enumeration-proof, all on the
reserved seams (zero parallel auth code paths).

---

## Phase 24 — The First Hour (onboarding & time-to-value)

**Goal —** Engineer the new user's first session so that within minutes
they experience the loop once: a subject exists, something is captured, the
AI has done one visibly grounded thing, and a first review is scheduled for
tomorrow — a reason to come back before they've left.

**Why now —** P23 opened the doors; an un-designed first hour would burn
every stranger who walks in. The old roadmap's largest omission — it never
mentioned onboarding. For a commercial product, time-to-first-value is a
number, and this phase exists to make that number small.

**User value —** No empty-room paralysis. The product asks what you're
learning, accepts what you already have (paste notes, upload a PDF), shows
the AI doing something real with *your* material immediately, and tomorrow
there's a queue waiting.

**Product impact —** Activation — the multiplier on all acquisition ever
done. Also the funnel seam: the Phase 13/14 landing page's CTA finally
lands somewhere designed.

**Technical architecture —** Almost entirely frontend + content. A light
`onboarding_state` on preferences (V13, a column not a system); a guided
first-run flow composing existing capabilities (subject create → paste/
upload → AI generate cards → schedule); an empty-state audit turning every
module's empty state into a next-action; instrumentation of the
first-session funnel (client events to a minimal endpoint — measurement,
not a product-analytics platform).

**Backend work —** Onboarding-state persistence; funnel event endpoint
(append-only, minimal); optional starter-template content (one exemplary
subject with notes + deck, cloneable server-side so it's real data, not
mocks).

**Frontend work —** The guided flow (skippable, resumable, never modal-
jailed); empty-state redesign across all features; "first review scheduled"
moment made visible; landing→signup→onboarding route seam polished.

**AI work —** The first AI moment is chosen for reliability: generate five
cards from pasted text (the highest-success-rate, fastest-to-wow grounded
action). Tuned so the first impression cannot be a refusal or a hallucination.

**Design work —** The onboarding flow borrows the product page's one-shot
reveal choreography (Phases 13–14) — the marketing story and the first-run
story become one narrative. **No new glass surfaces**; the moment uses the
materials that exist. Filter budget: unchanged (6).

**Risks —** Onboarding that lectures instead of doing (every step produces a
real artifact in the user's account); skip-hostility (skippable at every
step, resumable from Today); measuring nothing (the funnel events are the
phase's acceptance test, not an option).

**Verification —** Playwright: complete flow from fresh signup → subject +
content + cards + scheduled review exist as real DB rows; skip at each step
leaves a sane state; funnel events recorded; both locales (zh-CN first
impression matters most); empty-state sweep across all modules.

**Completion criteria —** A fresh account reaches "first review scheduled"
in one guided session; every empty state in the product proposes a next
action; the first-session funnel is measurable end to end.

---

## Phase 25 — Everywhere (responsive, PWA, offline reviews)

**Goal —** The daily loop works beautifully on a phone: a full responsive
pass, an installable PWA, the review queue available offline with safe
sync, and push reminders through the P22 notification system.

**Why now —** Reviews and streaks are commute-and-queue behaviors; a
desktop-only daily habit is a contradiction. After P24, real users exist to
retain — meeting them on mobile is the highest-leverage retention
investment left before monetization.

**User value —** Install from the browser, review cards on the subway with
no signal, get one gentle push when today's queue is ready. The streak
becomes survivable in real life.

**Product impact —** Retention and habit depth (mobile review is the
stickiest loop surface); reach without the cost of native apps (which stay
2.0).

**Technical architecture —** PWA via the Vite plugin (manifest + service
worker; the build-tooling dependency is sanctioned here); offline scope is
**deliberately narrow**: the review queue only — due cards prefetch to
IndexedDB, grades queue locally and replay on reconnect (grading is
last-write-wins per card and P15's scheduler is deterministic, so conflicts
resolve honestly); web push through `NotificationService`'s new channel.
Notes/reader stay online-only in 1.0 — offline editing is a CRDT-shaped
problem and is explicitly refused here.

**Backend work —** Batch grade-sync endpoint (idempotent, timestamped); push
subscription storage + channel in the notification module (V14); no other
surface changes.

**Frontend work —** The bulk of the phase: responsive audit across all
`features/*` (the review session, Today, and the palette are the priority
surfaces); PWA manifest/SW/update flow ("new version" toast, never a silent
break); offline review flow + sync status; push opt-in done respectfully
(asked in context, after value is proven, never on first load).

**AI work —** None. (AI surfaces remain online; they degrade with honest
offline messaging.)

**Design work —** The dock adapts to touch (the FluidGlass-derived bar was
born for this shape); glass **fallback paths become primary paths** on iOS
Safari — the frosted fallback must be verified legible and premium, since
for mobile-Safari users the fallback *is* the brand. Touch = no pointer:
light-tracking correctly rests at zero by construction (the skill's design
vindicated). Reduced filter count on small viewports if measurement demands
it. Filter budget: ≤6, measured on mobile hardware.

**Risks —** Offline sync corruption (narrow scope + idempotent replay +
deterministic scheduler = bounded blast radius); SW update traps (versioned,
kill-switch-able); iOS push/PWA platform quirks (test on real devices;
degrade to in-app + email gracefully); responsive regressions on desktop
(Playwright matrix grows both ways).

**Verification —** Lighthouse PWA pass; airplane-mode review session →
reconnect → grades reconciled exactly once; push round-trip on Android +
iOS-supported paths; Playwright viewport matrix (375/768/1440) across the
core loop; Safari fallback visual review.

**Completion criteria —** A user installs the PWA, completes an offline
review session that syncs cleanly, and receives a due-queue push. The core
loop is excellent at 375px, and the glass fallback reads premium on iOS.

---

# Era V — The Business (Phases 26–27)

*The product earns money honestly and can be operated by its makers. Nothing
new for users to learn — the value built over five eras gets a price, and
the company gets eyes.*

---

## Phase 26 — Premium (billing, credits, and the paid tier)

**Goal —** Free and Pro tiers with real payments (Stripe behind a
`PaymentProvider` interface), a per-user AI credit ledger with transparent
metering, plan-based limits enforced at API and UI, and upgrade surfaces
that persuade without nagging.

**Why now —** Willingness-to-pay is finally testable against real value:
grounded AI (P18–20) has marginal cost worth metering, and real users
(P23–25) exist to charge. Billing built earlier would have priced a
guess; later, and launch has no business model. AI metering
instrumentation lands here *before* launch scale, protecting margin from
day one of revenue.

**User value —** Honest pricing: the free tier runs the whole loop at real
(bounded) usefulness; Pro removes ceilings (AI credits, documents, decks).
Usage is always visible — no surprise walls, no dark patterns; limits warn
before they bind.

**Product impact —** Revenue, and the discipline revenue imposes: the
Free/Pro line is the clearest statement of what the product believes its
value is. Metering also gives the company its first real unit economics.

**Technical architecture —** New `billing` package (constitution error-code
range); Stripe as source of truth, webhook reconciliation idempotent
(jobs infra from P22); `PaymentProvider` interface per the external-service
rule; token-level metering instrumented inside the `ai` package at the
provider seam (every AI call debits the ledger); plan-gate as a single
policy service consulted by API guards and exposed to the frontend as
capability flags (never string-matched plan names in UI code); V15
migration (subscriptions, ledger, usage).

**Backend work —** Billing module + Stripe integration + webhooks; credit
ledger + metering hooks; plan policy service + enforcement on gated
endpoints (documents, AI, deck counts); usage endpoints; invoice/receipt
surfacing via Stripe.

**Frontend work —** Pricing page (public, joins the landing family);
Settings → Plan & Usage (current plan, credit meter, invoices, cancel —
cancellation as respectful as signup); upgrade moments at natural ceilings
(out of credits mid-conversation, document cap on upload) with graceful
degradation, never data hostage-taking.

**AI work —** Accurate token accounting across streaming (count at the
provider seam, reconcile on stream end); per-feature cost attribution so
pricing iterations have data; free-tier model/routing decisions if
economics demand (behind `AiProvider`, invisible to product code).

**Design work —** Premium surfaces are the skill's stated *highest
expression* of the material — the upgrade dialog and Pro plan card carry
maximum optical depth, fresnel, and dispersion; this is where the glass
brand and the commercial brand are the same thing. And the skill's
counterweight rules: pricing numbers, terms, and the cancel button are
maximally legible solid content. No glass on invoices. Filter budget: 6 →
**7** (upgrade dialog, transient).

**Risks —** Payment correctness (Stripe as truth; idempotent webhooks;
never store cards; replay-tested); metering drift vs. provider billing
(daily reconciliation job); gate inconsistency between UI and API (single
policy service, contract-tested); dark-pattern drift under revenue pressure
(the "no hostage" rule is written here so it can be pointed at later).

**Verification —** Test-mode subscribe/upgrade/cancel/expire lifecycle;
webhook replay storm → no double effects; AI call → ledger debit → usage UI
agree with provider dashboard; every gated feature blocks correctly at API
(not just UI) with the designed message; cancellation leaves data intact
and exportable.

**Completion criteria —** A real user can pay, see exactly what they're
using, hit a limit gracefully, and cancel cleanly. Every AI request is
metered. The margin per Pro user is a known number.

---

## Phase 27 — The Operator (admin console & product analytics)

**Goal —** A minimal, admin-gated operator console: user/account
management, product health (activation funnel, retention cohorts, DAU),
AI cost and margin monitoring, feature flags for safe rollout, and an
audit log on sensitive actions.

**Why now —** A paid product with strangers on it cannot be operated
through SQL by hand. This is deliberately *after* billing (revenue is the
most important dashboard) and *before* launch (flags and visibility are
launch-safety equipment). Scope is a cockpit, not a back-office suite —
single `admin` role via the reserved `roles`/`permissions` tables; full
RBAC/organizations remain 2.0.

**User value —** Indirect but real: support that can actually help
(account lookup, credit adjustment with audit trail), safer releases
(flags), a faster product because its makers can finally see it.

**Product impact —** Operability and decision quality — P24's funnel, P22's
notification health, and P26's unit economics become visible in one place.
Scalability of the *company*, not just the software.

**Technical architecture —** `admin` package gated by the first real role
(activating `UserPrincipal` authorities + `@PreAuthorize` — enabled since
Phase 2, used now); read-model façades over existing tables for metrics
(owns no domain tables, same discipline as workspace/analytics); feature-
flag service (DB-backed, cached in-process, consulted server-side);
audit-log activation (the last reserved seam this roadmap uses) for admin
actions and sensitive user events; V16 migration (roles seed, flags, audit).

**Backend work —** Role wiring + admin guards; metrics read models
(activation, retention cohorts, AI cost/margin, revenue reconciliation);
flag service + evaluation; audit interceptor for admin mutations; support
actions (account state, credit adjustment) — each audited.

**Frontend work —** `features/admin/` behind route guards (the reserved
`roles` meta finally used): overview dashboard, user lookup/detail, flags
UI, audit browser. Charts follow the dataviz discipline already in the
repo (custom SVG, token palette).

**AI work —** None user-facing. (Cost dashboards consume P26's metering.)

**Design work —** **No visual changes to the design language.** Per the
skill: admin is solid data surfaces; glass appears only in what the app
already provides (dialogs). The console proves the design system scales to
density — tables, cohort grids — without new invention. Filter budget:
unchanged (7).

**Risks —** Admin authz blast radius (the guard pattern tested hardest;
admin endpoints get their own authz test suite); PII discipline in admin
views (minimum necessary, audited access); flag sprawl (flags expire —
each carries an owner and a removal date); metric lies (every dashboard
number reconciled against source-of-truth queries in tests).

**Verification —** Non-admin 403s on every admin route/endpoint
(exhaustive); support action → audit entry round-trip; flag flip changes
server behavior without deploy; metrics match hand-run SQL on seeded data;
PII review of every admin view.

**Completion criteria —** An operator can answer "how is the product doing,
who is this user, what did we change" in one console; every sensitive
action is audited; a risky feature can ship dark behind a flag.

---

# Era VI — Launch (Phase 28)

---

## Phase 28 — Version 1.0 (hardening, trust, and the gate)

**Goal —** No new features. The existing product becomes production-grade:
observability, performance under load, a full security review, operational
runbooks, CI/CD, and the launch checklist — then the 1.0 gate is either
passed or it isn't.

**Why now —** Everything before this built value; 1.0 is trust. A hardening
phase at the end is not where quality *starts* (every phase verified as it
shipped) — it is where the cross-cutting guarantees are proven at once,
under load, adversarially.

**User value —** Speed, reliability, and safety as felt qualities: the app
is fast at the 95th percentile, never loses a review grade, and deserves
the payment details it holds.

**Product impact —** The license to market. Launch marketing (the Phase
13/14 landing story) can finally be turned on against a product that keeps
its promises.

**Technical architecture —** Observability (Actuator + metrics/tracing +
error tracking) with SLOs on the core loop (Today load, review grade,
palette open→result, AI first-token); Redis introduced **only where
measurement demands** (read-model caching, rate limiting) per the reserved-
seam rule; load testing at target concurrency; security sweep (authz walk
of every endpoint against ownership/role matrices, dependency audit,
secrets audit, abuse-path review of P23/P26 surfaces); Docker/CI/CD
hardening on the existing `docker/` base; backup/restore rehearsed.

**Backend work —** Instrumentation, cache where proven, rate limiters,
authz test matrix completion, backup/restore + migration-rollback drills.

**Frontend work —** Performance pass (bundle budgets per route, LCP on
landing/login — the long-standing lotus-asset item closes here), full
accessibility audit (palette, editor, reader deepest), i18n completeness
sweep, error/empty-state final audit.

**AI work —** Provider-failure drills (DeepSeek outage → honest degradation,
queued retries where sane); cost-anomaly alarms wired to P27 dashboards;
prompt-injection review of every surface that feeds user/document content
into prompts (reader chat especially).

**Design work —** The **glass system audit** as a launch gate: the skill's
implementation checklist run against every glass surface shipped since
P16; filter budget verified (≤7, every filter accounted for and
transient-mounted where specified); fallback matrix (Safari/Firefox/mobile)
visually reviewed; reduced-motion sweep of every one-shot; a final optical-
hierarchy review — one scene, one light, everywhere. No new surfaces.

**Risks —** Scope discipline (the phase's only enemy is "one more
feature" — the 2.0 list below is the pressure valve); late deep defects
(mitigated by per-phase verification all along; this phase confirms, not
discovers); cache-invalidation correctness where Redis lands.

**Verification —** Load test meets SLOs; security review signed off with
findings closed; chaos drill (kill AI provider, kill DB replica, replay
webhooks) degrades per design; full `verify`-skill regression of every
era's completion criteria; launch checklist 100%.

**Completion criteria — Version 1.0 is declared** when the definition below
is true and this phase's gates are green.

---

## Version 1.0 — Definition

**1.0 exists when a stranger can, without ever meeting us:** discover the
product through the landing experience → sign up and verify in a minute →
be guided to their first scheduled review within one session → run the
daily loop (Today, reviews, focus, notes, documents) on desktop and phone,
online and offline-for-reviews → ask their own corpus questions and get
cited answers → feel the product's pulse for a month (streaks, one
respectful reminder channel, a weekly AI reflection) → pay for Pro, watch
their usage transparently, and cancel cleanly if they choose — while we
watch the whole system breathe from one console, behind SLOs, flags, and
audits, with every glass surface passing the design-system checklist and
every fallback reading premium.

Concretely, all fourteen phases' completion criteria hold simultaneously
under production load. That sentence-length journey *is* the launch test
script.

**Explicitly NOT in 1.0 (by design, not omission):**

| Postponed to 2.0 | Why postponed |
| --- | --- |
| Teams, organizations, full RBAC, seat billing | Highest-risk authz retrofit in the codebase; B2B sales motion is a different company muscle. Earn it with single-player revenue first. The reserved seams keep it a 2.0 project, not a rewrite. |
| Community publishing, comments, follows | Network features on a small user base are empty rooms plus moderation cost. Data gravity, not social graph, is 1.0's retention bet. |
| Marketplace, plugins, sponsor placements | An ecosystem needs an economy; an economy needs a population. Also the highest taste-risk surfaces — rushed, they cheapen the brand the glass system built. |
| Agent runtime (multi-step autonomous AI) | The bounded AI compositions in 1.0 (generate kit, weekly reflection) deliver the value without open-ended cost/safety surface. Agents earn their keep when metering data proves demand. |
| Knowledge-graph visualization | The connective *value* ships in 1.0 (wiki-links, backlinks, related-notes, semantic search). The visualization is spectacular but unproven as a daily behavior; it needs corpus density that only live users create. When it comes, the skill already knows how it should look. |
| Browser extension / clipper | A second deployable + store-review pipeline for a capture path the reader and paste flows cover adequately at this scale. |
| Real-time collaboration (CRDT), shared editing | Single-player excellence first; CRDT complexity contaminates the editor architecture if attempted casually. |
| Native mobile/desktop apps | The PWA covers the habit loop; native is a cost multiplier justified only by proven mobile engagement numbers. |
| Voice/video AI, OCR for scanned documents | Real user value, real cost, not loop-critical. OCR is the first candidate once document-chat usage data exists. |
| Enterprise SSO/SCIM, audit exports | Follows teams, follows B2B — a 2.0 era of its own. |

**The 2.0 question will not be "what's next on the list."** It will be asked
against live data: what do paying users do daily, where does the funnel
leak, what does AI cost per retained learner. This roadmap ends by making
that question answerable.

---

## Sequencing rationale (one paragraph)

The loop comes first (15–17) because a learning product that cannot make
yesterday's capture into today's review has no reason to be opened
tomorrow — and it comes cheap, on seams reserved since Phase 5.
Intelligence (18–20) follows because grounding needs a corpus to ground in,
and it is the era that justifies the price. Rhythm (21–22) waits until
there is real behavior to sustain — gamifying an empty product is hollow —
and deliberately builds the async/email backbone one phase before identity
needs it. Opening (23–25) puts strangers, their first hour, and their
phones ahead of monetization, because charging unactivated users is how
products die politely. The business era (26–27) prices proven value and
gives the company eyes, and launch (28) converts accumulated quality into
public trust. Infrastructure never leads: storage arrives inside the
reader, retrieval inside grounded AI, jobs inside momentum — every
capability is born with its first user already waiting.
