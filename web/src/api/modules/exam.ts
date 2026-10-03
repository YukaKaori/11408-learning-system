import { api } from '@/api/http'

/** The four papers of 11408, in exam order. */
export type ExamSubjectCode = 'politics' | 'english1' | 'math1' | 'cs408'

export type QuestionTypeCode = 'single_choice' | 'multi_choice' | 'fill_blank' | 'open'

/** Where the preparation year stands (`ExamPhase.java`). */
export type ExamPhase = 'foundation' | 'intensive' | 'past-papers' | 'sprint' | 'finished'

// --- The syllabus --------------------------------------------------------------
//
// Mirror of SyllabusContent.java — the authored content files ARE the wire
// shape. `score` is always exam score; `weight` (1–3) is how heavily a 考点 is
// examined within its module.

export interface SyllabusPointDto {
  code: string
  name: string
  weight: number
}

export interface SyllabusChapterDto {
  code: string
  name: string
  points: SyllabusPointDto[]
}

export interface SyllabusModuleDto {
  code: string
  name: string
  /** Exam score the module carries (0 for foundations such as 词汇与语法). */
  score: number
  chapters: SyllabusChapterDto[]
}

/** One part of the paper as printed. `score` is null when questions differ in value. */
export interface SyllabusSectionDto {
  code: string
  name: string
  questionType: QuestionTypeCode
  count: number
  score: number | null
  total: number
}

export interface SyllabusSubjectDto {
  code: ExamSubjectCode
  paperCode: string
  name: string
  fullScore: number
  durationMinutes: number
  /** Which exam day the paper is sat on (1: 政治, 英语; 2: 数学, 专业课). */
  examDay: 1 | 2
  /** Start time on that day, `HH:mm`. */
  startTime: string
  /** The earliest 考研年份 offered for this paper's 真题 record. */
  pastPaperFirstYear: number
  /** Whether module scores are the syllabus's approximate shares (政治, 数学). */
  scoreApproximate: boolean | null
  sections: SyllabusSectionDto[]
  modules: SyllabusModuleDto[]
}

export interface SyllabusDto {
  exam: string
  title: string
  syllabusYear: number
  subjects: SyllabusSubjectDto[]
}

export function getSyllabus() {
  return api.get<SyllabusDto>('/v1/exam/syllabus')
}

// --- The exam profile ------------------------------------------------------------

export interface ExamTargetsDto {
  politics: number | null
  english1: number | null
  math1: number | null
  cs408: number | null
}

/**
 * Mirror of ExamProfileResponse.java. Complete even before anything is saved
 * (`configured: false`): the next exam, its estimated date, no targets.
 */
export interface ExamProfileDto {
  configured: boolean
  targetYear: number
  /** ISO date of exam day one. */
  examDate: string
  /** True while the date is the system's estimate — say "预计", never present a guess as fact. */
  examDateEstimated: boolean
  daysRemaining: number
  phase: ExamPhase
  targets: ExamTargetsDto
  /** The four targets summed; null unless all four are set. */
  targetTotal: number | null
}

/** The whole profile, replaced on save; null date = use the estimate, null target = none. */
export interface UpdateExamProfilePayload {
  targetYear: number
  examDate: string | null
  targets: ExamTargetsDto
}

export function getExamProfile() {
  return api.get<ExamProfileDto>('/v1/exam/profile')
}

export function updateExamProfile(payload: UpdateExamProfilePayload) {
  return api.put<ExamProfileDto>('/v1/exam/profile', payload)
}
