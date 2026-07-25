import { AiStreamError, openSseStream, parseSseStream, parseStreamError } from '@/api/sse'
import type { SendMessagePayload } from '@/api/modules/ai'
import type { ChatMessage } from './types'

/**
 * Chat provider abstraction — the ONLY seam through which the chat UI talks
 * to a model. {@link ServerSseChatProvider} is the real DeepSeek-backed
 * implementation (via the server's AiService); the UI never changes based on
 * which provider is wired in.
 */
export interface ChatProvider {
  /** Stable identifier, e.g. 'server-sse'. */
  readonly id: string
  /**
   * Stream the assistant reply for the given history as text chunks.
   * Consumers append chunks as they arrive; the generator completing means
   * the reply is finished. Abort by breaking out of the loop.
   */
  streamReply(history: ChatMessage[]): AsyncGenerator<string, void, undefined>
  /** Cancels an in-flight {@link streamReply}, if any. No-op otherwise. */
  cancel?(): void
}

// Re-exported for existing importers; the transport itself now lives in
// `api/sse.ts`, shared with the Notes selection toolbar (Phase 16 Step 5).
export { AiStreamError }

/**
 * Real chat provider — POSTs the latest user message to the AI Tutor's
 * streaming endpoint and yields token deltas as they arrive over SSE.
 */
export class ServerSseChatProvider implements ChatProvider {
  readonly id = 'server-sse'

  private abortController: AbortController | null = null

  constructor(
    private readonly conversationId: string,
    private readonly context: Omit<SendMessagePayload, 'content'> = {},
  ) {}

  cancel() {
    this.abortController?.abort()
  }

  async *streamReply(history: ChatMessage[]): AsyncGenerator<string, void, undefined> {
    const lastUserMessage = [...history].reverse().find((message) => message.role === 'user')
    if (!lastUserMessage) return

    const payload: SendMessagePayload = {
      content: lastUserMessage.content,
      subjectName: this.context.subjectName,
      subjectDescription: this.context.subjectDescription,
      subjectId: this.context.subjectId,
    }

    this.abortController = new AbortController()
    const reader = await openSseStream(
      `/v1/ai/conversations/${this.conversationId}/messages`,
      payload,
      this.abortController.signal,
    )
    try {
      for await (const event of parseSseStream(reader)) {
        if (event.event === 'token') {
          yield event.data
        } else if (event.event === 'error') {
          throw parseStreamError(event.data)
        } else if (event.event === 'done') {
          return
        }
      }
    } finally {
      this.abortController = null
    }
  }
}
