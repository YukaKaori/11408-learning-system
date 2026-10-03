package com.yuka.learning.flashcard.dto;

import com.yuka.learning.flashcard.entity.Flashcard;

import java.time.ZoneId;

/**
 * The outcome of grading a card: where the scheduler placed it next. Lets a
 * session view show the interval the grade earned ("next in 3 days") and advance.
 *
 * @param state        resulting FSRS phase: 1=learning, 2=review, 3=relearning
 * @param dueAt        next due instant as epoch millis
 * @param intervalDays whole-day interval assigned (0 while still in sub-day learning steps)
 */
public record GradeResponse(String cardId, int state, Long dueAt, int intervalDays) {

    public static GradeResponse from(Flashcard card) {
        return new GradeResponse(
                String.valueOf(card.getId()),
                card.getState(),
                card.getDueAt() != null
                        ? card.getDueAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : null,
                card.getIntervalDays() != null ? card.getIntervalDays() : 0);
    }
}
