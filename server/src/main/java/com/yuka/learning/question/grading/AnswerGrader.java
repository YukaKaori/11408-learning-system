package com.yuka.learning.question.grading;

import com.yuka.learning.exam.QuestionType;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Grades an answer against a question's grading key — a pure function with no
 * I/O, the only place in the system that decides whether an answer is right.
 *
 * <p>The grader is deliberately honest about what it cannot know. A choice
 * question has exactly one right answer, so it is graded outright. A fill-blank
 * answer that matches an accepted form is correct; one that does not match is
 * <em>not</em> declared wrong — {@code 1/2}, {@code 0.5} and {@code \frac12} are
 * the same number, and a string comparison is no judge of mathematics — so the
 * candidate is asked to compare against the reference instead. Open answers
 * (解答、综合应用、翻译、写作) are always judged by the candidate against the
 * reference answer and its scoring points.
 */
public final class AnswerGrader {

    private AnswerGrader() {
    }

    /** What the grader concluded. */
    public enum Verdict {
        CORRECT,
        WRONG,
        /** The grader cannot decide; the candidate judges against the reference. */
        NEEDS_SELF_GRADE
    }

    /**
     * @param type      the question type
     * @param answerKey the stored key — letters for choice, the accepted forms
     *                  (already normalized) for fill-blank, null for open
     * @param response  what the candidate submitted; null or blank = no answer
     */
    public static Verdict grade(QuestionType type, String answerKey, List<String> acceptedForms, String response) {
        boolean blank = response == null || response.isBlank();
        return switch (type) {
            case SINGLE_CHOICE, MULTI_CHOICE -> {
                if (blank) {
                    yield Verdict.WRONG;
                }
                yield normalizeChoice(response).equals(answerKey) ? Verdict.CORRECT : Verdict.WRONG;
            }
            case FILL_BLANK -> {
                if (blank) {
                    yield Verdict.WRONG;
                }
                String normalized = normalizeFill(response);
                yield acceptedForms.contains(normalized) ? Verdict.CORRECT : Verdict.NEEDS_SELF_GRADE;
            }
            case OPEN -> Verdict.NEEDS_SELF_GRADE;
        };
    }

    /**
     * Choice letters in canonical form: upper-case, de-duplicated, sorted, with
     * everything that is not a letter dropped — {@code "c, a"} and {@code "AC"}
     * are the same answer.
     */
    public static String normalizeChoice(String raw) {
        if (raw == null) {
            return "";
        }
        TreeSet<Character> letters = new TreeSet<>();
        for (char c : Normalizer.normalize(raw, Normalizer.Form.NFKC).toUpperCase(Locale.ROOT).toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                letters.add(c);
            }
        }
        StringBuilder sb = new StringBuilder(letters.size());
        letters.forEach(sb::append);
        return sb.toString();
    }

    /**
     * A fill-blank value in comparable form: NFKC (full-width → half-width),
     * whitespace and TeX math delimiters removed, lower-cased, a trailing full
     * stop dropped. Normalization only removes notation noise; it never tries to
     * do algebra.
     */
    public static String normalizeFill(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace("$", "")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        while (s.endsWith(".") || s.endsWith("。")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
