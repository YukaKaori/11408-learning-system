package com.yuka.learning.sitting;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * What a candidate would score on a paper, estimated from the papers they have
 * actually sat. Pure — no I/O, no clock — so every estimate on screen can be
 * recomputed by hand from the sittings list beside it.
 *
 * <p><strong>Only whole papers estimate a paper.</strong> A sitting that covers
 * every section (or records only a total, which is a whole paper by
 * definition) is evidence of what 180 minutes yield; a section drill is not —
 * doing reading A alone in 70 minutes says nothing about the time the essay
 * would have taken. Section drills still count in the section profile, where
 * they are exactly the right evidence.
 *
 * <p><strong>Recent sittings count most.</strong> Each sitting is weighted
 * {@code 0.5^(age / 30 days)} — the mastery model's half-life, so a paper-level
 * figure and the 考点 figures beneath it age at the same rate — and sittings
 * older than {@link #WINDOW_DAYS} stop counting: a mock from the spring is not
 * evidence about December.
 *
 * <p>Scores are compared as rates of what each sitting was out of, then
 * converted back to the paper's full score, so a record kept under an older
 * paper structure still contributes on the right scale.
 */
public final class ScoreEstimate {

    public static final double HALF_LIFE_DAYS = 30.0;

    /** Sittings older than this no longer count towards an estimate. */
    public static final int WINDOW_DAYS = 90;

    private ScoreEstimate() {
    }

    /**
     * One sitting as the model sees it.
     *
     * @param complete whether it covered the whole paper
     */
    public record Sat(LocalDate satOn, double score, double fullScore, boolean complete,
                      List<SectionScore> sections) {
    }

    public record SectionScore(String code, double score, double full) {
    }

    /**
     * @param score    the weighted estimate on the paper's full-score scale
     * @param sittings how many whole papers it rests on
     * @param low      the lowest of those papers, on the same scale
     * @param high     the highest
     */
    public record PaperEstimate(double score, int sittings, double low, double high) {
    }

    /**
     * @param rate     weighted share of the section's worth earned, in [0, 1];
     *                 null when no sitting in the window covered the section
     * @param sittings how many sittings it rests on
     */
    public record SectionProfile(String code, Double rate, int sittings) {
    }

    /** The paper estimate, or null when no whole paper was sat inside the window. */
    public static PaperEstimate estimate(List<Sat> sats, double paperFullScore, LocalDate today) {
        double weighted = 0;
        double weights = 0;
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        int n = 0;
        for (Sat sat : sats) {
            if (!sat.complete() || sat.fullScore() <= 0 || !inWindow(sat.satOn(), today)) {
                continue;
            }
            double rate = sat.score() / sat.fullScore();
            double w = weight(sat.satOn(), today);
            weighted += w * rate;
            weights += w;
            low = Math.min(low, rate);
            high = Math.max(high, rate);
            n++;
        }
        if (n == 0) {
            return null;
        }
        return new PaperEstimate(weighted / weights * paperFullScore, n, low * paperFullScore,
                high * paperFullScore);
    }

    /** One profile per section code, in the order given (the paper's printed order). */
    public static List<SectionProfile> sections(List<Sat> sats, List<String> sectionCodes, LocalDate today) {
        List<SectionProfile> profiles = new ArrayList<>(sectionCodes.size());
        for (String code : sectionCodes) {
            double weighted = 0;
            double weights = 0;
            int n = 0;
            for (Sat sat : sats) {
                if (!inWindow(sat.satOn(), today)) {
                    continue;
                }
                for (SectionScore section : sat.sections()) {
                    if (section.code().equals(code) && section.full() > 0) {
                        double w = weight(sat.satOn(), today);
                        weighted += w * section.score() / section.full();
                        weights += w;
                        n++;
                    }
                }
            }
            profiles.add(new SectionProfile(code, n == 0 ? null : weighted / weights, n));
        }
        return profiles;
    }

    /** The weight of a sitting on {@code satOn}, seen from {@code today}. */
    public static double weight(LocalDate satOn, LocalDate today) {
        long age = Math.max(0, ChronoUnit.DAYS.between(satOn, today));
        return Math.pow(0.5, age / HALF_LIFE_DAYS);
    }

    static boolean inWindow(LocalDate satOn, LocalDate today) {
        return ChronoUnit.DAYS.between(satOn, today) <= WINDOW_DAYS;
    }
}
