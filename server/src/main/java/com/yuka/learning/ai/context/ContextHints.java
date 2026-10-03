package com.yuka.learning.ai.context;

/**
 * What a caller tells {@link LearningContextService#build} about the request.
 *
 * <p>{@code nodeCode} is a <em>resolved</em> syllabus code — callers pass it
 * through {@code Syllabus.resolve} first, never a raw client string. It scopes
 * the context: the tutor is told where in the syllabus the conversation sits,
 * and the diagnosis, notes and materials it sees are narrowed to that subtree.
 *
 * <p>{@code focusLabel}/{@code focusContent} carry the primary text an action
 * operates on (a note selection, a question with the candidate's answer). The
 * caller has already fetched it with an ownership check; this class never
 * fetches it.
 */
public record ContextHints(
        String nodeCode,
        String statsSnapshot,
        String focusLabel,
        String focusContent) {

    public static ContextHints empty() {
        return new ContextHints(null, null, null, null);
    }

    public static ContextHints scoped(String nodeCode) {
        return new ContextHints(nodeCode, null, null, null);
    }

    public static ContextHints focus(String nodeCode, String label, String content) {
        return new ContextHints(nodeCode, null, label, content);
    }
}
