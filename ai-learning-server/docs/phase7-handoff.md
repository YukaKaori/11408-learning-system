# Phase 7 — Continuation Handoff

**Theme:** Data Realization & Production Foundation — replace every fake data source with real persistence.
**Status at handoff:** Backend ~55% (4 of 7 backend modules built, unverified build); Frontend 0%; Docs/verification 0%.
**Session ended:** paused deliberately by user request after the Task module, before Calendar/Analytics/Workspace services and all frontend work.

> ⚠️ **The backend has NOT been compiled yet.** No `mvn` build has run this session. Treat all new Java as "written, not verified." First action next session should be a compile (see §16).

---

## 1. Overall progress of Phase 7

| Layer | State |
|-------|-------|
| DB schema (V4) | ✅ written (activity_events + production indexes) — not yet applied/migrated |
| Activity module | ✅ complete (entity, mapper, dto, service, controller) |
| Subject module | ✅ complete (CRUD + derived study stats + activity recording) |
| Material module | ✅ complete (CRUD + activity recording) |
| Task module | ✅ complete (CRUD + completion lifecycle + activity recording) |
| Calendar/StudySession module | ❌ only entity+mapper exist — no service/controller/DTOs |
| Analytics/Statistics engine | ❌ empty (package-info only) |
| Workspace read-model | ❌ empty (package-info only) |
| Frontend API modules | ❌ none created |
| Frontend view rewiring | ❌ none — all views still on mock.ts |
| Mock removal | ❌ all 8 mock.ts files still present |
| Polish (Mission 8) | ❌ not started |
| Performance (Mission 9) | ❌ not started |
| Verification (Mission 10) | ❌ not started — no build run |
| Delivery report | ❌ not started |

---

## 2. Every completed task

- **V4 migration authored** — `activity_events` table + 5 production-integrity indexes (tasks status/completed, flashcards reviewed, notes updated, subjects status).
- **Activity module (new package `activity/`)** — the workspace event timeline write+read side. `ActivityService.record(userId, type, subjectId, refId, title)` is the single entry point; `ActivityController` exposes `GET /api/v1/activities?limit=`.
- **Subject module** — full CRUD. Study minutes & last-studied are **derived** from `study_sessions` via `SubjectMapper.studyStatsByUser` (SQL `TIMESTAMPDIFF`), never stored. Records `SUBJECT_CREATED` / `SUBJECT_COMPLETED` activities. Accent stored in `subjects.color` as a named accent string (`indigo`, …) so the frontend contract is unchanged.
- **Material module** — full CRUD scoped under a subject (ownership double-checked via parent subject + denormalized user_id). Records `MATERIAL_UPLOADED`.
- **Task module** — full CRUD with lifecycle: `completedAt` stamped/cleared on DONE transitions, `TASK_COMPLETED` activity emitted exactly once. List supports `status`, `subjectId`, `dueFrom`, `dueTo` filters (for calendar + "today").

## 3. Every unfinished task

- Calendar/StudySession service + controller + DTOs (SESSION_FINISHED activity).
- Analytics/Statistics engine service + controller (Missions 3 & 6 — all SQL-derived).
- Workspace aggregate read-model service + controller (Mission 2).
- Wire `AiConversationService` to emit `AI_CONVERSATION` activity, flashcard review to emit `REVIEW_FINISHED`/`FLASHCARDS_GENERATED`, note create to emit `NOTE_CREATED` (Mission 5 — activity recording is only wired into subject/material/task so far).
- All frontend API modules + view rewiring + mock deletion.
- Polish, performance, verification, delivery report.

---

## 4. Files created (this session)

```
ai-learning-server/src/main/resources/db/migration/V4__create_activity_and_production_indexes.sql
ai-learning-server/src/main/java/com/yuka/ailearningserver/activity/entity/ActivityType.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/activity/entity/ActivityEvent.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/activity/mapper/ActivityEventMapper.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/activity/dto/ActivityResponse.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/activity/ActivityService.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/activity/ActivityController.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/subject/dto/SubjectStudyStats.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/subject/dto/SubjectResponse.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/subject/dto/CreateSubjectRequest.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/subject/dto/UpdateSubjectRequest.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/subject/SubjectErrorCode.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/subject/SubjectService.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/subject/SubjectController.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/material/dto/MaterialResponse.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/material/dto/CreateMaterialRequest.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/material/dto/UpdateMaterialRequest.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/material/MaterialErrorCode.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/material/MaterialService.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/material/MaterialController.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/task/dto/TaskResponse.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/task/dto/CreateTaskRequest.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/task/dto/UpdateTaskRequest.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/task/TaskErrorCode.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/task/TaskService.java
ai-learning-server/src/main/java/com/yuka/ailearningserver/task/TaskController.java
docs/phase7-handoff.md   (this file)
```

## 5. Files modified

```
ai-learning-server/src/main/java/com/yuka/ailearningserver/subject/mapper/SubjectMapper.java
   → added studyStatsByUser(userId) @Select projection into SubjectStudyStats
```

> Note: `task/mapper/LearningTaskMapper.java` and `material/mapper/LearningMaterialMapper.java` already existed as plain `BaseMapper` interfaces; attempts to re-write them no-op'd and they are unchanged/correct.

## 6. Database changes

- **New table `activity_events`** (V4): `id, user_id, type VARCHAR(48), subject_id, ref_id, title, created_at, updated_at, deleted`; indexes `(user_id, created_at)`, `(subject_id)`. `type` is the `ActivityType` enum **name string** (not a tinyint) so new event kinds never need a migration.
- **New indexes** (V4): `learning_tasks(user_id,status)`, `learning_tasks(user_id,completed_at)`, `flashcards(user_id,last_reviewed_at)`, `notes(user_id,updated_at)`, `subjects(user_id,status)`.
- **No unique constraints added** on business tables — deliberate: logical-delete (`deleted` flag) makes hard `UNIQUE(user_id,name)` collide with soft-deleted rows. Live-row uniqueness is a service-layer concern. (Documented in the V4 header.)
- **Migration not yet applied.** Flyway runs it on next backend startup against MySQL 9.1 (root pwd `1234`).

---

## 7. Remaining mock data (all still live in production path)

```
ai-learning-web/src/features/subjects/mock.ts     (mockSubjects, mockMaterials, getSubject, materialsOf)
ai-learning-web/src/features/workspace/mock.ts     (dashboardStats, continueLearning)
ai-learning-web/src/features/analytics/mock.ts
ai-learning-web/src/features/calendar/mock.ts
ai-learning-web/src/features/flashcards/mock.ts    (totalDue() consumed by workspace/mock.ts)
ai-learning-web/src/features/notes/mock.ts
ai-learning-web/src/features/tasks/mock.ts
ai-learning-web/src/features/ai-tutor/mock.ts
```

Views still importing mocks: `SubjectsView.vue`, `SubjectDetailView.vue`, `WorkspaceView.vue`, `AnalyticsView.vue`, `CalendarView.vue` (+ flashcards/notes/ai-tutor which already have real APIs but may still reference mock.ts — verify). `flashcards/mock.ts` `totalDue()` is a hidden dependency of `workspace/mock.ts`.

## 8. Remaining APIs to build

- **Calendar:** `GET/POST /api/v1/sessions`, `PUT/DELETE /api/v1/sessions/{id}` (StudySession CRUD, with date-range list for the calendar grid). Emit `SESSION_FINISHED`.
- **Analytics/Statistics engine** (dedicated REST, all SQL-derived): learning streak, today/weekly/monthly duration, average session, average review score, subject ranking, most-active-day, most-active-hour, subject distribution, learning heatmap, study frequency, completion rate. Suggest `GET /api/v1/analytics/overview` + `GET /api/v1/analytics/heatmap` + `GET /api/v1/analytics/subjects`.
- **Workspace read-model:** `GET /api/v1/workspace/overview` aggregating: continue-learning (recent sessions), today's tasks, recent notes, recent AI conversations, recent flashcards, learning streak, weekly study time, recent activity timeline.
- **Activity emission gaps:** hook `AI_CONVERSATION`, `NOTE_CREATED`, `FLASHCARDS_GENERATED`, `REVIEW_FINISHED` into their existing services.

## 9. Remaining frontend work

- New `api/modules/`: `subject.ts`, `material.ts`, `task.ts`, `session.ts`, `activity.ts`, `analytics.ts`, `workspace.ts` (mirror the DTOs — same pattern as `note.ts`/`flashcard.ts`).
- Rewire `SubjectsView`, `SubjectDetailView`, `CalendarView`, `AnalyticsView`, `WorkspaceView` to real APIs.
- Delete the 8 `mock.ts` files once nothing imports them; anything genuinely deferred must be documented as future work (not silently faked).
- Mission 8 polish (glass surfaces, skeletons, empty states, motion) and Mission 9 perf (request caching, lazy-load, virtualization).

## 10. Remaining backend work

Calendar service/controller; Analytics service/controller; Workspace service/controller; activity-emission hooks into ai/note/flashcard services; then compile + run.

## 11. Remaining documentation

- `docs/architecture.md` — add the activity/analytics/workspace read-model packages, note the "no DB unique on soft-deleted tables" rule.
- `docs/product-domain.md` — mark subjects/materials/tasks/sessions as **real** (no longer mock).
- `docs/ai-engine.md` / `V3` note — conversations still snapshot subject *name*; decide whether to add a real `subject_id` FK now that subjects are persisted (currently deferred).
- **Phase 7 Delivery Report** (Mission's 12-point deliverable) — not started.

## 12. Current architecture status

All Phase 1–6 rules intact and followed: package-by-feature, `ApiResponse<T>`, `BaseEntity` (snowflake id + audit + logical delete), MyBatis-Plus `BaseMapper` + `LambdaQueryWrapper`, `@AuthenticationPrincipal AuthenticatedUser`, per-module `ErrorCode` enums with reserved code ranges, Flyway. New modules mirror the `note`/`flashcard` reference modules exactly. **No architecture redesign, no abstractions replaced** — per the phase mandate.

Error-code ranges in use: Note 130000, **Subject 140000 (new)**, **Material 150000 (new)**, **Task 160000 (new)**. Next free range: 170000 (suggest Calendar 170000, Analytics 180000, Workspace 190000).

## 13. Technical debt

- **Backend uncompiled** — highest-risk item; verify first.
- `SubjectService.get` and `list` both call `studyStatsByUser` (a full per-user aggregate) even for a single subject — fine now, revisit if it shows up in profiling.
- Activity recording is not transactional with the domain write (matches existing `NoteService` non-transactional style); a crash between the two leaves no activity row. Acceptable for now.
- No pagination on list endpoints (subjects/tasks/materials) — single-user scale, fine for Phase 7.

## 14. Known bugs

None observed (nothing has been run). **Unverified risks to check on first compile:**
- MyBatis mapping of `SubjectMapper.studyStatsByUser` → `SubjectStudyStats` bean (uses camelCase SQL aliases + setters; should be safe regardless of `mapUnderscoreToCamelCase`).
- `LambdaQueryWrapper.orderByAsc(Subject::getStatus)` orders by the enum's stored int — intended, but confirm.
- Enum `@EnumValue` round-tripping for the new modules (reuses existing SubjectStatus/TaskStatus/TaskPriority/MaterialType — all pre-existing and proven).

## 15. Important implementation decisions

1. **Subject accent** stored as a named-accent string (`indigo`,…) in `subjects.color` (16-char col), not a hex — keeps the frontend `SubjectAccent` type unchanged. `SubjectResponse.accent` defaults to `indigo`, `icon` to `book-open`.
2. **Study time is always derived** from `study_sessions` (`TIMESTAMPDIFF(MINUTE, starts_at, ends_at)`), never denormalized onto subjects — single source of truth. Only `progress` stays user-curated on the subject row.
3. **`activity_events.type` is a string enum name**, not a tinyint — no migration for new event kinds; timeline is self-describing.
4. **No DB unique constraints on soft-deleted tables** — uniqueness enforced (if ever) in the service layer.
5. **Timestamps cross the API as epoch-ms `long`** (system default zone), matching the existing `NoteResponse` convention.
6. **Task tokens:** status `todo|inProgress|done`, priority `low|medium|high` (camelCase `inProgress` matches frontend `TaskStatus`).

## 16. Exact recommended next task for the next session

1. **Compile the backend first:** `cd ai-learning-server && ./mvnw -q -DskipTests compile` (JDK 22). Fix any errors in the 26 new files before writing more.
2. Then build the **Calendar/StudySession module** (service + controller + DTOs, `SESSION_FINISHED` activity) — it is the last CRUD dependency the Analytics and Workspace read-models need.
3. Then **Analytics** (statistics engine), then **Workspace** read-model.
4. Only after the backend compiles and those three exist, start frontend API modules + view rewiring.

## 17. Do NOT change in future work

- Do not redesign architecture or replace abstractions (explicit phase mandate).
- Do not store study time / last-studied on the subject row — keep it derived.
- Do not convert `activity_events.type` to a tinyint.
- Do not add hard DB unique constraints to logically-deleted business tables.
- Do not alter the established `note`/`flashcard` module shapes — new modules must match them.
- Do not change the epoch-ms timestamp API convention.
- Do not touch V1–V4 migrations after they've been applied to a real DB; add V5+ instead.
- Keep working strictly within Phase 7 — **do not begin Phase 8.**

## 18. Phase 7 roadmap checklist

- [ ] **Mission 1 — Remove all mock.ts:** 8 mock files still present, 0 removed. Not started (frontend).
- [~] **Mission 2 — Workspace realization:** backend read-model not built; frontend not wired. Activity timeline write-side exists.
- [~] **Mission 3 — Analytics realization:** not built (empty package).
- [~] **Mission 4 — Calendar realization:** Task CRUD ✅ done; StudySession CRUD ❌ (entity/mapper only); calendar frontend ❌.
- [x] **Mission 5 — Workspace event timeline:** `activity_events` table + `ActivityService` ✅; auto-recording wired for subject/material/task ✅, **still to wire** ai-conversation/note/flashcard-review.
- [~] **Mission 6 — Learning statistics engine:** not built.
- [x] **Mission 7 — Production data integrity:** V4 indexes + nullable/cascade/logical-delete review ✅ authored (not yet applied); revisit if new tables appear.
- [ ] **Mission 8 — Workspace polish:** not started.
- [ ] **Mission 9 — Performance:** not started.
- [ ] **Mission 10 — Verification:** not started (no build run).
- [ ] **Deliverables — Phase 7 Delivery Report + Conventional Commit:** not started.

**Legend:** [x] done · [~] partially done · [ ] not started.

---

### Suggested Conventional Commit for the work-so-far (when ready to commit)
```
feat: Phase 7 (partial) — real Subject/Material/Task CRUD, activity timeline & production indexes

Backend persistence for subjects, materials and tasks replacing frontend mocks;
new activity_events timeline (auto-recorded on subject/material/task writes);
derived subject study time from study_sessions; V4 production indexes.
Calendar/Analytics/Workspace services and all frontend rewiring still pending.
```
