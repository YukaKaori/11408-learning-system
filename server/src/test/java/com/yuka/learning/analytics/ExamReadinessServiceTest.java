package com.yuka.learning.analytics;

import com.yuka.learning.analytics.dto.ExamReadinessResponse;
import com.yuka.learning.mistake.MistakeService;
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

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The analytics face of the practice loop: per-paper accuracy, the practice
 * trend and the mistake book, from the answer log.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExamReadinessServiceTest {

    private static final Long USER = 1L;
    private static final Long PRACTICE_SESSION = 7001L;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Autowired
    private ExamReadinessService readinessService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private MistakeService mistakeService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        for (String table : List.of("exam_profiles", "practice_sessions", "question_attempts", "mistakes")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        // Library content stays; only candidates' own questions are test data.
        jdbcTemplate.update("DELETE FROM question_points WHERE question_id IN (SELECT id FROM questions WHERE user_id IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM questions WHERE user_id IS NOT NULL");
    }

    @Test
    void accuracyAndTheTrendMeasurePracticeNotCapturedMistakes() {
        Instant now = Instant.now();
        Question practised = ownQuestion("cs408.os.process.sync");
        questionService.recordAttempt(USER, practised, PRACTICE_SESSION, "A", AttemptResult.CORRECT, false, 30, now);

        // A mistake filed from paper: wrong by construction, stamped when entered.
        Question captured = ownQuestion("cs408.os.process.sync");
        questionService.recordAttempt(USER, captured, null, "B", AttemptResult.WRONG, false, null, now);
        mistakeService.onAttempt(USER, captured, AttemptResult.WRONG, now, ZONE);

        ExamReadinessResponse readiness = readinessService.readiness(USER, ZONE);

        ExamReadinessResponse.Subject cs408 = readiness.subjects().stream()
                .filter(subject -> subject.subject().equals("cs408")).findFirst().orElseThrow();
        assertThat(cs408.answered30d()).isEqualTo(1);
        assertThat(cs408.accuracy30d()).isEqualTo(1.0);
        assertThat(readiness.practice().getLast().answered()).isEqualTo(1);
        assertThat(readiness.practice().getLast().correct()).isEqualTo(1);
        // …while the captured mistake is still in the book, where it belongs.
        assertThat(readiness.activeMistakes()).isEqualTo(1);
        assertThat(cs408.mistakes()).isEqualTo(1);
    }

    @Test
    void anAccountWithoutAnswersReportsNoAccuracyRatherThanZero() {
        ExamReadinessResponse readiness = readinessService.readiness(USER, ZONE);

        assertThat(readiness.subjects()).extracting(ExamReadinessResponse.Subject::subject)
                .containsExactly("politics", "english1", "math1", "cs408");
        assertThat(readiness.subjects()).allSatisfy(subject -> {
            assertThat(subject.accuracy30d()).isNull();
            assertThat(subject.readiness()).isZero();
        });
        assertThat(readiness.practice()).hasSize(14).allSatisfy(day -> assertThat(day.answered()).isZero());
    }

    /** One of the candidate's own questions, tagged with {@code point}. */
    private Question ownQuestion(String point) {
        return questionService.createOwn(USER, questionService.validate(new QuestionRequest(
                point.substring(0, point.indexOf('.')), null, "open", "题目", null, null, "参考答案", null, null,
                3, null, null, null, List.of(point))));
    }
}
