package com.yuka.learning.ai.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@code FLASHCARDS} prompt contract (Phase 15, Step 5). Two things
 * must never silently drift: the exact JSON shape {@code AiGenerationService}
 * parses into {@code FlashcardsWire}, and the spaced-repetition quality rules
 * that make generated cards actually schedulable (atomic, brief answers).
 */
class PromptTemplateTest {

    @Test
    void flashcardsPromptKeepsTheParseableJsonContract() {
        String prompt = PromptTemplate.FLASHCARDS.systemPrompt();

        assertThat(PromptTemplate.FLASHCARDS.structuredJson()).isTrue();
        // The keys FlashcardsWire (cards[].front/back) is deserialized from.
        assertThat(prompt).contains("\"cards\"", "\"front\"", "\"back\"");
        // The model must emit bare JSON — stripCodeFence tolerates a fence, but
        // the instruction not to add prose/markdown protects the parser.
        assertThat(prompt).contains("JSON").contains("markdown");
    }

    @Test
    void flashcardsPromptEncodesSchedulableCardRules() {
        String prompt = PromptTemplate.FLASHCARDS.systemPrompt();

        // Atomicity (one fact per card) and answer-side brevity are the two
        // properties a real scheduler depends on.
        assertThat(prompt).contains("原子化");
        assertThat(prompt).contains("只考察一个知识点");
        assertThat(prompt).contains("答案简洁");
    }
}
