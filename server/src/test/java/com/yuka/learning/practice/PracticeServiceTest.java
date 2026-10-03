package com.yuka.learning.practice;

import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.mistake.MistakeService;
import com.yuka.learning.mistake.dto.MistakeOutcome;
import com.yuka.learning.practice.dto.AnswerResponse;
import com.yuka.learning.practice.dto.PracticeReport;
import com.yuka.learning.practice.dto.PracticeSessionResponse;
import com.yuka.learning.practice.dto.PracticeSummaryResponse;
import com.yuka.learning.practice.dto.StartPracticeRequest;
import com.yuka.learning.practice.dto.SubmitAnswerRequest;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.entity.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Practice against the shipped library: every mode draws, every answer takes
 * the same grade → log → mistake-book path, the two-step protocol records
 * nothing until the candidate has judged an answer the grader cannot, and a
 * session's report diagnoses it 考点 by 考点.
 */
@SpringBootTest
@ActiveProfiles("test")
class PracticeServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER = 2L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private PracticeService practiceService;
    @Autowired
    private MistakeService mistakeService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        for (String table : List.of("practice_sessions", "question_attempts", "mistakes", "exam_profiles")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        jdbcTemplate.update("DELETE FROM question_points WHERE question_id IN (SELECT id FROM questions WHERE user_id IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM questions WHERE user_id IS NOT NULL");
    }

    @Test
    void choiceAnswersAreGradedAtOnceAndFeedTheMistakeBook() {
        PracticeSessionResponse session = start("topic", "cs408.ds.basics.complexity", null, 5);
        assertThat(session.session().total()).isEqualTo(1);
        PracticeSessionResponse.Item item = session.items().getFirst();
        assertThat(item.answer()).isNull(); // nothing that reveals the answer before it is given
        assertThat(item.question().points()).containsExactly("cs408.ds.basics.complexity");

        AnswerResponse answer = answer(session, item.question().id(), "A", null);

        assertThat(answer.outcome()).isEqualTo(AnswerResponse.Outcome.GRADED);
        assertThat(answer.result()).isEqualTo("wrong");
        assertThat(answer.solution().answer()).isEqualTo("B");
        assertThat(answer.mistake().change()).isEqualTo(MistakeOutcome.Change.RECORDED);
        assertThat(answer.progress()).isEqualTo(new AnswerResponse.Progress(1, 0, 1, true));

        PracticeSessionResponse reloaded = practiceService.get(USER, id(session));
        assertThat(reloaded.session().status()).isEqualTo("completed");
        assertThat(reloaded.items().getFirst().answer().result()).isEqualTo("wrong");
        assertThat(reloaded.items().getFirst().answer().solution().answer()).isEqualTo("B");

        assertThatThrownBy(() -> answer(session, item.question().id(), "B", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PracticeErrorCode.SESSION_CLOSED));
    }

    @Test
    void openAnswersRecordNothingUntilTheCandidateHasJudgedThem() {
        PracticeSessionResponse session = start("topic", "cs408.ds.list.application", null, 5);
        String questionId = session.items().getFirst().question().id();

        AnswerResponse reveal = answer(session, questionId, "头插法", null);
        assertThat(reveal.outcome()).isEqualTo(AnswerResponse.Outcome.NEEDS_SELF_GRADE);
        assertThat(reveal.result()).isNull();
        assertThat(reveal.mistake()).isNull();
        assertThat(reveal.solution().answer()).contains("头插法");
        assertThat(reveal.progress().answered()).isZero(); // step one writes nothing

        AnswerResponse graded = answer(session, questionId, "头插法", "partial");
        assertThat(graded.outcome()).isEqualTo(AnswerResponse.Outcome.GRADED);
        assertThat(graded.result()).isEqualTo("partial");
        // A partly-right answer is still a mistake worth redoing.
        assertThat(graded.mistake().change()).isEqualTo(MistakeOutcome.Change.RECORDED);
        assertThat(graded.progress().completed()).isTrue();

        PracticeSessionResponse again = start("topic", "cs408.ds.list.application", null, 5);
        assertThatThrownBy(() -> answer(again, questionId, null, "maybe"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PracticeErrorCode.SELF_GRADE_INVALID));
    }

    @Test
    void fillBlanksMatchAnAcceptedFormOrAskTheCandidate() {
        PracticeSessionResponse first = start("topic", "math1.calculus.limit.limit", null, 5);
        String questionId = first.items().getFirst().question().id();
        AnswerResponse matched = answer(first, questionId, "1/6", null);
        assertThat(matched.result()).isEqualTo("correct");
        assertThat(matched.mistake().change()).isEqualTo(MistakeOutcome.Change.NONE);

        PracticeSessionResponse second = start("topic", "math1.calculus.limit.limit", null, 5);
        AnswerResponse unmatched = answer(second, questionId, "0.16667", null);
        assertThat(unmatched.outcome()).isEqualTo(AnswerResponse.Outcome.NEEDS_SELF_GRADE);
        AnswerResponse judged = answer(second, questionId, "0.16667", "correct");
        assertThat(judged.result()).isEqualTo("correct");
        assertThat(practiceService.get(USER, id(second)).items().getFirst().answer().selfGraded()).isTrue();
    }

    @Test
    void topicPracticeDrawsUntriedQuestionsFirst() {
        PracticeSessionResponse first = start("topic", "cs408.os.process", null, 1);
        String tried = first.items().getFirst().question().id();
        // The self-grade is ignored for choice questions and needed for open ones:
        // either way the attempt is logged.
        answer(first, tried, "Z", "wrong");

        PracticeSessionResponse second = start("topic", "cs408.os.process", null, 50);
        List<String> drawn = second.items().stream().map(item -> item.question().id()).toList();
        assertThat(drawn).hasSizeGreaterThan(2);
        assertThat(drawn.getLast()).isEqualTo(tried);
        assertThat(second.session().title()).isEqualTo("操作系统 › 进程管理");
    }

    @Test
    void drawingRulesAreEnforced() {
        assertRejected(() -> start("topic", null, null, 5), PracticeErrorCode.SCOPE_REQUIRED);
        assertRejected(() -> start("exam", null, "cs408", 5), PracticeErrorCode.MODE_INVALID);
        assertRejected(() -> start("topic", "politics.current.policy.domestic", null, 5),
                PracticeErrorCode.NO_QUESTIONS_AVAILABLE);
        assertRejected(() -> start("mistakes", null, null, 5), PracticeErrorCode.NO_QUESTIONS_AVAILABLE);
    }

    @Test
    void mistakeModeRedoesDueMistakesAndAdvancesTheirStreak() {
        Question subnet = library("cs408-cn-004");
        Instant threeDaysAgo = Instant.now().minus(Duration.ofDays(3));
        questionService.recordAttempt(USER, subnet, null, "A", AttemptResult.WRONG, false, null, threeDaysAgo);
        mistakeService.onAttempt(USER, subnet, AttemptResult.WRONG, threeDaysAgo, ZONE);

        PracticeSessionResponse redo = start("mistakes", null, null, null);
        assertThat(redo.session().mode()).isEqualTo("mistakes");
        assertThat(redo.items()).singleElement()
                .satisfies(item -> assertThat(item.question().id()).isEqualTo(String.valueOf(subnet.getId())));

        AnswerResponse answer = answer(redo, String.valueOf(subnet.getId()), "B", null);
        assertThat(answer.result()).isEqualTo("correct");
        assertThat(answer.mistake().change()).isEqualTo(MistakeOutcome.Change.PROGRESSED);
        assertThat(answer.mistake().correctStreak()).isEqualTo(1);
    }

    @Test
    void weakPointPracticeSpreadsAcrossSeveralRecommendedPoints() {
        PracticeSessionResponse session = start("weakness", null, "cs408", 6);

        assertThat(session.items()).hasSize(6);
        long distinctPoints = session.items().stream()
                .map(item -> item.question().points().getFirst())
                .distinct().count();
        assertThat(distinctPoints).isGreaterThanOrEqualTo(3);
        assertThat(session.items()).allSatisfy(item ->
                assertThat(item.question().subject()).isEqualTo("cs408"));
    }

    @Test
    void theReportDiagnosesTheSessionPointByPoint() {
        PracticeSessionResponse session = start("topic", "cs408.os.process", null, 3);
        String right = session.items().get(0).question().id();
        String wrong = session.items().get(1).question().id();
        answer(session, right, questionService.solutionOf(questionService.requireVisible(USER, Long.valueOf(right)))
                .answer(), "correct");
        answer(session, wrong, "Z", "wrong");

        PracticeReport report = practiceService.finish(USER, id(session));

        assertThat(report.session().status()).isEqualTo("completed");
        assertThat(report.session().answered()).isEqualTo(2);
        assertThat(report.session().correct()).isEqualTo(1);
        assertThat(report.wrong()).isEqualTo(1);
        assertThat(report.wrongQuestionIds()).containsExactly(wrong);
        assertThat(report.points()).isNotEmpty();
        PracticeReport.PointResult weakest = report.points().getFirst();
        assertThat(weakest.correct()).isLessThan(weakest.attempted());
    }

    @Test
    void recentListsNewestFirstWithProgressDerivedFromTheLog() {
        PracticeSessionResponse older = start("topic", "cs408.cn", null, 2);
        PracticeSessionResponse newer = start("random", null, "politics", 2);
        answer(newer, newer.items().getFirst().question().id(), "A", "wrong");

        List<PracticeSummaryResponse> recent = practiceService.recent(USER, 10);
        assertThat(recent).extracting(PracticeSummaryResponse::id)
                .containsExactly(newer.session().id(), older.session().id());
        assertThat(recent.getFirst().answered()).isEqualTo(1);
        assertThat(recent.getLast().answered()).isZero();
    }

    @Test
    void aSessionBelongsToItsCandidateAndOnlyAcceptsItsOwnQuestions() {
        PracticeSessionResponse session = start("topic", "cs408.ds.basics.complexity", null, 5);
        assertRejected(() -> practiceService.get(OTHER, id(session)), PracticeErrorCode.SESSION_ACCESS_DENIED);

        String outsider = String.valueOf(library("math1-limit-002").getId());
        assertRejected(() -> answer(session, outsider, "B", null), PracticeErrorCode.QUESTION_NOT_IN_SESSION);
    }

    // --- helpers ------------------------------------------------------------------

    private PracticeSessionResponse start(String mode, String nodeCode, String subject, Integer count) {
        return practiceService.start(USER, new StartPracticeRequest(mode, nodeCode, subject, count), ZONE);
    }

    private AnswerResponse answer(PracticeSessionResponse session, String questionId, String response,
                                  String selfGrade) {
        return practiceService.answer(USER, id(session),
                new SubmitAnswerRequest(questionId, response, selfGrade, 30), ZONE);
    }

    private static Long id(PracticeSessionResponse session) {
        return Long.valueOf(session.session().id());
    }

    private Question library(String packKey) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM questions WHERE pack_key = ?", Long.class, packKey);
        return questionService.requireVisible(USER, id);
    }

    private static void assertRejected(Runnable call, PracticeErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
