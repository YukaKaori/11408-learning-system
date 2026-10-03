# Architecture & Engineering Constitution

Decisions recorded here are binding until explicitly revised. When adding code,
match these conventions — do not invent parallel ones.

## Product positioning

**11408 Learning System** — the preparation system for China's postgraduate
entrance exam in the 11408 combination: 政治 (101) · 英语一 (201) · 数学一 (301)
· 计算机学科专业基础 408. One system for the whole loop a candidate runs every
day: learn a 考点 → practise it → diagnose the mistakes → redo them on schedule
→ remember with spaced repetition → plan the day → watch readiness rise — and,
around it, the year: whole papers sat under time, hours measured per paper,
and a plan that divides each day by the gap between estimate and target.

It is a single-candidate product, not an educational admin system, and
commercial SaaS quality is the bar. UI language: modern, premium, minimal — in
the spirit of Apple, Linear, Notion, Raycast, Vercel, Stripe. Until 2026-09 it
was the general-purpose "AI Learning Platform"; § 11408 below records what the
transformation changed and why.

## Confirmed platform decisions (2026-07)

| Topic | Decision |
| --- | --- |
| Java | 22 — use modern language features (records, pattern matching, virtual threads where useful) |
| Repository | Single monorepo `11408-learning-system` (local folder `11408-Learning-System`; artifacts `11408-learning-system-server` / `11408-learning-system-web`). The hosted remotes are renamed by the account owner, not by tooling — until then they keep their old names; see README § Repository |
| Migrations | Flyway only; production schema is never changed manually |
| i18n | i18n-ready from day one; default `zh-CN`, fallback `en-US`; no hard-coded UI text |
| Frameworks | Spring Boot 4 / Vue 3 + TS + Vite / Pinia / Element Plus (themed) / MyBatis-Plus / MySQL |

## Backend

**Style: modular monolith, package-by-feature.** Each feature owns
`controller / service / mapper / entity / dto` under its package. Cross-cutting
code lives in `common/`; framework wiring in `config/`.

### Conventions

- **API prefix**: `/api/v1/...`. Controllers return `ApiResponse<T>`; DTOs are records.
- **Envelope**: `{ code, message, data, timestamp }`; `code = 0` means success.
  HTTP status still carries transport semantics (400/401/404/500…).
- **Errors**: services throw `BusinessException(ErrorCode)`. Only
  `GlobalExceptionHandler` builds error responses. Error-code ranges:
  40000–49999 common client, 50000–59999 common server, then 10000 per feature
  module starting at 100000 — auth 100000, subject 110000 (**retired** with the
  subject module in 2026-09; the range is never reused), material 120000,
  note 130000, flashcard 140000, task 150000, calendar 160000,
  workspace 170000 (reserved — pure façade, composes other modules' errors,
  owns no codes of its own), analytics 180000, ai 190000, preferences 200000,
  exam 210000, question 220000, practice 230000, mistake 240000,
  sitting 250000 (the plan is a read model and owns none; the focus timer
  lives in calendar's range).
  See § Phase 7 and § 11408 for the per-module code lists. The frontend gives
  actionable codes their own message (`web/src/api/errorKeys.ts` →
  `error.codes.<code>`); every other failure renders the generic message.
- **Entities** extend `BaseEntity` (snowflake id, `created_at`, `updated_at`,
  `deleted`); audit fields are filled automatically. Entities never cross the API
  boundary — map to DTOs.
- **Validation**: Bean Validation annotations on request DTOs; no manual checks in
  controllers.
- **Configuration**: YAML per profile (`dev` default, `prod`); all custom settings
  under `app.*` bound via `AppProperties`. Secrets only from environment variables.

### External-service abstraction (mandatory)

Business code never talks to a vendor SDK directly. Define the interface in
`infrastructure/` when the capability is first needed:

- `StorageService` → Aliyun OSS / MinIO / S3 / local
- AI abstraction → Claude / OpenAI / Gemini / DeepSeek / local models
  (provider implementations + configuration layer + conversation management).
  Landed in Phase 6 as `ai/provider/AiProvider.java` (feature-package-local,
  not `infrastructure/` — the interface has no callers outside the `ai`
  package) with `DeepSeekProvider` as the sole implementation; see
  `docs/ai-engine.md`.
- `NotificationService`, cache, search, MQ — same pattern.

Reserved (do not implement early): Redis, OSS, WebSocket, Elasticsearch, MQ,
scheduler, audit log.

## Frontend

**Structure**: `api/` (axios + typed endpoint modules) · `components/` (design-system)
· `composables/` · `features/<module>/` (one folder per product module, loaded
lazily by the router) · `layouts/` · `locales/` · `router/` · `stores/` ·
`styles/` · `views/` (full-bleed screens outside the app shell: login, welcome,
404).

### Conventions

- **Design tokens first**: every color/spacing/radius/shadow comes from
  `styles/tokens.css`. Element Plus is bridged to the tokens in
  `styles/element-theme.css` — never style against EP defaults.
- **Dark mode**: `html.dark` class, three-way preference (light/dark/system) in the
  app store.
- **i18n**: all user-visible text through vue-i18n keys; `en-US` must mirror
  `zh-CN` key-for-key (enforced by unit test).
- **HTTP**: all requests go through `api/http.ts` helpers, which unwrap the
  envelope and normalize failures to `ApiError` (with i18n message key).
- **Element Plus**: on-demand via unplugin resolvers; prefer custom token-based
  components for signature surfaces, EP for complex primitives (tables, pickers).

## Design system (Phase 3)

Full reference: `docs/design-system.md`. Binding conventions only, here:

- **Tokens are the only source of visual values.** `styles/tokens.css` defines the base
  scales (typography, color, spacing, radius, shadow, motion, glass); `styles/motion.css`
  holds transition/keyframe tokens. Components never hard-code a color, size, or timing.
- **Component split**: signature surfaces (`AppButton`, `AppInput`, `AppCard`,
  `AppAvatar`, `AppTag`, `AppBadge`, `AppEmpty`, `AppLoading`, `AppSkeleton`,
  `AppSection`, `AppPageHeader`, `AppSearch`) are custom-built from tokens. Complex
  primitives with real positioning/focus-trap logic (`AppDialog`, `AppDrawer`,
  `AppTooltip`, `AppPagination`) are themed wrappers over Element Plus — do not
  reimplement that logic from scratch.
- **Icons**: only `AppIcon` may import from the underlying icon library
  (`lucide-vue-next`). Application code never imports icon components directly — this
  keeps the icon set swappable.
- **Registration**: `src/components/` exports are explicit (`src/components/index.ts`
  barrel) — no auto-import for app components, matching the existing
  `unplugin-vue-components` config which is scoped to Element Plus only.
- **Theme engine**: `stores/app.ts` owns `light` / `dark` / `system`, persisted, applied
  via the `html.dark` class. A `glass` mode is a reserved extension point (tokens exist
  in `tokens.css`; no toggle yet).
- **Glass theme, full component skinning, and the premium login** are reserved for
  Phase 4+ — this phase only prepares the tokens.

## Database

See `database/README.md`: snake_case, utf8mb4, mandatory audit columns, logical
foreign keys, migration-only changes.

## Identity & security (Phase 2)

### Architecture

Stateless authentication with a two-token model:

- **Access token** — self-contained HS256 JWT (jjwt), 30 min TTL. Validated by
  signature only; no database lookup per request. Claims: `sub` (user id),
  `username`, `iss`, `iat`, `exp`, `jti`.
- **Refresh token** — opaque 256-bit random value, 14 day TTL. Stored **hashed**
  (SHA-256) in `refresh_tokens`; the raw value exists only on the client.

Key classes: `TokenService` (abstraction — the only seam token consumers see;
`JwtTokenService` is the jjwt/MySQL implementation), `JwtAuthenticationFilter`
(bearer-token authentication), `SecurityConfig` (filter chain),
`DbUserDetailsService` + `UserPrincipal` (password login path via
`AuthenticationManager`), `AuthService`/`AuthController` (use-cases + REST).

### Refresh-token rotation & reuse detection

Every `/auth/refresh` **rotates**: the presented token is revoked
(`revoked_at`), a new one is issued, and the two are linked (`replaced_by_id`).
Presenting an already-revoked token is treated as theft: **every live token of
that user is revoked** and the request fails with `REFRESH_TOKEN_REUSED`.
Logout revokes the presented token and is idempotent.

### JWT lifecycle

```
issue (login)          → HS256-signed, exp = now + access-token-ttl
validate (per request) → signature + iss + exp checked in JwtAuthenticationFilter
expire                 → 100010 TOKEN_EXPIRED → frontend silently refreshes
```

Signing key: `app.security.jwt.secret` (env `JWT_SECRET`, ≥ 32 bytes — the
application refuses to start otherwise).

### Login flow (sequence)

```mermaid
sequenceDiagram
    participant W as Web (Vue)
    participant S as Server (Spring Security)
    participant DB as MySQL

    W->>S: POST /api/v1/auth/login {usernameOrEmail, password}
    S->>DB: load user (username OR email)
    S->>S: BCrypt verify + account-state checks
    S->>DB: insert refresh_tokens (SHA-256 hash), update last_login_*
    S-->>W: {accessToken (JWT), refreshToken, expiresIn, user}
    W->>W: tokenStorage.set(...)

    W->>S: GET /api/v1/... (Authorization: Bearer <access>)
    S->>S: verify JWT signature — no DB hit
    S-->>W: 200

    Note over W,S: access token expires
    W->>S: GET /api/v1/... → 401 code 100010
    W->>S: POST /api/v1/auth/refresh {refreshToken}
    S->>DB: hash lookup → revoke old, insert new (rotation)
    S-->>W: new {accessToken, refreshToken}
    W->>S: replay original request

    W->>S: POST /api/v1/auth/logout {refreshToken}
    S->>DB: revoke token
    S-->>W: 200 (idempotent)
```

### Security decisions

| Decision | Rationale |
| --- | --- |
| CSRF disabled | Pure bearer-token API — no cookie-based session to forge |
| CORS via `CorsConfigurationSource` bean | Security's CorsFilter runs before auth: 401s carry CORS headers, preflights need no token |
| Refresh tokens hashed at rest | A leaked DB dump cannot be replayed |
| Login error is always `INVALID_CREDENTIALS` for bad user *or* bad password | No account enumeration |
| Errors funnel through `GlobalExceptionHandler` | Entry point / denied handler delegate via `HandlerExceptionResolver` — one envelope builder |
| Snowflake ids serialized as strings in DTOs | Exceed JS safe-integer range |
| `@EnableMethodSecurity` on now | RBAC phase adopts `@PreAuthorize` without config changes |

Extension points reserved (schema and/or seams exist, no implementation):
RBAC (`roles`/`permissions` tables + empty authorities in `UserPrincipal`),
OAuth2/third-party login and MFA (additional issuance paths behind
`TokenService`), email verification & password reset (account-state +
`app.security.password-policy` config), "sign out everywhere"
(`TokenService.revokeAllForUser`).

### Auth error codes (100000–109999)

| Code | Meaning | HTTP |
| --- | --- | --- |
| 100000 | Invalid credentials | 401 |
| 100001 | Account locked | 403 |
| 100002 | Account disabled | 403 |
| 100010 | Access token expired | 401 |
| 100011 | Access token invalid | 401 |
| 100020 | Refresh token invalid | 401 |
| 100021 | Refresh token expired | 401 |
| 100022 | Refresh token reused (rotation violation) | 401 |

### Frontend auth infrastructure

- `api/token-storage.ts` — sole owner of token persistence (localStorage today;
  designed to swap to httpOnly-cookie refresh + in-memory access token later).
- `api/http.ts` — attaches `Authorization`; on 401 performs a **single-flight**
  refresh and replays the failed request; unrecoverable sessions trigger the
  handler registered by the router (redirect to `/login`).
- `stores/auth.ts` — user identity + login/logout/session-restore actions.
- `router/guards.ts` — `requiresAuth` / `guestOnly` meta flags enforced in
  `beforeEach`; `roles`/`permissions` meta reserved for the RBAC phase.

## Roadmap

The canonical roadmap is [`docs/roadmap.md`](roadmap.md) (the 11408 roadmap,
2026-09). The list below is the history of the platform the product was built
on, kept for reference.

1. **Phase 1 — Foundation** ✅: plumbing, standards, initial design tokens, zero business features.
2. **Phase 2 — Identity** ✅: Spring Security 7 + JWT (access/refresh), user schema (V1 migration), frontend auth flow + route guards.
3. **Phase 3 — Enterprise design system** ✅: full token architecture (typography, color,
   spacing, radius, shadow, motion, glass prep), the `AppX` component library, icon
   abstraction (`AppIcon` over lucide), layout system (header/sidebar/content,
   responsive), accessibility baseline, `docs/design-system.md`. No business modules, no
   AI, no login redesign.
4. **Phase 4 — Premium authentication & signature welcome experience** ✅: the login +
   post-auth welcome screens that give the platform its first impression, built on the
   Phase 2 auth logic (unchanged) and Phase 3 tokens. See `docs/authentication-experience.md`.
5. **Phase 5 — AI-native workspace shell & product domain** ✅: the full domain model
   (Subject/Note/FlashcardDeck+Flashcard/LearningTask/StudySession) and every product
   module (Workspace, Subjects, AI Tutor, Flashcards, Notes, Calendar, Analytics,
   Profile, Settings) built with realistic mock data and a real (empty) backend schema
   (V2 migration). AI Tutor ships as a fully real chat UI wired to a swappable
   `ChatProvider`, streaming a canned reply — the seam Phase 6 fills in. See
   `docs/product-domain.md`.
6. **Phase 6 — AI learning engine (DeepSeek integration)** ✅: the `ai` backend package
   (`AiProvider`/`DeepSeekProvider`, true SSE token streaming over `RestClient` +
   virtual threads, persisted conversations, the context/prompt pipeline), real CRUD
   for Notes and Flashcards, and AI actions surfaced across AI Tutor, Notes, Flashcards,
   Subjects and Analytics. Subjects/Tasks stay mock-data-only this phase — AI context
   for them is client-supplied, not resolved server-side. See `docs/ai-engine.md`.
7. **Phase 7 — Commercial Product Foundation** ✅: real per-user Subject/Material/
   Task/Calendar/Preferences CRUD (V4/V5 migrations), the `subject` domain wired
   through Notes/Flashcards/AI Tutor, real Workspace/Analytics read models
   replacing every mock, the dark theme's black+purple luxury re-skin, and a
   UX unification pass. All 8 `features/*/mock.ts` files deleted. See
   § Phase 7 below and `docs/product-domain.md`, `docs/mock-migration.md`,
   `docs/phase7-delivery-report.md`.
8. **Phases 8–14** ✅ — the Liquid Material design language, the login
   environment and the product website (`docs/phase8-handoff.md` …
   `docs/phase14-handoff.md`).
9. **Phase 15** ✅ — the memory engine: real FSRS-6 spaced repetition
   (`docs/phase15-handoff.md`).
10. **Phase 16** ✅ — Notes 2.0, the knowledge workspace (TipTap, wiki links,
    backlinks; `docs/phase16-handoff.md`).
11. **Phase 17** ✅ — Today, the daily cockpit: a server-ranked, capped,
    completable plan (`docs/phase17-handoff.md`).
12. **11408 transformation** (2026-09) — § 11408 below.

## 11408 — the exam-preparation system (2026-09)

The general learning workspace became an exam-preparation system. This
section is binding; the domain model is in `docs/product-domain.md` and the
AI grounding in `docs/ai-engine.md`.

### What changed, in one paragraph

Free-form per-user **subjects were retired** and replaced by the fixed,
versioned **11408 syllabus** as the one anchor of every artifact. A
**question bank** (library content packs plus the candidate's own captured
questions), **practice sessions** with honest grading, a **mistake book**
scheduled by FSRS, and a **mastery model** derived from the answer log were
added. Today, Analytics, the AI tutor, notes, cards, tasks, sessions and
materials were re-anchored on syllabus nodes. Nothing proven was rebuilt:
FSRS, Notes, Today's server-owned plan, the AI pipeline and the Liquid
Material system carried over.

### Packages (backend `com.yuka.learning`)

| Package | Owns | Notes |
| --- | --- | --- |
| `exam` | `exam_profiles`; the syllabus (content, not tables) | `ExamCalendar` (date estimate, pure), `ExamPhase`, `ExamProfileService`, `syllabus/SyllabusLoader` |
| `question` | `questions`, `question_points`, `question_attempts` | the bank, `QuestionPackImporter`, `grading/QuestionRules` + `grading/AnswerGrader` |
| `practice` | `practice_sessions` | drawing sets, the two-step answer protocol, reports |
| `mistake` | `mistakes` | the mistake book; `MistakeScheduling` (FSRS-6, no sub-day steps, resolve after 3) |
| `mastery` | nothing — a read-model façade | `MasteryModel`, `MasterySnapshot`, `RecommendationService` |
| `srs` | nothing — pure scheduling | FSRS-6 (`Fsrs6Scheduler`), shared by `flashcard` and `mistake` |
| `sitting` | `paper_sittings` (V10) | 真题 by year and 模拟卷 by name, scored by section; `ScoreEstimate` (pure) — the paper estimate and section profile |
| `plan` | nothing — a read model | `PlanModel` (pure) — the day's split per paper, the weekly cadence, the phase timeline; `PlanService` composes it with time spent |
| `calendar` | + `focus_timers` (V10) | `FocusService` — the study timer; stopping it writes an ordinary `study_sessions` row |
| `subject` | — | **deleted**; `subjects` table kept (expand/contract), never read |

### Binding decisions

- **Syllabus as content, not data.** The exam blueprint and one JSON file per
  paper (`server/src/main/resources/exam/`) are versioned with the code,
  validated strictly at boot (unique codes, every code under its parent's
  prefix, 考点 weights 1–3, module scores summing to the paper's full score —
  the client re-checks the same file in `syllabusIndex.spec.ts`) and served
  verbatim by `GET /v1/exam/syllabus`. No table holds the syllabus; a
  syllabus revision is a content change reviewed like code.
- **Node codes are the only anchor.** Hierarchical codes
  (`cs408.os.process.sync`): the paper is the first segment and a subtree is
  a prefix (`within(code, scope)` — a prefix is not a parent: `cs408.osx` ∉
  `cs408.os`). Every anchored table carries a nullable `node_code`; writes go
  through `Syllabus.resolve`, and `''` is the clear sentinel on partial
  updates. Questions must be tagged with 考点 (leaves) of one paper.
- **Expand/contract for the subject retirement.** V8 added `node_code`
  everywhere and stopped reading `subject_id`; the `subjects` table and the
  old columns stay until a later contract migration drops them. Existing
  links were carried over best-effort — only subject names whose meaning is
  unambiguous (数据结构 → `cs408.ds`, 线代 → `math1.linear`, 政治 →
  `politics` …) were mapped; anything else stays unanchored rather than
  guessed. No data is destroyed by the transition.
- **Content packs are idempotent.** Library questions ship as JSON packs
  (`exam/questions/*.json`), imported at boot keyed by `pack_key` with a
  SHA-256 content hash: unchanged questions are skipped, changed ones updated,
  removed ones **retired** (never deleted — attempts reference them).
- **Grading is honest.** Choice questions and fill-blanks that match an
  accepted form (after notation-only normalization) are graded by the server.
  Anything it cannot judge — an unmatched fill-blank, every open question —
  returns `needs_self_grade` with the reference answer and records nothing;
  the candidate grades themself and submits again. A blank answer to a
  choice or fill-blank is an honest wrong ("不会"). The system never marks an
  answer wrong that it could not actually judge.
- **The mistake book is automatic and spaced.** Any non-correct graded
  attempt files or updates a mistake. Redos are scheduled by FSRS-6 with no
  sub-day steps (a fresh mistake is first due tomorrow); a mistake resolves
  after 3 consecutive correct redos made on or after their due day; a wrong
  answer to a resolved question reactivates it.
- **Mastery is derived, never stored.** Per 考点: recency-weighted accuracy
  (30-day half-life) with a Bayesian prior of one pseudo-attempt at 0.5, so
  one lucky answer is not mastery. Levels: < 0.6 weak, < 0.8 developing,
  < 0.9 proficient, else mastered. Aggregates (chapter/module/paper) report
  *readiness* — Σ share × mastery with untested counting as 0 — and
  *coverage*, both weighted by exam score.
- **Recommendations are explainable and phase-aware.** priority = importance
  (the score attributable to the 考点) × need (1 − mastery, or the phase's
  appetite for untested ground, + 0.1 per open mistake up to +0.3) ×
  freshness (¼ within 12 h, 0.6 within 36 h). Phases come from the exam
  countdown: foundation > 180 days, intensive ≤ 180, past papers ≤ 90,
  sprint ≤ 30. Today shows at most one suggestion per paper.
- **Today stays a plan.** Due mistakes joined the plan as a fourth
  commitment (retired by the redo set, like cards by the review session);
  recommended 考点 travel *beside* the plan as an offer and never decide
  whether the day is complete. The server still owns rank, cap and state.
- **The exam date is an estimate until confirmed.** Day one is estimated as
  the Saturday between 20 and 26 December before the admission year (true
  every year 2015–2025) and is always labelled "预计" until the candidate
  confirms the official date.
- **Exam content renders through one component.** Stems, options, answers,
  解析 and AI replies are markdown + LaTeX rendered by `RichText.vue` —
  markdown-it with raw HTML off and validated links, KaTeX without `trust`.
  It is the only `v-html` in the app, is not exported from the components
  barrel, and is imported only by lazily-loaded feature code, so KaTeX never
  loads with the shell (`richTextBundle.spec.ts`, `markdown.spec.ts`).

### The exam year — binding decisions (M1, 2026-10)

The 考点 loop says what is secured point by point; the exam is sat as four
timed papers on a fixed date after a year in which time is the scarce
resource. M1 adds that outer loop without a second kind of anything.

- **Only whole papers estimate a paper.** A sitting covering every printed
  section (or a total recorded on its own) feeds `ScoreEstimate`: a
  recency-weighted mean (the mastery model's 30-day half-life) over the last
  90 days, compared as rates of what each record was out of. Section drills
  feed the per-section profile only. No sitting, no estimate — "—", never a
  readiness figure dressed up as a score.
- **The server writes the total.** `score`/`full_score` are written with
  `sections` by `SittingService` only — the sum of the sections (each capped at
  the section's worth from the syllabus content) or the recorded total — and
  snapshot the paper as it was, so a syllabus revision cannot rescore history.
- **One kind of time.** Measured study is a `study_sessions` row however it
  was recorded — the calendar, or the focus timer, which is one server-side
  row per candidate (a start while running is a *switch* that saves the
  running block). Under a minute is dropped as a mis-tap; past 12 hours the
  stop needs an explicit end (`FOCUS_TOO_LONG`) rather than recording a night.
- **A day studied is any study.** The streak counts a day with an ended
  session, an answered question or a reviewed card. Study *minutes* stay the
  recorded sessions' — answering questions is activity, not timed time.
- **The plan is derived, never stored.** `PlanModel`: each phase has a base
  split (政治 grows from 5% to 30% toward the exam; 数学 and 408 carry the
  foundation), tilted by `share ∝ base × (1 + gap)` where
  `gap = (target − estimate) / full` comes only from whole-paper estimates; a
  paper without a gap takes the others' mean, so silence neither gains nor
  loses time. Minutes are dealt in 5-minute blocks by largest remainder, so
  they always sum to the daily goal. Whole papers per week (the cadence)
  start in the past-paper phase. The one input edited on the plan is the
  daily goal itself (`user_preferences.daily_goal_minutes`, default 8 h).
- **The exam timetable is content.** Each paper's exam day, start time and
  first 真题 year live in its syllabus file and are validated at boot (day one
  政治 then 英语, day two 数学 then 专业课).
- **Today's time band frames the day, never the plan.** It renders the plan's
  hours per paper above the server-ranked plan, and its one verb — timing a
  paper — is dispatched to the focus store (`todayContract.spec.ts`).
- **The shell imports no feature code statically.** The timer's controls live
  in `features/focus/` and reach the sidebar and header through
  `defineAsyncComponent` (`richTextBundle.spec.ts`).

### Error codes

| Range | Module | Codes |
| --- | --- | --- |
| 210000–219999 | `exam` | `NODE_NOT_FOUND`, `SUBJECT_INVALID`, `PROFILE_INVALID` |
| 220000–229999 | `question` | `QUESTION_NOT_FOUND`, `QUESTION_ACCESS_DENIED`, `QUESTION_READ_ONLY`, `QUESTION_INVALID` |
| 230000–239999 | `practice` | `SESSION_NOT_FOUND`, `SESSION_ACCESS_DENIED`, `SESSION_CLOSED`, `QUESTION_NOT_IN_SESSION`, `ALREADY_ANSWERED`, `NO_QUESTIONS_AVAILABLE`, `MODE_INVALID`, `SCOPE_REQUIRED`, `SELF_GRADE_INVALID` |
| 240000–249999 | `mistake` | `MISTAKE_NOT_FOUND`, `MISTAKE_ACCESS_DENIED`, `MISTAKE_CAUSE_INVALID`, `MISTAKE_RESULT_INVALID` |
| 250000–259999 | `sitting` | `SITTING_NOT_FOUND`, `SITTING_ACCESS_DENIED`, `SITTING_KIND_INVALID`, `SITTING_SECTION_INVALID`, `SITTING_SCORE_INVALID`, `SITTING_PAPER_INVALID`, `SITTING_DATE_INVALID` |
| 160004–160006 | `calendar` (focus) | `FOCUS_NOT_RUNNING`, `FOCUS_END_INVALID`, `FOCUS_TOO_LONG` |

### Migrations

`V8__exam_foundation.sql` (exam profiles, `node_code` on every anchored
table), `V9__question_bank_practice_mistakes.sql` (questions, tags,
attempts, practice sessions, mistakes) and `V10__paper_sittings_and_focus.sql`
(paper sittings, the running focus timer, the 8-hour daily-goal default). The
H2 test schema mirror (`server/src/test/resources/schema.sql`) tracks V10.

## Phase 7 — Commercial Product Foundation

Turned the Phase 5 mock-data shell into a real, per-user-isolated SaaS product.
Full design rationale (D1–D10) lives in the phase plan; this section records
what's binding going forward. Detailed module docs: `docs/product-domain.md`
(domain model, module responsibilities), `docs/mock-migration.md` (what
replaced each deleted mock), `docs/ai-engine.md` § subject-resolution (AI
`subjectId` flow), `docs/design-system.md` (dark theme identity, view-state
pattern, `StatTile`).

### New/changed modules and error-code ranges

| Range | Module | Notes |
| --- | --- | --- |
| 110000–119999 | `subject` | `SUBJECT_NOT_FOUND`, `SUBJECT_ACCESS_DENIED`, `SUBJECT_STATUS_INVALID` |
| 120000–129999 | `material` | `MATERIAL_NOT_FOUND`, `MATERIAL_ACCESS_DENIED`, `MATERIAL_TYPE_INVALID` |
| 150000–159999 | `task` | `TASK_NOT_FOUND`, `TASK_ACCESS_DENIED`, `TASK_STATUS_INVALID`, `TASK_PRIORITY_INVALID` |
| 160000–169999 | `calendar` | `SESSION_NOT_FOUND`, `SESSION_ACCESS_DENIED`, `SESSION_TIME_INVALID`, `SESSION_WINDOW_INVALID` |
| 170000–179999 | `workspace` | reserved, unused — `GET /v1/workspace/summary` is a pure façade over subject/task/calendar/analytics services and surfaces *their* error codes, never its own |
| 180000–189999 | `analytics` | `ANALYTICS_RANGE_INVALID` (the only validation surface — window must be 1–90 days) |
| 200000–209999 | `preferences` | `PREFERENCE_THEME_INVALID`, `PREFERENCE_LOCALE_INVALID` |

`ai` (190000–199999) gained no new codes this phase; see `docs/ai-engine.md`
for its full table (unchanged since Phase 6, `subject_id` resolution reuses
`subject`'s own codes, not new ones).

### D1 — `OwnershipGuard`

`common/OwnershipGuard.require(entity, ownerFn, userId, notFoundCode,
deniedCode)` is the single ownership check every user-scoped module calls
after a primary-key load: `null` → `notFoundCode`, owner mismatch →
`deniedCode`, otherwise the entity is returned non-null and confirmed owned.
Introduced this phase to replace eight copies of the same branch across
`note`/`flashcard`/`ai` (retrofitted, behavior-neutral) and every new Phase 7
service (`subject`, `material`, `task`, `calendar`). New user-scoped modules
call this instead of writing the check inline.

### D2 — Subject delete cascade *(retired 2026-09 with the subject module; kept as history)*

`SubjectService.delete` is `@Transactional` and, in order: **soft-deletes**
the subject's materials (`MaterialMapper.delete(...)` — `LearningMaterial`
extends `BaseEntity`, whose `deleted` column is `@TableLogic`, so MyBatis-Plus
turns this into an `UPDATE ... SET deleted = 1`, not a physical `DELETE`),
then **nullifies** `subject_id` on every note/deck/task/session/conversation
that referenced it, then hard-deletes the subject row itself. Rationale:
materials have no existence independent of their subject (existentially
owned — soft-delete matches every other module's logical-delete convention);
notes/decks/tasks/sessions/conversations are user-authored content that
exists independently of any subject link (nullable by design since Phase 5) —
deleting a subject must never destroy them. The delete confirmation dialog
states this distinction explicitly rather than leaving it implicit.

### Read-model contracts (Workspace, Analytics)

Both packages own **zero tables** — `GET /v1/workspace/summary` and the three
`GET /v1/analytics/*` endpoints aggregate existing tables
(`subjects`/`learning_materials`/`learning_tasks`/`study_sessions`/`notes`/
`flashcard_decks`/`flashcards`/`ai_conversations`) with column-projected SQL,
scoped to the authenticated user, computed on every request — no
materialization, no cache. `weekDeltaPercent` and similar week-over-week
metrics are **nullable**, rendered as `—` rather than `0`, when the prior
week has no baseline to compare against (an empty or first-week account must
never show a fabricated percentage). If aggregation ever becomes measurably
slow at scale, the fix is a materialized summary table added *inside* the
owning package by a new migration — never by widening a source domain
(`subjects`, `study_sessions`, …) to carry derived data it doesn't own.

### Preferences reconciliation contract

`user_preferences` (V4: `user_id` unique, `theme`/`locale`/`daily_goal_minutes`
with defaults, audit columns) is the server source of truth for theme, locale
and daily study goal. `GET /v1/preferences` returns the defaults
(`system`/`zh-CN`/`60`) when no row exists yet — no 404, so a brand-new
account gets a valid response — and `PUT` upserts. The frontend contract:

1. **Boot (before auth resolves)**: `stores/app.ts` reads `localStorage`
   directly and applies it immediately — this is the FOUC-safe path, it never
   waits on a network round trip.
2. **After login or session-restore**: `reconcileFromServer()` fetches
   `GET /v1/preferences` and overwrites local state — **server always wins**
   over whatever `localStorage`/OS `prefers-color-scheme` produced at boot.
   This runs on every login and every full page load with a live session
   (cheap, single GET, accepted at this scale).
3. **Every user-initiated change** (Settings page, or `AppSidebar`'s inline
   theme/locale chips) applies the change locally first (instant feedback,
   also written to `localStorage` so it survives the *next* boot before
   reconciliation completes), then calls `updatePreferences(...)` in the
   background. A failed background persist is swallowed where there is no UI
   room for an inline error (the sidebar chips); Settings surfaces it with the
   existing inline-error-line pattern. Nothing rolls back the optimistic local
   apply — the next login's reconciliation is the eventual-consistency
   backstop if the persist silently failed.

### AI `subjectId` resolution flow *(superseded 2026-09 by syllabus scoping — see `docs/ai-engine.md` § Context pipeline)*

`ContextHints.subjectId()` (a resolved, ownership-validated id — never a raw
client-sent id trusted as-is) flows into `LearningContextService.build()`,
which pulls the subject's real name/description/material titles (via
`SubjectService.resolveOwnedSubject`, same 110000/110001 codes as every other
subject access) and scopes note counts/titles to that subject. Chat endpoints
persist the link on `ai_conversations.subject_id` (V5) with `subject_name`
kept as a display snapshot so conversation lists stay readable after a rename
or delete; D2's cascade nullifies `subject_id` on delete but leaves the
snapshot. String-hint fallback (client-supplied name/description, no id)
remains supported for legacy callers and every one-shot generation endpoint,
which still takes text hints by design. Full detail: `docs/ai-engine.md` §
Context pipeline.

### Auth extension points reserved (unchanged from Phase 2, still not implemented)

Registration, email verification, password reset, and OAuth2/third-party
login remain deliberately out of scope this phase — the schema/seam reservations
listed under § Identity & security (`roles`/`permissions` tables, empty
`UserPrincipal` authorities, `TokenService.revokeAllForUser`,
`app.security.password-policy` config) are unchanged and still the intended
extension points when that work is scoped. Phase 7 added one small,
non-conflicting auth surface: `PUT /v1/auth/profile` (nickname/avatar only,
same `auth` package/error range, no new codes) and `createdAt` on
`AuthUserResponse` (backs Profile's real "member since").

## Git

- Trunk: `main`. Feature branches `feature/<topic>`, fixes `fix/<topic>`.
- Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`).
- Never commit secrets; `.env*.local` are ignored.
