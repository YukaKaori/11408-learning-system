# Phase 16 Plan — Notes 2.0: The Knowledge Workspace

**Status:** APPROVED architecture (2026-07-24). Implementation in progress,
step by step, each stopping for approval. This document is the **implementation
contract** — the architecture is not to be redesigned and scope is not to be
expanded; already-made decisions are revisited only if a correctness issue is
discovered.

**Binding context:** `docs/roadmap.md` (Phase 16), `docs/architecture.md`,
`docs/design-system.md`, `.claude/skills/optical-glass-design-system/SKILL.md`.
Phase 15 (Memory Engine) is complete and untouched.

---

## Product goal (one line)

Transform Notes from a read-only CRUD box into the **Knowledge Workspace** — the
loop's *Capture* stage and the corpus every Intelligence-era feature stands on:
a real editor, `[[wiki-links]]` + backlinks, autosave, and inline AI, with
markdown kept as the storage format.

## Knowledge Workspace layout

Three columns, re-roled around thinking:

- **Left — Capture rail (solid):** note list, search, pinned-first, subject filter.
- **Center — The Canvas (solid, content-primary):** the real editor; *the work*,
  so emphatically solid — glass never touches it.
- **Right — Context rail (solid):** Outline (kept) + **Backlinks (new)**; two
  reserved-but-empty slots — **Related notes** (P18) and **Cards from this note**
  (future) — architected now, filled later.
- **Floating GLASS selection toolbar** (on selection only): the one new glass surface.

---

## Scope lock (v1 cut-list — binding)

**In scope (v1 editor schema — a small, closed, losslessly-serializable set):**
headings h1–h3, paragraph, bold/italic/inline-code/strikethrough, link,
bullet + ordered list, task list (checkboxes), blockquote, fenced code block,
horizontal rule, and the custom **wiki-link** inline node (`[[text]]`). External
image markdown round-trips (renders).

**Out of scope (deferred — do not build this phase):**
- Tables
- Embeds beyond external image markdown
- **Image upload** (needs P20 `StorageService`) — external image *render* only
- Collaboration / CRDT (explicitly refused; LWW autosave is not a collab base)
- Retrieval-grounding of AI, citations, AI memory — **P18** (see AI boundaries)
- Knowledge-graph visualization — 2.0
- Any redesign of another module

## AI collaboration boundaries

1. AI acts on a **selection or block**, never silently on the whole note.
2. AI **proposes; the user disposes** — every edit is Accept/Discard-confirmed
   before it enters the note or triggers autosave. No autonomous writes.
3. Applied **in place**, not in a modal (replace selection / insert below / append).
4. **AI chrome is glass; AI content is solid & legible.**
5. **No ambient AI** in the writing surface — the environment stays calm.
6. **Grounding boundary:** P16 keeps the *current ungrounded* action semantics
   (selection text + subject-name hint). Retrieval/citations/memory are **P18**.
   P16 relocates AI inline; it does not make it smarter.

## Optical Glass boundary

- **Appears:** exactly one new displacement-filter surface — the **floating
  selection toolbar**, built on the existing `GlassSurface` + `.glass-material`,
  low density / low edge energy, mounted only while a selection exists; it
  expands in place to stream an AI result (glass frame, **solid content inset**).
  **Displacement-filter budget: 2 → 3.**
- **Must NOT appear:** the editor canvas/body, the list rail, the context rail,
  the slash menu, the `[[` autocomplete (all solid). No second new glass this phase.
- `AppDialog`'s frost is the pre-existing CSS `--glass-*` bridge, **not** a
  displacement filter — it does not count against the budget and is unchanged.

---

## Engineering decisions (locked in Step 0)

### Editor dependency (the era's one sanctioned heavy FE dependency)
The **TipTap v3 stack on ProseMirror**, code-split into the notes route:

| Package | Version | Role |
| --- | --- | --- |
| `@tiptap/vue-3` | ^3.28.0 | Vue 3 editor bindings |
| `@tiptap/pm` | ^3.28.0 | ProseMirror primitives bundle (schema, decorations, state) |
| `@tiptap/starter-kit` | ^3.28.0 | Base nodes/marks + markdown input rules |
| `tiptap-markdown` | ^0.9.0 | Markdown parse (markdown-it) + serialize round-trip |

### Markdown serialization strategy (FINALIZED)
**Markdown remains the storage format; the editor is a round-trip view.**
```
load:  notes.content (md) ──parse──► ProseMirror doc ──► TipTap render
save:  TipTap doc ──serialize──► md ──► PUT /v1/notes/{id} { content }
```
- **Primary: `tiptap-markdown@0.9.0`** (declares peer `@tiptap/core ^3.0.1` —
  verified TipTap-v3-compatible), **gated behind round-trip property tests**
  in Step 3 (`md → doc → md` must be stable for the v1 schema).
- **Fallback (documented, not installed): `prosemirror-markdown`** configured
  against the TipTap schema, if fidelity fails the Step 3 gate.

### Route-level code-split (CONFIRMED)
`router/index.ts` already lazy-loads every feature route, including
`notes → () => import('@/features/notes/NotesView.vue')`. Because no other route
imports TipTap, the editor stack will resolve into the notes route chunk (or a
shared editor chunk), never the main `index-*.js` bundle. Verified: the notes
route already emits a distinct `NotesView-*.js` chunk. No router change needed.

### Autosave contract
Debounced (~1s) + on blur + route-leave guard + `beforeunload`; **single
in-flight save, last-write-wins** (no optimistic-concurrency version); visible
state `Saving… / Saved / Save failed — retry`; reuses the existing partial,
idempotent `PUT /v1/notes/{id}`.

---

## Backend design (Step 1–2, not yet built)

**V7 `note_links`** — a derived index maintained on save:
```
id, user_id, source_note_id, target_note_id (NULL = dangling),
target_title, + audit columns
KEY (source_note_id) · KEY (target_note_id) · KEY (user_id, target_title)
```
- Rebuild on `@Transactional` `NoteService.create/update`: physical
  `deleteBySourceNoteId` (real DELETE — a derived index, not soft-delete
  tombstones; a documented exception) + re-extract `\[\[([^\]]+)\]\]` (dedup per
  distinct target title) + resolve titles against the user's notes (latest-updated
  wins; unmatched → NULL).
- **Backlinks:** `GET /api/v1/notes/{id}/backlinks` → sources where
  `user_id=? AND (target_note_id=? OR (target_note_id IS NULL AND target_title = <this title>))`.
  Title-fallback covers "linked before the target existed" without cross-note writes.
- `OwnershipGuard` on every path. No new error codes (reuse 130000/130001;
  dangling is a valid state, not an error). `[[` autocomplete + forward-link
  resolution use the already-loaded client note list — no `titles` endpoint.

---

## Implementation steps (each stops for approval)

| Step | Deliverable |
| --- | --- |
| **0 — Scope & deps lock** ✅ | Deps added + code-split confirmed + strategy finalized + this doc + clean build. |
| **1 — V7 backend schema** | `note_links` migration + entity + mapper (incl. physical `deleteBySourceNoteId`) + H2 mirror. |
| **2 — Link extraction + backlinks** | Link rebuild in `@Transactional` create/update; backlinks endpoint; ownership; unit tests. |
| **3 — Editor core** | TipTap + markdown round-trip + input rules + autosave; remove the `<textarea>`; round-trip property tests. |
| **4 — Wiki-links + context rail** | `[[` autocomplete (solid), link decoration + navigation, slash menu (solid), Backlinks panel + Outline. |
| **5 — Glass toolbar + inline AI** | `GlassSurface` selection toolbar, expand-to-stream result, Accept/Discard in place; rewire `NOTE_*` inline; delete modal AI path; filter-budget assertion ≤3. |
| **6 — Workspace polish + locales** | Three-column finish, empty/loading/error states, remove old preview UI, zh-CN/en-US parity, responsive + reduced-motion. |
| **7 — Release gate + handoff** | Full `verify`-skill run, type-check/lint/unit/build, `docs/phase16-handoff.md`. |

## Verification gates (per roadmap)
Round-trip property tests (md→doc→md); Playwright (write, link, backlink nav,
autosave survives reload, inline AI edits in place); locale parity; **filter
budget assertion ≤3**; reduced-motion + both themes; `verify` skill end-to-end.
