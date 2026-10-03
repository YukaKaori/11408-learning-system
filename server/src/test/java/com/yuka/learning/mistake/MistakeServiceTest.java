package com.yuka.learning.mistake;

import com.yuka.learning.common.api.PageResponse;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.mistake.dto.CaptureMistakeRequest;
import com.yuka.learning.mistake.dto.MistakeDetailResponse;
import com.yuka.learning.mistake.dto.MistakeOutcome;
import com.yuka.learning.mistake.dto.MistakeResponse;
import com.yuka.learning.mistake.dto.MistakeStatsResponse;
import com.yuka.learning.mistake.dto.UpdateMistakeRequest;
import com.yuka.learning.question.QuestionErrorCode;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.dto.QuestionRequest;
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
 * The mistake book's lifecycle: a wrong answer is recorded and first due on a
 * later day; only redos made on their due day advance the streak; three of
 * them resolve the mistake; a miss resets the streak and reactivates a
 * resolved one; offline mistakes are captured with their question and
 * diagnosis in one transaction.
 *
 * <p>History is simulated with past instants: "due" is judged against the real
 * today, so an attempt 30 days ago with a one-day interval is due now.
 */
@SpringBootTest
@ActiveProfiles("test")
class MistakeServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER = 2L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MistakeService mistakeService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM mistakes");
        jdbcTemplate.update("DELETE FROM question_attempts");
        jdbcTemplate.update("DELETE FROM question_points WHERE question_id IN (SELECT id FROM questions WHERE user_id IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM questions WHERE user_id IS NOT NULL");
    }

    @Test
    void aWrongAnswerIsRecordedAndFirstDueOnALaterDay() {
        Question question = question("cs408.cn.link.arq");
        Instant now = Instant.now();

        MistakeOutcome outcome = mistakeService.onAttempt(USER, question, AttemptResult.WRONG, now, ZONE);

        assertThat(outcome.change()).isEqualTo(MistakeOutcome.Change.RECORDED);
        assertThat(outcome.correctStreak()).isZero();
        assertThat(outcome.resolveStreak()).isEqualTo(3);
        assertThat(outcome.nextDueAt()).isGreaterThanOrEqualTo(now.plus(Duration.ofDays(1)).toEpochMilli() - 1000);
        assertThat(mistakeService.dueCount(USER, ZONE)).isZero(); // not before tomorrow
        assertThat(mistakeService.stats(USER, ZONE).active()).isEqualTo(1);
    }

    @Test
    void aCorrectAnswerToANeverMissedQuestionLeavesTheBookAlone() {
        MistakeOutcome outcome = mistakeService.onAttempt(USER, question("math1.linear.matrix.rank"),
                AttemptResult.CORRECT, Instant.now(), ZONE);
        assertThat(outcome.change()).isEqualTo(MistakeOutcome.Change.NONE);
        assertThat(mistakeService.stats(USER, ZONE).active()).isZero();
    }

    @Test
    void threeDueDayCorrectRedosResolveTheMistake() {
        Question question = question("cs408.os.memory.replacement");
        Instant start = Instant.now().minus(Duration.ofDays(40));
        mistakeService.onAttempt(USER, question, AttemptResult.WRONG, start, ZONE);
        assertThat(mistakeService.dueCount(USER, ZONE)).isEqualTo(1);

        MistakeOutcome first = mistakeService.onAttempt(USER, question, AttemptResult.CORRECT,
                start.plus(Duration.ofDays(1)), ZONE);
        MistakeOutcome second = mistakeService.onAttempt(USER, question, AttemptResult.CORRECT,
                start.plus(Duration.ofDays(4)), ZONE);
        MistakeOutcome third = mistakeService.onAttempt(USER, question, AttemptResult.CORRECT,
                start.plus(Duration.ofDays(12)), ZONE);

        assertThat(List.of(first.change(), second.change(), third.change())).containsExactly(
                MistakeOutcome.Change.PROGRESSED, MistakeOutcome.Change.PROGRESSED, MistakeOutcome.Change.RESOLVED);
        assertThat(List.of(first.correctStreak(), second.correctStreak(), third.correctStreak()))
                .containsExactly(1, 2, 3);
        assertThat(third.nextDueAt()).isNull();
        MistakeStatsResponse stats = mistakeService.stats(USER, ZONE);
        assertThat(stats.active()).isZero();
        assertThat(stats.resolved()).isEqualTo(1);
        assertThat(mistakeService.dueCount(USER, ZONE)).isZero();
    }

    @Test
    void anEarlyCorrectAnswerStrengthensMemoryButDoesNotCountTowardTheStreak() {
        Question question = question("politics.marx.practice.truth");
        Instant now = Instant.now();
        mistakeService.onAttempt(USER, question, AttemptResult.WRONG, now, ZONE);

        // Right after reading the solution: not yet due, so not evidence of mastery.
        MistakeOutcome early = mistakeService.onAttempt(USER, question, AttemptResult.CORRECT,
                now.plus(Duration.ofMinutes(5)), ZONE);

        assertThat(early.change()).isEqualTo(MistakeOutcome.Change.PROGRESSED);
        assertThat(early.correctStreak()).isZero();
    }

    @Test
    void aMissResetsTheStreakAndReactivatesAResolvedMistake() {
        Question question = question("math1.calculus.series.power");
        Instant start = Instant.now().minus(Duration.ofDays(30));
        mistakeService.onAttempt(USER, question, AttemptResult.WRONG, start, ZONE);
        mistakeService.onAttempt(USER, question, AttemptResult.CORRECT, start.plus(Duration.ofDays(1)), ZONE);

        MistakeOutcome relapse = mistakeService.onAttempt(USER, question, AttemptResult.PARTIAL,
                start.plus(Duration.ofDays(5)), ZONE);
        assertThat(relapse.change()).isEqualTo(MistakeOutcome.Change.RELAPSED);
        assertThat(relapse.correctStreak()).isZero();

        Long id = Long.valueOf(relapse.mistakeId());
        mistakeService.resolve(USER, id, ZONE);
        assertThat(mistakeService.stats(USER, ZONE).resolved()).isEqualTo(1);

        MistakeOutcome back = mistakeService.onAttempt(USER, question, AttemptResult.WRONG, Instant.now(), ZONE);
        assertThat(back.change()).isEqualTo(MistakeOutcome.Change.REACTIVATED);
        assertThat(back.mistakeId()).isEqualTo(relapse.mistakeId()); // reactivated, not duplicated
        MistakeDetailResponse detail = mistakeService.detail(USER, id, ZONE);
        assertThat(detail.mistake().status()).isEqualTo("active");
        assertThat(detail.mistake().wrongCount()).isEqualTo(3);
        assertThat(detail.mistake().resolvedAt()).isNull();
    }

    @Test
    void captureBringsAPaperMistakeInWithItsQuestionAndDiagnosis() {
        MistakeResponse captured = mistakeService.capture(USER, new CaptureMistakeRequest(
                request("cs408.co.data.float"), "BF80 0000H", null, "calculation", "阶码忘了加偏置 127"), ZONE);

        assertThat(captured.status()).isEqualTo("active");
        assertThat(captured.cause()).isEqualTo("calculation");
        assertThat(captured.note()).isEqualTo("阶码忘了加偏置 127");
        assertThat(captured.question().mine()).isTrue();
        assertThat(captured.question().source()).isEqualTo("王道 · 模拟卷 3");

        MistakeDetailResponse detail = mistakeService.detail(USER, Long.valueOf(captured.id()), ZONE);
        assertThat(detail.attempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.captured()).isTrue();
            assertThat(attempt.result()).isEqualTo("wrong");
            assertThat(attempt.response()).isEqualTo("BF80 0000H");
        });
        assertThat(mistakeService.stats(USER, ZONE).byCause()).containsEntry("calculation", 1);
    }

    @Test
    void captureRejectsACorrectResultAndAnUnknownCause() {
        assertThatThrownBy(() -> mistakeService.capture(USER,
                new CaptureMistakeRequest(request("cs408.co.data.float"), null, "correct", null, null), ZONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MistakeErrorCode.MISTAKE_RESULT_INVALID));
        assertThatThrownBy(() -> mistakeService.capture(USER,
                new CaptureMistakeRequest(request("cs408.co.data.float"), null, null, "bad-luck", null), ZONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MistakeErrorCode.MISTAKE_CAUSE_INVALID));
    }

    @Test
    void theBookFiltersByScopeCauseAndDueDay() {
        Instant past = Instant.now().minus(Duration.ofDays(3));
        Question os = question("cs408.os.process.deadlock");
        Question cn = question("cs408.cn.network.subnet");
        Question math = question("math1.calculus.ode.first-order");
        mistakeService.onAttempt(USER, os, AttemptResult.WRONG, past, ZONE);
        mistakeService.onAttempt(USER, cn, AttemptResult.WRONG, past, ZONE);
        mistakeService.onAttempt(USER, math, AttemptResult.WRONG, Instant.now(), ZONE); // due tomorrow
        Long osMistake = Long.valueOf(page(filter(null, "cs408.os", null, false)).getFirst().id());
        mistakeService.update(USER, osMistake, new UpdateMistakeRequest("concept", "死锁四条件记混"), ZONE);

        assertThat(page(filter(null, "cs408", null, false))).hasSize(2);
        assertThat(page(filter(null, "cs408.os", null, false))).singleElement()
                .satisfies(m -> assertThat(m.cause()).isEqualTo("concept"));
        assertThat(page(filter(null, null, "concept", false))).hasSize(1);
        assertThat(page(filter(null, null, "undiagnosed", false))).hasSize(2);
        assertThat(page(filter(null, null, null, true))).hasSize(2).allMatch(MistakeResponse::due);
        assertThat(mistakeService.list(USER, new MistakeService.Filter(null, "math1", null, null, false),
                1, 20, ZONE).items()).singleElement().satisfies(m -> assertThat(m.due()).isFalse());
        assertThat(mistakeService.dueQuestionIds(USER, ZONE, null, 10)).containsExactlyInAnyOrder(os.getId(), cn.getId());
    }

    @Test
    void deletingACapturedMistakeRemovesTheQuestionItWasCapturedWith() {
        MistakeResponse captured = mistakeService.capture(USER,
                new CaptureMistakeRequest(request("english1.writing.practical.letter"), null, "partial", null, null),
                ZONE);
        Long questionId = Long.valueOf(captured.question().id());

        mistakeService.delete(USER, Long.valueOf(captured.id()));

        assertThat(mistakeService.stats(USER, ZONE).active()).isZero();
        assertThatThrownBy(() -> questionService.requireVisible(USER, questionId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(QuestionErrorCode.QUESTION_NOT_FOUND));
    }

    @Test
    void aMistakeBelongsToItsCandidate() {
        MistakeResponse captured = mistakeService.capture(USER,
                new CaptureMistakeRequest(request("cs408.ds.sort.analysis"), null, null, null, null), ZONE);
        Long id = Long.valueOf(captured.id());

        assertThatThrownBy(() -> mistakeService.detail(OTHER, id, ZONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MistakeErrorCode.MISTAKE_ACCESS_DENIED));
        assertThatThrownBy(() -> mistakeService.update(OTHER, id, new UpdateMistakeRequest("careless", null), ZONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MistakeErrorCode.MISTAKE_ACCESS_DENIED));
        assertThat(mistakeService.stats(OTHER, ZONE).active()).isZero();
    }

    private List<MistakeResponse> page(MistakeService.Filter filter) {
        PageResponse<MistakeResponse> page = mistakeService.list(USER, filter, 1, 20, ZONE);
        return page.items();
    }

    private static MistakeService.Filter filter(String status, String nodeCode, String cause, boolean due) {
        return new MistakeService.Filter(status, null, nodeCode, cause, due);
    }

    private Question question(String point) {
        return questionService.createOwn(USER, questionService.validate(request(point)));
    }

    private static QuestionRequest request(String point) {
        return new QuestionRequest(point.substring(0, point.indexOf('.')), null, "open", "我在模拟卷上做错的题",
                null, null, "参考答案", null, null, 3, null, "王道 · 模拟卷 3", null, List.of(point));
    }
}
