/**
 * Flashcards — spaced-repetition memory training. Decks group cards and
 * optionally belong to a subject.
 *
 * <p>Phase 6 adds real CRUD ({@link com.yuka.learning.flashcard.FlashcardService},
 * {@link com.yuka.learning.flashcard.FlashcardController}), including
 * {@code createDeckFromGenerated} — the persistence target the {@code ai}
 * package writes to when the user generates a deck. The card schema still
 * reserves the scheduling columns ({@code due_at}, {@code interval_days},
 * {@code ease}) for a future review engine (SM-2 or FSRS) — no migration
 * required when it lands. Reserved error-code range: 140000–149999.
 */
package com.yuka.learning.flashcard;
