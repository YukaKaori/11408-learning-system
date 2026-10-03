package com.yuka.learning.exam.dto;

import com.yuka.learning.exam.ExamPhase;
import com.yuka.learning.exam.ExamSubject;

/**
 * The candidate's exam as the product uses it: the countdown, the phase, and
 * the targets. When no profile has been saved yet ({@code configured = false})
 * the response is still complete — the next exam, its estimated date, no
 * targets — so a brand-new account gets a truthful countdown on day one.
 *
 * @param configured        whether the candidate has saved a profile
 * @param targetYear        考研年份, e.g. 2027
 * @param examDate          ISO date of exam day one
 * @param examDateEstimated true while the date is the system's estimate, so the
 *                          client says "预计" instead of presenting a guess as fact
 * @param daysRemaining     whole days from the caller's today to day one
 * @param phase             where the preparation year stands
 * @param targetTotal       the four targets summed, or {@code null} unless all four are set
 */
public record ExamProfileResponse(
        boolean configured,
        int targetYear,
        String examDate,
        boolean examDateEstimated,
        long daysRemaining,
        ExamPhase phase,
        Targets targets,
        Integer targetTotal) {

    /** Target score per paper; {@code null} = not set. */
    public record Targets(Integer politics, Integer english1, Integer math1, Integer cs408) {

        public static final Targets NONE = new Targets(null, null, null, null);

        /** The target for one paper, or {@code null}. */
        public Integer of(ExamSubject subject) {
            return switch (subject) {
                case POLITICS -> politics;
                case ENGLISH_1 -> english1;
                case MATH_1 -> math1;
                case CS_408 -> cs408;
            };
        }

        public Integer total() {
            if (politics == null || english1 == null || math1 == null || cs408 == null) {
                return null;
            }
            return politics + english1 + math1 + cs408;
        }
    }
}
