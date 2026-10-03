package com.yuka.learning.flashcard.dto;

import com.yuka.learning.flashcard.entity.Flashcard;

import java.time.ZoneId;

public record CardResponse(String id, String deckId, String front, String back, Long dueAt) {

    public static CardResponse from(Flashcard card) {
        return new CardResponse(
                String.valueOf(card.getId()),
                String.valueOf(card.getDeckId()),
                card.getFront(),
                card.getBack(),
                card.getDueAt() != null
                        ? card.getDueAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : null);
    }
}
