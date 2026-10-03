/**
 * Workspace — the aggregation façade behind the learning dashboard
 * ("continue learning", today's goal, due cards, streak). It owns NO tables
 * and NO entities: it composes read models over subject, material, note,
 * flashcard, task, calendar, preferences, analytics and AI conversations,
 * and must never be written to directly.
 *
 * <p>Implemented in Phase 7 as a single aggregate endpoint
 * ({@code GET /api/v1/workspace/summary} — one round trip, one loading
 * state). Phase 17 adds {@code GET /api/v1/workspace/today}, the ordered
 * action plan; it is a second composition over the same domains and likewise
 * owns nothing. The two endpoints are deliberately independent — Today's plan
 * must never wait on the dashboard's ledger — and the small duplicated
 * computation (goal, streak) is the accepted price of that independence.
 *
 * <p>Reserved error-code range: 170000–179999 (still unused — the read models
 * have no failure modes of their own).
 */
package com.yuka.learning.workspace;
