import type { Router } from 'vue-router'
import { setSessionExpiredHandler } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

/**
 * Route access control.
 *
 * Meta flags (declared in router/index.ts):
 *   requiresAuth — only reachable with a session; otherwise → login with redirect.
 *   guestOnly    — login and friends; authenticated users are sent home.
 *   roles / permissions — reserved: checks are wired here in the RBAC phase so
 *   route declarations can already carry them.
 */
export function installAuthGuards(router: Router) {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()

    // One-time session restore before the first navigation resolves.
    if (!auth.initialized) {
      await auth.restoreSession()
    }

    if (to.matched.some((record) => record.meta.requiresAuth) && !auth.isAuthenticated) {
      return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
    }
    if (to.matched.some((record) => record.meta.guestOnly) && auth.isAuthenticated) {
      return { path: '/' }
    }

    // Future: evaluate to.meta.roles / to.meta.permissions against the user here.
    return true
  })

  // The http layer signals an unrecoverable 401 (refresh failed) → drop the
  // session and return to login, remembering where the user was.
  setSessionExpiredHandler(() => {
    const auth = useAuthStore()
    auth.reset()
    const current = router.currentRoute.value
    if (!current.meta.guestOnly) {
      router.push({ name: 'login', query: { redirect: current.fullPath } })
    }
  })
}
