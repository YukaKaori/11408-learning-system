package com.yuka.learning.question.grading;

import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.QuestionType;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules every question obeys, whoever wrote it — the content team through
 * a pack or a candidate capturing a mistake from a book. One validator, two
 * entry points ({@code QuestionPackImporter}, {@code QuestionService}), so a
 * library question and a captured one can never be graded by different rules.
 *
 * <p>Pure: it reads the syllabus but performs no I/O. Violations raise
 * {@link IllegalArgumentException} with a precise message; each caller decides
 * whether that is a client error (a captured question) or a boot failure (a
 * broken content pack).
 */
public final class QuestionRules {

    public static final int MAX_TEXT = 20_000;
    public static final int MAX_OPTION = 2_000;
    public static final int MAX_POINTS = 6;
    /** Mirrors {@code questions.answer_key VARCHAR(512)}. */
    private static final int MAX_KEY = 512;

    private QuestionRules() {
    }

    /** A question as written, before validation. */
    public record Draft(String subject, String section, String type, List<String> points, Integer difficulty,
                        Double score, String source, Integer sourceYear, String stem, String passage,
                        List<String> options, String answer, List<String> accept, String analysis) {
    }

    /**
     * A question that passed every rule, in stored form.
     *
     * @param answerKey the grading key: canonical letters for choice, the
     *                  normalized accepted forms joined by {@code \n} for
     *                  fill-blank (normalized forms never contain whitespace),
     *                  null for open questions
     */
    public record Validated(ExamSubject subject, String section, QuestionType type, List<String> points,
                            int difficulty, BigDecimal score, String source, Integer sourceYear, String stem,
                            String passage, List<String> options, String answer, String answerKey,
                            String analysis) {
    }

    public static Validated validate(Syllabus syllabus, Draft draft) {
        ExamSubject subject = ExamSubject.fromCode(draft.subject());
        require(subject != null, "unknown subject '" + draft.subject() + "'");
        QuestionType type = QuestionType.fromCode(draft.type());
        require(type != null, "unknown type '" + draft.type() + "'");

        String section = blankToNull(draft.section());
        if (section != null) {
            boolean known = syllabus.subject(subject).sections().stream()
                    .map(SyllabusContent.Section::code)
                    .anyMatch(section::equals);
            require(known, "section '" + section + "' is not part of " + subject.code());
        }

        List<String> points = draft.points() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(
                draft.points().stream().filter(p -> p != null && !p.isBlank()).map(String::trim).toList()));
        require(!points.isEmpty(), "at least one 考点 is required");
        require(points.size() <= MAX_POINTS, "at most " + MAX_POINTS + " 考点 per question");
        for (String point : points) {
            require(syllabus.contains(point), "unknown 考点 '" + point + "'");
            // Evidence is only ever read at 考点 level (mastery rolls up from there),
            // so a question tagged with a chapter would silently count for nothing.
            require(syllabus.require(point).isPoint(), "'" + point + "' is not a 考点");
            require(ExamSubject.ofNode(point) == subject, "考点 '" + point + "' is not in " + subject.code());
        }

        String stem = requireText(draft.stem(), "stem");
        String answer = requireText(draft.answer(), "answer");
        String passage = optionalText(draft.passage(), "passage");
        String analysis = optionalText(draft.analysis(), "analysis");

        List<String> options = draft.options() == null ? List.of() : draft.options();
        String answerKey;
        if (type.isChoice()) {
            require(options.size() >= 2 && options.size() <= 8, "choice questions need 2–8 options");
            for (String option : options) {
                require(option != null && !option.isBlank(), "options must not be blank");
                require(option.length() <= MAX_OPTION, "an option is longer than " + MAX_OPTION);
            }
            answerKey = AnswerGrader.normalizeChoice(answer);
            char last = (char) ('A' + options.size() - 1);
            require(!answerKey.isEmpty() && answerKey.chars().allMatch(c -> c <= last),
                    "answer letters must name existing options");
            if (type == QuestionType.SINGLE_CHOICE) {
                require(answerKey.length() == 1, "a single-choice answer is exactly one letter");
            } else {
                require(answerKey.length() >= 2, "a multi-choice answer has at least two letters");
            }
            answer = answerKey;
        } else {
            require(options.isEmpty(), "only choice questions carry options");
            if (type == QuestionType.FILL_BLANK) {
                List<String> accept = draft.accept() == null || draft.accept().isEmpty()
                        ? List.of(answer) : draft.accept();
                Set<String> forms = new LinkedHashSet<>();
                for (String form : accept) {
                    String normalized = AnswerGrader.normalizeFill(form);
                    if (!normalized.isEmpty()) {
                        forms.add(normalized);
                    }
                }
                require(!forms.isEmpty(), "a fill-blank question needs at least one accepted form");
                answerKey = String.join("\n", forms);
                require(answerKey.length() <= MAX_KEY, "accepted forms are too long");
            } else {
                answerKey = null;
            }
        }

        int difficulty = draft.difficulty() == null ? 3 : draft.difficulty();
        require(difficulty >= 1 && difficulty <= 5, "difficulty must be 1–5");
        BigDecimal score = null;
        if (draft.score() != null) {
            require(draft.score() > 0 && draft.score() <= 150, "score must be in (0, 150]");
            score = BigDecimal.valueOf(draft.score());
        }
        if (draft.sourceYear() != null) {
            require(draft.sourceYear() >= 1990 && draft.sourceYear() <= 2100, "source year is out of range");
        }
        String source = blankToNull(draft.source());
        require(source == null || source.length() <= 128, "source is longer than 128");

        return new Validated(subject, section, type, List.copyOf(points), difficulty, score, source,
                draft.sourceYear(), stem, passage, List.copyOf(options), answer, answerKey, analysis);
    }

    /** The accepted fill-blank forms stored in a grading key. */
    public static List<String> acceptedForms(String answerKey) {
        return answerKey == null || answerKey.isEmpty() ? List.of() : List.of(answerKey.split("\n"));
    }

    private static String requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        require(value.length() <= MAX_TEXT, field + " is longer than " + MAX_TEXT);
        return value.strip();
    }

    private static String optionalText(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        require(value.length() <= MAX_TEXT, field + " is longer than " + MAX_TEXT);
        return value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
