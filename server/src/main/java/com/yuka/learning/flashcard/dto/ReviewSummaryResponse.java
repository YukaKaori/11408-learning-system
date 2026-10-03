package com.yuka.learning.flashcard.dto;

/**
 * The day's review truth for the current user, bucketed in the caller's timezone.
 * The server owns these numbers so every client agrees on "today"; a session
 * screen can render its recap from this rather than trusting a local tally.
 *
 * @param reviewedToday total graded reviews today (any grade, incl. repeats of the same card)
 * @param againCount    today's reviews graded "again"
 * @param hardCount     today's reviews graded "hard"
 * @param goodCount     today's reviews graded "good"
 * @param easyCount     today's reviews graded "easy"
 * @param dueRemaining  in-progress cards still due right now (not yet reviewed away)
 * @param newRemaining  new-card introductions still allowed today under the cap
 */
public record ReviewSummaryResponse(int reviewedToday, int againCount, int hardCount, int goodCount,
                                    int easyCount, int dueRemaining, int newRemaining) {
}
