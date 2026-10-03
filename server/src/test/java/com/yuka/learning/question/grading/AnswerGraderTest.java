package com.yuka.learning.question.grading;

import com.yuka.learning.exam.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yuka.learning.question.grading.AnswerGrader.Verdict.CORRECT;
import static com.yuka.learning.question.grading.AnswerGrader.Verdict.NEEDS_SELF_GRADE;
import static com.yuka.learning.question.grading.AnswerGrader.Verdict.WRONG;
import static org.assertj.core.api.Assertions.assertThat;

/** The grader: outright where the answer is decidable, honest where it is not. */
class AnswerGraderTest {

    @Test
    void singleChoiceIsGradedOutrightAndIgnoresCaseAndNoise() {
        assertThat(AnswerGrader.grade(QuestionType.SINGLE_CHOICE, "B", List.of(), "B")).isEqualTo(CORRECT);
        assertThat(AnswerGrader.grade(QuestionType.SINGLE_CHOICE, "B", List.of(), " b ")).isEqualTo(CORRECT);
        assertThat(AnswerGrader.grade(QuestionType.SINGLE_CHOICE, "B", List.of(), "C")).isEqualTo(WRONG);
        assertThat(AnswerGrader.grade(QuestionType.SINGLE_CHOICE, "B", List.of(), null)).isEqualTo(WRONG);
    }

    @Test
    void multiChoiceNeedsExactlyTheRightSetInAnyOrder() {
        assertThat(AnswerGrader.grade(QuestionType.MULTI_CHOICE, "ACD", List.of(), "DCA")).isEqualTo(CORRECT);
        assertThat(AnswerGrader.grade(QuestionType.MULTI_CHOICE, "ACD", List.of(), "a, c, d")).isEqualTo(CORRECT);
        // 政治多选 has no partial credit: a missing letter is wrong, an extra letter is wrong.
        assertThat(AnswerGrader.grade(QuestionType.MULTI_CHOICE, "ACD", List.of(), "AC")).isEqualTo(WRONG);
        assertThat(AnswerGrader.grade(QuestionType.MULTI_CHOICE, "ACD", List.of(), "ABCD")).isEqualTo(WRONG);
    }

    @Test
    void fillBlankMatchesAcceptedFormsAndOtherwiseAsksTheCandidate() {
        List<String> accepted = List.of("1/6", "\\frac{1}{6}");
        assertThat(AnswerGrader.grade(QuestionType.FILL_BLANK, null, accepted, "1/6")).isEqualTo(CORRECT);
        assertThat(AnswerGrader.grade(QuestionType.FILL_BLANK, null, accepted, "$\\frac{1}{6}$")).isEqualTo(CORRECT);
        assertThat(AnswerGrader.grade(QuestionType.FILL_BLANK, null, accepted, "１／６")).isEqualTo(CORRECT);
        // Not a match is not proof of wrong: 0.1666… is the same number. The candidate judges.
        assertThat(AnswerGrader.grade(QuestionType.FILL_BLANK, null, accepted, "0.1667")).isEqualTo(NEEDS_SELF_GRADE);
        assertThat(AnswerGrader.grade(QuestionType.FILL_BLANK, null, accepted, "")).isEqualTo(WRONG);
    }

    @Test
    void openAnswersAreAlwaysSelfGraded() {
        assertThat(AnswerGrader.grade(QuestionType.OPEN, null, List.of(), "我的解答……")).isEqualTo(NEEDS_SELF_GRADE);
        assertThat(AnswerGrader.grade(QuestionType.OPEN, null, List.of(), null)).isEqualTo(NEEDS_SELF_GRADE);
    }

    @Test
    void normalizationRemovesNotationNoiseOnly() {
        assertThat(AnswerGrader.normalizeChoice("c,a")).isEqualTo("AC");
        assertThat(AnswerGrader.normalizeFill(" $\\dfrac{\\pi}{2}$ ")).isEqualTo("\\dfrac{\\pi}{2}");
        assertThat(AnswerGrader.normalizeFill("24。")).isEqualTo("24");
        assertThat(AnswerGrader.normalizeFill("X + 1")).isEqualTo("x+1");
    }
}
