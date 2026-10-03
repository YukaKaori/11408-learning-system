import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { API_SUCCESS_CODE, ApiError, type ApiResponse } from './types'
import { messageKeyFor } from './errorKeys'
import { tokenStorage } from './token-storage'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15_000,
})

/**
 * The caller's IANA timezone, resolved once. The backend uses it purely for
 * day-bucketing (the flashcard new-card cap, "reviewed today", the live due
 * count); a missing/invalid value falls back to the server zone, so this is a
 * best-effort hint, never required.
 */
export const CLIENT_TIMEZONE = (() => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone
  } catch {
    return ''
  }
})()

http.interceptors.request.use((config) => {
  const token = tokenStorage.getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (CLIENT_TIMEZONE) {
    config.headers['X-Client-Timezone'] = CLIENT_TIMEZONE
  }
  return config
})

// ---------------------------------------------------------------------------
// Silent refresh
//
// On a 401 the interceptor rotates the refresh token once (single-flight — all
// concurrent 401s await the same rotation) and replays the failed request.
// When rotation itself fails the session is over: tokens are cleared and the
// handler registered by the router redirects to the login page.
// ---------------------------------------------------------------------------

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

/** Endpoints whose 401s are final — refreshing could not help. */
const NO_REFRESH_URLS = ['/v1/auth/login', '/v1/auth/refresh']

let onSessionExpired: (() => void) | null = null

/** Registered once by the router guards; invoked when the session is unrecoverable. */
export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler
}

interface RefreshResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

let refreshInFlight: Promise<void> | null = null

async function refreshTokens(): Promise<void> {
  const refreshToken = tokenStorage.getRefreshToken()
  if (!refreshToken) {
    throw new ApiError(-1, 'No refresh token', 'auth.error.sessionExpired')
  }
  // Bare axios call: must not pass through these interceptors.
  const { data: envelope } = await axios.post<ApiResponse<RefreshResult>>(
    `${import.meta.env.VITE_API_BASE_URL}/v1/auth/refresh`,
    { refreshToken },
    { timeout: 15_000 },
  )
  if (envelope.code !== API_SUCCESS_CODE) {
    throw new ApiError(envelope.code, envelope.message, 'auth.error.sessionExpired')
  }
  tokenStorage.set(envelope.data)
}

function ensureRefreshed(): Promise<void> {
  refreshInFlight ??= refreshTokens().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

http.interceptors.response.use(undefined, async (error: unknown) => {
  if (!(error instanceof AxiosError) || error.response?.status !== 401) {
    throw error
  }
  const config = error.config as RetriableConfig | undefined
  if (!config || config._retried || NO_REFRESH_URLS.some((url) => config.url?.includes(url))) {
    throw error
  }
  if (!tokenStorage.hasSession()) {
    onSessionExpired?.()
    throw error
  }
  try {
    await ensureRefreshed()
  } catch (refreshError) {
    tokenStorage.clear()
    onSessionExpired?.()
    throw refreshError
  }
  config._retried = true
  return http.request(config)
})

function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }
  if (error instanceof AxiosError) {
    if (error.code === AxiosError.ECONNABORTED) {
      return new ApiError(-1, error.message, 'error.timeout')
    }
    const body = error.response?.data as Partial<ApiResponse<unknown>> | undefined
    if (body && typeof body.code === 'number') {
      return new ApiError(body.code, body.message ?? error.message, messageKeyFor(body.code))
    }
    if (error.response) {
      return new ApiError(-1, error.message, 'error.server')
    }
    return new ApiError(-1, error.message, 'error.network')
  }
  return new ApiError(-1, error instanceof Error ? error.message : String(error), 'error.unknown')
}

async function unwrap<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  try {
    const { data: envelope } = await request
    if (envelope.code !== API_SUCCESS_CODE) {
      throw new ApiError(envelope.code, envelope.message, messageKeyFor(envelope.code))
    }
    return envelope.data
  } catch (error) {
    throw toApiError(error)
  }
}

/** Typed request helpers — every endpoint module goes through these. */
export const api = {
  get: <T>(url: string, config?: AxiosRequestConfig) =>
    unwrap<T>(http.get<ApiResponse<T>>(url, config)),
  post: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    unwrap<T>(http.post<ApiResponse<T>>(url, body, config)),
  put: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    unwrap<T>(http.put<ApiResponse<T>>(url, body, config)),
  patch: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    unwrap<T>(http.patch<ApiResponse<T>>(url, body, config)),
  delete: <T>(url: string, config?: AxiosRequestConfig) =>
    unwrap<T>(http.delete<ApiResponse<T>>(url, config)),
}

/**
 * For the one non-axios caller (the AI Tutor's SSE fetch, which needs a raw
 * `ReadableStream` axios can't give it): rotates the refresh token via the
 * same single-flight logic the 401 interceptor uses, so a 401 mid-stream is
 * recovered the same way a normal API call's 401 would be.
 */
export async function refreshTokenAfterUnauthorized(): Promise<string> {
  try {
    await ensureRefreshed()
  } catch (error) {
    tokenStorage.clear()
    onSessionExpired?.()
    throw error
  }
  return tokenStorage.getAccessToken() ?? ''
}
