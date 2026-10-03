/**
 * Single owner of token persistence — no other module touches storage keys.
 *
 * Current strategy: localStorage for both tokens. Acceptable for this phase and
 * swappable behind this interface without touching callers; the planned upgrade
 * is an httpOnly cookie for the refresh token (server-set) with only the
 * short-lived access token kept in JS-visible memory.
 */

const ACCESS_TOKEN_KEY = 'alp.accessToken'
const REFRESH_TOKEN_KEY = 'alp.refreshToken'

export interface TokenPair {
  accessToken: string
  refreshToken: string
}

export const tokenStorage = {
  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY)
  },

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  },

  set(tokens: TokenPair): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
  },

  clear(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  },

  /** Whether a session may be restorable (a refresh token is present). */
  hasSession(): boolean {
    return this.getRefreshToken() !== null
  },
}
