/**
 * The plan — how the candidate's time reaches the exam date. A read model:
 * owns no tables and computes per request from the exam profile (date, phase,
 * targets), the daily-hours preference, the sittings' paper estimates and the
 * study sessions (time actually spent).
 *
 * <p>It answers three questions the 考点-level loop cannot: how today's hours
 * divide among the four papers ({@link com.yuka.learning.plan.PlanModel}), how
 * many whole papers this week the phase calls for, and where the year stands
 * between now and the two exam days. Nothing it shows is stored, so it moves
 * the moment the evidence does — a sitting logged, a target changed, an hour
 * timed. No error-code range: it composes other modules' errors.
 */
package com.yuka.learning.plan;
