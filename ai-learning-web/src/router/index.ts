import { watch } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { i18n } from '@/locales'
import AppLayout from '@/layouts/AppLayout.vue'
import { installAuthGuards } from './guards'

declare module 'vue-router' {
  interface RouteMeta {
    titleKey?: string
    /** Route requires an authenticated session. */
    requiresAuth?: boolean
    /** Route is for unauthenticated visitors only (e.g. login). */
    guestOnly?: boolean
    /** Reserved for the RBAC phase — evaluated by the auth guards. */
    roles?: string[]
    permissions?: string[]
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { titleKey: 'auth.login.title', guestOnly: true },
    },
    {
      // Post-authentication welcome experience — the bridge into the
      // workspace. Full-bleed, so it lives outside AppLayout.
      path: '/welcome',
      name: 'welcome',
      component: () => import('@/views/WelcomeView.vue'),
      meta: { titleKey: 'welcome.title', requiresAuth: true },
    },
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: { name: 'today' },
        },
        {
          // Today is the post-login landing view (Phase 17). The feature folder
          // stays `workspace/` to match the backend `workspace` façade package.
          path: 'today',
          name: 'today',
          component: () => import('@/features/workspace/TodayView.vue'),
          meta: { titleKey: 'nav.today' },
        },
        {
          // Existing links, bookmarks and deep links from before Phase 17 must
          // keep working — the route was renamed, not removed.
          path: 'workspace',
          redirect: { name: 'today' },
        },
        {
          path: 'subjects',
          name: 'subjects',
          component: () => import('@/features/subjects/SubjectsView.vue'),
          meta: { titleKey: 'nav.subjects' },
        },
        {
          path: 'subjects/:id',
          name: 'subject-detail',
          component: () => import('@/features/subjects/SubjectDetailView.vue'),
          meta: { titleKey: 'nav.subjects' },
        },
        {
          path: 'ai-tutor/:conversationId?',
          name: 'ai-tutor',
          component: () => import('@/features/ai-tutor/AiTutorView.vue'),
          meta: { titleKey: 'nav.aiTutor' },
        },
        {
          path: 'flashcards',
          name: 'flashcards',
          component: () => import('@/features/flashcards/FlashcardsView.vue'),
          meta: { titleKey: 'nav.flashcards' },
        },
        {
          path: 'notes',
          name: 'notes',
          component: () => import('@/features/notes/NotesView.vue'),
          meta: { titleKey: 'nav.notes' },
        },
        {
          path: 'calendar',
          name: 'calendar',
          component: () => import('@/features/calendar/CalendarView.vue'),
          meta: { titleKey: 'nav.calendar' },
        },
        {
          path: 'analytics',
          name: 'analytics',
          component: () => import('@/features/analytics/AnalyticsView.vue'),
          meta: { titleKey: 'nav.analytics' },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/features/profile/ProfileView.vue'),
          meta: { titleKey: 'nav.profile' },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/features/settings/SettingsView.vue'),
          meta: { titleKey: 'nav.settings' },
        },
        {
          path: 'design-system',
          name: 'design-system',
          component: () => import('@/views/DesignSystemView.vue'),
          meta: { titleKey: 'designSystem.title' },
        },
      ],
    },
    {
      // Branded 404 — full-bleed, reachable without a session (the CTA into
      // the workspace routes through the auth guard like any deep link).
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { titleKey: 'notFound.title' },
    },
  ],
})

installAuthGuards(router)

function applyDocumentTitle(): void {
  const titleKey = router.currentRoute.value.meta.titleKey as string | undefined
  const appName = i18n.global.t('app.name')
  document.title = titleKey ? `${i18n.global.t(titleKey)} · ${appName}` : appName
}

router.afterEach(applyDocumentTitle)

// Settings' locale switch changes i18n.global.locale without a navigation,
// so router.afterEach alone leaves a stale-language tab title until the
// next route change — keep it in sync immediately.
watch(i18n.global.locale, applyDocumentTitle)

export default router
