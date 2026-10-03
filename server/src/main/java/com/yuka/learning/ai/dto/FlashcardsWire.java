package com.yuka.learning.ai.dto;

import java.util.List;

/** Internal — parsed from the model's JSON output, then persisted via FlashcardService. */
public record FlashcardsWire(List<Card> cards) {

    public record Card(String front, String back) {
    }
}
