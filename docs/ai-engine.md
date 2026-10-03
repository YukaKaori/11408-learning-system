# AI Learning Engine

> **2026-09 — the 11408 transformation.** The engine now tutors an 11408
> candidate: every call is grounded in the exam countdown, a syllabus scope,
> the candidate's diagnosis in that scope and their own notes, materials and
> cards there (§ Context pipeline), and three streaming tutoring actions sit
> on the practice loop (§ Tutoring on the practice loop). The provider,
> streaming, conversation and prompt machinery below are unchanged since
> Phase 6/7. Where this document still mentions subjects, it is describing
> the retired design (§ Phase 6 limitation … is kept as history).

Phase 6 deliverable. This document records how the `ai` backend package and
its frontend consumers are built — read `docs/architecture.md` first for the
engineering constitution (the `AiService` abstraction was reserved there
since Phase 1), and `docs/product-domain.md` for the workspace shell and
`ChatProvider` seam this phase plugs into.

Phase 5 built every AI-touching UI surface against a stable interface without
calling a real model. Phase 6 turns that seam real: a provider-abstracted AI
service backed by DeepSeek, true SSE token streaming, persisted conversations,
a context/prompt pipeline, and generation actions surfaced across AI Tutor,
Notes, Flashcards, Subjects and Analytics — a provider swap and a set of new
endpoints, not a UI rewrite.

## Package layout

Package-by-feature, matching every existing module:

```
ai/
  config/AiConfig.java             RestClient + virtual-thread executor beans
  provider/AiProvider.java         interface: id(), isConfigured(), chat(ChatRequest, ChatStreamListener)
  provider/DeepSeekProvider.java   OpenAI-compatible /chat/completions, SSE parsing, retry + error translation
  provider/dto/*                   DeepSeek wire DTOs (package-private)
  context/LearningContext.java     record: exam countdown + targets, syllabus scope, diagnosis, materials/notes/cards in scope, stats snapshot, focus content
  context/ContextHints.java        caller-supplied hints: a resolved syllabus nodeCode, a stats snapshot, focus label/content
  context/LearningContextService.java   builds LearningContext per request from the exam profile, the syllabus, the mastery snapshot and the candidate's corpus
  prompt/PromptTemplate.java       one system prompt per use-case (TUTOR, EXPLAIN, QUIZ, FLASHCARDS, STUDY_PLAN, SUMMARY, SUGGESTIONS, NOTE_*, WEAK_POINTS, WEEKLY_SUMMARY)
  prompt/PromptBuilder.java        renders template + context + history + input into the final message list; enforces maxPromptChars
  stream/SseRelay.java             drives AiProvider.chat on a virtual thread, relays tokens to SseEmitter, handles cancellation
  stream/RelayCallback.java        onFinished/onFailed — lets the caller persist whatever text actually streamed
  entity/AiConversation.java, entity/AiMessage.java, entity/AiMessageRole.java
  mapper/AiConversationMapper.java, mapper/AiMessageMapper.java
  service/AiConversationService.java   conversation CRUD + "send message → stream reply → persist" orchestration
  service/AiGenerationService.java     one-shot use-cases: explain/summary/suggestions/quiz/flashcards/study-plan/note-actions/weekly-summary/weak-points
  controller/AiChatController.java     /api/v1/ai/conversations (CRUD) + POST .../{id}/messages (text/event-stream)
  controller/AiGenerationController.java   /api/v1/ai/generate/*, /api/v1/ai/notes/actions, /api/v1/ai/analytics/*
  dto/*                             request/response records, one per endpoint
  exception/AiErrorCode.java        190000–199999
  util/PromptSizeGuard.java         char-length check shared by PromptBuilder
```

Frontend: `api/modules/ai.ts` (typed conversation CRUD + generation calls),
`features/ai-tutor/provider.ts` (`ChatProvider` interface +
`ServerSseChatProvider`, the real implementation that replaced Phase 5's
`MockChatProvider`).

## Provider abstraction

```java
public interface AiProvider {
    String id();
    boolean isConfigured();
    void chat(ChatRequest request, ChatStreamListener listener);
}
```

`DeepSeekProvider` is the only implementation today, registered as the
`AiProvider` bean. Every other package in `ai/` — prompt building, context
assembly, conversation persistence, generation parsing — depends only on this
interface, never on DeepSeek's wire format. Swapping in another vendor (Tongyi,
Doubao, Kimi, OpenAI, Claude, Gemini, a local Ollama model) means adding a new
`AiProvider` implementation and flipping `app.ai.provider`; nothing above the
provider layer changes.

`isConfigured()` backs **lazy, non-startup-fatal validation**: a blank
`DEEPSEEK_API_KEY` doesn't stop the app from booting — every other feature
keeps working, and the first AI call simply returns
`PROVIDER_NOT_CONFIGURED` (503) instead of a stack trace.

### DeepSeek integration

DeepSeek exposes an OpenAI-compatible `POST /chat/completions`. Every call
requests `"stream": true`, including one-shot generation use-cases —
`AiGenerationService` gets its "single answer" by having its
`ChatStreamListener` concatenate tokens instead of the provider running a
separate non-streaming code path, so there is exactly one request shape to
maintain.

- **Retries** (bounded, exponential backoff, 3 attempts) apply only to
  failures *before* the first token is emitted — a connection refusal or a
  5xx before any bytes arrive. Once tokens have started flowing, a failure is
  reported as `STREAM_INTERRUPTED` and never retried, since retrying would
  duplicate content the client already rendered.
- **HTTP status → error code** translation: `401/403` → `PROVIDER_AUTH_FAILED`,
  `429` → `RATE_LIMITED`, `402` → `QUOTA_EXCEEDED`, `400/404` →
  `INVALID_MODEL`, everything else → `PROVIDER_UNAVAILABLE` (5xx is retryable,
  the rest aren't).
- **SSE line parsing**: DeepSeek's stream is `data: {...}\n\n` frames ending
  in `data: [DONE]`. `DeepSeekProvider` reads line-by-line, skips blank/non-
  `data:` lines, parses each JSON chunk, and forwards `delta.content` to the
  listener; a `finish_reason` on any chunk or the `[DONE]` sentinel ends the
  call. Malformed chunks are logged and skipped rather than failing the whole
  stream.

## Streaming mechanics

The servlet stack (`spring-boot-starter-webmvc`) was kept as-is — no
WebFlux/reactive stack was added. Two pieces make streaming work without it:

1. **Outbound (server → DeepSeek)**: a `RestClient` built on
   `JdkClientHttpRequestFactory`, wrapping a single connection-pooled
   `java.net.http.HttpClient` (`AiConfig#aiRestClient`). This exposes the
   response body as a live `InputStream` instead of buffering the whole
   response, which is what lets `DeepSeekProvider` relay tokens as they
   arrive rather than waiting for DeepSeek to finish.
2. **Inbound (server → browser)**: a `SseEmitter` (5 minute timeout) driven
   from a virtual thread (`AiConfig#aiStreamingExecutor`,
   `spring.threads.virtual.enabled: true`). `SseRelay` is the only class that
   touches `SseEmitter` — it submits `AiProvider.chat(...)` to the executor
   and forwards `onToken`/`onComplete`/`onError` as `token`/`done`/`error` SSE
   events. Holding a virtual thread open for a slow model response is cheap,
   so one open connection per in-flight generation costs nothing a platform
   thread wouldn't be needed for anyway.

**Cancellation**: a client abort (browser closes the connection, or the user
clicks "Stop generating") makes the next `emitter.send()` throw, which cancels
the backing `Future` and interrupts the provider thread. `DeepSeekProvider`
checks `Thread.interrupted()` between reads of the upstream stream and between
retry attempts, so an interrupt unblocks it at the next line read — a stalled
connection with literally no further bytes won't unblock instantly, an
accepted tradeoff rather than reaching for lower-level socket control.
Whatever text streamed before the cancel is still persisted (`truncated =
true` on the message row), so the user never loses a partial answer.

## Context pipeline

```java
public record ContextHints(String nodeCode, String statsSnapshot,
        String focusLabel, String focusContent)

public record LearningContext(String exam, String targets,
        String scores, String plan,
        String scope, String scopeDetail, String diagnosis,
        List<String> materialTitles,
        int totalNotes, List<String> recentNoteTitles,
        int totalFlashcards, int dueFlashcards,
        String statsSnapshot, String focusLabel, String focusContent)
```

`LearningContextService.build(userId, hints)` assembles the context every AI
call is grounded in, in four layers, all read per request from real data:

1. **The exam** — the countdown and phase ("2027 考研 · 初试 2026-12-26（预计）·
   距今 88 天 · 真题阶段") and the target scores, from `ExamProfileService`. An
   estimated date is labelled as one here too. Since the exam-year milestone
   (M1, 2026-10) two more facts travel with it:
   - **模考估分** — what whole papers have actually yielded, from
     `SittingService.estimates` ("408 112.5（近 3 套，98–121）"); scoped requests
     see only their own paper's estimate, and a paper never sat shows nothing
     rather than a guess.
   - **时间规划** — for general (unscoped) requests only, how today's study time
     divides among the papers and what has been studied, from `PlanService`
     ("每天 8h：政治 1.6h · 英语一 1.6h · 数学一 2.6h · 408 2.3h；今天已学 3.5h；
     本周整套模考 1/6"). A question about one 考点 does not need the timetable;
     "我该怎么安排时间" does.
2. **The scope** — where in the syllabus the request sits (`nodeCode`, always
   passed through `Syllabus.resolve` first — never a raw client string) and
   what that node contains or is worth.
3. **The diagnosis** — readiness, the weakest 考点 and the open mistakes in
   scope, from the same `MasterySnapshot` the syllabus map shows the
   candidate, so the tutor never contradicts the product.
4. **The corpus** — the candidate's notes and reference materials anchored
   in scope: the subtree *and* its ancestors (a note on all of 操作系统, or a
   textbook filed under all of 408, is still context for 进程同步). Card
   totals and due counts are account-wide.

`PromptBuilder` renders the non-empty parts into a `## 考生情况` block
appended to the template's system prompt. A conversation's scope is persisted
on `ai_conversations.node_code` (V8); `nodeCode` on a send follows the
partial-update convention (omitted keeps it, `''` clears it).

The Phase 6/7 description of the two context sources follows, kept for the
parts that still hold (the focus-content contract, no controller ever
concatenating a prompt):

- **Real, server-resolved data** — recent note titles and flashcard/deck
  counts, queried from `NoteMapper`/`FlashcardDeckMapper`/`FlashcardMapper`,
  scoped to `userId`. Since Phase 7, `hints.subjectId()` — a *resolved,
  ownership-validated* id the caller obtained via
  `SubjectService.resolveOwnedSubject*()` — additionally pulls the subject's
  real name/description, its material titles (up to 10, newest first), and
  scopes the note count/titles to that subject. A stale id (subject deleted
  since it was persisted on a conversation) degrades gracefully to the string
  hints instead of failing the chat.
- **Caller-supplied hints** — subject name/description (fallback when no
  subject id is present — legacy clients and the generation endpoints), an
  analytics stats snapshot, and "focus content" (e.g. the note text a
  rewrite/explain action is operating on). The caller is responsible for
  having already fetched and ownership-checked whatever it passes as
  `focusContent`; `LearningContext` never fetches it itself.

`PromptBuilder` renders the non-empty parts of a `LearningContext` into a
`## 学习背景` block appended to the template's system prompt, then prepends
conversation history and the current user input — every AI use-case (chat and
one-shot generation alike) goes through this one method, so no controller or
service ever concatenates a prompt string itself.

### Phase 6 limitation, closed in Phase 7: server-side subject resolution *(history — subjects were retired in 2026-09; conversations are now scoped by `node_code`)*

Phase 6 shipped with subjects living only in frontend mocks, so every AI
action sent the subject's `name`/`description` as plain client-supplied text.
Phase 7 closed that boundary: Subject CRUD is real, `ai_conversations` gained
a nullable `subject_id` logical FK (V5), and the chat endpoints accept a
`subjectId`:

- `POST /ai/conversations` and `POST /ai/conversations/{id}/messages` take an
  optional `subjectId` (string wire id). It must reference a subject owned by
  the caller (`SubjectService.resolveOwnedSubject`, same 110000/110001 errors
  as everywhere else) and is persisted on the conversation, with
  `subject_name` refreshed as a display snapshot from the real subject.
  Per the partial-update convention, `null` keeps the current link and
  `""` clears it; on later sends the conversation's stored link supplies the
  context without the client resending it.
- `LearningContextService` resolves the id to the subject's real
  name/description/material titles and subject-scoped notes (see above).
- **String hints remain supported**: requests without a `subjectId` behave
  exactly as in Phase 6 — hint-only chat and all generation endpoints
  (`statsSnapshot`, Subject-page text context) still work unchanged.

`subject_name` is kept deliberately (not retired) so conversation lists stay
readable after a subject is renamed or deleted — subject deletion nullifies
`subject_id` (Phase 7 cascade in `SubjectService.delete`) but leaves the
snapshot.

## Tutoring on the practice loop (2026-09)

Three streaming actions put the tutor where a candidate actually needs it —
beside a question, a mistake or a 考点 — rather than only in the chat:

| Endpoint | Template | Grounded in |
| --- | --- | --- |
| `POST /v1/ai/questions/{id}/explain/stream` | `QUESTION_EXPLAIN` | the question (stem, options, reference answer, 解析), the candidate's own answer when given, scoped to its first 考点 |
| `POST /v1/ai/mistakes/{id}/diagnose/stream` | `MISTAKE_DIAGNOSIS` | the question, every attempt, the candidate's cause and reflection |
| `POST /v1/ai/knowledge/explain/stream` | `POINT_EXPLAIN` | the node, scoped context and diagnosis |

All three speak the chat's SSE vocabulary (`token` / `done` / `error`), so the
frontend reads them with the same `streamTokens` helper and renders them in
one component (`features/ai-tutor/AiExplainPanel.vue`: solid, `aria-live`,
stop/regenerate). Nothing is persisted — an explanation is read, not stored.
Ownership is checked before anything reaches the prompt: a question must be
visible to the caller, a mistake must be theirs.

Every tutoring template carries one shared **stance** (`PromptTemplate.STANCE`):
answer in the candidate's language, write math as LaTeX (`$…$`, `$$…$$` —
the frontend renders it with KaTeX), defer to the syllabus and standard
textbooks, say "not sure" rather than invent a source, year or question
number, and flag the knowledge cut-off on current-affairs politics. The
note-rewrite templates opt out — they transform the candidate's own text.

## Prompt system

One `PromptTemplate` enum entry per use-case — the *only* place a system
prompt string is written. Two shapes:

- **Free text** (`TUTOR`, `EXPLAIN`, `SUMMARY`, `SUGGESTIONS`, the `NOTE_*`
  rewrite family, `WEAK_POINTS`, `WEEKLY_SUMMARY`) — the model's reply is
  used as-is.
- **Structured JSON** (`QUIZ`, `FLASHCARDS`, `STUDY_PLAN`,
  `structuredJson() == true`) — the prompt documents an exact JSON shape
  inline (e.g. `{"questions":[{"question":...,"options":[...],"answer":...,
  "explanation":...}]}`), and `AiGenerationService` parses the response with
  Jackson into a typed DTO. `stripCodeFence()` first strips a
  ` ```json ... ``` ` wrapper if the model added one despite being told not
  to; a parse failure surfaces as `GENERATION_PARSE_FAILED` (500), not a
  silent empty result.

Prompts are written in Chinese — the product targets Chinese users and
DeepSeek is the default provider — but `TUTOR`'s prompt explicitly instructs
the model to answer in whatever language the user wrote in.

Since Phase 15 the `FLASHCARDS` prompt no longer just asks for "concise
cards": it encodes the spaced-repetition rules that make a card *schedulable* —
atomicity (one fact per card; compound content is split into several cards),
answer-side brevity (the back is the single fact, not an explanation),
self-contained questions, no list-answers, and language-matching — because the
generated deck now flows straight into the real FSRS scheduler (Phase 15
review engine), where low-quality cards cannot be reviewed effectively. The
JSON wire shape (`{"cards":[{"front":...,"back":...}]}`) is unchanged, so the
`FlashcardsWire` parse path is untouched; `PromptTemplateTest` guards both the
shape and the quality directives against regression.

## Conversation flow (AI Tutor)

`AiConversationService` owns conversation CRUD (list/create/rename/archive/
delete, all ownership-checked against `userId`) plus the send-message
use-case:

1. Persist the user's message.
2. If this is the conversation's first message, derive a title from it (first
   24 chars + `…`).
3. Resolve the optional `subjectId` (persisting the link and refreshing the
   `subjectName` snapshot), then build `LearningContext` from the
   conversation's subject link — or the client-supplied `subjectName`/
   `subjectDescription` fallback — plus real note/flashcard counts (chat has
   no note/flashcard "focus"; that's the generation endpoints' job).
4. `PromptBuilder.build(TUTOR, context, history, null)` — history is the
   full prior message list, mapped to `ChatTurn`s.
5. Hand the message list to `SseRelay.stream(...)`, whose `RelayCallback`
   persists the assistant's reply (or partial reply, if cancelled/failed)
   once the stream ends.

### Streaming send flow (sequence)

```mermaid
sequenceDiagram
    participant W as Web (Vue, ServerSseChatProvider)
    participant S as Server (AiChatController → AiConversationService)
    participant R as SseRelay (virtual thread)
    participant D as DeepSeek

    W->>S: POST /api/v1/ai/conversations/{id}/messages {content, subjectName?}
    S->>S: persist user message, build LearningContext + prompt
    S->>R: stream(ChatRequest)
    S-->>W: 200 text/event-stream (SseEmitter opens)
    R->>D: POST /chat/completions {stream: true}
    D-->>R: data: {delta...}  (repeated)
    R-->>W: event: token, data: <chunk>  (repeated)
    W->>W: append chunk, render incrementally

    alt user clicks "Stop generating"
        W->>S: abort the fetch (AbortController)
        S->>R: emitter.send() throws → Future.cancel(true)
        R->>D: interrupt provider thread
        R->>S: onFinished(partialText, cancelled=true)
        S->>S: persist assistant message (truncated=true)
    else stream completes normally
        D-->>R: data: [DONE]
        R-->>W: event: done, data: <finish reason>
        R->>S: onFinished(fullText, cancelled=false)
        S->>S: persist assistant message
    end

    alt provider error (any point)
        R-->>W: event: error, data: {code, message}  (ApiResponse envelope)
        R->>S: onFailed(partialText, error)
        S->>S: persist partial assistant message if non-empty
    end
```

Event names on the wire: `token` (data = raw text delta), `done` (data =
finish reason), `error` (data = the standard `ApiResponse` failure envelope,
so the frontend can key off the same numeric error codes as regular REST
calls).

## Non-chat generation

`AiGenerationService` covers every one-shot use-case behind
`/api/v1/ai/generate/*`, `/api/v1/ai/notes/actions` and
`/api/v1/ai/analytics/*`:

| Endpoint | Template | Notes |
| --- | --- | --- |
| `POST /generate/explain` | `EXPLAIN` | `topic` required |
| `POST /generate/summary` | `SUMMARY` | `text` required (the content to summarize) |
| `POST /generate/suggestions` | `SUGGESTIONS` | subject context only |
| `POST /generate/quiz` | `QUIZ` (structured) | parses into `QuizResponse` |
| `POST /generate/flashcards` | `FLASHCARDS` (structured) | parses into cards, then **persists** a real deck via `FlashcardService.createDeckFromGenerated` — this is the one generation endpoint with a side effect beyond returning text |
| `POST /generate/study-plan` | `STUDY_PLAN` (structured) | `goal` + `availableMinutesPerDay` required; parses into `StudyPlanResponse` |
| `POST /notes/actions` | one of the `NOTE_*` templates, selected by `NoteActionRequest.action()` | operates on `text` (a note's content or a selection within it) |
| `POST /analytics/weekly-summary` | `WEEKLY_SUMMARY` | `statsSnapshot` is a client-composed text blob (see the Subjects/Analytics scoping note above) |
| `POST /analytics/weak-points` | `WEAK_POINTS` | same `statsSnapshot` contract |

Every one-shot call runs through the same `generateRaw()` helper: build
context → build prompt → run `aiProvider.chat()` synchronously (accumulating
tokens instead of streaming them to the client) → return the text, or throw
if the provider reported an error or returned nothing. Structured endpoints
additionally run the result through `parseJson()`.

## Frontend integration

- **`api/modules/ai.ts`** — typed conversation CRUD and every generation call,
  through `api/http.ts`'s existing `unwrap`/`ApiError` pattern. This is the
  only file that knows the AI endpoint URLs; every view calls through it.
- **`ChatProvider` / `ServerSseChatProvider`** (`features/ai-tutor/provider.ts`)
  — the seam `docs/product-domain.md` reserved in Phase 5. Same
  `streamReply(history): AsyncGenerator<string>` shape as the old
  `MockChatProvider`, so `AiTutorView.vue`'s control flow didn't change shape,
  only its data source. Internally: `fetch()` + `ReadableStream` (axios can't
  expose a live stream for a POST body), manual SSE frame parsing
  (`parseSseStream`), an `AbortController` wired to `cancel()`, and a 401 path
  that reuses `api/http.ts`'s single-flight refresh (`refreshTokenAfterUnauthorized`)
  before retrying once.
- **AI Tutor** (`AiTutorView.vue`) — real conversation list/create/rename/
  archive/delete; sending a message creates a conversation on first send if
  none is active, then opens the stream.
- **Notes** (`NotesView.vue`) — an AI toolbar (explain/rewrite/continue/
  simplify/expand/translate/summarize/generate-flashcards) operating on the
  selected text (or the whole note if nothing is selected), applying results
  back into the editor via an `AppDialog` confirmation step.
- **Flashcards** (`FlashcardsView.vue`) — real deck/card CRUD; AI-generated
  decks from Notes/Subjects/the conversation flow appear here immediately
  since they're persisted through the same `FlashcardService`.
- **Subjects** (`SubjectDetailView.vue`) — an AI actions row (Ask AI, Generate
  Summary/Quiz/Flashcards/Study Plan, Explain Knowledge, Learning
  Suggestions), each sending the mock subject's name/description (plus
  material titles, for summary/quiz/flashcards) as the client-supplied
  context described above. "Ask AI" creates a real AI Tutor conversation
  seeded with `subjectName` and navigates there — no separate chat
  implementation.
- **Analytics** (`AnalyticsView.vue`) — an "AI insights" panel calling
  `generateWeeklySummary`/`generateWeakPoints` with a client-composed stats
  snapshot string, rendered as two text blocks.

No view builds its own prompt string or duplicates a template — every AI
surface is a thin caller of `api/modules/ai.ts`.

## Configuration

Bound at `app.ai.*` via `AppProperties.Ai`:

```yaml
app:
  ai:
    provider: deepseek
    max-prompt-chars: 24000
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:}   # secret — environment only, never hardcoded, never logged
      base-url: https://api.deepseek.com
      model: deepseek-chat
      temperature: 0.7
      top-p: 0.95
      max-tokens: 2048
      timeout: 60s
      streaming-enabled: true
```

`dev` and `prod` profiles both inherit this block unmodified — the only thing
that differs between environments is whether `DEEPSEEK_API_KEY` is set in the
environment. A blank key means every AI endpoint returns
`PROVIDER_NOT_CONFIGURED` (503); nothing else in the app is affected.

## Database

`V3__create_ai_conversation_tables.sql` — same conventions as V1/V2 (snowflake
ids, audit + logical-delete columns, indexed logical FKs, `utf8mb4_unicode_ci`):

- **`ai_conversations`**: `user_id`, `title`, `subject_id` (nullable logical
  FK, added by V5 in Phase 7), `subject_name` (nullable display snapshot —
  see the subject-resolution note above), `archived`.
- **`ai_messages`**: `conversation_id`, `user_id` (denormalized, so per-user
  queries need no join), `role` (0=user, 1=assistant, 2=system), `content`
  (`MEDIUMTEXT`), `truncated` (set when a reply was cut short by cancellation
  or a mid-stream failure).

## Error codes (190000–199999)

| Code | Meaning | HTTP |
| --- | --- | --- |
| 190000 | Provider not configured (`DEEPSEEK_API_KEY` blank) | 503 |
| 190001 | Provider unavailable / network error | 502 |
| 190002 | Provider timeout | 504 |
| 190003 | Provider rejected the API key | 502 |
| 190004 | Rate limited | 429 |
| 190005 | Quota exceeded | 402 |
| 190006 | Invalid model | 400 |
| 190007 | Stream interrupted (after tokens had started) | 500 |
| 190008 | Context/prompt too large | 400 |
| 190009 | Generation output failed to parse | 500 |
| 190010 | Conversation not found | 404 |
| 190011 | Conversation access denied (not owner) | 403 |

All funnel through the existing `GlobalExceptionHandler` via
`BusinessException`, same envelope and i18n-message-key pattern as every other
module's error codes.

## Security

- API key lives only in `DEEPSEEK_API_KEY` (environment), never in a YAML
  file or a log line — `AppProperties.Ai.DeepSeek.apiKey()`'s Javadoc says so
  explicitly, and no log statement in `DeepSeekProvider` prints the request
  body (only status codes and a truncated error body).
- Every conversation read/write is ownership-checked (`requireOwned`) against
  the authenticated `userId` before any query — the same pattern
  `AuthService`/`NoteService`/`FlashcardService` use.
- `PromptSizeGuard` rejects an oversized prompt (`CONTEXT_TOO_LARGE`, 400)
  before it's ever sent to DeepSeek, bounding both cost and the blast radius
  of a pathological client payload.
- All AI endpoints require an authenticated session — no anonymous access to
  any `/api/v1/ai/*` route.

## Performance

- One shared, connection-pooled `HttpClient` for every outbound DeepSeek
  call — no per-request client construction.
- Streaming end-to-end (DeepSeek → server → browser) means the user sees the
  first token as soon as DeepSeek emits it, not after the full completion.
- Virtual threads make holding a connection open for a slow model response
  cheap; `spring.threads.virtual.enabled: true` applies the same tradeoff to
  the servlet container generally.
- One-shot generation calls (`AiGenerationService`) still request
  `stream: true` from DeepSeek internally (one request shape to maintain) but
  accumulate before returning — the client gets one response, not partial
  JSON it would have to buffer itself before parsing.

## Extending to another provider

Adding a second model provider (Tongyi, Doubao, Kimi, OpenAI, Claude, Gemini,
a local Ollama model) means:

1. Implement `AiProvider` (`id()`, `isConfigured()`, `chat(...)`) — wire
   parsing is provider-specific and stays inside the new class, mirroring how
   `DeepSeekProvider`'s wire DTOs are package-private to `provider.dto`.
2. Add the provider's config block under `app.ai.*` (`AppProperties.Ai` gains
   a sibling record next to `DeepSeek`).
3. Register the bean and select it via `app.ai.provider`.

Nothing in `context/`, `prompt/`, `stream/`, either service, either
controller, or any frontend file changes — that's the point of the seam.

## What the next phase finds waiting for it

- ~~Real Subject/Task CRUD … `subjectId`-based context lookup~~ — **done in
  Phase 7**: `LearningContextService` resolves subjects server-side and
  `ai_conversations.subject_id` is a real logical FK (see the
  subject-resolution section). What remains open on that path is the
  frontend sending `subjectId` from a real subject picker (Phase 7 frontend
  steps) and eventually extending `subjectId` to the one-shot generation
  DTOs, which still use string hints by design.
- A spaced-repetition review engine (flashcard `due_at`/`interval_days`/`ease`
  columns already exist, reserved since Phase 5) would let Analytics' stats
  snapshot include real review data instead of the mock numbers it composes
  today.
- `docs/architecture.md`'s reserved-but-unimplemented list (Redis, OSS,
  WebSocket, Elasticsearch, MQ, scheduler, audit log) is unaffected by this
  phase — none of them were needed for a stateless HTTP/SSE integration.
