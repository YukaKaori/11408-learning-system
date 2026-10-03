package com.yuka.learning.flashcard.dto;

import com.yuka.learning.flashcard.entity.FlashcardDeck;

public record DeckResponse(String id, String nodeCode, String name, String description, int cardCount,
                            int dueCount, int newCount) {

    public static DeckResponse from(FlashcardDeck deck, int cardCount, int dueCount, int newCount) {
        return new DeckResponse(
                String.valueOf(deck.getId()),
                deck.getNodeCode(),
                deck.getName(),
                deck.getDescription(),
                cardCount,
                dueCount,
                newCount);
    }
}
