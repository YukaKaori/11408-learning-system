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
          // The syllabus map — the 11408 exam as one tree; every artifact in
          // the app anchors somewhere in it.
          path: 'syllabus',
          name: 'syllabus',
          component: () => import('@/features/syllabus/SyllabusView.vue'),
          meta: { titleKey: 'nav.syllabus' },
        },
        {
          path: 'syllabus/:code',
          name: 'syllabus-node',
          component: () => import('@/features/syllabus/SyllabusNodeView.vue'),
          meta: { titleKey: 'nav.syllabus' },
        },
        {
          path: 'practice',
          name: 'practice',
          component: () => import('@/features/practice/PracticeView.vue'),
          meta: { titleKey: 'nav.practice' },
        },
        {
          path: 'practice/:id',
          name: 'practice-session',
          component: () => import('@/features/practice/PracticeSessionView.vue'),
          meta: { titleKey: 'nav.practice' },
        },
        {
          // Papers sat under exam conditions — 真题 by year, 模拟卷 by name —
          // and what they say about each paper: the estimate against the
          // target, where the points are lost, the 真题 shelf.
          path: 'sittings',
          name: 'sittings',
          component: () => import('@/features/sittings/SittingsView.vue'),
          meta: { titleKey: 'nav.sittings' },
        },
        {
          path: 'mistakes',
          name: 'mistakes',
          component: () => import('@/features/mistakes/MistakesView.vue'),
          meta: { titleKey: 'nav.mistakes' },
        },
        {
          // Per-user subjects were retired for the fixed 11408 syllabus (V8);
          // old links and bookmarks land on the map instead of a 404.
          path: 'subjects/:id?',
          redirect: { name: 'syllabus' },
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
          // The plan: how the day's hours divide among the four papers, the
          // week's whole papers, the phases and the exam timetable. The
          // calendar is its concrete schedule and shares its section.
          path: 'plan',
          name: 'plan',
          component: () => import('@/features/plan/PlanView.vue'),
          meta: { titleKey: 'nav.plan' },
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
