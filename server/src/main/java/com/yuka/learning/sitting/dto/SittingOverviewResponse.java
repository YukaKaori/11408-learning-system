package com.yuka.learning.sitting.dto;

import java.util.List;

/**
 * The paper-level picture, per paper in exam order: the estimate against the
 * target, where the points are lost, the score trajectory and the 真题 record.
 *
 * @param latestPaperYear the newest 真题 that exists (the 考研年份 before the next exam)
 */
public record SittingOverviewResponse(int latestPaperYear, List<Paper> papers) {

    /**
     * @param target   the candidate's target score, or null
     * @param estimate null until a whole paper was sat in the last 90 days
     * @param sections one per printed section, in paper order
     * @param trend    up to the last 12 whole papers, oldest first
     * @param total    every sitting of this paper ever recorded
     */
    public record Paper(String subject, double fullScore, Integer target, Estimate estimate,
                        List<Section> sections, List<Point> trend, PastPapers pastPapers, int total) {
    }

    /**
     * @param score    recency-weighted, on the paper's full-score scale
     * @param sittings the whole papers it rests on
     * @param latest   the most recent of them
     */
    public record Estimate(double score, int sittings, double low, double high, Point latest) {
    }

    /**
     * @param rate         weighted share of the section earned; null when untested
     * @param averageScore {@code rate × full}; null when untested
     */
    public record Section(String code, double full, Double rate, Double averageScore, int sittings) {
    }

    public record Point(String id, String satOn, double score, double fullScore, String kind, String title,
                        Integer paperYear) {
    }

    /**
     * The 真题 shelf: the recent years every candidate works through, plus any
     * older year that has a record.
     */
    public record PastPapers(int firstYear, int lastYear, List<Year> years) {
    }

    /**
     * @param times  how many times this year's paper was sat (二刷 counts twice)
     * @param best   best whole-paper score, on the paper's scale; null when only sections were done
     * @param latest the most recent attempt's score, as recorded (may be a part)
     */
    public record Year(int year, int times, Double best, Double latest, Double latestFull) {
    }
}
