package com.yuka.learning.mistake.dto;

import com.yuka.learning.question.dto.QuestionResponse;

/**
 * One entry of the mistake book, with its question as asked (the solution is
 * on the detail view, so browsing the book is itself a retrieval exercise).
 *
 * @param status        {@code active} or {@code resolved}
 * @param correctStreak consecutive due-day correct redos so far
 * @param resolveStreak the streak that resolves it — the client renders "2 / 3"
 * @param dueAt         epoch ms; the redo is due on this calendar day
 * @param due           whether it is due today in the caller's timezone
 */
public record MistakeResponse(
        String id,
        QuestionResponse question,
        String status,
        String cause,
        String note,
        int wrongCount,
        int redoCount,
        int correctStreak,
        int resolveStreak,
        long dueAt,
        boolean due,
        long lastAttemptAt,
        long createdAt,
        Long resolvedAt) {
}
