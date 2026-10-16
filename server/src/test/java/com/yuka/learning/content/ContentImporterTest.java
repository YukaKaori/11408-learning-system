package com.yuka.learning.content;

import com.yuka.learning.paper.PaperService;
import com.yuka.learning.paper.dto.PaperCatalogResponse;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.dto.QuestionResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The versioned import: groups stored once and read by their questions, the
 * live version of every pack recorded, an older pack refused, library papers
 * imported from packs and retired when they leave.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContentImporterTest {

    private static final Long USER = 1L;

    @Autowired
    private ContentImporter importer;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private PaperService paperService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restore() {
        jdbcTemplate.update("DELETE FROM content_packs");
        importer.importAll();
    }

    @Test
    void aGroupIsStoredOnceAndItsQuestionsReadItsPassage() {
        importer.importAll();
        Map<String, Object> group = jdbcTemplate.queryForMap(
                "SELECT id, section, passage FROM question_groups WHERE group_key = 'english1-reading-g001'");
        List<Map<String, Object>> members = jdbcTemplate.queryForList(
                "SELECT id, group_id, group_seq, passage FROM questions WHERE group_id = ? ORDER BY group_seq",
                group.get("id"));
        assertThat(members).hasSize(5);
        assertThat(members).extracting(row -> ((Number) row.get("group_seq")).intValue())
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(members).allSatisfy(row -> assertThat(row.get("passage")).isNull());

        Long first = ((Number) members.getFirst().get("id")).longValue();
        QuestionResponse asked = questionService.detail(USER, first).question();
        assertThat(asked.groupId()).isEqualTo(String.valueOf(group.get("id")));
        assertThat(asked.passage()).isEqualTo(group.get("passage")).startsWith("For decades");
    }

    @Test
    void theLiveVersionOfEveryPackIsRecorded() {
        importer.importAll();
        List<Map<String, Object>> packs = jdbcTemplate.queryForList(
                "SELECT pack, version, question_count, source FROM content_packs ORDER BY pack");
        assertThat(packs).extracting(row -> row.get("pack"))
                .contains("core-cs408", "core-english1", "core-math1", "core-politics");
        assertThat(packs).allSatisfy(row -> assertThat(row.get("source")).isEqualTo("classpath"));
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM content_packs WHERE pack = 'core-english1'",
                Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void aPackOlderThanTheLiveOneIsRefused() {
        importer.importAll();
        jdbcTemplate.update("UPDATE content_packs SET version = 999 WHERE pack = 'core-cs408'");
        assertThatThrownBy(() -> importer.importAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("older than the live version 999");
    }

    @Test
    void aLibraryPaperIsImportedFromItsPackAndRetiredWhenItLeaves() {
        List<String> keys = jdbcTemplate.queryForList(
                "SELECT pack_key FROM questions WHERE user_id IS NULL AND section = 'cs408.choice' AND status = 0 "
                        + "ORDER BY pack_key LIMIT 40", String.class);
        assertThat(keys).hasSize(40);
        ContentPack extra = new ContentPack("test-papers", "测试试卷", 1, null, null, List.of(), List.of(),
                List.of(new ContentPack.Paper("test-mock-cs408-choice", "cs408", "mock", "测试 · 408 选择题专项",
                        null, List.of(new ContentPack.PaperSection("cs408.choice", keys)))));
        List<PackSource.Loaded> packs = new ArrayList<>(PackSource.select(PackSource.loadClasspath(objectMapper)));
        packs.add(new PackSource.Loaded(extra, PackSource.Origin.DIRECTORY, "test",
                PackSource.hash(objectMapper, extra)));

        ContentImporter.Report report = importer.importPacks(packs);

        assertThat(report.papersWritten()).isEqualTo(1);
        PaperCatalogResponse catalog = paperService.catalog(USER, "cs408");
        assertThat(catalog.library()).filteredOn(paper -> paper.title().equals("测试 · 408 选择题专项"))
                .singleElement().satisfies(paper -> {
            assertThat(paper.questionCount()).isEqualTo(40);
            assertThat(paper.fullScore()).isEqualTo(80.0);
            assertThat(paper.durationMinutes()).isEqualTo(80);
            assertThat(paper.whole()).isFalse();
            assertThat(paper.mine()).isFalse();
        });
        assertThat(jdbcTemplate.queryForObject("SELECT paper_count FROM content_packs WHERE pack = 'test-papers'",
                Integer.class)).isEqualTo(1);

        ContentImporter.Report again = importer.importAll();
        assertThat(again.papersRetired()).isEqualTo(1);
        assertThat(paperService.catalog(USER, "cs408").library())
                .noneMatch(paper -> paper.title().equals("测试 · 408 选择题专项"));
    }
}
