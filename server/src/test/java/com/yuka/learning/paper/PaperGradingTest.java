package com.yuka.learning.paper;

import com.yuka.learning.exam.QuestionType;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.grading.AnswerGrader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** How a paper is marked: the practice grader's verdicts, blanks, and the candidate's own scores. */
class PaperGradingTest {

    @Test
    void aBlankIsNeitherRightNorWrong() {
        assertThat(PaperGrading.decide(AnswerGrader.Verdict.WRONG, null)).isEqualTo(PaperGrading.Verdict.BLANK);
        assertThat(PaperGrading.decide(AnswerGrader.Verdict.WRONG, "  ")).isEqualTo(PaperGrading.Verdict.BLANK);
        assertThat(PaperGrading.decide(AnswerGrader.Verdict.CORRECT, "B")).isEqualTo(PaperGrading.Verdict.CORRECT);
        assertThat(PaperGrading.decide(AnswerGrader.Verdict.WRONG, "A")).isEqualTo(PaperGrading.Verdict.WRONG);
        assertThat(PaperGrading.decide(AnswerGrader.Verdict.NEEDS_SELF_GRADE, "1/2"))
                .isEqualTo(PaperGrading.Verdict.SELF);
    }

    @Test
    void theGraderIsThePracticeGrader() {
        // 政治多选 has no partial credit: a missing letter is wrong, as printed.
        AnswerGrader.Verdict missing = AnswerGrader.grade(QuestionType.MULTI_CHOICE, "ABD", List.of(), "AB");
        assertThat(PaperGrading.decide(missing, "AB")).isEqualTo(PaperGrading.Verdict.WRONG);
    }

    @Test
    void aFillBlankIsWorthAllOrNothing() {
        assertThat(PaperGrading.validSelfScore(QuestionType.FILL_BLANK, 5, 5)).isTrue();
        assertThat(PaperGrading.validSelfScore(QuestionType.FILL_BLANK, 5, 0)).isTrue();
        assertThat(PaperGrading.validSelfScore(QuestionType.FILL_BLANK, 5, 2.5)).isFalse();
    }

    @Test
    void anOpenAnswerIsScoredInHalfPointsWithinItsWorth() {
        assertThat(PaperGrading.validSelfScore(QuestionType.OPEN, 13, 9.5)).isTrue();
        assertThat(PaperGrading.validSelfScore(QuestionType.OPEN, 13, 13)).isTrue();
        assertThat(PaperGrading.validSelfScore(QuestionType.OPEN, 13, 9.25)).isFalse();
        assertThat(PaperGrading.validSelfScore(QuestionType.OPEN, 13, 13.5)).isFalse();
        assertThat(PaperGrading.validSelfScore(QuestionType.OPEN, 13, -1)).isFalse();
    }

    @Test
    void fullMarksAreCorrectNothingIsWrongAndTheRestIsPartial() {
        assertThat(PaperGrading.resultOf(10, 10)).isEqualTo(AttemptResult.CORRECT);
        assertThat(PaperGrading.resultOf(0, 10)).isEqualTo(AttemptResult.WRONG);
        assertThat(PaperGrading.resultOf(7.5, 10)).isEqualTo(AttemptResult.PARTIAL);
    }
}
