import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import { getSyllabus, type ExamSubjectCode, type SyllabusDto } from '@/api/modules/exam'
import { toApiError, type ApiError } from '@/api/types'
import {
  buildIndex,
  labelOf,
  pathOf,
  shortLabelOf,
  type SyllabusIndex,
  type SyllabusNode,
} from '@/features/syllabus/syllabusIndex'

/**
 * The syllabus, loaded once per session and shared by every view that shows a
 * node — the replacement for the retired per-user subjects cache. It is
 * reference data (identical for every candidate, static per deployment), so
 * one successful load serves the whole session.
 *
 * The index is marked raw: it is rebuilt wholesale on load and never mutated,
 * so there is nothing for Vue to track inside its Map.
 */
export const useSyllabusStore = defineStore('syllabus', {
  state: () => ({
    syllabus: null as SyllabusDto | null,
    index: null as SyllabusIndex | null,
    loaded: false,
    loading: false,
    error: null as ApiError | null,
  }),

  getters: {
    node(state): (code: string | null | undefined) => SyllabusNode | undefined {
      return (code) => (code && state.index ? state.index.nodes.get(code) : undefined)
    },
    /** Location below the paper, e.g. `操作系统 › 进程管理 › …`; the code itself until loaded. */
    label(state): (code: string | null | undefined) => string {
      return (code) => (state.index ? labelOf(state.index, code) : (code ?? ''))
    },
    shortLabel(state): (code: string | null | undefined) => string {
      return (code) => (state.index ? shortLabelOf(state.index, code) : (code ?? ''))
    },
    path(state): (code: string | null | undefined) => SyllabusNode[] {
      return (code) => (state.index ? pathOf(state.index, code) : [])
    },
    subjectDoc(state) {
      return (code: ExamSubjectCode) => state.syllabus?.subjects.find((s) => s.code === code)
    },
  },

  actions: {
    /** Loads once; `force` re-fetches (the error-retry path). Concurrent calls coalesce. */
    async load(force = false): Promise<void> {
      if (this.loading || (this.loaded && !force)) return
      this.loading = true
      this.error = null
      try {
        const syllabus = await getSyllabus()
        this.syllabus = markRaw(syllabus)
        this.index = markRaw(buildIndex(syllabus))
        this.loaded = true
      } catch (caught) {
        this.error = toApiError(caught)
      } finally {
        this.loading = false
      }
    },
  },
})
