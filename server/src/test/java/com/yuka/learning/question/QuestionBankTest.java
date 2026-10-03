package com.yuka.learning.question;

import com.yuka.learning.common.api.PageResponse;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.question.content.QuestionPack;
import com.yuka.learning.question.dto.QuestionDetailResponse;
import com.yuka.learning.question.dto.QuestionRequest;
import com.yuka.learning.question.dto.QuestionResponse;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.entity.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The question bank: library content imported idempotently from the packs,
 * withdrawn content retired rather than deleted, and the visibility rule —
 * library questions are readable by everyone and editable by no one; a
 * candidate's own questions are theirs alone.
 */
@SpringBootTest
@ActiveProfiles("test")
class QuestionBankTest {

    private static final Long USER = 1L;
    private static final Long OTHER = 2L;

    @Autowired
    private QuestionService questionService;
    @Autowired
    private QuestionPackImporter importer;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM question_attempts");
        jdbcTemplate.update("DELETE FROM question_points WHERE question_id IN (SELECT id FROM questions WHERE user_id IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM questions WHERE user_id IS NOT NULL");
        // Restore the library to exactly what the packs say (tests below perturb it).
        importer.importAll();
    }

    @Test
    void everyPackItemIsInTheLibraryAndActive() throws Exception {
        int items = 0;
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:exam/questions/*.json")) {
            try (InputStream in = resource.getInputStream()) {
                items += objectMapper.readValue(in, QuestionPack.class).questions().size();
            }
        }
        Integer library = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE user_id IS NULL AND status = 0 AND deleted = 0", Integer.class);
        assertThat(items).isGreaterThan(80);
        assertThat(library).isEqualTo(items);
    }

    @Test
    void reImportingUnchangedPacksChangesNothing() {
        QuestionPackImporter.Report report = importer.importAll();
        assertThat(report.inserted()).isZero();
        assertThat(report.updated()).isZero();
        assertThat(report.retired()).isZero();
        assertThat(report.unchanged()).isGreaterThan(80);
    }

    @Test
    void aChangedRecordIsUpdatedInPlaceKeepingItsId() {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM questions WHERE pack_key = 'cs408-ds-001'", Long.class);
        jdbcTemplate.update("UPDATE questions SET content_hash = 'stale', stem = 'edited' WHERE id = ?", id);

        QuestionPackImporter.Report report = importer.importAll();

        assertThat(report.updated()).isEqualTo(1);
        Question restored = questionService.requireVisible(USER, id);
        assertThat(restored.getStem()).startsWith("下列程序段的时间复杂度");
    }

    @Test
    void contentThatLeftThePacksIsRetiredNotDeleted() {
        jdbcTemplate.update("""
                INSERT INTO questions (id, user_id, subject, type, stem, answer, difficulty, pack_key, content_hash,
                                       status, created_at, updated_at, deleted)
                VALUES (777, NULL, 'cs408', 'open', 'gone', 'x', 3, 'cs408-withdrawn-001', 'h', 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");

        QuestionPackImporter.Report report = importer.importAll();

        assertThat(report.retired()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM questions WHERE id = 777", Integer.class))
                .isEqualTo(1);
        jdbcTemplate.update("DELETE FROM questions WHERE id = 777");
    }

    @Test
    void libraryQuestionsAreReadableByAllAndEditableByNone() {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM questions WHERE pack_key = 'cs408-os-002'", Long.class);

        QuestionDetailResponse detail = questionService.detail(OTHER, id);
        assertThat(detail.question().mine()).isFalse();
        assertThat(detail.solution().answer()).isEqualTo("C");
        assertThat(detail.question().points()).containsExactly("cs408.os.process.sync");

        assertThatThrownBy(() -> questionService.update(USER, id, ownRequest("cs408.os.process.sync")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(QuestionErrorCode.QUESTION_READ_ONLY));
    }

    @Test
    void ownQuestionsAreTheirAuthorsAlone() {
        QuestionResponse mine = questionService.create(USER, ownRequest("math1.linear.matrix.rank"));
        assertThat(mine.mine()).isTrue();

        assertThatThrownBy(() -> questionService.detail(OTHER, Long.valueOf(mine.id())))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(QuestionErrorCode.QUESTION_ACCESS_DENIED));
        assertThat(questionService.list(OTHER, null, "mine", 1, 20).total()).isZero();
        assertThat(questionService.list(USER, null, "mine", 1, 20).items())
                .extracting(QuestionResponse::id).containsExactly(mine.id());
    }

    @Test
    void aQuestionCannotMoveBetweenPapers() {
        QuestionResponse mine = questionService.create(USER, ownRequest("math1.linear.matrix.rank"));
        QuestionRequest moved = new QuestionRequest("cs408", null, "open", "题干", null, null, "答案", null, null,
                null, null, null, null, List.of("cs408.ds.tree.binary"));
        assertThatThrownBy(() -> questionService.update(USER, Long.valueOf(mine.id()), moved))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(QuestionErrorCode.QUESTION_INVALID));
    }

    @Test
    void listingFiltersByScopeAndPages() {
        PageResponse<QuestionResponse> os = questionService.list(USER, "cs408.os", "library", 1, 5);
        assertThat(os.items()).hasSize(5);
        assertThat(os.total()).isGreaterThan(5);
        assertThat(os.items()).allSatisfy(q -> assertThat(q.points()).allMatch(p -> p.startsWith("cs408.os.")));

        PageResponse<QuestionResponse> nothing = questionService.list(USER, "politics.current.policy.domestic",
                null, 1, 20);
        assertThat(nothing.items()).isEmpty();
    }

    @Test
    void availabilityAndTheAnswerLogAreScopedToTheCandidate() {
        long before = tagged(questionService.visibleTagsByQuestion(USER), "cs408.cn.link.arq");
        questionService.create(USER, ownRequest("cs408.cn.link.arq"));
        assertThat(tagged(questionService.visibleTagsByQuestion(USER), "cs408.cn.link.arq")).isEqualTo(before + 1);
        assertThat(tagged(questionService.visibleTagsByQuestion(OTHER), "cs408.cn.link.arq")).isEqualTo(before);

        Question library = questionService.requireVisible(USER,
                jdbcTemplate.queryForObject("SELECT id FROM questions WHERE pack_key = 'cs408-cn-003'", Long.class));
        questionService.recordAttempt(USER, library, null, "B", AttemptResult.CORRECT, false, 40, Instant.now());
        assertThat(questionService.detail(USER, library.getId()).history().attempts()).isEqualTo(1);
        assertThat(questionService.detail(OTHER, library.getId()).history().attempts()).isZero();
    }

    /** Visible questions tagged with {@code code}. */
    private static long tagged(Map<Long, List<String>> tags, String code) {
        return tags.values().stream().filter(codes -> codes.contains(code)).count();
    }

    private static QuestionRequest ownRequest(String point) {
        return new QuestionRequest(point.substring(0, point.indexOf('.')), null, "open", "我在书上做错的题",
                null, null, "参考答案", null, null, 3, null, "王道 · 第 2 章", null, List.of(point));
    }
}
