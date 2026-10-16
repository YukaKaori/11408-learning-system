---
name: verify
description: Build, launch and drive the 11408 Learning System (Spring Boot :8080 + Vite :5173) to verify changes at the real surface — API via curl, UI via Playwright from the npx cache.
---

# Verifying the 11408 Learning System

## Launch

- Backend: `cd server && ./mvnw -q spring-boot:run` (JDK 22, MySQL 9.1 running locally, DB `ai_learning`). Ready when `curl http://localhost:8080/api/v1/system/info` returns 200. The boot log line `Content packs imported: …` says what the import changed.
- Frontend: `cd web && npm run dev` → http://localhost:5173.
- Ports often stay held after stopping tasks: `netstat -ano | grep -E ':(8080|5173).*LISTEN'` then `taskkill //PID <pid> //F` (Git Bash).
- Don't run `./mvnw test`/`compile` while `spring-boot:run` is up — it recompiles `target/classes` under the running server. Stop it first.
- The dev backend twice died with Maven reporting exit code `-1073741819` (`0xC0000005`, no JVM crash report) during M2 verification and could not be reproduced afterwards (see `docs/m2-handoff.md` § Open). If it happens: restart it, or run the packaged jar (`./mvnw -q -DskipTests package && java -jar target/11408-learning-system-server-0.0.1-SNAPSHOT.jar`).

## API surface

- Login: `POST /api/v1/auth/login` with `{"usernameOrEmail":"demo","password":"Demo123456"}` (field is `usernameOrEmail`, not `username`) → `data.accessToken`, then `Authorization: Bearer`.
- Responses are the `{code, message, data, timestamp}` envelope; `code: 0` = OK.
- Avoid non-ASCII in Git-Bash curl `-d` payloads (UTF-8 mangling → spurious 500).

## Browser surface (Playwright, no local install needed)

Playwright 1.61 + Chromium live in the npx cache. Run scripts with:

```bash
NODE_PATH="C:/Users/10529/AppData/Local/npm-cache/_npx/361ceb562f3b3235/node_modules" node script.cjs
```

(If that hash is gone, `find "$(npm config get cache)/_npx" -maxdepth 4 -name playwright -type d`.)

- Login page: fill `input:not([type="password"])` with `demo`, `input[type="password"]` with `Demo123456`, press Enter, wait for URL to leave `/login`. Default locale is zh-CN (retry button says 重试).
- **Vite cold-start warmup**: the first render of a new Element Plus component (el-select, el-date-picker, …) triggers an "optimized dependencies changed" full-page reload that aborts SPA navigation/clicks mid-flight. Before asserting, visit every touched route once AND open every dialog that mounts a new EP component, then `page.reload()`.
- Force the D3 error state with `page.route(matcher, handler)` + trigger a fetch; `unroute` needs the **same** matcher/handler references (a fresh arrow function silently fails to unregister and retry keeps failing).
- Gotcha: in list rows (e.g. `.conv-row`), the row item itself is a `<button>` — action buttons by index are off by one (nth(2) is archive, not delete).
- Gotcha: never press Escape to dismiss an EP picker panel inside an AppDialog — Esc closes the dialog itself. Click the `.el-dialog__header` instead.
- UI mutations are optimistic — after a toggle/edit, poll the API for the persisted state instead of asserting immediately.

## Data hygiene

The dev DB was deleted outside a session and recreated on 2026-10-16; the old Phase 6 / M1 data is gone. It now holds only the `demo` account and verification test data (its exact contents: `docs/m2-handoff.md` § The database). **Never reset, drop or reseed it.** Say what a run added; delete records you create where the product can (sittings via `DELETE /api/v1/sittings/{id}` — the answer log keeps its attempts by design). AI streaming without `DEEPSEEK_API_KEY` yields the graceful error reply (抱歉，这次没能获得回复，请重试。) — the send/persist path still exercises fully.

**Time zones:** Windows here runs on US Eastern time, so the JVM writes Eastern local times into `DATETIME` columns, while MySQL's `NOW()` is UTC+8. Shift time columns relative to themselves (`col = DATE_SUB(col, INTERVAL n MINUTE)`); a value computed from `NOW()` is 12 hours off.

## The exam year (M1) — timer, sittings, plan

- Routes: `/sittings` (模考), `/plan` (规划; `/calendar` is its second tab). API: `/api/v1/focus` (GET, POST start, POST `/stop`, DELETE), `/api/v1/sittings` (+ `/overview`), `/api/v1/plan`.
- The timer UI is loaded after the shell (`defineAsyncComponent`): wait for `.focus-rail` / `.focus-compact` before asserting on it.
- A recorded session needs ≥ 1 minute. To test one without waiting, backdate the running timer relative to itself: `UPDATE ai_learning.focus_timers SET started_at = DATE_SUB(started_at, INTERVAL 70 MINUTE) WHERE user_id=<demo id>` (the demo id: `SELECT id FROM ai_learning.users WHERE username='demo'`), then reload and stop.
- The demo account (recreated 2026-10-16) has no saved preferences, so the plan divides the default 8-hour day; if you change the goal on `/plan` (调整), restore it afterwards.
- Hygiene: delete sittings through `DELETE /api/v1/sittings/{id}`, the timer's sessions in the calendar (or SQL for the demo id), and restore the exam-profile targets you changed (`PUT /api/v1/exam/profile`).

## Papers sat in the app (M2)

- Routes: `/sittings?subject=<paper>` (模考 with that paper's tab and its 在线模考 launcher), `/exam/:id` (the exam stage, full-bleed), `/sittings/papers/:id` (scoring, then the report). API: `GET /api/v1/papers?subject=` (catalog: library, own, what 组卷 can make), `POST /papers/assemble {subject, sections?}`, `/paper-sessions` (POST start, `GET ?state=open`, GET `/{id}`, PUT `/{id}/answers` (whole sheet), POST `/{id}/submit`, PUT `/{id}/scores`, POST `/{id}/complete`, DELETE = abandon), `GET /content/packs`.
- One paper at a time: a run that dies mid-paper leaves it `in_progress` and every start is refused (`260006`); abandon leftovers first (`DELETE /paper-sessions/{id}` — keeps only the time sat).
- The API never reveals answers before the hand-in; for scripted correct answers read `questions.answer_key` by id from MySQL (read-only). Batch output (`mysql -N -B`) is CRLF and an open question's key is empty — never `trim()` the whole output (it eats the last row's tab).
- Library sample papers: `politics-sample-paper-01`, `english1-sample-paper-01`, `math1-sample-paper-01` (titles end in （非真题））. 408 has no library paper; assemble it.
- Stage selectors: `.sheet .option` (choice buttons, A=0), `input.response-input` / `textarea.response-input`, `.navigator .cell` (nth = number − 1), `.passage strong.marker-current` (the lit `[n]` / `(n)`), `.save` ("答案已保存"), `.head-right button` 交卷 → `.el-dialog:visible` 交卷. Keyboard: letters answer, ←/→ move, F flags.
- Result selectors: `.cards .card` (one per answer to score; `input.points`, `button.choice` 满分 / 0 分 / 对 · / 错 ·), `.scoring-foot button` (complete, after the 600 ms score save), `.report`, `.hero-value`, `.map-cell` → review drawer. **More than one `.el-drawer` is mounted** — read `.el-drawer:visible`.
- Time-up, client: `await context.clock.install()` before the page loads, sit a little, then `await context.clock.fastForward('03:01:00')` — the stage hands in and lands on the result with `?timeUp=1`.
- Time-up, server: shift a session you started back by more than its limit + 2 min (`UPDATE paper_sessions SET started_at = DATE_SUB(started_at, INTERVAL n MINUTE), deadline_at = DATE_SUB(deadline_at, INTERVAL n MINUTE) WHERE id = … AND status = 0`); the next read hands it in at the deadline, a later save gets `260008`, and the result page says 时间到. Shift by limit + 1 to test the grace (a save still counts).
- The M2 runs (2026-10-16) were two scripts, `e2e-408.cjs` (27 checks) and `e2e-subjects.cjs` (84 checks), kept outside the repo in that session's scratchpad; what they covered is listed in `docs/m2-handoff.md` § Verification — rebuild from the selectors above if they are gone.
