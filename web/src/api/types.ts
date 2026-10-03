/** Mirror of the backend response envelope (see ApiResponse.java). */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export const API_SUCCESS_CODE = 0

/**
 * Normalized error thrown by the HTTP layer.
 * `messageKey` is an i18n key for user-facing display; `message` carries the
 * raw backend message for logging and debugging.
 */
export class ApiError extends Error {
  readonly code: number
  readonly messageKey: string

  constructor(code: number, message: string, messageKey: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.messageKey = messageKey
  }
}

/**
 * Normalizes any thrown value to an ApiError so error surfaces can always
 * render `t(error.messageKey)`. The http layer already rejects with ApiError;
 * this covers everything else (programming errors, non-axios rejections).
 */
export function toApiError(caught: unknown): ApiError {
  if (caught instanceof ApiError) {
    return caught
  }
  return new ApiError(-1, caught instanceof Error ? caught.message : String(caught), 'error.unknown')
}
