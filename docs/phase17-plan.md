# Phase 17 Plan — Today: the daily learning cockpit

**Status:** PROPOSED architecture (2026-07-27). Awaiting approval. Once approved
this document is the **implementation contract** — architecture is not to be
redesigned and scope is not to be expanded; decisions are revisited only if a
correctness issue is discovered.

**Binding context:** `docs/roadmap.md` (Phase 17) · `docs/architecture.md` ·
`docs/design-system.md` · `.claude/skills/liquid-material/SKILL.md` ·
`docs/phase16-handoff.md` (what P17 inherits).

Phase 15 (Memory Engine) and Phase 16 (Notes 2.0) are complete, committed and
untouched by this phase.

---

## 1. Product goal (one line)

Replace the Workspace dashboard's passive statistics with **Today** — one
prioritized, actionable, *completable* answer to **"what should I do right
now?"** — and make it the post-login landing view.

### The distinction that governs every decision in this phase

| A dashboard | Today |
| --- | --- |
| Reports state | Issues a plan |
| A grid of widgets | One ordered list |
| Constant — looks the same all day | **Shrinks** as work is done |
| Never ends | **Ends.** The day has a terminal state |
| Optimises for completeness | Optimises for *rank* |
| Read-only tiles | Every row has a verb |

If a change makes Today more complete but less ordered, it loses.

---

## 2. Data sources (what exists, and what may enter the plan)

Today owns **zero tables** and composes what the previous ten phases built.

| Source | Owner | What it offers | Time contract? |
| --- | --- | --- | --- |
| **Reviews** | `flashcard.ReviewService` (P15) — `dueCount(userId, zone)`, `queue(...)`, `summary(...)`, `review_logs` | The FSRS-scheduled due queue and the day's grading truth | **Yes** — `due_at`, decided by the algorithm |
| **Tasks** | `task` — `learning_tasks` (title, status, priority, `due_at`, `subject_id`) | User-authored commitments | **Yes** — `due_at`, nullable |
| **Calendar** | `calendar` — `study_sessions` (`starts_at`/`ends_at`, subject, title) | Time-boxed plans for the day | **Yes** — `starts_at` |
| **Preferences** | `preference` — `daily_goal_minutes` (default 60) | The goal ring's denominator | No |
| **Analytics** | `analytics` (zero-table read model) — `activity(userId, days)` (minutes + reviews per day), `streakDays`, retention | Today's minutes, streak, week shape | No |
| **Notes** | `note` + `note_links` (P16) | The corpus | **No** — a note has no due date |
| **Subjects** | `subject` | Continue-learning ranking by last activity | No |
| **AI conversations** | `ai` | Recent chats | No |

### The rule this table produces

**Only the three sources with a time contract may enter the plan.** Reviews,
tasks and sessions carry a due/start instant that *someone* — the scheduler or
the user — committed to. Notes, subjects and conversations do not; putting them
in the plan would mean inventing an obligation the user never made. They stay
**context**, demoted below the plan.

### Two inherited facts that matter here

- `X-Client-Timezone` (`common/ClientZone`, P15) is already sent by every
  frontend request and already used by the review endpoints. Today's whole
  notion of "today" is bucketed by it.
- **Correctness fix in scope:** `WorkspaceService.todaySessions()` currently
  buckets on `LocalDate.now()` (server zone) while `dueCards` buckets on the
  client zone — the same response can disagree with itself across a midnight
  boundary. Today's composition uses the client zone throughout, and the
  existing `summary` path is fixed to match.

---

## 3. Information architecture

Three bands, in descending priority. The first two are the phase; the third is
inherited content, demoted.

```
┌─ THE LINE ────────────────────────────────────────────────┐
│ Good morning, Yuka.        ●  42 / 60 min        🔥 12     │
│ 23 cards due · 2 tasks · 1 session at 14:00                │
├─ THE PLAN ────────────────────────────────────────────────┤
│ ⚠  Finish problem set 4          overdue 2d   [ Open   ]  │
│ ◆  23 cards due                  now          [ Review ]  │
│ ▣  Linear Algebra                14:00–15:30  [ Start  ]  │
│ ○  Read chapter 7                today        [ Done  ]   │
│    2 more scheduled today →                               │
├─ THE LEDGER (secondary, quiet) ───────────────────────────┤
│ Continue learning · Recent notes · Recent chats · Week    │
└───────────────────────────────────────────────────────────┘
```

1. **The Line** — greeting, the day in one sentence, the goal ring, the streak
   as a number. Renders before data (the greeting needs none).
2. **The Plan** — the view. One ordered list; each row is one commitment with
   one primary verb. Server-ordered, hard-capped, overflow collapses to a single
   link into the owning module.
3. **The Ledger** — the existing dashboard content (continue-learning, recent
   notes, recent conversations, week-activity chart), moved below the fold and
   visually quieted. Nothing of value is deleted; it stops being the headline.

### Prioritization rules (server-side, few, visible, stable)

Rank = **(tier, sortAt, kind, id)**. Three tiers only:

| Tier | Contains | `sortAt` |
| --- | --- | --- |
| **0 · overdue** | Tasks whose `dueAt` < start of today | `dueAt` |
| **1 · now** | The review aggregate when `dueCount > 0`; a session currently running (`startsAt ≤ now < endsAt`) | `now` / `startsAt` |
| **2 · scheduled** | Sessions starting later today; tasks due today | `startsAt` / `dueAt` |

Tie-break within a tier: `review` → `session` → `task`, then id ascending.

A fourth tier — **suggested** — is *defined and deliberately left empty*. It is
where P18's grounded "what to study next" lands. Same discipline as P16's
reserved rail slots: architected now, filled when it can be honest.

**Deliberate exclusions from the plan** (they keep Today from becoming a task
manager): tasks with no due date; tasks due after today; high-priority tasks
with no due date; sessions that already ended; every note, subject and
conversation.

### The review aggregate is one row, not N

`dueCount` cards collapse into a single plan item whose action opens the review
session. This is the one place Today aggregates, and it is required — 23 rows
would be a queue, not a plan.

### The four states (this is the honesty guard)

| State | Condition | What it says |
| --- | --- | --- |
| **planned** | plan is non-empty | The normal day |
| **complete** | plan empty **and** the user did something today (reviews graded, tasks closed, or minutes studied) | "Day complete" — earns the one-shot settle |
| **clear** | plan empty, nothing done today, but the account has content | "Nothing due today" — a resting state, **not** a celebration |
| **empty** | plan empty and the account has no subjects, tasks or cards at all | One honest next action (create a subject) |

Collapsing `clear` into `complete` would congratulate a user who did nothing —
exactly the fabrication this phase exists to avoid. Collapsing `empty` into
`clear` would show a brand-new account a finished day.

---

## 4. Scope lock (binding)

### In scope (v1)

**Backend**
- `GET /api/v1/workspace/today` on the existing façade — composed read model,
  client-zone bucketed, computed per request, **no migration, no new table, no
  new error code** (`workspace` 170000 stays reserved and unused).
- Ordering, tiering, capping and state derivation live **server-side** so every
  client agrees.
- The `todaySessions` client-zone correctness fix on the existing `summary`.

**Frontend**
- `features/workspace/` home becomes Today; route `workspace` → `today`
  (`/workspace` redirects, so existing links and bookmarks survive).
- The Line, the Plan, the Ledger; all four states; loading skeleton matching the
  plan's shape; error + retry (the `useAsync` view-state pattern).
- **Direct actions on every row** — start the review session, complete a task
  inline, open a task, open a session — each ending in a reload so Today
  visibly shrinks.
- The **day-complete moment**: one one-shot settle on existing motion tokens.
- Deletion of the "AI Suggestions" panel (see §6).
- zh-CN / en-US parity, both themes, reduced motion, responsive tiers.

### Out of scope (do not build this phase)

| Deferred | To |
| --- | --- |
| AI-suggested "what to study next", grounded prioritisation | **P18** (the `suggested` tier stays empty) |
| Focus timer, "start focus block", chrome recession | **P21** (Today's session row *opens* a session; it owns no timer) |
| Streak mechanics, streak freezes, weekly reflection, notifications, reminders | **P22** |
| Command-palette jump-to-anything | **P19** |
| Push, offline, PWA | **P25** |
| A guided first-run flow | **P24** (the `empty` state offers one next action, it is not onboarding) |
| Drag-reorder, "plan my day" editing, recurring tasks, task creation beyond the existing quick-add | Not planned — Today composes, it never re-implements Tasks or Calendar |
| Any new table, job, scheduler, cache or queue | Not needed |
| Any redesign of another module | — |

### Carry-over decision — P16 §4.1 (needs your call)

`docs/phase16-handoff.md` recommends the **external-image markdown round-trip**
fix (silent data loss: `![alt](url)` is dropped on save) as the first task of
Phase 17. It is declared P16 v1 scope that did not ship, ~40 lines mirroring
`WikiLinkNode`, with round-trip tests. **Recommendation: include it as Step 1**,
before any Today code, so no real note content is lost while this phase runs.
It is listed separately below and can be dropped from the plan on request.

---

## 5. Backend design

`GET /api/v1/workspace/today` · header `X-Client-Timezone` · `ApiResponse<TodayResponse>`

```java
record TodayResponse(
        String date,            // ISO local date in the caller's zone
        TodayState state,       // planned | complete | clear | empty
        Progress progress,
        List<PlanItem> plan,    // server-ordered, capped
        int remainingCount) {   // actionable items suppressed by the cap

    record Progress(
            int studiedMinutes, int goalMinutes,   // preferences
            int reviewsCompleted,                  // ReviewService.summary().reviewedToday
            int tasksCompleted,                    // completed_at inside today's client-zone window
            int sessionsCompleted,                 // today's sessions already ended
            int streakDays) { }                    // AnalyticsService.streakDays

    record PlanItem(
            String id,                       // "review" | "task:<id>" | "session:<id>"
            String kind,                     // review | task | session
            String tier,                     // overdue | now | scheduled
            long sortAt,                     // the instant the ordering used (epoch ms)
            ReviewFocus review,              // nullable
            TaskResponse task,               // nullable — owning module's DTO, reused
            StudySessionResponse session) { }// nullable — owning module's DTO, reused

    record ReviewFocus(int dueCards, int newCards, int total) { }
}
```

**Façade discipline.** Section DTOs are reused from their owning modules exactly
as `WorkspaceSummaryResponse` does — Today must not redefine the wire shape of a
task or a session. `ReviewFocus` is the one new shape, because the review
aggregate has no existing DTO (it is not `ReviewQueueResponse`, which carries
card payloads Today must never fetch).

**One source of truth.** `ReviewFocus.total` is `ReviewService.dueCount(userId,
zone)` — by construction equal to `queue(...).total()`, so the plan row, the
review session and the existing due tile can never disagree (the P15 guarantee,
extended, not re-derived).

**Cap.** `PLAN_LIMIT = 8`; anything beyond it is counted into `remainingCount`
and rendered as a single "N more scheduled today →" row linking into Calendar.
A hard server cap prevents Today from growing into a backlog.

**Relationship to `/workspace/summary`.** `summary` is **unchanged** (beyond the
timezone fix) and keeps its Phase 7 contract. Today's view fires both requests in
parallel: the plan band renders as soon as `/today` resolves and never waits on
the ledger. The small duplicate computation (goal, streak) is an accepted cost of
keeping the two contracts independent; if it is ever measured as slow, the fix is
to drop the duplicated block from one endpoint, not to introduce a cache.

**Ownership.** Every query is `user_id`-scoped; Today performs no id lookups from
client input, so no `OwnershipGuard` call is added. All mutations invoked from
Today go through the existing, already-guarded endpoints.

---

## 6. UI structure

```
features/workspace/
  TodayView.vue            the route — composes the three bands, owns the states
  today/PlanList.vue       the ordered list + the overflow row
  today/PlanRow.vue        one commitment: icon, title, when, one primary verb
  today/DayComplete.vue    the terminal state (one one-shot settle)
  today/LedgerBand.vue     the demoted secondary content, moved from WorkspaceView
```

- **Reuse, not re-implementation.** The review row mounts the existing
  `ReviewSessionView` (props `deckId`/`deckName`, `close` emit) — a legitimate
  second consumer — so the session ends *on Today* and the plan visibly shrinks.
  Task rows reuse `TaskFormDialog`; session rows reuse `SessionFormDialog`.
- **No new shared component** unless a second real consumer exists (the
  constitution's ≥2-consumer rule). The goal ring stays inline in `TodayView`
  until something else needs it.
- **Naming.** The route/nav becomes `today` (`nav.today`); the feature folder
  stays `features/workspace/` to match the backend `workspace` façade package.
- **Accessibility.** The plan is a real `<ul>`/`<li>` list of native buttons;
  state changes announce via `role="status"`; the day-complete reveal is
  decorative and never gates content.

### Design work — glass decision

**No new glass. Displacement-filter budget stays 3, asserted by the existing
`glassBudget.spec.ts` allow-list, which must stay green as a P17 gate.**

Justification against the skill:

- Today is **the work** — a dense, read-and-act surface. The skill is explicit:
  glass marks elevated, transient or premium layers, *never the work itself*.
  Dense working surfaces stay solid.
- The plan rows are content, and content is never glass.
- The phase therefore composes with existing tokens, `StatTile`, `AppCard`,
  and the view-state pattern. Discipline is the design contribution, exactly as
  it was for P15's review stage.
- **One net removal:** the "AI Suggestions" panel is deleted. Its honest
  rule-based nudges move into the plan itself.

  *Rationale corrected 2026-07-29 (`docs/phase17-material-audit.md` §5.2).* When
  this plan was written the panel was an `AppCard variant="glass"`, and half the
  justification was that it was decorative glass without function. **That ground
  is spent:** Phase 17.2 shipped after this plan and removed the `glass` variant
  entirely — the panel is `AppCard variant="flat"` today, a plain solid surface.

  **The removal is therefore not a glass migration and not a material change of
  any kind. It is a product-honesty decision.** Before Phase 18 there is no
  grounded recommendation capability, so a surface badged with a sparkles icon
  and headed "AI Suggestions" implies an intelligence that does not exist yet —
  and an *empty* one implies it most strongly of all, since it advertises a
  faculty that has nothing to say. The rules behind it are honest; the framing
  is not. The rules survive inside the plan, where they are commitments rather
  than machine advice; the panel goes.

  Scope is unchanged: this remains one content deletion in Step 5, with no
  effect on the displacement budget, which was never involved.
- **Motion:** the `complete` state earns exactly **one** one-shot reveal on
  existing motion tokens — a settle, not a celebration. No confetti, no badge,
  no sound. Zero under `prefers-reduced-motion`.

---

## 7. Implementation steps (each stops for approval)

| Step | Deliverable |
| --- | --- |
| **0 — Contract lock** | This document. No product code. |
| **1 — Carry-over: image round-trip** *(P16 §4.1 — pending your call)* | Inline `image` node mirroring `WikiLinkNode`; `![alt](src)` round-trips; regression tests. |
| **2 — Backend read model** | `TodayResponse` DTOs, `WorkspaceService.today()`, `GET /v1/workspace/today`, tier/order/cap/state rules, the `todaySessions` client-zone fix, unit tests. |
| **3 — Today view + the plan band** | Route rename + `/workspace` redirect, `TodayView`, `PlanList`/`PlanRow`, all four states as static compositions, skeleton/error. No actions yet. |
| **4 — Direct actions** | Review session mount, task complete/open, session open; every action reloads so Today shrinks. |
| **5 — The ledger + the day-complete moment** | `LedgerBand` (moved, quieted), `DayComplete` + the single one-shot settle, deletion of the AI-Suggestions panel. |
| **6 — Locales, responsive, a11y, reduced motion** | zh-CN/en-US parity, 375/768/1280 tiers, keyboard order, `role="status"`, both themes. |
| **7 — Release gate + handoff** | Full `verify`-skill run, all gates below, `docs/phase17-handoff.md`. |

---

## 8. Verification strategy

**Backend unit tests** (`WorkspaceTodayServiceTest`)
- Tier assignment for every source × condition (overdue task, due-today task,
  no-due task excluded, running session, upcoming session, ended session
  excluded, review aggregate present only when `dueCount > 0`).
- Ordering across mixed tiers, including tie-breaks.
- Cap of 8 + correct `remainingCount`.
- The four-state matrix, each state reached deliberately — including the
  `clear` ≠ `complete` distinction and the brand-new-account `empty` case.
- Timezone: the same data seen from UTC+13 and UTC−7 produces different days.
- `ReviewFocus.total == ReviewService.dueCount(...)` (one source of truth).

**Frontend unit tests**
- DTO/type mirror, state derivation, locale parity, no-empty-strings.
- `glassBudget.spec.ts` still green at exactly 3 — a hard gate.

**Playwright, live (via the `verify` skill)**
- Morning state: seeded overdue task + due cards + a session → correct order.
- Complete each item → the row disappears → the plan shrinks → the final
  completion flips to `complete` with exactly one settle; reload preserves it.
- `clear`, `empty` and error states each render correctly and are not confused.
- `/workspace` redirects to `/today`; Today is the post-login landing view.
- Both themes, both locales, reduced motion (instant, no settle), 375/768/1280.

**Housekeeping**
- `./mvnw test` green; `vue-tsc` + `oxlint` + `eslint` clean; `vite build` clean
  with no main-bundle regression.
- All seeded verification data deleted and the demo account restored to
  baseline, as in P15 and P16.

## 9. Completion criteria (from the roadmap, verbatim)

> Today is the post-login landing view; every item on it is directly actionable;
> finishing everything produces the completed state; a brand-new account sees a
> sane, non-fabricated Today.
