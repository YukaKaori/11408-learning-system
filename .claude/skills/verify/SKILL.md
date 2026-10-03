---
name: verify
description: Build, launch and drive the 11408 Learning System (Spring Boot :8080 + Vite :5173) to verify changes at the real surface — API via curl, UI via Playwright from the npx cache.
---

# Verifying the 11408 Learning System

## Launch

- Backend: `cd server && ./mvnw -q spring-boot:run` (JDK 22, MySQL 9.1 running locally, DB `ai_learning`). Ready when `curl http://localhost:8080/api/v1/system/info` returns 200.
- Frontend: `cd web && npm run dev` → http://localhost:5173.
- Ports often stay held after stopping tasks: `netstat -ano | grep -E ':(8080|5173).*LISTEN'` then `taskkill //PID <pid> //F` (Git Bash).

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

Dev DB is shared and NOT clean: two Phase 6 notes (未命名笔记, Verify Note) and one conversation (Hello, explain recursion) are deliberately kept. Delete anything you create (UI or `DELETE` via API) and re-verify the list afterwards. AI streaming without `DEEPSEEK_API_KEY` yields the graceful error reply (抱歉，这次没能获得回复，请重试。) — the send/persist path still exercises fully.

## The exam year (M1) — timer, sittings, plan

- Routes: `/sittings` (模考), `/plan` (规划; `/calendar` is its second tab). API: `/api/v1/focus` (GET, POST start, POST `/stop`, DELETE), `/api/v1/sittings` (+ `/overview`), `/api/v1/plan`.
- The timer UI is loaded after the shell (`defineAsyncComponent`): wait for `.focus-rail` / `.focus-compact` before asserting on it.
- A recorded session needs ≥ 1 minute. To test one without waiting, backdate the running timer: `UPDATE ai_learning.focus_timers SET started_at = DATE_SUB(NOW(), INTERVAL 70 MINUTE) WHERE user_id=<demo id>` (the demo id: `SELECT id FROM ai_learning.users WHERE username='demo'`), then reload and stop.
- The demo account saved preferences before V10, so its daily goal is the old 60 minutes and the plan divides one hour; change it on `/plan` (调整) and restore it afterwards.
- Hygiene: delete sittings through `DELETE /api/v1/sittings/{id}`, the timer's sessions in the calendar (or SQL for the demo id), and restore the exam-profile targets you changed (`PUT /api/v1/exam/profile`).
