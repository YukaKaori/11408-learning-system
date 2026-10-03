import { api } from '@/api/http'

/**
 * Mirror of AuthUserResponse.java — the snowflake id arrives as a string.
 * `createdAt` is epoch milliseconds ("member since").
 */
export interface AuthUser {
  id: string
  username: string
  email: string
  nickname: string | null
  avatar: string | null
  createdAt: number
}

/**
 * Partial update — omitted fields keep their value. Blank (`''`) clears the
 * field back to null (display falls back to the username).
 */
export interface UpdateProfilePayload {
  nickname?: string
  avatar?: string
}

export interface LoginPayload {
  usernameOrEmail: string
  password: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  /** Access-token lifetime in seconds. */
  expiresIn: number
  user: AuthUser
}

export function login(payload: LoginPayload) {
  return api.post<LoginResult>('/v1/auth/login', payload)
}

export function logout(refreshToken: string) {
  return api.post<void>('/v1/auth/logout', { refreshToken })
}

export function getCurrentUser() {
  return api.get<AuthUser>('/v1/auth/me')
}

export function updateProfile(payload: UpdateProfilePayload) {
  return api.put<AuthUser>('/v1/auth/profile', payload)
}

// Note: /v1/auth/refresh is intentionally absent — token refresh is owned by
// the http layer (see api/http.ts) so it can run inside the 401 interceptor.
