# Phase 17 Handoff — Today: the daily learning cockpit

**Status:** COMPLETE, verified, **uncommitted docs only** (all product code is
already committed — see §6). Release gate run **2026-08-01**.
**Contract:** `docs/phase17-plan.md` (the implementation contract) ·
`docs/phase17-material-audit.md` (the material gate) · `docs/roadmap.md` Phase 17.
**Authority for material:** `.claude/skills/liquid-material/` (SKILL.md +
`references/constitution.md`).

Phase 15 (Memory Engine) and Phase 16 (Notes 2.0) are complete and were not
redesigned by this phase. Phase 18 has **not** been started.

---

## 1. What shipped

The Workspace dashboard is gone. In its place is **Today** — one prioritized,
actionable, completable answer to *"what should I do right now?"*, composed from
the three data sources that carry a time contract, ordered by the server, and
built to **shrink** as the day is worked and to **end**.

Eight steps, in order:

| Step | Deliverable | Commit |
|---|---|---|
| 1 | Carry-over from P16 §4.1 — external image markdown round-trip (silent data loss) | `4316b16` |
| 2 | Backend read model: `GET /v1/workspace/today`, tier/order/cap/state rules, the client-zone fix | `994d0a1` |
| 17.2 | Liquid Material consolidation — one material, budget 3, `AppCard variant="glass"` deleted | `0f5604d` |
| — | The two glass skills merged into `liquid-material`; material audit | `65903bb`, `239bdd8` |
| 3 | `TodayView`, `PlanList`/`PlanRow`, all four states, skeleton + error | `f77295e` |
| 4 | Direct actions + the shrink loop | `ffaee23` |
| 5–6 | The ledger, the day-complete settle, locales/responsive/a11y/reduced-motion | `ab27b25` |
| **7** | **This release gate + this document** | *(docs only, uncommitted)* |

---

## 2. Product decisions

### 2.1 A plan, not a dashboard

Rank is the product. If a change makes Today more complete but less ordered, it
loses. This governed every decision below.

### 2.2 Only sources with a time contract may enter the plan

Reviews, tasks and calendar sessions carry a due/start instant that *someone* —
the FSRS scheduler or the user — committed to. Notes, subjects and conversations
do not. Putting them in the plan would mean **inventing an obligation the user
never made**, so they stay context and live in the Ledger, below the fold.

Deliberate exclusions, verified live: tasks with no due date (even high
priority), tasks due after today, sessions that already ended.

### 2.3 The review aggregate is one row, not N

`dueCount` cards collapse into a single plan item. This is the only place Today
aggregates, and it is required: 23 rows would be a queue, not a plan.
`ReviewFocus.total` is `ReviewService.dueCount(userId, zone)` by construction, so
the plan row, the review session and the due tile cannot disagree.

### 2.4 Four states, kept distinct — the honesty guard

| State | Condition | What it says |
|---|---|---|
| `planned` | plan non-empty | the normal day |
| `complete` | plan empty **and** real work recorded today | "Day complete" — earns the one settle |
| `clear` | plan empty, **nothing done**, account has content | "Nothing due today" — a resting state, **not** a celebration |
| `empty` | plan empty and the account has no subjects, tasks or cards | one honest next action |

Collapsing `clear` into `complete` would congratulate a user who did nothing.
Collapsing `empty` into `clear` would show a brand-new account a finished day.
All four were reached and rendered in this gate (§4.4).

### 2.5 The AI Suggestions panel was deleted

Not a material change — Phase 17.2 had already made it a plain solid
`AppCard variant="flat"`. It is a **product-honesty** decision: before Phase 18
there is no grounded recommendation capability, so a surface headed "AI
Suggestions" implies an intelligence that does not exist — and an *empty* one
implies it most strongly, advertising a faculty with nothing to say. The honest
rule-based nudges survive **inside the plan**, where they are commitments rather
than machine advice.

### 2.6 The `suggested` tier is defined and deliberately left empty

It is where P18's grounded "what to study next" lands. Same discipline as P16's
reserved rail slots: architected now, filled when it can be honest.

---

## 3. Architecture decisions

### 3.1 The server owns rank, cap and state

Tiering, ordering, the cap and the four-state derivation all live in
`WorkspaceService.today()`. The client renders `plan` in arrival order and
renders `state` as given — it never sorts, never re-tiers, never decides a day is
complete. Two clients must never disagree about the same day, and a client that
guessed at `complete` would get the flip wrong the moment the last row went.

`todayContract.spec.ts` asserts this negatively: *never re-orders the plan
client-side*, *never re-caps the plan client-side*, *shows the completed day only
on the server verdict*.

Rank = **(tier, sortAt, kind, id)**; tie-break `review → session → task`.
`PLAN_LIMIT = 8`; the overflow becomes `remainingCount` and one link into Calendar.

### 3.2 Zero new tables, zero migrations, zero new error codes

Today owns nothing. It is a composed read model over `flashcard`, `task`,
`calendar`, `preference` and `analytics`, computed per request, `user_id`-scoped
throughout. `workspace` error code 170000 stays reserved and unused. No job, no
cache, no queue.

### 3.3 Façade discipline — section DTOs are reused

`TaskResponse` and `StudySessionResponse` come from their owning modules exactly
as `WorkspaceSummaryResponse` does. `ReviewFocus` is the one new shape, because
the review aggregate has no existing DTO (it is deliberately *not*
`ReviewQueueResponse`, which carries card payloads Today must never fetch).

### 3.4 The client timezone is the whole notion of "today"

`X-Client-Timezone` (`common/ClientZone`, P15) buckets every window. **The
correctness fix in this phase's scope landed:** `WorkspaceService.todaySessions()`
used to bucket on `LocalDate.now()` (server zone) while `dueCards` bucketed on
the client zone, so one response could disagree with itself across a midnight
boundary. Both now use the client zone. Proven live in §4.2.

### 3.5 Today dispatches; it never re-implements

Every verb hands the work to the module that owns the commitment, and Today's
only contribution afterwards is to reload itself:

- **review** → mounts the existing `ReviewSessionView` (a legitimate second
  consumer) so the session ends *on Today* and the plan shrinks in place.
- **task** → the task module's own update endpoint — the same path the
  calendar's checkbox uses. `completedAt` is stamped server-side.
- **session** → the calendar. A session is retired by time passing, so it can
  only be *opened*; naming the verb "Start" would promise the P21 focus timer.

Nothing is spliced out of `plan` locally, so a failed action needs no rollback
and the row can simply be pressed again.

### 3.6 Two independent round trips

`/today` and `/summary` fire in parallel. The plan band renders the moment
`/today` resolves and never waits on the ledger. The small duplicated
computation (goal, streak) is the accepted cost of two independent contracts; if
it is ever measured as slow, the fix is to drop the duplicated block from one
endpoint, **not** to introduce a cache.

### 3.7 Material: no new glass, budget stays 3

Today is *the work*, and the work is never glass. The plan rows are content. The
Line is a sentence, not chrome. The day-complete moment is one one-shot settle on
existing motion tokens — motion, not a material. Verified at the real surface:
**zero `backdrop-filter` and zero `[data-material]` anywhere on Today** (§4.5).

---

## 4. Verification results

Everything below was executed on 2026-08-01 against the real stack (Spring Boot
:8080 + Vite :5173 + MySQL). Nothing is simulated except where §4.4 explicitly
says a captured real payload was replayed, and it says why.

### 4.1 Static gates — all green

| Gate | Result |
|---|---|
| `vue-tsc --build` | clean, exit 0 |
| `eslint .` (no `--fix`) | clean, exit 0 |
| `oxlint .` (no `--fix`) | clean, exit 0 |
| `vitest run` | **184 passed / 184**, 17 files |
| `vite build` | clean, exit 0, built in 2.48s |
| `./mvnw test` | **118 passed / 118**, 0 failures, 0 errors, BUILD SUCCESS |

Backend, per class: `WorkspaceTodayServiceTest` **27**, `WorkspaceServiceTest` 5,
`ReviewServiceTest` 6, `Fsrs6SchedulerTest` 12 (8 + 2 nested + 2 nested — surefire
attributes the outer class's tests to the first `@Nested` report, which is a
reporting quirk, not a gap), `AuthFlowIntegrationTest` 10, `AnalyticsServiceTest`
11, `NoteServiceTest` 10, `TaskServiceTest` 7, `AiSubjectContextTest` 7,
`NoteLinkExtractorTest` 6, `StudySessionServiceTest` 5, `SubjectServiceTest` 5,
`PreferenceServiceTest` 4, `PromptTemplateTest` 2, `AiLearningServerApplicationTests` 1.

Frontend guard specs, all green: `glassBudget.spec.ts` (7),
`materialTokens.spec.ts` (9), `todayContract.spec.ts` (23),
`imageMarkdown.spec.ts` (Step 1's round-trip + the three P16 §4.1 data-loss
regressions), `locales.spec.ts` (key parity + no empty translations).

Bundle: `TodayView` chunk **15.10 kB / 4.91 kB gzip**. Main `index` chunk 130.78 kB
(49.32 kB gzip) — no main-bundle regression. The pre-existing >500 kB warnings on
`NotesView` (526 kB) and `components` (403 kB) are inherited from P16 and
unchanged by this phase.

### 4.2 API layer

Ordering, live, with a seeded fixture (overdue task, 3 due cards, task due today,
session later today, plus a **no-due-date task that must be excluded**):

```
date 2026-08-01  state planned  remaining 0
  overdue    task     task:…597057   2026-07-30T05:39  P17 overdue commitment
  now        review   review         2026-08-01T05:40  3
  scheduled  task     task:…709121   2026-08-01T06:39  P17 due-today commitment
  scheduled  session  session:…76930 2026-08-01T07:39  P17 Verify Session
```

Exactly the contract: tier 0 → tier 1 → tier 2 ordered by `sortAt`, and the
no-due-date task absent.

**The timezone fix, proven decisively.** A session at `2026-08-01T14:00Z` is
tomorrow in Auckland (UTC+12) and today in Los Angeles (UTC−7):

| Zone | `/today` plan sessions | `/summary.todaySessions` |
|---|---|---|
| `Pacific/Auckland` | `[Verify Session]` | `[Verify Session]` |
| `America/Los_Angeles` | `[Verify Session, TZ probe]` | `[Verify Session, TZ probe]` |

Both endpoints agree with each other **in each zone** — which is exactly the
self-disagreement that existed before this phase.

State transitions observed live against the server:
`empty` (untouched demo account) → `clear` (one subject, nothing due) →
`planned` (fixture seeded) → `complete` (everything retired).

### 4.3 Playwright — the shrink loop and the day ending

Chromium, real backend, `Asia/Shanghai`, 1280×900. **39 / 41 checks passed**;
the 2 failures are one finding, reported in §5.1. **Zero console errors, zero
page errors** across the whole run.

| Check | Result |
|---|---|
| `/workspace` redirects to `/today` | PASS |
| app root `/` redirects to `/today` | PASS |
| 4 plan rows render, server order preserved | PASS (`overdue \| now \| scheduled \| scheduled`) |
| every row carries exactly one verb | PASS (`完成 \| 去复习 \| 完成 \| 打开`) |
| no-due-date task excluded | PASS |
| the day line is composed from the plan | PASS — `3 张卡片待复习 · 2 个任务 · 19:39 有学习安排` |
| greeting + goal ring render | PASS |
| completing the overdue task removes its row | PASS (4 → 3) |
| the completion is announced | PASS — `已完成「…」，已从计划中移除。` |
| completing the due-today task removes its row | PASS (3 → 2) |
| the review aggregate is one row, not N | PASS |
| the Phase 15 review stage mounts **on Today** | PASS (url stays `/today`) |
| 3 cards graded through the real UI | PASS |
| closing the session shrinks the plan in place, no navigation | PASS |
| the day-complete state renders after the last item clears | PASS — `今天完成了` |
| exactly **one** one-shot settle, not a loop | PASS (`animation-iteration-count: 1`) |
| the day reports back in real numbers | PASS — `复习 3 张卡片 · 完成 3 个任务 · 学习 60 分钟。` |
| complete survives a reload (server verdict, not a client flash) | PASS |

### 4.4 The four states, rendered

`planned` and `complete` were reached **entirely through the real UI and the real
server** (above). `empty` was the demo account's genuine state before seeding, and
`clear` was its genuine state after creating one subject — both confirmed against
the live API.

Their *render* paths were then exercised by replaying **those two real server
payloads, captured verbatim from this backend**. They cannot be reached a second
time on the same day, because real work is now recorded for the account and the
server — correctly — refuses to call such a day `clear`. Nothing was invented.

| Check | `clear` | `empty` |
|---|---|---|
| renders as a terminal state, not day-complete | PASS | PASS |
| carries **no** settle (only `complete` earns one) | PASS (`animation: none`) | PASS |
| shows no plan rows | PASS | PASS |
| call to action | none, correctly (a resting state) | exactly one — "Create a subject" |
| ledger | shown | **withheld** from a brand-new account |

Copy is honest in both: `clear` = "Nothing due today · No reviews are due and
nothing is scheduled. Study whatever you like." `empty` = "Start with a subject".
Neither congratulates.

### 4.5 Material, at the real surface

| Guard | Expected | Measured |
|---|---|---|
| `GlassSurface` consumers | 3 | **3** — `GlassDock.vue`, `LoginView.vue`, `NoteSelectionToolbar.vue` |
| `backdrop-filter` in source | 2 files | **2** — `GlassSurface.vue` (primitive) + `GlassScene.vue` (environmental veil) |
| `feDisplacementMap` | 1 file | **1** — `GlassSurface.vue` |
| legacy `--glass-bg/-border/-blur/-highlight` | 0 | **0** (one mention, inside the guard spec's own comment) |
| `backdrop-filter` computed anywhere on Today | 0 | **0** |
| `[data-material]` mounted on Today | 0 | **0** |
| `GlassSurface` import in `features/workspace/` | 0 | **0** |
| new colour literals in Today | 0 | **0** (asserted by `todayContract.spec.ts`) |

On `/notes` the runtime scan found exactly one element with a computed
`backdrop-filter` — `div.vue-devtools__panel`, the **dev-server-injected Vue
DevTools overlay**, not application code. No app surface on Notes carries glass
at rest.

**No new glass. No legacy glass tokens. No new blur. Budget unchanged at 3.**

### 4.6 Accessibility — including a real browser inspection

The browser's own accessibility tree (`ariaSnapshot`, Chromium), Today, planned
state:

```
- heading "下午好，Demo" [level=1]
- paragraph: 3 张卡片待复习 · 2 个任务 · 19:39 有学习安排
- text: 今日学习目标 0 / 60 分钟   连续学习天数 0 天
- list "今日计划，共 4 项":
  - listitem: - text: 已逾期： P17 overdue commitment 已逾期 2 天
              - button "完成"
  - listitem: - text: 3 张卡片待复习 现在
              - button "去复习"
  - listitem: - text: P17 due-today commitment 今天
              - button "完成"
  - listitem: - text: P17 Verify Session 19:39–21:09
              - button "打开"
- region "概览": …
- status
```

| Check | Result |
|---|---|
| the plan is a real `<ul>`/`<li>` of native `<button>`s | PASS |
| **every** button / link / heading has an accessible name (AX tree) | PASS — 0 unnamed |
| the list exposes `list` / `listitem` semantics; the list is named | PASS — "今日计划，共 4 项" |
| the live region is exposed as `status` | PASS |
| exactly one `<h1>` | PASS |
| the tier is carried in text, not only in colour | PASS — sr-only "已逾期：" |
| the meters name themselves in text | PASS — "今日学习目标", "连续学习天数" |
| every plan verb reachable by Tab, in rendered plan order | PASS — `0,1,2,3` |
| focus ring visible on a plan verb | PASS — `solid 2px`, offset `2px` |
| the day-complete flip is announced | PASS |

### 4.7 Responsive and themes

| Tier | Result |
|---|---|
| 375 (mobile, coarse pointer, populated plan) | no horizontal overflow (0px); plan verbs measured **44px** |
| 768 (tablet, populated plan) | no horizontal overflow (0px) |
| 1280 (desktop) | no horizontal overflow |

Both themes captured at every tier and on both the `planned` and `complete`
states. Dark theme resolves (`body` → `rgb(10, 9, 16)`); the day-complete mark
renders in the success token; the ledger stays quiet in both.

### 4.8 Reduced motion

Under `prefers-reduced-motion: reduce`, the day-complete section computes:

```json
{ "cls": "day-complete", "animationName": "none", "opacity": "1", "transform": "none" }
```

The one settle is **zero by construction** and the content renders in its final
position — never gated on the moment.

### 4.9 Locale parity, live

Switched through the real Settings control and re-rendered:

```
greeting  "Good morning, Demo"          rows[0].title "Overdue: P17 mobile touch target task"
day line  "1 tasks"                     rows[0].when  "overdue 1d"
meters    "Today's study goal 60 / 60 min",  "Learning streak 1 days"
                                        rows[0].verb  "Done"
```

No leaked zh-CN strings, no raw i18n keys. (Two English pluralisation
roughnesses — see §5.3.)

### 4.10 Regression — modules Today must not have disturbed

| Surface | Result |
|---|---|
| **Calendar navigation** | the session verb navigates to `/calendar`; the calendar renders and shows the session; next-period navigation changes the view |
| **Task completion consistency** | tasks completed on Today show as "Mark as not done" in Calendar — the two surfaces agree, because both go through the same task endpoint |
| **Notes** | the workspace loads, the editor mounts, the two kept notes are still listed; no app glass at rest |
| **Flashcards** | the deck list still renders |
| **Auth** | an expired session deep-linking `/today` is sent to `/login?redirect=/today` and returned to `/today` afterwards |
| **Console** | zero errors, zero page errors, across every run |

### 4.11 Data hygiene

Every seeded row was removed through the app's own DELETE endpoints (soft-delete,
`deleted = 1`): 1 subject, 6 tasks, 3 sessions, 1 deck, 3 cards. The three
`review_logs` rows created by grading are **not** removed by deleting their cards,
so they were deleted directly (§5.4). The demo account's locale, which the locale
test changed, was restored to `zh-CN`.

Final state — byte-identical to the pre-verification baseline:

```json
{ "date": "2026-08-01", "state": "empty", "plan": [], "remainingCount": 0,
  "progress": { "studiedMinutes": 0, "goalMinutes": 60, "reviewsCompleted": 0,
                "tasksCompleted": 0, "sessionsCompleted": 0, "streakDays": 0 } }
```

The two deliberately-kept Phase 6 notes (`未命名笔记`, `Verify Note`) and the kept
conversation (`Hello, explain recursion`) are intact.

---

## 5. Known limitations and open findings

### 5.1 The post-login landing is `/welcome`, not `/today` — **needs your call**

The roadmap's completion criterion reads *"Today is the post-login landing
view."* Traced exactly:

```
/login → /welcome → (CTA "开始学习") → / → /today
```

`/` and `/workspace` both redirect to `/today` correctly, the sidebar's home is
Today, the 404 CTA routes to `today`, and a deep link to `/today` after session
expiry returns to `/today` — **not** to `/welcome`. What sits in the way is the
Phase 8/9 branded welcome experience (`views/WelcomeView.vue`), reached because
`views/LoginView.vue:231` does `router.replace({ name: 'welcome' })` on a plain
sign-in.

So Today **is** the app's home; it is not the first screen after entering a
password. Whether that satisfies the criterion is a product judgement, not a bug
to fix silently, and changing it would delete a deliberate Phase 8/9 surface.
**Left as-is. One line changes it** (`LoginView.vue:231` → `{ name: 'today' }`)
if you want the literal reading.

### 5.2 Two stale copy strings from this phase's route rename — found, not fixed

The `workspace` route became `today` in Step 3, and two user-facing strings still
name the old one:

| String | File | Reads | Actually goes to |
|---|---|---|---|
| `notFound.action` | `locales/en-US.ts:864` | "Back to workspace" | Today |
| `notFound.action` | `locales/zh-CN.ts:839` | "返回工作台" | Today |
| goal-minutes description | `locales/en-US.ts:581` | "the Workspace's progress ring" | Today's ring |

Step 7 is a validation-and-documentation step, so no product code was edited.
These are three one-line copy fixes. (The `workspace.*` **i18n namespace** for the
Ledger's sections is intentional and documented in the locale files — do not
rename it.)

### 5.3 English pluralisation is not plural-aware

`"1 tasks"` and `"Learning streak 1 days"`. The zh-CN strings are unaffected
(Chinese has no plural inflection). Cosmetic, pre-existing pattern across the
app, and out of this phase's scope lock — but it is visible on Today's most
prominent line, so it is worth an early P18 cleanup.

### 5.4 Deleting a card does not delete its review logs

`DELETE /flashcards/cards/{id}` leaves `review_logs` rows behind, so an account
that deletes all its cards still reports `reviewsCompleted` for the day. This
made the demo account read `complete` after teardown until the rows were removed
by hand. Two consequences:

- **Verification hygiene:** the P15 and P16 verification runs left the same
  residue (six `review_logs` rows dated 2026-07-30 are still in the dev DB).
- **Product:** an account with zero content but graded reviews today resolves to
  `complete`, not `empty`. This is *correct* under the plan's definitions — the
  user really did work today, and telling them to "start with a subject" would be
  worse — but it is an ordering subtlety worth knowing.

Not a Phase 17 defect; Today only reads what the flashcard module records.

### 5.5 Inherited, unchanged

- `NotesView` (526 kB) and `components` (403 kB) exceed the 500 kB chunk warning.
  Inherited from P16, untouched here.
- `docs/design-system.md` § "Glass tokens" still documents four tokens with zero
  definitions and zero consumers (audit item **M3**, non-blocking, still deferred).
- The authenticated app still has **no glass chrome at all** — 100% of the
  product's glass chrome lives on unauthenticated surfaces. Correctly diagnosed
  and correctly sequenced behind the `--material-backdrop` adaptivity token; not
  Phase 17 work.

---

## 6. Deferred — explicitly not this phase

| Deferred | To |
|---|---|
| AI-suggested "what to study next" (the `suggested` tier stays empty) | **P18** |
| Focus timer, "start focus block", chrome recession | **P21** |
| Streak mechanics, freezes, weekly reflection, notifications, reminders | **P22** |
| Command-palette jump-to-anything | **P19** |
| Push, offline, PWA | **P25** |
| A guided first-run flow (the `empty` state offers one action; it is not onboarding) | **P24** |
| Authenticated nav chrome → `chrome` preset (budget 3→4) | after `--material-backdrop` (audit D1) |
| Wiki-link popover → `floating` (budget) | unscheduled (audit D2) |
| Toasts → whisper rank (budget) | with P22 (audit D3) |
| Map quality — SDF profile, edge mask, `yChannel` | `docs/phase18-glass-upgrade-plan.md` |
| Drag-reorder, "plan my day" editing, recurring tasks | not planned — Today composes, never re-implements |
| Any new table, job, scheduler, cache or queue | not needed |

---

## 7. Files

### Backend

| File | Change |
|---|---|
| `workspace/dto/TodayResponse.java` | **new** (179) — `TodayResponse`, `Progress`, `PlanItem`, `ReviewFocus` |
| `workspace/WorkspaceService.java` | +236 — `today()`, tier/order/cap/state, the `todaySessions` client-zone fix |
| `workspace/WorkspaceController.java` | +13 — `GET /v1/workspace/today` |
| `workspace/package-info.java` | façade contract note |
| `common/ClientZone.java` | **new** (36) — the shared client-zone resolver |
| `flashcard/ReviewService.java` | +23 — `dueCount(userId, zone)` exposed as the one source of truth |
| `workspace/WorkspaceTodayServiceTest.java` | **new** (501) — **27** tests |

### Frontend — Today

| File | Change |
|---|---|
| `features/workspace/TodayView.vue` | **new** (517) — the route, the three bands, the four states |
| `features/workspace/today/PlanList.vue` | **new** (100) — the ordered list + overflow row |
| `features/workspace/today/PlanRow.vue` | **new** (291) — one commitment, one verb |
| `features/workspace/today/DayComplete.vue` | **new** (127) — the terminal state + the one settle |
| `features/workspace/today/LedgerBand.vue` | **new** (632) — the demoted secondary content |
| `features/workspace/WorkspaceView.vue` | **deleted** (−1373) |
| `features/workspace/__tests__/todayContract.spec.ts` | **new** (516) — **23** tests |
| `api/modules/workspace.ts` | +73 — `getToday`, the DTO mirror |
| `router/index.ts` | route `workspace` → `today`, `/workspace` + `/` redirect |
| `layouts/AppSidebar.vue`, `views/NotFoundView.vue`, `views/WelcomeView.vue` | route rename follow-through |
| `locales/zh-CN.ts`, `locales/en-US.ts` | +98 / +99 — the `today.*` namespace, both locales |
| `styles/base.css` | +19 — the shared `.sr-only` utility |

### Frontend — Step 1 (P16 carry-over)

`features/notes/editor/ImageNode.ts` (**new**, 177), `extensions.ts`,
`NoteEditor.vue`, `__tests__/imageMarkdown.spec.ts` (**new**, 278).

### Frontend — Phase 17.2 material consolidation

`styles/tokens.css` (+87, the `--material-*` layer), `styles/glass.css` (+67, the
three presets), `components/experience/materials.ts` (**new**),
`GlassSurface.vue`, `GlassDock.vue`, `GlassScene.vue`, `NoteSelectionToolbar.vue`,
`LoginView.vue`, `AppCard.vue` (`glass` variant **deleted**),
`styles/element-theme.css` (dialogs/drawers made solid),
`__tests__/glassBudget.spec.ts` (+139), `styles/__tests__/materialTokens.spec.ts`
(**new**, 154).

### Docs

`phase17-plan.md`, `phase17-material-audit.md`, `liquid-material-system.md`,
`liquid-material-migration.md`, `liquid-glass-analysis.md`,
`liquid-glass-apple-audit.md`, `liquid-glass-migration-plan.md`,
`phase18-glass-upgrade-plan.md`, `design-system.md`,
`authentication-experience.md`, `roadmap.md`, and the merged
`.claude/skills/liquid-material/` (SKILL.md + 11 references).

**Totals across Phase 17:** 65 files, +10,188 / −1,734.

---

## 8. Completion criteria — measured

> *"Today is the post-login landing view; every item on it is directly
> actionable; finishing everything produces the completed state; a brand-new
> account sees a sane, non-fabricated Today."*

| Criterion | Verdict |
|---|---|
| Today is the post-login landing view | **partially** — `/`, `/workspace`, the sidebar and the 404 CTA all land on Today, and a deep link returns to it; a plain sign-in still passes through the Phase 8/9 `/welcome` bridge first (§5.1) |
| every item is directly actionable | **met** — every row carries exactly one verb; all three dispatch paths driven live |
| finishing everything produces the completed state | **met** — driven end-to-end through the real UI; one settle; survives reload |
| a brand-new account sees a sane, non-fabricated Today | **met** — `empty` renders one honest action, withholds the ledger, and is kept distinct from `clear` |

---

## 9. Carry-forward into Phase 18

1. **Decide §5.1** — whether a plain sign-in should skip `/welcome`.
2. **Fix §5.2** — three one-line copy strings still naming "workspace".
3. **Fill the `suggested` tier** — it is architected, empty, and waiting.
4. Optionally: §5.3 English pluralisation, §5.4 review-log cleanup on card delete,
   audit **M3** (`design-system.md` § "Glass tokens").
