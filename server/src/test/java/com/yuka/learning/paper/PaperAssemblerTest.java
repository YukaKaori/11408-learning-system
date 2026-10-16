package com.yuka.learning.paper;

import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;
import com.yuka.learning.exam.syllabus.SyllabusLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 组卷 on the real blueprint with synthetic pools: slots filled as printed,
 * fresh questions first, variable-score slots by combination, groups whole,
 * shortfalls reported rather than padded.
 */
class PaperAssemblerTest {

    private static Syllabus syllabus;

    @BeforeAll
    static void load() {
        syllabus = SyllabusLoader.load(JsonMapper.builder().build());
    }

    @Test
    void the408ChoiceSectionIsFilledModuleByModuleAsPrinted() {
        List<PaperAssembler.Candidate> pool = new ArrayList<>();
        long id = 1;
        for (String point : List.of("cs408.ds.tree.binary", "cs408.co.data.float", "cs408.os.memory.replacement",
                "cs408.cn.network.subnet")) {
            for (int i = 0; i < 15; i++) {
                pool.add(fresh(id++, point, 2.0));
            }
        }
        PaperAssembler.Result result = assemble("cs408.choice", pool);

        assertThat(result.complete()).isTrue();
        List<PaperLayout.Item> items = result.sections().getFirst().items();
        assertThat(items).hasSize(40);
        assertThat(items).allMatch(item -> item.score() == 2.0);
        // 11 数据结构, then 11 组成原理, 10 操作系统, 8 计算机网络 — in that order.
        assertThat(items.subList(0, 11)).allMatch(item -> item.questionId() <= 15);
        assertThat(items.subList(11, 22)).allMatch(item -> item.questionId() > 15 && item.questionId() <= 30);
        assertThat(items.subList(22, 32)).allMatch(item -> item.questionId() > 30 && item.questionId() <= 45);
        assertThat(items.subList(32, 40)).allMatch(item -> item.questionId() > 45);
        assertThat(new HashSet<>(items.stream().map(PaperLayout.Item::questionId).toList())).hasSize(40);
    }

    @Test
    void freshQuestionsComeBeforeSeenOnesAndAnsweredOnesLast() {
        List<PaperAssembler.Candidate> pool = new ArrayList<>();
        LocalDateTime longAgo = LocalDateTime.now().minusDays(60);
        LocalDateTime recently = LocalDateTime.now().minusDays(1);
        pool.add(new PaperAssembler.Candidate(1, "english1.writing.practical.letter", 10.0, null, 0, 2, recently));
        pool.add(new PaperAssembler.Candidate(2, "english1.writing.practical.letter", 10.0, null, 0, 2, longAgo));
        pool.add(new PaperAssembler.Candidate(3, "english1.writing.practical.letter", 10.0, null, 0, 1, null));
        pool.add(new PaperAssembler.Candidate(4, "english1.writing.practical.letter", 10.0, null, 0, 0, null));

        List<PaperAssembler.Candidate> ranked = PaperAssembler.rank(pool, new Random(7));
        assertThat(ranked).extracting(PaperAssembler.Candidate::id).containsExactly(4L, 3L, 2L, 1L);
        assertThat(assemble("english1.writing-a", pool).sections().getFirst().items())
                .extracting(PaperLayout.Item::questionId).containsExactly(4L);
    }

    @Test
    void aVariableScoreSlotTakesQuestionsWhoseScoresAddUpToItsTotal() {
        // 408 综合: 数据结构 2 questions = 23, 组成 2 = 23, 操作系统 2 = 15, 网络 1 = 9.
        List<PaperAssembler.Candidate> pool = new ArrayList<>(List.of(
                fresh(1, "cs408.ds.list.application", 15.0),
                fresh(2, "cs408.ds.graph.shortest-path", 10.0),
                fresh(3, "cs408.ds.tree.binary", 13.0),
                fresh(4, "cs408.co.memory.cache", 13.0),
                fresh(5, "cs408.co.cpu.pipeline", 10.0),
                fresh(6, "cs408.os.process.sync", 7.0),
                fresh(7, "cs408.os.memory.replacement", 8.0),
                fresh(8, "cs408.cn.network.subnet", 9.0)));
        PaperAssembler.Result result = assemble("cs408.comprehensive", pool);

        assertThat(result.complete()).isTrue();
        PaperLayout.Section section = result.sections().getFirst();
        assertThat(section.items()).hasSize(7);
        assertThat(section.worth()).isEqualTo(70.0);
        Set<Long> ds = new HashSet<>(section.items().subList(0, 2).stream().map(PaperLayout.Item::questionId).toList());
        assertThat(ds).isEqualTo(Set.of(2L, 3L)); // 13 + 10; 15 + 10 and 15 + 13 do not make 23
    }

    @Test
    void readingTextsAreTakenWholeInOrder() {
        List<PaperAssembler.Candidate> pool = new ArrayList<>();
        long id = 1;
        for (long group = 100; group < 105; group++) {
            for (int seq = 1; seq <= 5; seq++) {
                pool.add(new PaperAssembler.Candidate(id++, "english1.reading.types.detail", 2.0, group, seq, 0, null));
            }
        }
        // A text missing a question is not a whole text.
        pool.removeIf(candidate -> candidate.groupId() == 104 && candidate.groupSeq() == 3);
        PaperAssembler.Result result = assemble("english1.reading-a", pool);

        assertThat(result.complete()).isTrue();
        List<PaperLayout.Item> items = result.sections().getFirst().items();
        assertThat(items).hasSize(20);
        for (int text = 0; text < 4; text++) {
            List<Long> ids = items.subList(text * 5, text * 5 + 5).stream().map(PaperLayout.Item::questionId).toList();
            long first = ids.getFirst();
            assertThat(ids).containsExactly(first, first + 1, first + 2, first + 3, first + 4);
        }
        assertThat(items).noneMatch(item -> item.questionId() > 20); // group 104 was incomplete
    }

    @Test
    void aSlotTheBankCannotFillIsReportedNotPadded() {
        List<PaperAssembler.Candidate> pool = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            pool.add(fresh(i, "cs408.ds.tree.binary", 2.0)); // 数据结构 only
        }
        PaperAssembler.Result result = assemble("cs408.choice", pool);

        assertThat(result.complete()).isFalse();
        assertThat(result.sections()).isEmpty();
        assertThat(result.shortages()).extracting(PaperAssembler.Shortage::scope)
                .containsExactly("cs408.co", "cs408.os", "cs408.cn");
        assertThat(result.shortages().getFirst().needed()).isEqualTo(11);
        assertThat(result.shortages().getFirst().available()).isZero();
    }

    @Test
    void twoAssembliesFromAWidePoolDiffer() {
        List<PaperAssembler.Candidate> pool = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            pool.add(fresh(i, "math1.linear.matrix.rank", 5.0));
            pool.add(fresh(100 + i, "math1.calculus.limit.limit", 5.0));
            pool.add(fresh(200 + i, "math1.probability.moments.expectation", 5.0));
        }
        SyllabusContent.Section choice = syllabus.section("math1.choice").orElseThrow();
        List<Long> first = PaperAssembler.assemble(syllabus, List.of(choice), Map.of("math1.choice", pool),
                new Random(1)).sections().getFirst().items().stream().map(PaperLayout.Item::questionId).toList();
        List<Long> second = PaperAssembler.assemble(syllabus, List.of(choice), Map.of("math1.choice", pool),
                new Random(2)).sections().getFirst().items().stream().map(PaperLayout.Item::questionId).toList();
        assertThat(first).hasSize(10).isNotEqualTo(second);
    }

    private static PaperAssembler.Result assemble(String section, List<PaperAssembler.Candidate> pool) {
        return PaperAssembler.assemble(syllabus, List.of(syllabus.section(section).orElseThrow()),
                Map.of(section, pool), new Random(42));
    }

    private static PaperAssembler.Candidate fresh(long id, String point, double score) {
        return new PaperAssembler.Candidate(id, point, score, null, 0, 0, null);
    }
}
