package com.yuka.learning.flashcard.dto;

import java.util.List;

/**
 * The cards to review right now, in study order: in-progress cards that have come
 * due first, then the day's remaining brand-new introductions (capped by
 * {@code app.flashcard.new-cards-per-day}).
 *
 * @param cards    the ordered queue (due-in-progress first, then new)
 * @param dueCount how many of {@code cards} are in-progress cards that fell due
 * @param newCount how many of {@code cards} are new-card introductions this batch
 * @param total    convenience: {@code cards.size()}
 */
public record ReviewQueueResponse(List<ReviewCardResponse> cards, int dueCount, int newCount, int total) {

    public static ReviewQueueResponse of(List<ReviewCardResponse> due, List<ReviewCardResponse> fresh) {
        List<ReviewCardResponse> all = new java.util.ArrayList<>(due.size() + fresh.size());
        all.addAll(due);
        all.addAll(fresh);
        return new ReviewQueueResponse(List.copyOf(all), due.size(), fresh.size(), all.size());
    }
}
