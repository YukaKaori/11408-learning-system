package com.yuka.learning.content;

import java.util.List;

/**
 * A content pack — {@code classpath:exam/questions/**}{@code /*.json}, or a file
 * in {@code app.content.packs-dir} — exactly as authored (format v2). The
 * authoring guide is {@code content/README.md}.
 *
 * <p>Library content ships as reviewable files beside the code, like the
 * syllabus: a pull request that adds twenty 操作系统 questions is a diff a
 * subject expert can read, and {@link ContentImporter} turns it into rows
 * idempotently. Reading is strict — an unknown field fails the pack, so a typo
 * such as {@code anwser} is caught instead of silently dropping the answer.
 *
 * @param pack        the pack's id ({@code [a-z0-9-]}, ≤ 64), e.g. {@code core-cs408}
 * @param title       a human-readable name
 * @param version     bumped by the author on every content change; the live
 *                    version is recorded, and an older one is refused
 * @param description optional — what the pack covers, for reviewers
 * @param generator   optional — the script that produced the pack, for packs
 *                    whose answers are computed ({@code content/generators/…})
 * @param groups      shared material read by several questions
 * @param questions   the questions
 * @param papers      library papers composed of questions (any pack's)
 */
public record ContentPack(String pack, String title, Integer version, String description, String generator,
                          List<Group> groups, List<Item> questions, List<Paper> papers) {

    public List<Group> groupsOrEmpty() {
        return groups == null ? List.of() : groups;
    }

    public List<Item> questionsOrEmpty() {
        return questions == null ? List.of() : questions;
    }

    public List<Paper> papersOrEmpty() {
        return papers == null ? List.of() : papers;
    }

    /**
     * Shared material — a reading text, a cloze text. Its questions name it in
     * {@link Item#group()}; their order in the pack is their order in the group.
     *
     * @param key stable, globally unique ({@code [a-z0-9-]}, ≤ 64)
     */
    public record Group(String key, String subject, String section, String title, String passage,
                        String source) {
    }

    /**
     * One question.
     *
     * @param key    stable, globally unique id ({@code [a-z0-9-]}, ≤ 64) — the
     *               row's identity across re-imports; never reuse a retired key
     * @param group  the group key when the question reads shared material; such
     *               a question carries no passage of its own
     * @param points the 考点 codes the question tests; the first is its primary
     *               考点 — the one a paper's slot is filled by
     * @param answer letters for choice types, the value for fill-blank, the model
     *               answer (with scoring points) for open questions
     * @param accept extra accepted fill-blank forms
     */
    public record Item(String key, String subject, String section, String type, String group,
                       List<String> points, Integer difficulty, Double score, String source, Integer sourceYear,
                       String stem, String passage, List<String> options, String answer, List<String> accept,
                       String analysis) {
    }

    /**
     * A library paper: printed sections in printed order, each listing its
     * questions by key in paper order. A real paper (真题, where licensed) or a
     * curated mock; sections may be a subset of the paper's.
     *
     * @param kind {@code mock} | {@code past_paper}
     * @param year the 考研年份 of a past paper
     */
    public record Paper(String key, String subject, String kind, String title, Integer year,
                        List<PaperSection> sections) {
    }

    public record PaperSection(String code, List<String> questions) {
    }
}
