# Product Domain

Originally a Phase 5 deliverable (mock-data shell); **updated in Phase 7** to
describe the real, per-user-isolated product — every module below is backed
by a real database table and a real endpoint today, not a fixture. Read
`docs/architecture.md` first for the engineering constitution this domain
model has to obey (Phase 7's additions are in its own § Phase 7 section), and
`docs/design-system.md` for the visual language every screen below reuses.
Phase-7-specific migration bookkeeping (what replaced each deleted mock) is
in `docs/mock-migration.md`.

## Positioning

The AI Learning Platform is an AI-native learning workspace — not an educational
admin system, not a course-delivery CMS. Everything revolves around one verb:
**learning**. The product surface is organized around what a learner actually does:
learn, practice, review, create, reflect, track progress — with AI assistance
arriving as a first-class capability in Phase 6, not bolted on afterward.

Phase 5 built the full workspace shell and every product module with realistic
mock data and a real (empty) backend schema. Phase 6 made AI real (DeepSeek,
streaming, persisted conversations). Phase 7 made everything else real: Subject/
Material/Task/Calendar/Preferences CRUD, the Workspace/Analytics read models,
and subject linkage across Notes/Flashcards/AI Tutor — closing the "every view
reads from `mock.ts`" gap described lower in this document as a historical
note. Zero `mock.ts` files remain anywhere in `src/features/`.

## Domain model

```
User (Phase 2)
  └─ Subject                          (anchor of the learning domain)
       ├─ LearningMaterial            (pdf / markdown / video / article / link / document)
       ├─ Note                        (optional subject link)
       ├─ FlashcardDeck → Flashcard   (optional subject link)
       ├─ LearningTask                (optional subject link)
       └─ StudySession                (optional subject link)

Workspace   — read-model façade, owns no tables, aggregates the above
Analytics   — read-model façade, owns no tables, derives from the above
```

**Subject is the anchor.** Every other learning artifact *may* hang off a subject via
a logical `subject_id` FK, but none of them *require* one — a note, task, or study
session can exist independently. This mirrors how people actually learn: not
everything is filed under a course.

**Workspace and Analytics own no tables.** They are aggregation façades. The
workspace dashboard endpoint (Phase 6+) will compose reads across subjects, tasks,
sessions and flashcards; if that composition ever gets expensive, a materialized
summary table is added *inside* the `analytics` package by a new migration — never by
widening the source domains to carry derived data they don't own.

### Entity relationships

| Entity | Belongs to | Optional subject link | Notes |
| --- | --- | --- | --- |
| `Subject` | User | — | `progress` is the only denormalized/user-curated field; study time and material counts are derived. |
| `LearningMaterial` | Subject (required) | — | Exactly one of `sourceUrl` (external) or `storageKey` (uploaded, future `StorageService`) is expected to be set. |
| `Note` | User | optional | Markdown source only; outline/excerpt are computed client-side, never stored. |
| `FlashcardDeck` / `Flashcard` | User | optional | Scheduling columns (`due_at`, `interval_days`, `ease`) are reserved for a future spaced-repetition engine — present in the schema now so no migration is needed when it lands. |
| `LearningTask` | User | optional | Deliberately not a project-management system: no assignees, no nesting, no workflow states beyond todo/in-progress/done. |
| `StudySession` | User | optional | Duration is always derived (`endsAt - startsAt`), never stored. Powers both the calendar and analytics. |

## Module responsibilities

| Product module | Route | Backend package | Responsibility |
| --- | --- | --- | --- |
| Workspace | `/workspace` | `workspace` (façade) | Learning dashboard: greeting, streak/goal/due-cards stats, continue-learning rail, today's tasks/sessions, recent notes/chats. |
| Subjects | `/subjects`, `/subjects/:id` | `subject`, `material` | Subject grid + detail: progress, materials, related notes. |
| AI Tutor | `/ai-tutor/:conversationId?` | `ai` | Conversation list + streaming chat thread, built against the `ChatProvider` abstraction (see below); optional real subject linkage since Phase 7. |
| Flashcards | `/flashcards` | `flashcard` | Deck list, card list, review-mode preview (flip only — spaced-repetition scheduling is a Phase 8+ candidate, columns already reserved). |
| Notes | `/notes` | `note` | Note list + outline rail + read-only content preview, optional real subject linkage since Phase 7 (full markdown editor is a later phase). |
| Calendar | `/calendar` | `calendar`, `task` | Week/month views merging real study sessions with real due-dated tasks; session/task create-edit dialog shared with Workspace. |
| Analytics | `/analytics` | `analytics` (façade) | Real weekly activity, subject time distribution, study heatmap, streak — server-aggregated, zero mock data, validated against the dataviz six-checks palette. |
| Profile | `/profile` | `user`, `auth` (existing) | Identity + real learning-overview stats; `memberSince` from `AuthUserResponse.createdAt`; nickname/avatar editable via `PUT /v1/auth/profile`. |
| Settings | `/settings` | `preference` | Appearance/language/daily-goal controls, persisted server-side (`user_preferences`, V4) and reconciled cross-device — see `docs/architecture.md` § Phase 7 preferences reconciliation contract. |

Every route above is wired in `router/index.ts` and backed by a real endpoint —
the historical "even where the underlying API doesn't exist yet" caveat from
Phase 5 no longer applies as of Phase 7.

## Frontend architecture

Feature-first, one directory per module under `src/features/`:

```
features/<module>/
  types.ts        — domain types (mirrors the backend entity/DTO shape)
  <Module>View.vue
  components/     — module-local components (e.g. subjects/components/SubjectCard.vue)
```

The `mock.ts` fixture file this section historically documented existed in all
8 product modules (`subjects`, `ai-tutor`, `analytics`, `calendar`, `flashcards`,
`notes`, `tasks`, `workspace`) through Phase 5–6 and was **deleted in Phase 7**
once each view had a real `api/modules/<feature>.ts` to call instead — see
`docs/mock-migration.md` for the per-file mapping. `types.ts` needed no
changes when the swap happened in most modules, because it was written to
mirror the backend DTO shape from the start (Phase 5's own design goal, see
below); subject-scoped types picked up a `subjectId` field where linkage was
added (B4).

`src/views/` keeps only the pages that predate the feature-module pattern
(`LoginView`, `WelcomeView`, `DesignSystemView`, `NotFoundView` — the last
added in Phase 7's de-demo pass) — every product surface lives under
`features/`, per the architecture doc's "grow into `features/` when modules
appear."

Nothing here duplicates Phase 1–4 infrastructure: every view is built from the
existing `AppX` component library, existing layout/router/i18n/API-layer seams, and
existing design tokens. Two additions were made *to* those shared layers (not
around them), because every module needed them:

- **Accent tokens** (`--accent-indigo/teal/amber/rose/violet` in `tokens.css`) — the
  fixed-order categorical palette used for subject identity and every chart's
  categorical color. Validated per mode (light/dark) with the dataviz skill's
  six-checks script (lightness band, chroma floor, CVD separation, contrast) before
  being written into the token file.
- **Sidebar collapse state** (`stores/app.ts`) — persisted alongside the existing
  theme/locale preferences, since the fuller nine-item nav needed a rail mode.

### AI provider abstraction (`features/ai-tutor/provider.ts`)

```ts
interface ChatProvider {
  readonly id: string
  streamReply(history: ChatMessage[]): AsyncGenerator<string, void, undefined>
}
```

The chat UI (`AiTutorView.vue`) only knows this interface — it appends yielded
chunks, auto-scrolls, and shows a busy/streaming state. Phase 5 shipped it wired
to `MockChatProvider`, which typed out an i18n-supplied canned reply with
realistic timing. Since Phase 6 it's wired to `ServerSseChatProvider`, which
opens a real SSE connection to the backend's AI service; the UI did not change
shape when that swap happened — this was the seam the roadmap's "SSE streaming
chat" item plugged into. See `docs/ai-engine.md` for the real implementation.

## Backend architecture

Package-by-feature, matching every existing module (`auth`, `user`). Phase 5 added
seven packages with `entity` + `mapper` only (schema-first, no endpoints yet).
Phase 7 gave every one of them its first real `service`/`controller`/`dto`
(plus `preference`, new this phase):

```
subject/      entity, mapper, service/SubjectService (D1/D2), controller, dto/*
material/     entity, mapper, service/MaterialService, controller, dto/*
note/         entity/Note, mapper/NoteMapper                      (real since Phase 6; +subjectId Phase 7)
flashcard/    entity/FlashcardDeck, entity/Flashcard, mapper/*     (real since Phase 6; +subjectId Phase 7)
task/         entity, mapper, service/TaskService, controller, dto/*
calendar/     entity, mapper, service/StudySessionService, controller, dto/*
workspace/    controller/WorkspaceController, service — façade only, no entities/mapper
analytics/    controller/AnalyticsController, service — façade only, no entities/mapper
preference/   entity/UserPreference, mapper, service, controller, dto/*   (new Phase 7)
```

Schema: `V2__create_learning_domain_tables.sql` (Phase 5, seven tables:
`subjects`, `learning_materials`, `notes`, `flashcard_decks`, `flashcards`,
`learning_tasks`, `study_sessions`), `V3__create_ai_conversation_tables.sql`
(Phase 6), `V4__create_user_preferences.sql` (Phase 7, `user_preferences`),
`V5__add_subject_id_to_ai_conversations.sql` (Phase 7, nullable logical FK +
index on `ai_conversations`). Same conventions throughout — snowflake ids,
audit columns, logical (indexed, unconstrained) foreign keys,
`utf8mb4_unicode_ci`.

Reserved error-code ranges (contiguous with the existing `100000–109999` auth
block, 10000 per module):

| Range | Module |
| --- | --- |
| 110000–119999 | `subject` |
| 120000–129999 | `material` |
| 130000–139999 | `note` |
| 140000–149999 | `flashcard` |
| 150000–159999 | `task` |
| 160000–169999 | `calendar` |
| 170000–179999 | `workspace` |
| 180000–189999 | `analytics` |
| 190000–199999 | `ai` (Phase 6) |
| 200000–209999 | `preference` (Phase 7) |

Full per-code detail (which codes are actually in use vs. reserved-but-empty):
`docs/architecture.md` § Phase 7.

## Data flow (Phase 7 — current state)

Every view reads through `api/modules/<module>.ts` (typed axios calls via
`api/http.ts`, envelope unwrapped, errors normalized to `ApiError`) and the
standard `useAsync` view-state sequence (`docs/design-system.md` § View-state
pattern): Skeleton while loading, then content | `AppEmpty` | an
error-with-retry line. Cross-view entity caches (e.g. the `subjects` Pinia
store used for the subject picker across Notes/Flashcards/AI-Tutor/Calendar)
are options-API stores mirroring `stores/auth.ts`; single-view data stays
local to the view. No query/cache library was introduced — `useAsync` plus
Pinia covers every current need at this app's scale (~10 views).

This retired the Phase 5→6 "today vs. later phase" distinction this section
used to document: the mock fixtures' internal consistency (a subject's
materials/notes/sessions all referencing the same subject id) is now simply
what the real per-user data looks like, produced by real writes instead of a
fixture file. `types.ts` needed minimal changes during the swap because it
was written to mirror the backend entities from the start (see the Phase 5
design goal below, which held).

## Architecture decisions

| Decision | Rationale |
| --- | --- |
| Subject is optional everywhere except `LearningMaterial` | Materials without a subject have no home in the UI (there's no "unfiled materials" view); notes/tasks/sessions all have one. |
| No AI/conversation persistence in Phase 5 | Keeps this phase's migration free of a schema that Phase 6's `AiService` design might still change; the chat UI already proves the streaming UX without it. |
| `ChatProvider` abstraction introduced now, not in Phase 6 | The chat UI is real product surface today (Phase 6 explicitly says "prepare... do NOT implement AI yet") — building it against an interface instead of a hardcoded mock means Phase 6 is a provider swap, not a UI rewrite. |
| Flashcard scheduling columns reserved but unused | Same reasoning as the RBAC tables in `V1`: the schema is complete on day one so the review engine's arrival needs no migration, only a service. |
| Workspace/Analytics own no tables | Prevents duplicate-source-of-truth bugs (e.g. a cached "total study minutes" drifting from the sessions it's summed from). |
| Accent color is a token, not a per-subject hex in code | Keeps the categorical palette centrally validated (CVD-safe, contrast-safe in both themes) instead of allowing ad-hoc colors to creep in as subjects are added. |

## Phase 6 readiness (historical — resolved)

What Phase 6 found waiting for it, kept for the historical record:

- `ChatProvider` interface + `MockChatProvider` reference implementation to replace.
- Empty `ai` backend package slot and reserved error-code range (190000–199999).
- `AiService` abstraction point already named in `docs/architecture.md`'s
  infrastructure section — the SSE provider implements against it.
- Every product surface that AI will eventually touch (AI Tutor chat, flashcard
  generation, note summarization, analytics "AI usage" stat) already had a stable UI
  and route, so Phase 6 was additive, not a redesign.

All resolved in Phase 6 — see `docs/ai-engine.md`.

## Phase 7 summary

Phase 7 closed the remaining "mock data" gap this document originally
described as future work: real Subject/Material/Task/Calendar/Preferences
CRUD, real Workspace/Analytics read models, subject linkage through
Notes/Flashcards/AI-Tutor, and the D2 delete-cascade policy. Full delivery
detail: `docs/phase7-delivery-report.md` and `docs/phase7-final-report.md`.
Deferred items (file upload/OSS, spaced-repetition engine, server-side AI
suggestions, client-timezone streak, `subject_name` snapshot retirement):
`docs/mock-migration.md` § Deferred items.

What a future phase finds waiting for it: every domain entity, error range,
and UI surface this document describes is real and stable; there is no
remaining "mock vs. real" seam anywhere in `src/features/`. Candidate next
areas are listed in `docs/phase7-final-report.md`'s "Recommended Phase 8
scope" — not decided here.
