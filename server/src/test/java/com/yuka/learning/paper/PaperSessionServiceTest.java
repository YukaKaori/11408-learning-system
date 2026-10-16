package com.yuka.learning.paper;

import com.yuka.learning.calendar.FocusService;
import com.yuka.learning.calendar.dto.StartFocusRequest;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.paper.dto.PaperSessionResponse;
import com.yuka.learning.paper.dto.SaveAnswersRequest;
import com.yuka.learning.paper.dto.SaveScoresRequest;
import com.yuka.learning.paper.dto.StartPaperSessionRequest;
import com.yuka.learning.paper.entity.Paper;
import com.yuka.learning.paper.mapper.PaperMapper;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.dto.QuestionRequest;
import com.yuka.learning.question.dto.QuestionResponse;
import com.yuka.learning.sitting.SittingErrorCode;
import com.yuka.learning.sitting.SittingService;
import com.yuka.learning.sitting.dto.SaveSittingRequest;
import com.yuka.learning.sitting.dto.SittingResponse;
import com.yuka.learning.workspace.WorkspaceService;
import com.yuka.learning.workspace.dto.TodayResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A whole 408 paper sat in the app, end to end: the clock is the server's,
 * nothing is revealed before the hand-in, the grader marks what it can, the
 * candidate scores the rest, and the result is ordinary evidence — attempts,
 * mistakes, a 模考 record that estimates the paper, a study session, a Today row.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaperSessionServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER = 2L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** The 7 comprehensive questions of the test paper, worth 70 together like the real section. */
    private static final double[] COMPREHENSIVE = {13, 10, 13, 10, 7, 8, 9};

    @Autowired
    private PaperSessionService sessions;
    @Autowired
    private PaperMapper paperMapper;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private SittingService sittingService;
    @Autowired
    private FocusService focusService;
    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        for (String table : List.of("paper_sessions", "paper_sittings", "question_attempts", "mistakes",
                "study_sessions", "focus_timers", "exam_profiles", "practice_sessions")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        jdbcTemplate.update("DELETE FROM papers WHERE user_id IS NOT NULL");
        jdbcTemplate.update("DELETE FROM question_points WHERE question_id IN (SELECT id FROM questions WHERE user_id IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM questions WHERE user_id IS NOT NULL");
    }

    @Test
    void aPaperInProgressRevealsNothingAndRunsOnTheServerClock() {
        Paper paper = whole408();
        PaperSessionResponse started = start(paper);

        assertThat(started.status()).isEqualTo("in_progress");
        assertThat(started.sections()).extracting(PaperSessionResponse.Section::code)
                .containsExactly("cs408.choice", "cs408.comprehensive");
        assertThat(started.sections().getFirst().items()).hasSize(40);
        assertThat(started.sections().get(1).items()).extracting(PaperSessionResponse.Item::number)
                .containsExactly(41, 42, 43, 44, 45, 46, 47);
        assertThat(started.sections()).flatExtracting(PaperSessionResponse.Section::items)
                .allSatisfy(item -> {
                    assertThat(item.mark()).isNull();
                    assertThat(item.solution()).isNull();
                });
        assertThat(started.deadlineAt() - started.startedAt()).isEqualTo(180L * 60 * 1000);
        assertThat(started.score()).isNull();

        assertThatThrownBy(() -> start(paper)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_SESSION_ACTIVE));
        assertThatThrownBy(() -> sessions.get(OTHER, Long.valueOf(started.id()), ZONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_SESSION_ACCESS_DENIED));
    }

    @Test
    void theSheetIsSavedWholeAndOnlyForThisPaper() {
        PaperSessionResponse started = start(whole408());
        Long id = Long.valueOf(started.id());
        String first = started.sections().getFirst().items().get(0).question().id();
        String second = started.sections().getFirst().items().get(1).question().id();

        assertThat(sessions.save(USER, id, sheet(Map.of(first, entry("B", true, 40))), ZONE).answered()).isEqualTo(1);
        // A sheet replaces the stored one: the first answer is gone, not merged.
        assertThat(sessions.save(USER, id, sheet(Map.of(second, entry("C", false, 15))), ZONE).answered()).isEqualTo(1);
        PaperSessionResponse reloaded = sessions.get(USER, id, ZONE);
        assertThat(reloaded.sections().getFirst().items().get(0).response()).isNull();
        assertThat(reloaded.sections().getFirst().items().get(1).response()).isEqualTo("C");
        assertThat(reloaded.sections().getFirst().items().get(1).seconds()).isEqualTo(15);

        assertThatThrownBy(() -> sessions.save(USER, id, sheet(Map.of("12345", entry("A", false, 1))), ZONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_ANSWER_INVALID));
    }

    @Test
    void theHandInMarksWhatTheGraderCanJudgeAndWritesTheEvidence() {
        PaperSessionResponse started = start(whole408());
        Long id = Long.valueOf(started.id());
        backdateStart(id, 120);

        List<PaperSessionResponse.Item> choice = started.sections().getFirst().items();
        Map<String, SaveAnswersRequest.Entry> answers = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) { // 30 right
            answers.put(choice.get(i).question().id(), entry(answerOf(choice.get(i)), false, 60));
        }
        for (int i = 30; i < 35; i++) { // 5 wrong
            answers.put(choice.get(i).question().id(), entry(wrongOf(choice.get(i)), false, 60));
        }
        // 36–40 left blank.
        for (PaperSessionResponse.Item item : started.sections().get(1).items()) {
            answers.put(item.question().id(), entry("我的解答……", false, 600));
        }

        PaperSessionResponse handedIn = sessions.submit(USER, id, sheet(answers), ZONE);

        assertThat(handedIn.status()).isEqualTo("scoring");
        assertThat(handedIn.pending()).isEqualTo(7);
        assertThat(handedIn.score()).isEqualTo(60.0);
        List<PaperSessionResponse.Item> marked = handedIn.sections().getFirst().items();
        assertThat(marked.subList(0, 30)).allSatisfy(item -> assertThat(item.mark().verdict()).isEqualTo("correct"));
        assertThat(marked.subList(30, 35)).allSatisfy(item -> {
            assertThat(item.mark().verdict()).isEqualTo("wrong");
            assertThat(item.mark().mistake().change().wire()).isEqualTo("recorded");
        });
        assertThat(marked.subList(35, 40)).allSatisfy(item -> {
            assertThat(item.mark().verdict()).isEqualTo("blank");
            assertThat(item.mark().points()).isZero();
        });
        assertThat(handedIn.sections()).flatExtracting(PaperSessionResponse.Section::items)
                .allSatisfy(item -> assertThat(item.solution()).isNotNull());
        assertThat(handedIn.sections().getFirst().score()).isEqualTo(60.0);
        assertThat(handedIn.sections().get(1).score()).isNull();

        // The answer log: one attempt per graded answer, in this sitting; no attempt for a blank.
        assertThat(count("SELECT COUNT(*) FROM question_attempts WHERE session_id = ?", id)).isEqualTo(35);
        assertThat(count("SELECT COUNT(*) FROM mistakes WHERE user_id = ?", USER)).isEqualTo(5);
        // The record: the fully marked section, already — not yet a whole paper.
        SittingResponse record = sittingService.list(USER, "cs408", 10).getFirst();
        assertThat(record.sessionId()).isEqualTo(handedIn.id());
        assertThat(record.sections()).extracting(SittingResponse.Section::code).containsExactly("cs408.choice");
        assertThat(record.score()).isEqualTo(60.0);
        assertThat(record.complete()).isFalse();
        assertThat(record.durationMinutes()).isEqualTo(120);
        // The time sat is study time.
        assertThat(count("SELECT COUNT(*) FROM study_sessions WHERE user_id = ? AND node_code = 'cs408'", USER))
                .isEqualTo(1);
    }

    @Test
    void scoringCompletesThePaperTheRecordAndTheEstimate() {
        PaperSessionResponse started = start(whole408());
        Long id = Long.valueOf(started.id());
        Map<String, SaveAnswersRequest.Entry> answers = new LinkedHashMap<>();
        for (PaperSessionResponse.Item item : started.sections().getFirst().items()) {
            answers.put(item.question().id(), entry(answerOf(item), false, 50));
        }
        List<PaperSessionResponse.Item> open = started.sections().get(1).items();
        open.forEach(item -> answers.put(item.question().id(), entry("解答", false, 700)));
        sessions.submit(USER, id, sheet(answers), ZONE);

        String q41 = open.getFirst().question().id();
        assertThatThrownBy(() -> sessions.score(USER, id, scores(Map.of(q41, 13.5))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_SCORE_INVALID));
        assertThatThrownBy(() -> sessions.score(USER, id, scores(Map.of(q41, 9.25))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_SCORE_INVALID));
        String choiceId = started.sections().getFirst().items().getFirst().question().id();
        assertThatThrownBy(() -> sessions.score(USER, id, scores(Map.of(choiceId, 2.0))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_SCORE_INVALID));

        double[] given = {13, 5, 0, 10, 7, 4.5, 9};
        Map<String, Double> partial = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            partial.put(open.get(i).question().id(), given[i]);
        }
        assertThat(sessions.score(USER, id, scores(partial)).pending()).isEqualTo(1);
        assertThatThrownBy(() -> sessions.complete(USER, id, ZONE)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_SCORING_INCOMPLETE));
        assertThat(sessions.score(USER, id, scores(Map.of(open.get(6).question().id(), given[6]))).score())
                .isEqualTo(80 + 48.5);

        PaperSessionResponse done = sessions.complete(USER, id, ZONE);

        assertThat(done.status()).isEqualTo("completed");
        assertThat(done.score()).isEqualTo(128.5);
        assertThat(done.sections().get(1).items()).extracting(item -> item.mark().result())
                .containsExactly("correct", "partial", "wrong", "correct", "correct", "partial", "correct");
        assertThat(count("SELECT COUNT(*) FROM question_attempts WHERE session_id = ? AND self_graded = 1", id))
                .isEqualTo(7);
        // Partial and wrong open answers are mistakes too, as in practice.
        assertThat(count("SELECT COUNT(*) FROM mistakes WHERE user_id = ?", USER)).isEqualTo(3);

        SittingResponse record = sittingService.list(USER, "cs408", 10).getFirst();
        assertThat(record.complete()).isTrue();
        assertThat(record.score()).isEqualTo(128.5);
        assertThat(record.fullScore()).isEqualTo(150.0);
        assertThat(sittingService.estimates(USER, ZONE).get(ExamSubject.CS_408).score()).isEqualTo(128.5);

        PaperSessionResponse.Report report = done.report();
        assertThat(report.answered()).isEqualTo(47);
        assertThat(report.blank()).isZero();
        assertThat(report.correct()).isEqualTo(44);
        assertThat(report.partial()).isEqualTo(2);
        assertThat(report.wrong()).isEqualTo(1);
        assertThat(report.mistakesFiled()).isEqualTo(3);
        assertThat(report.sections()).extracting(PaperSessionResponse.SectionResult::score)
                .containsExactly(80.0, 48.5);
        assertThat(report.standing().counted()).isTrue();
        assertThat(report.standing().estimate()).isEqualTo(128.5);
        assertThat(report.points()).isNotEmpty();
        // Completing twice changes nothing.
        assertThat(sessions.complete(USER, id, ZONE).score()).isEqualTo(128.5);
        assertThat(count("SELECT COUNT(*) FROM question_attempts WHERE session_id = ?", id)).isEqualTo(47);
    }

    @Test
    void theClockHandsInAPaperLeftOpen() {
        PaperSessionResponse started = start(whole408());
        Long id = Long.valueOf(started.id());
        PaperSessionResponse.Item first = started.sections().getFirst().items().getFirst();
        sessions.save(USER, id, sheet(Map.of(first.question().id(), entry(answerOf(first), false, 30))), ZONE);
        LocalDateTime past = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusMinutes(200);
        jdbcTemplate.update("UPDATE paper_sessions SET started_at = ?, deadline_at = ? WHERE id = ?",
                Timestamp.valueOf(past), Timestamp.valueOf(past.plusMinutes(180)), id);

        PaperSessionResponse found = sessions.get(USER, id, ZONE);

        assertThat(found.status()).isEqualTo("completed"); // the open questions were blank: nothing to score
        assertThat(found.score()).isEqualTo(2.0);
        assertThat(found.submittedAt()).isEqualTo(found.deadlineAt());
        assertThatThrownBy(() -> sessions.save(USER, id, sheet(Map.of()), ZONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_TIME_UP));
        // The full 180 minutes were sat.
        assertThat(count("SELECT COUNT(*) FROM study_sessions WHERE user_id = ?", USER)).isEqualTo(1);
    }

    @Test
    void abandoningKeepsOnlyTheTimeSat() {
        PaperSessionResponse started = start(whole408());
        Long id = Long.valueOf(started.id());
        backdateStart(id, 30);
        PaperSessionResponse.Item first = started.sections().getFirst().items().getFirst();
        sessions.save(USER, id, sheet(Map.of(first.question().id(), entry(answerOf(first), false, 30))), ZONE);

        sessions.abandon(USER, id, ZONE);

        assertThatThrownBy(() -> sessions.get(USER, id, ZONE)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_SESSION_NOT_FOUND));
        assertThat(count("SELECT COUNT(*) FROM question_attempts WHERE user_id = ?", USER)).isZero();
        assertThat(count("SELECT COUNT(*) FROM paper_sittings WHERE user_id = ?", USER)).isZero();
        Map<String, Object> studied = jdbcTemplate.queryForMap(
                "SELECT starts_at, ends_at FROM study_sessions WHERE user_id = ?", USER);
        assertThat(java.time.Duration.between(((Timestamp) studied.get("starts_at")).toLocalDateTime(),
                ((Timestamp) studied.get("ends_at")).toLocalDateTime()).toMinutes()).isEqualTo(30);
        // A new paper can start now.
        assertThat(start(whole408()).status()).isEqualTo("in_progress");
    }

    @Test
    void anAppScoredRecordIsReadOnlyButItsNoteIsNot() {
        PaperSessionResponse started = start(whole408());
        Long id = Long.valueOf(started.id());
        PaperSessionResponse.Item first = started.sections().getFirst().items().getFirst();
        Map<String, SaveAnswersRequest.Entry> answers = new HashMap<>();
        answers.put(first.question().id(), entry(answerOf(first), false, 30));
        started.sections().get(1).items().forEach(item -> answers.put(item.question().id(), entry("解答", false, 1)));
        PaperSessionResponse handedIn = sessions.submit(USER, id, sheet(answers), ZONE);
        Long sittingId = Long.valueOf(handedIn.sittingId());

        SaveSittingRequest edit = new SaveSittingRequest("cs408", "mock", "改", null, LocalDate.now(ZONE), 180,
                List.of(new SaveSittingRequest.SectionScore("cs408.choice", 80.0)), null, null);
        assertThatThrownBy(() -> sittingService.update(USER, sittingId, edit, ZONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SittingErrorCode.SITTING_READ_ONLY));
        assertThat(sittingService.updateNote(USER, sittingId, "选择题时间分配太松").note()).isEqualTo("选择题时间分配太松");

        // A record the candidate deleted is not brought back by the rest of the scoring.
        sittingService.delete(USER, sittingId);
        Map<String, Double> zero = new HashMap<>();
        started.sections().get(1).items().forEach(item -> zero.put(item.question().id(), 0.0));
        sessions.score(USER, id, scores(zero));
        PaperSessionResponse done = sessions.complete(USER, id, ZONE);
        assertThat(done.sittingId()).isNull();
        assertThat(sittingService.list(USER, "cs408", 10)).isEmpty();
    }

    @Test
    void startingAPaperSavesTheRunningStudyTimer() {
        focusService.start(USER, new StartFocusRequest("math1", "线代"));
        jdbcTemplate.update("UPDATE focus_timers SET started_at = ? WHERE user_id = ?",
                Timestamp.valueOf(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusMinutes(25)), USER);

        start(whole408());

        assertThat(focusService.current(USER)).isNull();
        assertThat(count("SELECT COUNT(*) FROM study_sessions WHERE user_id = ? AND node_code = 'math1'", USER))
                .isEqualTo(1);
    }

    @Test
    void anOpenPaperLeadsTodaysPlan() {
        PaperSessionResponse started = start(whole408());

        List<TodayResponse.PlanItem> plan = workspaceService.today(USER, ZONE).plan();

        assertThat(plan.getFirst().kind()).isEqualTo(TodayResponse.PlanKind.PAPER);
        assertThat(plan.getFirst().paper().sessionId()).isEqualTo(started.id());
        assertThat(plan.getFirst().paper().status()).isEqualTo("in_progress");
        assertThat(plan.getFirst().paper().total()).isEqualTo(47);
        assertThat(workspaceService.today(USER, ZONE).state()).isEqualTo(TodayResponse.TodayState.PLANNED);
    }

    // --- fixtures ---------------------------------------------------------------------

    /**
     * A whole 408 paper: 40 library choice questions and 7 comprehensive
     * questions of the candidate's own, worth 13/10/13/10/7/8/9 like the
     * printed section.
     */
    private Paper whole408() {
        List<Long> choice = jdbcTemplate.queryForList("SELECT id FROM questions WHERE user_id IS NULL "
                + "AND status = 0 AND section = 'cs408.choice' ORDER BY pack_key LIMIT 40", Long.class);
        assertThat(choice).hasSize(40);
        List<PaperLayout.Item> open = new ArrayList<>();
        for (double score : COMPREHENSIVE) {
            QuestionResponse own = questionService.create(USER, new QuestionRequest("cs408", "cs408.comprehensive",
                    "open", "综合题 " + score, null, null, "评分要点", null, "解析", 3, score, "测试", null,
                    List.of("cs408.ds.list.application")));
            open.add(new PaperLayout.Item(Long.parseLong(own.id()), score));
        }
        PaperLayout layout = new PaperLayout(List.of(
                new PaperLayout.Section("cs408.choice", choice.stream().map(q -> new PaperLayout.Item(q, 2.0)).toList()),
                new PaperLayout.Section("cs408.comprehensive", open)));
        Paper paper = new Paper();
        paper.setUserId(USER);
        paper.setSubject("cs408");
        paper.setKind("mock");
        paper.setTitle("测试整卷");
        paper.setSections(objectMapper.writeValueAsString(layout));
        paper.setQuestionCount(47);
        paper.setFullScore(BigDecimal.valueOf(150));
        paper.setDurationMinutes(180);
        paper.setWhole(true);
        paper.setStatus(0);
        paperMapper.insert(paper);
        return paper;
    }

    private PaperSessionResponse start(Paper paper) {
        return sessions.start(USER, new StartPaperSessionRequest(String.valueOf(paper.getId())), ZONE);
    }

    private void backdateStart(Long id, int minutes) {
        LocalDateTime startedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusMinutes(minutes);
        jdbcTemplate.update("UPDATE paper_sessions SET started_at = ?, deadline_at = ? WHERE id = ?",
                Timestamp.valueOf(startedAt), Timestamp.valueOf(startedAt.plusMinutes(180)), id);
    }

    private String answerOf(PaperSessionResponse.Item item) {
        return jdbcTemplate.queryForObject("SELECT answer FROM questions WHERE id = ?", String.class,
                Long.parseLong(item.question().id()));
    }

    private String wrongOf(PaperSessionResponse.Item item) {
        String right = answerOf(item);
        return right.equals("A") ? "B" : "A";
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static SaveAnswersRequest sheet(Map<String, SaveAnswersRequest.Entry> answers) {
        return new SaveAnswersRequest(answers);
    }

    private static SaveAnswersRequest.Entry entry(String response, boolean flagged, int seconds) {
        return new SaveAnswersRequest.Entry(response, flagged, seconds);
    }

    private static SaveScoresRequest scores(Map<String, Double> points) {
        return new SaveScoresRequest(points);
    }
}
