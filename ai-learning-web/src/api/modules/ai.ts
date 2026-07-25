import { api } from '@/api/http'
import { streamTokens } from '@/api/sse'
import type { FlashcardDeckDto } from './flashcard'

/**
 * Mirror of ConversationSummaryResponse.java. `subjectId` links the
 * conversation to a real subject; `subjectName` is a display snapshot taken
 * when the link was made (see docs/ai-engine.md).
 */
export interface ConversationSummaryDto {
  id: string
  title: string
  subjectId: string | null
  subjectName: string | null
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

/**
 * Mirror of CreateConversationRequest.java. `subjectId` must reference a
 * subject owned by the caller; when present the server persists the link and
 * snapshots the real subject's name, ignoring the plain-text `subjectName`
 * hint (kept for callers without an id).
 */
export interface CreateConversationPayload {
  title?: string
  subjectName?: string
  subjectId?: string
}

export function createConversation(payload: CreateConversationPayload = {}) {
  return api.post<ConversationDetailDto>('/v1/ai/conversations', payload)
}

/**
 * Mirror of SendMessageRequest.java — the body of the SSE send (posted by
 * `ServerSseChatProvider`, not axios). `subjectId` follows the partial-update
 * convention: omitted/null keeps the conversation's current link, `''`
 * unlinks it. The name/description hints never override a resolved subject.
 */
export interface SendMessagePayload {
  content: string
  subjectName?: string
  subjectDescription?: string
  subjectId?: string
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
 * Plain-text subject hints for the generation endpoints, which don't take a
 * subject id — conversations do (see {@link SendMessagePayload}).
 */
export interface SubjectContext {
  subjectName?: string
  subjectDescription?: string
}

export interface GenerationResult {
  content: string
}

export interface QuizQuestionDto {
  question: string
  options: string[]
  answer: string
  explanation: string
}

export interface QuizResultDto {
  questions: QuizQuestionDto[]
}

export interface StudyPlanResultDto {
  dailyTasks: string[]
  weeklyPlan: string
  reviewSchedule: string
  estimatedCompletion: string
  suggestions: string[]
}

export function generateExplain(payload: SubjectContext & { topic: string }) {
  return api.post<GenerationResult>('/v1/ai/generate/explain', payload)
}

export function generateSummary(payload: SubjectContext & { text: string }) {
  return api.post<GenerationResult>('/v1/ai/generate/summary', payload)
}

export function generateSuggestions(payload: SubjectContext) {
  return api.post<GenerationResult>('/v1/ai/generate/suggestions', payload)
}

export function generateQuiz(payload: SubjectContext & { text?: string }) {
  return api.post<QuizResultDto>('/v1/ai/generate/quiz', payload)
}

export function generateFlashcards(
  payload: SubjectContext & { text?: string; deckName?: string; deckDescription?: string },
) {
  return api.post<FlashcardDeckDto>('/v1/ai/generate/flashcards', payload)
}

export function generateStudyPlan(payload: {
  goal: string
  availableMinutesPerDay: number
  subjects?: string[]
}) {
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

export function noteAiAction(payload: SubjectContext & { action: NoteAiAction; text: string }) {
  return api.post<GenerationResult>('/v1/ai/notes/actions', {
    ...payload,
    action: payload.action.toUpperCase(),
  })
}

/**
 * Streaming twin of {@link noteAiAction} — same request body and the same
 * ungrounded prompt server-side, delivered token by token. Backs the Notes
 * selection toolbar (Phase 16 Step 5); abort by aborting `signal`.
 */
export function streamNoteAiAction(
  payload: SubjectContext & { action: NoteAiAction; text: string },
  signal?: AbortSignal,
): AsyncGenerator<string, void, undefined> {
  return streamTokens(
    '/v1/ai/notes/actions/stream',
    { ...payload, action: payload.action.toUpperCase() },
    signal,
  )
}

export function generateWeeklySummary(statsSnapshot: string) {
  return api.post<GenerationResult>('/v1/ai/analytics/weekly-summary', { statsSnapshot })
}

export function generateWeakPoints(statsSnapshot: string) {
  return api.post<GenerationResult>('/v1/ai/analytics/weak-points', { statsSnapshot })
}
