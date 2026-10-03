package com.yuka.learning.ai.context;

import java.util.List;

/**
 * The assembled, ready-to-render context for {@code PromptBuilder}: who this
 * candidate is as an 11408 examinee, right now. Every field is derived from
 * real data on the request; nothing is inferred into storage.
 *
 * @param exam           the countdown, e.g. "2027 考研 · 初试 2026-12-26（预计）· 距今 88 天 · 真题阶段"
 * @param targets        the target scores, or null when none are set
 * @param scores         whole-paper estimates from recent sittings (in scope), or null without any
 * @param plan           today's time plan across the papers — general requests only, else null
 * @param scope          the syllabus location the request is about, or null
 * @param scopeDetail    what the scope contains or weighs, or null
 * @param diagnosis      readiness, weakest 考点 and open mistakes in scope, or null with no evidence
 * @param materialTitles the candidate's reference materials in scope
 */
public record LearningContext(
        String exam,
        String targets,
        String scores,
        String plan,
        String scope,
        String scopeDetail,
        String diagnosis,
        List<String> materialTitles,
        int totalNotes,
        List<String> recentNoteTitles,
        int totalFlashcards,
        int dueFlashcards,
        String statsSnapshot,
        String focusLabel,
        String focusContent) {
}
