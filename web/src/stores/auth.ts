import { defineStore } from 'pinia'
import * as authApi from '@/api/modules/auth'
import type { AuthUser, LoginPayload, UpdateProfilePayload } from '@/api/modules/auth'
import { tokenStorage } from '@/api/token-storage'
import { useAppStore } from '@/stores/app'

/**
 * Authentication state. Tokens live in tokenStorage (the http layer reads them
 * directly); this store owns the user identity and the session lifecycle.
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as AuthUser | null,
    /** True once session restoration has been attempted (guards wait for it). */
    initialized: false,
  }),

  getters: {
    isAuthenticated(state): boolean {
      return state.user !== null
    },
  },

  actions: {
    async login(payload: LoginPayload) {
      const result = await authApi.login(payload)
      tokenStorage.set(result)
      this.user = result.user
      this.initialized = true
      await useAppStore().reconcileFromServer()
    },

    /**
     * Best-effort server-side revocation; local state is always cleared, so
     * logout never fails from the user's point of view.
     */
    async logout() {
      const refreshToken = tokenStorage.getRefreshToken()
      try {
        if (refreshToken) {
          await authApi.logout(refreshToken)
        }
      } catch {
        // Server unreachable or token already revoked — local logout still proceeds.
      } finally {
        this.reset()
      }
    },

    /**
     * Updates nickname/avatar; the store adopts the server echo so every
     * consumer (layout avatar, workspace greeting, profile) stays in sync.
     */
    async updateProfile(payload: UpdateProfilePayload) {
      this.user = await authApi.updateProfile(payload)
    },

    /**
     * Restores the session on app boot: if tokens exist, load the current user
     * (an expired access token is refreshed transparently by the http layer).
     */
    async restoreSession() {
      if (this.initialized) {
        return
      }
      if (tokenStorage.hasSession()) {
        try {
          this.user = await authApi.getCurrentUser()
          await useAppStore().reconcileFromServer()
        } catch {
          tokenStorage.clear()
        }
      }
      this.initialized = true
    },

    /** Drops the local session without a server call (e.g. session expired). */
    reset() {
      tokenStorage.clear()
      this.user = null
      this.initialized = true
    },
  },
})
