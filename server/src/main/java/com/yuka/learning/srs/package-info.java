/**
 * The spaced-repetition memory engine — one scheduler for everything a
 * candidate must not forget.
 *
 * <p>The core is {@link com.yuka.learning.srs.ReviewScheduler} — a <em>pure,
 * deterministic</em> function from an item's current
 * {@link com.yuka.learning.srs.SchedulingState scheduling state} plus a
 * {@link com.yuka.learning.srs.Rating grade} to its next scheduling state. It
 * performs no I/O and holds no per-request state, so it is exhaustively
 * unit-testable and trivially cacheable.
 *
 * <p>The sole implementation, {@link com.yuka.learning.srs.Fsrs6Scheduler}, is a
 * faithful transcription of FSRS-6 (the memory-state model and learning-step
 * state machine of the {@code open-spaced-repetition/py-fsrs} reference), using
 * the published default 21-parameter weight vector. Fuzzing is intentionally
 * omitted so scheduling is fully deterministic.
 *
 * <p><strong>Two consumers, two parameter sets.</strong> The package was born
 * inside {@code flashcard} (Phase 15) and moved here when the 11408 transformation
 * gave it a second consumer:
 * <ul>
 *   <li>{@code flashcard} — memory cards, with FSRS's sub-day learning steps
 *       (1m / 10m), because a card is re-shown inside one sitting;</li>
 *   <li>{@code mistake} — wrong-answer re-attempts (错题重做), with <em>no</em>
 *       sub-day steps, because re-solving a question minutes after reading its
 *       solution proves nothing; the first redo lands on a later day.</li>
 * </ul>
 * Both consumers share this exact memory model, so "how well do I remember
 * this" means the same thing for a fact on a card and for a method in a
 * question. Neither consumer ever forks the math.
 *
 * <p>The interface is the evolution seam: a future re-tuning, a newer FSRS
 * version, or per-user optimized weights swap in behind {@code ReviewScheduler}
 * without touching callers, endpoints, or the frontend.
 */
package com.yuka.learning.srs;
