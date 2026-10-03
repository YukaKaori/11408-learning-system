/**
 * Flashcards — spaced-repetition memory training. Mirrors the backend
 * `flashcard_decks` / `flashcards` tables; scheduling state stays opaque to
 * the UI until the review engine lands.
 */

export interface FlashcardDeck {
  id: string
  /** Syllabus anchor (any depth). */
  nodeCode?: string
  name: string
  description?: string
  cardCount: number
  dueCount: number
}

export interface Flashcard {
  id: string
  deckId: string
  /** Prompt side (markdown-ish plain text for now). */
  front: string
  /** Answer side. */
  back: string
  /** Epoch ms; undefined = new card. */
  dueAt?: number
}
