package com.yuka.learning.paper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.paper.dto.AssemblePaperRequest;
import com.yuka.learning.paper.dto.PaperSessionResponse;
import com.yuka.learning.paper.dto.PaperSummaryResponse;
import com.yuka.learning.paper.dto.SaveAnswersRequest;
import com.yuka.learning.paper.dto.SaveScoresRequest;
import com.yuka.learning.paper.dto.StartPaperSessionRequest;
import com.yuka.learning.paper.entity.Paper;
import com.yuka.learning.paper.mapper.PaperMapper;
import com.yuka.learning.sitting.SittingService;
import com.yuka.learning.sitting.dto.SittingResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The other three papers sat in the app, from the shipped sample packs: every
 * question format is asked, marked as the printed paper marks it, and written
 * as ordinary evidence. 408 has its own test ({@link PaperSessionServiceTest});
 * here the formats only the other papers have — 政治多选 all or nothing, 数学填空
 * judged by its accepted forms or by the candidate, English texts sat whole with
 * their passage, translation and writing scored against the reference.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaperSubjectsTest {

    private static final Long USER = 1L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private PaperSessionService sessions;
    @Autowired
    private PaperService papers;
    @Autowired
    private PaperMapper paperMapper;
    @Autowired
    private SittingService sittingService;
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
    }

    @Test
    void politicsMultipleChoiceIsAllOrNothingAndTheAnalysisIsScoredByTheCandidate() {
        PaperSessionResponse started = start("politics-sample-paper-01");
        assertThat(started.sections()).extracting(PaperSessionResponse.Section::code)
                .containsExactly("politics.single", "politics.multi", "politics.analysis");
        assertThat(started.sections()).extracting(section -> section.items().size()).containsExactly(16, 17, 5);
        assertThat(started.timeLimitMinutes()).isEqualTo(180);
        assertThat(started.fullScore()).isEqualTo(100.0);
        assertThat(started.sections().get(2).items()).extracting(PaperSessionResponse.Item::number)
                .containsExactly(34, 35, 36, 37, 38);

        Map<String, SaveAnswersRequest.Entry> answers = new LinkedHashMap<>();
        started.sections().getFirst().items().forEach(item -> answers.put(id(item), entry(answerOf(item))));
        List<PaperSessionResponse.Item> multi = started.sections().get(1).items();
        answers.put(id(multi.get(0)), entry(answerOf(multi.get(0))));
        String full = answerOf(multi.get(1));
        answers.put(id(multi.get(1)), entry(full.substring(0, full.length() - 1))); // one letter short
        started.sections().get(2).items().forEach(item -> answers.put(id(item), entry("答：……")));
        Long sessionId = Long.valueOf(started.id());

        PaperSessionResponse handedIn = sessions.submit(USER, sessionId, new SaveAnswersRequest(answers), ZONE);

        assertThat(handedIn.status()).isEqualTo("scoring");
        assertThat(handedIn.pending()).isEqualTo(5);
        List<PaperSessionResponse.Item> marked = handedIn.sections().get(1).items();
        assertThat(marked.get(0).mark().verdict()).isEqualTo("correct");
        assertThat(marked.get(0).mark().points()).isEqualTo(2.0);
        // A right answer that misses one letter is wrong, as printed — and a mistake.
        assertThat(marked.get(1).mark().verdict()).isEqualTo("wrong");
        assertThat(marked.get(1).mark().points()).isZero();
        assertThat(marked.get(1).mark().mistake().change().wire()).isEqualTo("recorded");
        assertThat(marked.subList(2, 17)).allSatisfy(item -> assertThat(item.mark().verdict()).isEqualTo("blank"));
        assertThat(handedIn.score()).isEqualTo(16 + 2.0);

        Map<String, Double> given = new HashMap<>();
        double[] points = {7.5, 10, 0, 5.5, 9};
        List<PaperSessionResponse.Item> analysis = started.sections().get(2).items();
        for (int i = 0; i < points.length; i++) {
            given.put(id(analysis.get(i)), points[i]);
        }
        assertThat(sessions.score(USER, sessionId, new SaveScoresRequest(given)).pending()).isZero();
        PaperSessionResponse done = sessions.complete(USER, sessionId, ZONE);

        assertThat(done.score()).isEqualTo(18 + 32.0);
        assertThat(done.sections().get(2).items()).extracting(item -> item.mark().result())
                .containsExactly("partial", "correct", "wrong", "partial", "partial");
        SittingResponse record = sittingService.list(USER, "politics", 10).getFirst();
        assertThat(record.complete()).isTrue();
        assertThat(record.score()).isEqualTo(50.0);
        assertThat(done.report().standing().counted()).isTrue();
        assertThat(sittingService.estimates(USER, ZONE).get(ExamSubject.POLITICS).score()).isEqualTo(50.0);
        // 16 + 2 graded at hand-in, 5 scored at completion; the 15 blanks wrote nothing.
        assertThat(count("SELECT COUNT(*) FROM question_attempts WHERE session_id = ?", sessionId)).isEqualTo(23);
    }

    @Test
    void mathFillBlanksAreGradedByTheirFormsOrScoredAllOrNothing() {
        PaperSessionResponse started = start("math1-sample-paper-01");
        assertThat(started.sections()).extracting(section -> section.items().size()).containsExactly(10, 6, 6);
        // 解答题 worth 10, 12, 12, 12 (高等数学 46), 12 (线代), 12 (概率) — the printed slots.
        assertThat(started.sections().get(2).items()).extracting(PaperSessionResponse.Item::worth)
                .containsExactly(10.0, 12.0, 12.0, 12.0, 12.0, 12.0);
        assertThat(started.fullScore()).isEqualTo(150.0);

        Map<String, SaveAnswersRequest.Entry> answers = new LinkedHashMap<>();
        started.sections().getFirst().items().forEach(item -> answers.put(id(item), entry(answerOf(item))));
        List<PaperSessionResponse.Item> fill = started.sections().get(1).items();
        answers.put(id(fill.get(0)), entry(acceptedFormOf(fill.get(0))));
        answers.put(id(fill.get(1)), entry("一个书上没有的写法"));     // nothing the grader can match
        List<PaperSessionResponse.Item> solution = started.sections().get(2).items();
        answers.put(id(solution.get(1)), entry("补面、高斯公式……"));
        Long sessionId = Long.valueOf(started.id());

        PaperSessionResponse handedIn = sessions.submit(USER, sessionId, new SaveAnswersRequest(answers), ZONE);

        assertThat(handedIn.pending()).isEqualTo(2);
        assertThat(handedIn.sections().get(1).items().get(0).mark().verdict()).isEqualTo("correct");
        assertThat(handedIn.sections().get(1).items().get(1).mark().verdict()).isEqualTo("self");
        assertThat(handedIn.score()).isEqualTo(50 + 5.0);

        String unmatched = id(fill.get(1));
        assertThatThrownBy(() -> sessions.score(USER, sessionId, new SaveScoresRequest(Map.of(unmatched, 2.5))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_SCORE_INVALID));
        assertThatThrownBy(() -> sessions.score(USER, sessionId,
                new SaveScoresRequest(Map.of(id(solution.get(1)), 12.5))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaperErrorCode.PAPER_SCORE_INVALID));
        sessions.score(USER, sessionId, new SaveScoresRequest(Map.of(unmatched, 5.0, id(solution.get(1)), 7.5)));
        PaperSessionResponse done = sessions.complete(USER, sessionId, ZONE);

        assertThat(done.score()).isEqualTo(55 + 5 + 7.5);
        assertThat(done.sections().get(1).items().get(1).mark().result()).isEqualTo("correct");
        assertThat(done.sections().get(2).items().get(1).mark().result()).isEqualTo("partial");
        assertThat(count("SELECT COUNT(*) FROM mistakes WHERE user_id = ?", USER)).isEqualTo(1);
        assertThat(sittingService.list(USER, "math1", 10).getFirst().complete()).isTrue();
    }

    @Test
    void englishTextsAreSatWholeWithTheirPassageAndScoredAsPrinted() {
        PaperSessionResponse started = start("english1-sample-paper-01");
        assertThat(started.sections()).extracting(PaperSessionResponse.Section::code).containsExactly(
                "english1.cloze", "english1.reading-a", "english1.reading-b", "english1.translation",
                "english1.writing-a", "english1.writing-b");
        assertThat(started.sections()).extracting(section -> section.items().size())
                .containsExactly(20, 20, 5, 5, 1, 1);
        assertThat(started.fullScore()).isEqualTo(100.0);

        // A text is asked once: every blank of the cloze carries the same passage and group.
        List<PaperSessionResponse.Item> cloze = started.sections().getFirst().items();
        assertThat(cloze).extracting(item -> item.question().groupId()).containsOnly(cloze.getFirst().question().groupId());
        assertThat(cloze.getFirst().question().passage()).contains("**[1]**").contains("**[20]**");
        assertThat(cloze).allSatisfy(item -> assertThat(item.question().passage())
                .isEqualTo(cloze.getFirst().question().passage()));
        assertThat(cloze).extracting(PaperSessionResponse.Item::worth).containsOnly(0.5);
        // Reading texts stay whole and in order: four runs of five.
        List<PaperSessionResponse.Item> reading = started.sections().get(1).items();
        for (int text = 0; text < 4; text++) {
            List<PaperSessionResponse.Item> run = reading.subList(text * 5, text * 5 + 5);
            assertThat(run).extracting(item -> item.question().groupId()).containsOnly(run.getFirst().question().groupId());
        }
        assertThat(started.sections().get(3).items()).extracting(PaperSessionResponse.Item::number)
                .containsExactly(46, 47, 48, 49, 50);

        Map<String, SaveAnswersRequest.Entry> answers = new LinkedHashMap<>();
        cloze.forEach(item -> answers.put(id(item), entry(answerOf(item))));
        reading.subList(0, 5).forEach(item -> answers.put(id(item), entry(wrongOf(item))));
        started.sections().get(3).items().forEach(item -> answers.put(id(item), entry("我的译文")));
        PaperSessionResponse.Item letter = started.sections().get(4).items().getFirst();
        answers.put(id(letter), entry("Dear friends, ..."));
        Long sessionId = Long.valueOf(started.id());

        PaperSessionResponse handedIn = sessions.submit(USER, sessionId, new SaveAnswersRequest(answers), ZONE);

        assertThat(handedIn.pending()).isEqualTo(6);
        assertThat(handedIn.score()).isEqualTo(10.0);
        // The record already holds every section the grader decided alone.
        SittingResponse partial = sittingService.list(USER, "english1", 10).getFirst();
        assertThat(partial.sections()).extracting(SittingResponse.Section::code)
                .containsExactly("english1.cloze", "english1.reading-a", "english1.reading-b", "english1.writing-b");
        assertThat(partial.complete()).isFalse();

        Map<String, Double> given = new HashMap<>();
        double[] translation = {2, 1.5, 1, 0.5, 0};
        for (int i = 0; i < 5; i++) {
            given.put(id(started.sections().get(3).items().get(i)), translation[i]);
        }
        given.put(id(letter), 8.0);
        sessions.score(USER, sessionId, new SaveScoresRequest(given));
        PaperSessionResponse done = sessions.complete(USER, sessionId, ZONE);

        assertThat(done.score()).isEqualTo(10 + 5 + 8.0);
        assertThat(done.report().wrong()).isEqualTo(5 + 1);
        assertThat(done.report().blank()).isEqualTo(15 + 5 + 1);
        SittingResponse record = sittingService.list(USER, "english1", 10).getFirst();
        assertThat(record.complete()).isTrue();
        assertThat(record.sections()).extracting(SittingResponse.Section::score)
                .containsExactly(10.0, 0.0, 0.0, 5.0, 8.0, 0.0);
    }

    @Test
    void theShippedBankAssemblesAWholePaperOfEverySubject() {
        for (String subject : List.of("politics", "english1", "math1", "cs408")) {
            PaperSummaryResponse paper = papers.assemble(USER, new AssemblePaperRequest(subject, null));
            assertThat(paper.whole()).as(subject).isTrue();
            assertThat(paper.fullScore()).as(subject).isEqualTo(subject.equals("math1") || subject.equals("cs408")
                    ? 150.0 : 100.0);
        }
        assertThat(papers.catalog(USER, "english1").assembly().whole()).isTrue();
        assertThat(papers.catalog(USER, "english1").library()).extracting(PaperSummaryResponse::title)
                .singleElement().asString().contains("非真题");
    }

    // --- fixtures ---------------------------------------------------------------------

    private PaperSessionResponse start(String paperKey) {
        Paper paper = paperMapper.selectOne(new LambdaQueryWrapper<Paper>().eq(Paper::getPaperKey, paperKey));
        assertThat(paper).as(paperKey).isNotNull();
        return sessions.start(USER, new StartPaperSessionRequest(String.valueOf(paper.getId())), ZONE);
    }

    private String answerOf(PaperSessionResponse.Item item) {
        return jdbcTemplate.queryForObject("SELECT answer FROM questions WHERE id = ?", String.class,
                Long.parseLong(item.question().id()));
    }

    /** A fill-blank's first accepted form, as the grading key stores it. */
    private String acceptedFormOf(PaperSessionResponse.Item item) {
        String key = jdbcTemplate.queryForObject("SELECT answer_key FROM questions WHERE id = ?", String.class,
                Long.parseLong(item.question().id()));
        return key.split("\n")[0];
    }

    private String wrongOf(PaperSessionResponse.Item item) {
        return answerOf(item).equals("A") ? "B" : "A";
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static String id(PaperSessionResponse.Item item) {
        return item.question().id();
    }

    private static SaveAnswersRequest.Entry entry(String response) {
        return new SaveAnswersRequest.Entry(response, false, 30);
    }
}
