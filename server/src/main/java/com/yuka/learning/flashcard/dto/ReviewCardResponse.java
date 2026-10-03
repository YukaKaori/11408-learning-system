package com.yuka.learning.flashcard.dto;

import com.yuka.learning.flashcard.entity.Flashcard;

/**
 * A card as presented in a review session — content only, no memory internals.
 * The scheduling state (stability, difficulty, due date) is the engine's private
 * concern; the reviewer only needs the two faces and whether this is a first
 * encounter (so the UI can mark it "new").
 *
 * @param isNew true when the card has never been reviewed (a new-card introduction)
 */
public record ReviewCardResponse(String id, String deckId, String front, String back, boolean isNew) {

    public static ReviewCardResponse from(Flashcard card) {
        return new ReviewCardResponse(
                String.valueOf(card.getId()),
                String.valueOf(card.getDeckId()),
                card.getFront(),
                card.getBack(),
                card.getLastReviewedAt() == null);
    }
}
