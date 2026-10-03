package com.yuka.learning.question.content;

import java.util.List;

/**
 * A content pack — {@code classpath:exam/questions/*.json} — as authored.
 *
 * <p>Library questions ship as reviewable content beside the code, like the
 * syllabus: a pull request that adds twenty 操作系统 questions is a diff a
 * subject expert can read, and the importer turns it into rows idempotently.
 * Licensed collections (real past papers, publisher banks) are future packs in
 * the same format.
 *
 * @param pack  the pack's id, e.g. {@code core-cs408}
 * @param title a human-readable name
 */
public record QuestionPack(String pack, String title, List<Item> questions) {

    /**
     * One question.
     *
     * @param key    stable, globally unique id ({@code [a-z0-9-]}, ≤ 64) — the
     *               row's identity across re-imports; never reuse a retired key
     * @param points the 考点 codes the question tests
     * @param answer letters for choice types, the value for fill-blank, the model
     *               answer (with scoring points) for open questions
     * @param accept extra accepted fill-blank forms
     */
    public record Item(String key, String subject, String section, String type, List<String> points,
                       Integer difficulty, Double score, String source, Integer sourceYear, String stem,
                       String passage, List<String> options, String answer, List<String> accept,
                       String analysis) {
    }
}
