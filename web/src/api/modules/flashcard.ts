import { api } from '@/api/http'

/** Mirror of DeckResponse.java. `nodeCode` anchors the deck in the syllabus. */
export interface FlashcardDeckDto {
  id: string
  nodeCode: string | null
  name: string
  description: string | null
  cardCount: number
  /** In-progress cards whose review has come due. */
  dueCount: number
  /** Never-reviewed cards waiting to be learned. */
  newCount: number
}

/** Mirror of CardResponse.java. */
export interface FlashcardDto {
  id: string
  deckId: string
  front: string
  back: string
  dueAt: number | null
}

/** `nodeCode` must be a syllabus node code (any depth). */
export interface CreateDeckPayload {
  name: string
  description?: string
  nodeCode?: string
}

/**
 * Partial update — omitted fields keep their value; `nodeCode: ''` un-anchors
 * the deck from the syllabus.
 */
export interface UpdateDeckPayload {
  name?: string
  description?: string
  nodeCode?: string
}

export function listDecks() {
  return api.get<FlashcardDeckDto[]>('/v1/flashcards/decks')
}

export function createDeck(payload: CreateDeckPayload) {
  return api.post<FlashcardDeckDto>('/v1/flashcards/decks', payload)
}

export function updateDeck(deckId: string, payload: UpdateDeckPayload) {
  return api.put<FlashcardDeckDto>(`/v1/flashcards/decks/${deckId}`, payload)
}

export function deleteDeck(deckId: string) {
  return api.delete<void>(`/v1/flashcards/decks/${deckId}`)
}

export function listCards(deckId: string) {
  return api.get<FlashcardDto[]>(`/v1/flashcards/decks/${deckId}/cards`)
}

export function createCard(deckId: string, payload: { front: string; back: string }) {
  return api.post<FlashcardDto>(`/v1/flashcards/decks/${deckId}/cards`, payload)
}

export function updateCard(cardId: string, payload: { front?: string; back?: string }) {
  return api.put<FlashcardDto>(`/v1/flashcards/cards/${cardId}`, payload)
}

export function deleteCard(cardId: string) {
  return api.delete<void>(`/v1/flashcards/cards/${cardId}`)
}

// --- Review engine (Phase 15) ----------------------------------------------

/** The four-button FSRS grade. Values are the on-the-wire form (never reorder). */
export enum Grade {
  Again = 1,
  Hard = 2,
  Good = 3,
  Easy = 4,
}

/** Mirror of ReviewCardResponse.java — content only, no memory internals. */
export interface ReviewCardDto {
  id: string
  deckId: string
  front: string
  back: string
  isNew: boolean
}

/** Mirror of ReviewQueueResponse.java. */
export interface ReviewQueueDto {
  cards: ReviewCardDto[]
  dueCount: number
  newCount: number
  total: number
}

/** Mirror of GradeResponse.java — where the scheduler placed the card next. */
export interface GradeResultDto {
  cardId: string
  state: number
  dueAt: number | null
  intervalDays: number
}

/** Mirror of ReviewSummaryResponse.java — the day's review truth (client-zone). */
export interface ReviewSummaryDto {
  reviewedToday: number
  againCount: number
  hardCount: number
  goodCount: number
  easyCount: number
  dueRemaining: number
  newRemaining: number
}

/** The due queue: in-progress-due first, then capped new cards. Optionally deck-scoped. */
export function fetchReviewQueue(deckId?: string) {
  return api.get<ReviewQueueDto>('/v1/flashcards/review/queue', {
    params: deckId ? { deckId } : undefined,
  })
}

export function gradeCard(cardId: string, rating: Grade) {
  return api.post<GradeResultDto>(`/v1/flashcards/review/${cardId}`, { rating })
}

export function getReviewSummary() {
  return api.get<ReviewSummaryDto>('/v1/flashcards/review/summary')
}
