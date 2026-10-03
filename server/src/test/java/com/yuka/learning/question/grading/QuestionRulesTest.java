package com.yuka.learning.question.grading;

import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.QuestionType;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The rules every question obeys, whether it ships in a pack or is captured by a candidate. */
class QuestionRulesTest {

    private static Syllabus syllabus;

    @BeforeAll
    static void load() {
        syllabus = SyllabusLoader.load(JsonMapper.builder().build());
    }

    @Test
    void aValidChoiceQuestionIsNormalizedToCanonicalLetters() {
        QuestionRules.Validated v = QuestionRules.validate(syllabus, draft("politics", "politics.multi",
                "multi_choice", List.of("politics.marx.practice.truth"), List.of("甲", "乙", "丙", "丁"), "c a b", null));
        assertThat(v.subject()).isEqualTo(ExamSubject.POLITICS);
        assertThat(v.type()).isEqualTo(QuestionType.MULTI_CHOICE);
        assertThat(v.answer()).isEqualTo("ABC");
        assertThat(v.answerKey()).isEqualTo("ABC");
        assertThat(v.difficulty()).isEqualTo(3);
    }

    @Test
    void fillBlankKeysAreTheNormalizedAcceptedForms() {
        QuestionRules.Validated v = QuestionRules.validate(syllabus, draft("math1", "math1.fill", "fill_blank",
                List.of("math1.calculus.limit.limit"), null, "$\\dfrac{1}{6}$", List.of("1/6", " $\\frac{1}{6}$ ")));
        assertThat(QuestionRules.acceptedForms(v.answerKey())).containsExactly("1/6", "\\frac{1}{6}");
        assertThat(v.answer()).isEqualTo("$\\dfrac{1}{6}$"); // the display form is untouched
    }

    @Test
    void openQuestionsHaveNoGradingKey() {
        QuestionRules.Validated v = QuestionRules.validate(syllabus, draft("cs408", "cs408.comprehensive", "open",
                List.of("cs408.ds.list.application"), null, "头插法……", null));
        assertThat(v.answerKey()).isNull();
    }

    @Test
    void brokenQuestionsAreRejectedWithTheRuleNamed() {
        assertRejected(draft("cs408", null, "single_choice", List.of("cs408.ds.tree.binary"),
                List.of("a", "b"), "C", null), "existing options");
        assertRejected(draft("cs408", null, "single_choice", List.of("cs408.ds.tree.binary"),
                List.of("a", "b", "c"), "AB", null), "exactly one letter");
        assertRejected(draft("politics", null, "multi_choice", List.of("politics.marx.practice.truth"),
                List.of("a", "b", "c"), "A", null), "at least two letters");
        assertRejected(draft("math1", null, "open", List.of("math1.linear.matrix.rank"),
                List.of("a", "b"), "x", null), "only choice questions");
        assertRejected(draft("math1", null, "open", List.of(), null, "x", null), "at least one 考点");
        assertRejected(draft("math1", null, "open", List.of("cs408.ds.tree.binary"), null, "x", null), "not in math1");
        assertRejected(draft("math1", null, "open", List.of("math1.linear"), null, "x", null), "not a 考点");
        assertRejected(draft("math1", "cs408.choice", "open", List.of("math1.linear.matrix.rank"), null, "x", null),
                "not part of math1");
        assertRejected(draft("history", null, "open", List.of("math1.linear.matrix.rank"), null, "x", null),
                "unknown subject");
    }

    private static void assertRejected(QuestionRules.Draft draft, String message) {
        assertThatThrownBy(() -> QuestionRules.validate(syllabus, draft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    private static QuestionRules.Draft draft(String subject, String section, String type, List<String> points,
                                             List<String> options, String answer, List<String> accept) {
        return new QuestionRules.Draft(subject, section, type, points, null, null, null, null, "题干",
                null, options, answer, accept, null);
    }
}
