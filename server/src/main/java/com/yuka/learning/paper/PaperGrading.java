package com.yuka.learning.paper;

import com.fasterxml.jackson.annotation.JsonValue;
import com.yuka.learning.exam.QuestionType;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.grading.AnswerGrader;

import java.util.Locale;

/**
 * How a paper sat in the app is marked — the practice grader's verdicts turned
 * into points, plus the rules for what the candidate marks themself. Pure.
 *
 * <p>The grader is the same one practice uses ({@link AnswerGrader}); a paper
 * adds two things only. A <em>blank</em> scores nothing and is not evidence —
 * on a timed paper running out of time is not "不会". And what the grader cannot
 * judge is scored by the candidate in points against the scoring points, as an
 * examiner would: a fill-blank all or nothing, an open answer in half points.
 */
public final class PaperGrading {

    private PaperGrading() {
    }

    /** How a question was decided at submission. */
    public enum Verdict {
        /** Graded right — full worth. */
        CORRECT,
        /** Graded wrong — nothing. */
        WRONG,
        /** Left unanswered — nothing, and no attempt is recorded. */
        BLANK,
        /** The grader cannot judge it; the candidate scores it against the reference. */
        SELF;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The decision for one answer; {@code response} null or blank means unanswered. */
    public static Verdict decide(AnswerGrader.Verdict graded, String response) {
        if (response == null || response.isBlank()) {
            return Verdict.BLANK;
        }
        return switch (graded) {
            case CORRECT -> Verdict.CORRECT;
            case WRONG -> Verdict.WRONG;
            case NEEDS_SELF_GRADE -> Verdict.SELF;
        };
    }

    /**
     * Whether {@code points} is a mark the candidate may give: a fill-blank is
     * worth all or nothing (as on the printed paper); an open answer anything
     * from 0 to its worth in steps of ½.
     */
    public static boolean validSelfScore(QuestionType type, double worth, double points) {
        if (points < 0 || points > worth + 1e-9) {
            return false;
        }
        if (type == QuestionType.FILL_BLANK) {
            return points == 0 || Math.abs(points - worth) < 1e-9;
        }
        return Math.abs(points * 2 - Math.rint(points * 2)) < 1e-9;
    }

    /**
     * The answer-log result of a self-scored answer: full marks are correct,
     * nothing is wrong, and anything between earned some of its points.
     */
    public static AttemptResult resultOf(double points, double worth) {
        if (points >= worth - 1e-9) {
            return AttemptResult.CORRECT;
        }
        return points <= 1e-9 ? AttemptResult.WRONG : AttemptResult.PARTIAL;
    }
}
