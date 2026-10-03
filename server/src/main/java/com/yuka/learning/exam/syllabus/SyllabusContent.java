package com.yuka.learning.exam.syllabus;

import java.util.List;

/**
 * The syllabus exactly as it is authored in {@code classpath:exam/} — and, by
 * design, exactly as it is served by {@code GET /v1/exam/syllabus}. The content
 * files are the product's reference data, so the wire shape is the file shape:
 * there is no second model to drift from it.
 *
 * <p>Vocabulary: a <em>subject</em> is one paper (政治, 英语一, 数学一, 408); a
 * <em>module</em> is a course inside it with its exam score (数据结构 45); a
 * <em>chapter</em> groups <em>points</em> — the 考点 every question, note, card
 * and mistake is anchored to. {@code score} always means exam score;
 * {@code weight} (1–3) is how heavily a 考点 is examined within its module.
 */
public final class SyllabusContent {

    private SyllabusContent() {
    }

    /**
     * {@code exam/blueprint.json} — the exam's identity and its papers in order.
     *
     * @param syllabusYear the admission year the syllabus is written for (2027 =
     *                     the exam sat in December 2026)
     */
    public record Blueprint(String exam, String title, int syllabusYear, List<String> subjects) {
    }

    /**
     * One paper — {@code exam/syllabus/<code>.json}.
     *
     * @param examDay            which of the two exam days the paper is sat on (1: 政治 then
     *                           英语; 2: 数学 then 专业课) — the timetable a sprint rehearses
     * @param startTime          the paper's start time on that day, {@code HH:mm}
     * @param pastPaperFirstYear the earliest 考研年份 the product offers for this paper's
     *                           真题 record (408 and 英语一: the years those papers were
     *                           introduced, 2009 and 2010)
     * @param scoreApproximate   whether module scores are the syllabus's approximate
     *                           shares (政治, 数学) rather than fixed allocations (408)
     */
    public record Subject(String code, String paperCode, String name, int fullScore, int durationMinutes,
                          int examDay, String startTime, int pastPaperFirstYear,
                          Boolean scoreApproximate, List<Section> sections, List<Module> modules) {
    }

    /**
     * One part of the paper as it is printed: its question type and scoring.
     *
     * @param score per-question score; {@code null} when questions in the section
     *              carry different scores (408 综合应用题, 数学解答题)
     * @param total the section's total score
     */
    public record Section(String code, String name, String questionType, int count, Double score, double total) {
    }

    /** A course within a paper and the exam score it carries (0 for foundations such as 词汇与语法). */
    public record Module(String code, String name, int score, List<Chapter> chapters) {
    }

    public record Chapter(String code, String name, List<Point> points) {
    }

    /** A 考点. {@code weight}: 1 occasionally examined, 2 regularly, 3 a perennial focus. */
    public record Point(String code, String name, int weight) {
    }
}
