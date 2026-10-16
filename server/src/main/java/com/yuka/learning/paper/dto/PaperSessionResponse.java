package com.yuka.learning.paper.dto;

import com.yuka.learning.mistake.dto.MistakeOutcome;
import com.yuka.learning.question.dto.QuestionResponse;
import com.yuka.learning.question.dto.QuestionSolution;

import java.util.List;

/**
 * A paper being sat, being scored, or done — one shape for all three, so the
 * client renders whatever state the server says it is in.
 *
 * <p><strong>Nothing leaks before submission.</strong> While {@code status} is
 * {@code in_progress} every item carries the question as asked and the
 * candidate's own answer — no {@code mark}, no {@code solution}. From
 * submission on, every item carries both.
 *
 * @param status    {@code in_progress} | {@code scoring} | {@code completed}
 * @param serverNow the server clock; the countdown is {@code deadlineAt − serverNow}
 * @param score     null while in progress; then the points decided so far; the total once completed
 * @param pending   answers awaiting the candidate's score (scoring only)
 * @param sittingId the 模考 record this sitting wrote, once it wrote one
 * @param report    the diagnosis, once completed
 */
public record PaperSessionResponse(String id, String paperId, String subject, String kind, String title,
                                   Integer paperYear, String status, boolean whole, int timeLimitMinutes,
                                   long startedAt, long deadlineAt, Long submittedAt, Long completedAt,
                                   long serverNow, double fullScore, Double score, int pending, String sittingId,
                                   List<Section> sections, Report report) {

    /**
     * @param minutes the suggested time for the section
     * @param score   null until every question of the section is decided
     */
    public record Section(String code, int minutes, double full, Double score, List<Item> items) {
    }

    /**
     * @param number   the question's number on the paper (1…n across sections, as printed)
     * @param worth    what it is worth on this paper
     * @param seconds  time the candidate spent on it
     * @param mark     null while in progress
     * @param solution null while in progress
     */
    public record Item(int number, QuestionResponse question, double worth, String response, boolean flagged,
                       int seconds, Mark mark, QuestionSolution solution) {
    }

    /**
     * @param verdict {@code correct} | {@code wrong} | {@code blank} | {@code self}
     * @param points  null for a self-scored answer not yet scored
     * @param result  the answer-log result once recorded ({@code correct} / {@code partial} / {@code wrong});
     *                null for a blank, and for a self-scored answer until the paper is completed
     * @param mistake what the answer did to the mistake book, once recorded
     */
    public record Mark(String verdict, Double points, String result, MistakeOutcome mistake) {
    }

    /**
     * The diagnosis of a completed paper.
     *
     * @param secondsUsed  hand-in minus start
     * @param points       per 考点 the paper tested, weakest first
     * @param mistakesFiled answers that entered (or re-entered) the mistake book
     * @param standing     the paper estimate after this sitting
     */
    public record Report(int answered, int blank, int correct, int partial, int wrong, long secondsUsed,
                         List<SectionResult> sections, List<PointResult> points, int mistakesFiled,
                         Standing standing) {
    }

    /**
     * @param seconds time spent on the section's questions
     * @param minutes the suggested time
     */
    public record SectionResult(String code, double score, double full, int seconds, int minutes) {
    }

    /**
     * @param attempted answered questions testing the 考点
     * @param correct   of which fully right
     * @param earned    points earned on them
     * @param worth     points they were worth
     */
    public record PointResult(String nodeCode, int attempted, int correct, double earned, double worth) {
    }

    /**
     * @param estimate the paper estimate now (null without a whole paper in the window)
     * @param sittings the whole papers it rests on
     * @param target   the candidate's target, or null
     * @param counted  whether this sitting is part of the estimate (a whole paper)
     */
    public record Standing(Double estimate, int sittings, Integer target, boolean counted) {
    }
}
