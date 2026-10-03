import { CLIENT_TIMEZONE, refreshTokenAfterUnauthorized } from '@/api/http'
import { tokenStorage } from '@/api/token-storage'

/**
 * Shared SSE transport for the server's streaming AI endpoints.
 *
 * Extracted from `features/ai-tutor/provider.ts` in Phase 16 Step 5, once a
 * second real consumer appeared (the Notes selection toolbar) — per the
 * constitution's "shared code needs ≥2 real consumers" rule. Both streams
 * speak the identical event vocabulary, so one reader serves both:
 *
 *   `token` → raw text delta · `done` → finish reason · `error` → the standard
 *   `ApiResponse` failure envelope.
 *
 * Uses `fetch`/`ReadableStream` rather than the axios helpers in `api/http.ts`,
 * because axios exposes no live stream for a POST body. The one 401 case reuses
 * the same single-flight refresh axios's interceptor uses.
 */

/** Structured payload of an `error` SSE event — mirrors ApiResponse.java's failure shape. */
export class AiStreamError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'AiStreamError'
    this.code = code
  }
}

interface SseEvent {
  event: string
  data: string
}

/** Minimal SSE frame parser for a `ReadableStream<Uint8Array>` — handles multi-line `data:` blocks. */
export async function* parseSseStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
): AsyncGenerator<SseEvent> {
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let separator: number
    while ((separator = buffer.indexOf('\n\n')) !== -1) {
      const block = buffer.slice(0, separator)
      buffer = buffer.slice(separator + 2)
      yield parseEventBlock(block)
    }
  }
}

export function parseEventBlock(block: string): SseEvent {
  let event = 'message'
  const dataLines: string[] = []
  for (const rawLine of block.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  return { event, data: dataLines.join('\n') }
}

export function parseStreamError(data: string): AiStreamError {
  try {
    const parsed = JSON.parse(data) as { code?: number; message?: string }
    return new AiStreamError(parsed.code ?? -1, parsed.message ?? 'AI stream error')
  } catch {
    return new AiStreamError(-1, data || 'AI stream error')
  }
}

/** Authorized SSE POST, retried once through the shared refresh on a 401. */
export async function openSseStream(
  path: string,
  body: unknown,
  signal?: AbortSignal,
): Promise<ReadableStreamDefaultReader<Uint8Array>> {
  const url = `${import.meta.env.VITE_API_BASE_URL}${path}`
  const payload = JSON.stringify(body)

  let response = await postSse(url, payload, signal)
  if (response.status === 401) {
    await refreshTokenAfterUnauthorized()
    response = await postSse(url, payload, signal)
  }
  if (!response.ok || !response.body) {
    throw new AiStreamError(-1, `AI stream request failed (${response.status})`)
  }
  return response.body.getReader()
}

function postSse(url: string, body: string, signal?: AbortSignal): Promise<Response> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${tokenStorage.getAccessToken() ?? ''}`,
  }
  // The tutor's context includes the exam countdown, which is the candidate's
  // day, not the server's — same header the axios interceptor sends.
  if (CLIENT_TIMEZONE) {
    headers['X-Client-Timezone'] = CLIENT_TIMEZONE
  }
  return fetch(url, { method: 'POST', headers, body, signal })
}

/**
 * Token deltas of one streaming AI call. Completes on `done`; throws
 * {@link AiStreamError} on `error`. Abort by aborting the supplied signal.
 */
export async function* streamTokens(
  path: string,
  body: unknown,
  signal?: AbortSignal,
): AsyncGenerator<string, void, undefined> {
  const reader = await openSseStream(path, body, signal)
  for await (const event of parseSseStream(reader)) {
    if (event.event === 'token') {
      yield event.data
    } else if (event.event === 'error') {
      throw parseStreamError(event.data)
    } else if (event.event === 'done') {
      return
    }
  }
}
