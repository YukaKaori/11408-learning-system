import { api } from '@/api/http'
import { streamTokens } from '@/api/sse'
import type { FlashcardDeckDto } from './flashcard'

/**
 * Mirror of ConversationSummaryResponse.java. `nodeCode` scopes the
 * conversation to a syllabus node; the server grounds every reply in it.
 */
export interface ConversationSummaryDto {
  id: string
  title: string
  nodeCode: string | null
  archived: boolean
  updatedAt: number
}

/** Mirror of MessageResponse.java. */
export interface AiMessageDto {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: number
  truncated: boolean
}

/** Mirror of ConversationDetailResponse.java. */
export interface ConversationDetailDto extends ConversationSummaryDto {
  messages: AiMessageDto[]
}

export function listConversations() {
  return api.get<ConversationSummaryDto[]>('/v1/ai/conversations')
}

/** Mirror of CreateConversationRequest.java. */
export interface CreateConversationPayload {
  title?: string
  nodeCode?: string
}

export function createConversation(payload: CreateConversationPayload = {}) {
  return api.post<ConversationDetailDto>('/v1/ai/conversations', payload)
}

/**
 * Mirror of SendMessageRequest.java — the body of the SSE send (posted by
 * `ServerSseChatProvider`, not axios). `nodeCode` follows the partial-update
 * convention: omitted keeps the conversation's scope, `''` clears it.
 */
export interface SendMessagePayload {
  content: string
  nodeCode?: string
}

export function getConversation(id: string) {
  return api.get<ConversationDetailDto>(`/v1/ai/conversations/${id}`)
}

export function renameConversation(id: string, title: string) {
  return api.patch<ConversationSummaryDto>(`/v1/ai/conversations/${id}`, { title })
}

export function archiveConversation(id: string, archived: boolean) {
  return api.patch<ConversationSummaryDto>(`/v1/ai/conversations/${id}`, { archived })
}

export function deleteConversation(id: string) {
  return api.delete<void>(`/v1/ai/conversations/${id}`)
}

// --- Generation -------------------------------------------------------------

/**
 * The scope of a generation request: a syllabus node code, resolved and
 * grounded server-side (exam countdown, scope, diagnosis). No free-text
 * description of a subject is ever sent.
 */
export interface NodeScope {
  nodeCode?: string
}

export interface GenerationResult {
  content: string
}

export interface StudyPlanResultDto {
  dailyTasks: string[]
  weeklyPlan: string
  reviewSchedule: string
  estimatedCompletion: string
  suggestions: string[]
}

export function generateExplain(payload: NodeScope & { topic: string }) {
  return api.post<GenerationResult>('/v1/ai/generate/explain', payload)
}

export function generateFlashcards(
  payload: NodeScope & { text?: string; deckName?: string; deckDescription?: string },
) {
  return api.post<FlashcardDeckDto>('/v1/ai/generate/flashcards', payload)
}

export function generateStudyPlan(payload: NodeScope & { goal: string; availableMinutesPerDay: number }) {
  return api.post<StudyPlanResultDto>('/v1/ai/generate/study-plan', payload)
}

export type NoteAiAction =
  | 'explain'
  | 'rewrite'
  | 'continue'
  | 'simplify'
  | 'expand'
  | 'translate'
  | 'summarize'

export function noteAiAction(payload: NodeScope & { action: NoteAiAction; text: string }) {
  return api.post<GenerationResult>('/v1/ai/notes/actions', {
    ...payload,
    action: payload.action.toUpperCase(),
  })
}

/**
 * Streaming twin of {@link noteAiAction} — same request body and prompt
 * server-side, delivered token by token. Backs the Notes selection toolbar;
 * abort by aborting `signal`.
 */
export function streamNoteAiAction(
  payload: NodeScope & { action: NoteAiAction; text: string },
  signal?: AbortSignal,
): AsyncGenerator<string, void, undefined> {
  return streamTokens(
    '/v1/ai/notes/actions/stream',
    { ...payload, action: payload.action.toUpperCase() },
    signal,
  )
}

// --- Tutoring on the practice loop -------------------------------------------
//
// Three streams, one event vocabulary (token / done / error): walking through
// a question, diagnosing a mistake, explaining a 考点. Nothing is persisted —
// an explanation is read, not stored.

/** Walks through one question; `response` lets the walk-through start from the candidate's own answer. */
export function streamQuestionExplain(
  questionId: string,
  response: string | null,
  signal?: AbortSignal,
): AsyncGenerator<string, void, undefined> {
  return streamTokens(`/v1/ai/questions/${questionId}/explain/stream`, { response }, signal)
}

/** Diagnoses one mistake from its full attempt history, cause and reflection. */
export function streamMistakeDiagnosis(
  mistakeId: string,
  signal?: AbortSignal,
): AsyncGenerator<string, void, undefined> {
  return streamTokens(`/v1/ai/mistakes/${mistakeId}/diagnose/stream`, {}, signal)
}

/** Explains a syllabus node against the candidate's own standing on it. */
export function streamPointExplain(
  nodeCode: string,
  signal?: AbortSignal,
): AsyncGenerator<string, void, undefined> {
  return streamTokens('/v1/ai/knowledge/explain/stream', { nodeCode }, signal)
}

export function generateWeeklySummary(statsSnapshot: string) {
  return api.post<GenerationResult>('/v1/ai/analytics/weekly-summary', { statsSnapshot })
}

export function generateWeakPoints(statsSnapshot: string) {
  return api.post<GenerationResult>('/v1/ai/analytics/weak-points', { statsSnapshot })
}
