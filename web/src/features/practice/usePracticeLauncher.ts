import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { startPractice, type StartPracticePayload } from '@/api/modules/practice'
import { toApiError } from '@/api/types'

/** `PracticeErrorCode.NO_QUESTIONS_AVAILABLE` — the scope has nothing to draw. */
const NO_QUESTIONS_AVAILABLE = 230005

/**
 * The one path from any "练习" button to a running session: Today's focus, the
 * syllabus map, a 考点 page, the mistake book and the practice launcher all
 * draw through here, so a set is always drawn by the server and always opens
 * the same stage.
 *
 * `launching` holds the key of the launch in flight (one at a time, so a
 * double click cannot draw two sets); `errorKey` is an i18n key.
 */
export function usePracticeLauncher() {
  const router = useRouter()
  const launching = ref<string | null>(null)
  const errorKey = ref<string | null>(null)

  async function launch(payload: StartPracticePayload, key?: string): Promise<void> {
    if (launching.value !== null) return
    launching.value = key ?? `${payload.mode}:${payload.nodeCode ?? payload.subject ?? ''}`
    errorKey.value = null
    try {
      const drawn = await startPractice(payload)
      await router.push({ name: 'practice-session', params: { id: drawn.session.id } })
    } catch (caught) {
      const error = toApiError(caught)
      errorKey.value =
        error.code === NO_QUESTIONS_AVAILABLE
          ? payload.mode === 'mistakes'
            ? 'practice.errors.noDueMistakes'
            : 'practice.errors.noQuestions'
          : error.messageKey
    } finally {
      launching.value = null
    }
  }

  return { launch, launching, errorKey }
}
