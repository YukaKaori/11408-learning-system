# Development Guide

The default engineering reference for every session in this repository. It records *how we work*; **what is binding** lives in `docs/architecture.md` (the constitution) — when the two overlap, the constitution wins. Keep this guide short: add a rule only when its absence has already cost time.

## Project philosophy

The 11408 Learning System — an exam-preparation product for the 11408 postgraduate entrance exam — built to **commercial SaaS quality** (Apple/Linear/Notion register — see `docs/architecture.md` § Product positioning). Two consequences:

- **Honest data over fake polish.** Empty accounts get designed empty states and CTAs, never fabricated numbers. Metrics without a meaningful value render "—", not 0 (nullable `weekDeltaPercent` convention).
- **Business completion before visual polish.** A phase's feature steps land fully before its theming/UX pass; never ship a half-wired view because the styling sprint arrived.

## Architecture principles

- Backend: modular monolith, package-by-feature (`controller/service/mapper/entity/dto` per feature). Frontend: `api/ · components/ · composables/ · features/ · stores/ · locales/`. Full rules: `docs/architecture.md`.
- **Prefer extending an existing module over a new abstraction.** New shared code needs ≥2 real consumers (e.g. `OwnershipGuard` replaced 8 copies; `useAsync` replaced per-view load state). No speculative seams.
- Read models (workspace, analytics) are façades over existing tables — they reuse other modules' DTOs and add no persistence of their own.
- External vendors only behind an interface (`AiProvider`, future `StorageService`) — never SDK calls in business code.

## API & error conventions

- `/api/v1/...`, `ApiResponse<T>` envelope (`code 0` = success), record DTOs with `from()` mappers, snowflake ids as **strings**, instants as **epoch ms** (exception: calendar-bucket dates are ISO `yyyy-MM-dd` strings).
- Error codes: one range per feature (see the table in `docs/architecture.md`); services throw `BusinessException(ErrorCode)`; only `GlobalExceptionHandler` builds error bodies.
- **Partial-update convention**: omitted/null fields keep their value; explicit clear sentinels are `""` (nullable strings/links, e.g. `nodeCode`) and `0` (nullable instants, e.g. `dueAt`). Document sentinels on the request DTO and its frontend payload type.
- Frontend mirrors: `api/modules/<feature>.ts` with `XxxDto` / `CreateXxxPayload` / `UpdateXxxPayload`, string-literal unions for closed vocabularies. **Write types against the actual Java records, not from memory.**

## Frontend conventions

- **Tokens only** — every visual value comes from `styles/tokens.css`; Element Plus goes through the `element-theme.css` bridge. Custom `AppX` components for signature surfaces; EP for complex primitives (dialogs, pickers, tables). Icons only via `AppIcon`.
- **Load state is `useAsync`** (`data/loading/error/reload`): render Skeleton → content | `AppEmpty` | error-with-retry (`t(error.messageKey)` + `common.retry`). Views that mutate list items keep a local working copy synced from `data` by a `watch`.
- Cross-view entity caches are Pinia stores (options API, like `stores/auth.ts`); single-view data stays in the view via `useAsync`.
- i18n: all UI text through vue-i18n; `en-US` mirrors `zh-CN` key-for-key (unit-test enforced). Demo/content strings are not UI chrome and stay unlocalized.
- `src/types/components.d.ts` is generated — commit it together with new components.

## Testing & verification

- Backend: `./mvnw test` (H2 + real Spring context; keep `src/test/resources/schema.sql` in sync with migrations). Test gotchas that already bit: whole-second time anchors (DATETIME rounds), jdbcTemplate backdating for `updatedAt` ordering assertions.
- Frontend: `npm run type-check && npm run test:unit && npm run build`, plus `npm run lint`.
- **Every step ends with runtime verification at the real surface** — API via curl, UI via Playwright — following `.claude/skills/verify/SKILL.md` (launch recipe, login selectors, data hygiene). Static green is not verification.
- Anything you create while verifying gets deleted afterwards; re-check the affected lists.

## Commit & documentation workflow

- Conventional Commits on `main` (this phase); **one logical step = one focused commit**; docs/handoff updates are separate `docs:` commits. Commit messages via `git commit -F <file>` (PowerShell mangles quotes in `-m`).
- Work proceeds in **confirmed phases split into ordered steps** (current plan: 13 steps in the Phase 7 plan file). Implement only the current step; never start the next until the current one is verified and committed. Never stop mid-step with partial functionality.
- Before ending a session, rewrite `docs/phase7-handoff.md` (or its successor) for the new boundary: session summary, status, exact resume point, verification performed, gotchas. The handoff is the authoritative resume document.
- Architecture-shaping decisions go into the handoff when made and into `docs/architecture.md` at the phase's docs step.

## Code review checklist (self-review before commit)

1. Matches the constitution (tokens, envelope, error ranges, package placement)?
2. Reuses the existing solution (`useAsync`, `OwnershipGuard`, `StatTile`, existing dialogs) instead of a parallel one?
3. Types mirror the wire truth (nullability, string ids, epoch ms)?
4. Both locales updated, mirror test green?
5. Ownership enforced on every new backend query (`user_id` scoping / `OwnershipGuard`)?
6. No dead code, unused imports, debug output, or undocumented TODOs?
7. Loading, empty, and error states all designed — not just the happy path?

## Refactoring & performance

- Refactor opportunistically but **behavior-neutral changes ride with the step that needed them**, called out in the commit message (e.g. the AI deep-link fix inside the step-6 retrofit).
- Performance: measure before materializing. In-memory aggregation over slim column-projected selects is the default (D5); no caching layers before a measured need. Frontend: lazy-loaded routes, no new dependencies without clear necessity.

## Security principles

- Every query is user-scoped; cross-user access must 404/403 (`OwnershipGuard`). Never trust client-sent ids or codes — resolve ownership server-side (`OwnershipGuard`, `QuestionService.requireVisible`) and syllabus codes through `Syllabus.resolve`.
- Secrets only from environment variables (`JWT_SECRET`, `DEEPSEEK_API_KEY`); never in YAML, code, or commits.
- Auth details (token model, rotation, reuse detection): `docs/architecture.md` § Identity & security.

## Decision-making

When multiple approaches are viable: follow existing architecture → prefer the reusable solution → prefer long-term maintainability → prefer consistency over cleverness → avoid new dependencies and speculative abstractions. If a decision could influence future phases, record it (handoff now, architecture.md at the docs step). If documentation and assumptions conflict, the documentation wins; if two documents conflict, `docs/architecture.md` wins.
