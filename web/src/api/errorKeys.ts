/**
 * Business error codes that deserve a message of their own. Every other
 * failure envelope renders the generic `error.server`; these are the ones a
 * candidate can act on ("this 考点 has no questions yet", "the question is
 * missing an answer"), so each has `error.codes.<code>` in every locale —
 * `locales.spec.ts` guards that.
 *
 * Codes mirror the backend `*ErrorCode` enums (ranges in docs/architecture.md).
 */
export const KNOWN_ERROR_CODES: readonly number[] = [
  120000, // MaterialErrorCode.MATERIAL_NOT_FOUND
  130000, // NoteErrorCode.NOTE_NOT_FOUND
  160004, // CalendarErrorCode.FOCUS_NOT_RUNNING
  160005, // CalendarErrorCode.FOCUS_END_INVALID
  160006, // CalendarErrorCode.FOCUS_TOO_LONG
  210000, // ExamErrorCode.NODE_NOT_FOUND
  210002, // ExamErrorCode.PROFILE_INVALID
  220000, // QuestionErrorCode.QUESTION_NOT_FOUND
  220002, // QuestionErrorCode.QUESTION_READ_ONLY
  220003, // QuestionErrorCode.QUESTION_INVALID
  230000, // PracticeErrorCode.SESSION_NOT_FOUND
  230002, // PracticeErrorCode.SESSION_CLOSED
  230005, // PracticeErrorCode.NO_QUESTIONS_AVAILABLE
  230007, // PracticeErrorCode.SCOPE_REQUIRED
  240000, // MistakeErrorCode.MISTAKE_NOT_FOUND
  240003, // MistakeErrorCode.MISTAKE_RESULT_INVALID
  250000, // SittingErrorCode.SITTING_NOT_FOUND
  250003, // SittingErrorCode.SITTING_SECTION_INVALID
  250004, // SittingErrorCode.SITTING_SCORE_INVALID
  250005, // SittingErrorCode.SITTING_PAPER_INVALID
  250006, // SittingErrorCode.SITTING_DATE_INVALID
  250007, // SittingErrorCode.SITTING_READ_ONLY
  260000, // PaperErrorCode.PAPER_NOT_FOUND
  260002, // PaperErrorCode.PAPER_SECTION_INVALID
  260003, // PaperErrorCode.PAPER_CONTENT_SHORT
  260004, // PaperErrorCode.PAPER_SESSION_NOT_FOUND
  260006, // PaperErrorCode.PAPER_SESSION_ACTIVE
  260007, // PaperErrorCode.PAPER_SESSION_STATE
  260008, // PaperErrorCode.PAPER_TIME_UP
  260010, // PaperErrorCode.PAPER_SCORE_INVALID
  260011, // PaperErrorCode.PAPER_SCORING_INCOMPLETE
]

const KNOWN = new Set(KNOWN_ERROR_CODES)

/** The i18n key for a failure envelope's code. */
export function messageKeyFor(code: number): string {
  return KNOWN.has(code) ? `error.codes.${code}` : 'error.server'
}
