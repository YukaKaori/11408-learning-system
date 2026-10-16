package com.yuka.learning.content;

import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The content rules, on the shipped packs and on synthetic ones. Framework-free:
 * this is exactly the check the boot import and {@link ContentCheck} run.
 */
class PackValidatorTest {

    private static Syllabus syllabus;
    private static JsonMapper mapper;

    @BeforeAll
    static void load() {
        mapper = JsonMapper.builder().build();
        syllabus = SyllabusLoader.load(mapper);
    }

    @Test
    void theShippedPacksAreValid() {
        List<ContentPack> packs = PackSource.select(PackSource.loadClasspath(mapper)).stream()
                .map(PackSource.Loaded::pack).toList();
        PackValidator.Result result = PackValidator.validate(syllabus, packs);
        assertThat(result.errors()).isEmpty();
        assertThat(result.content().items()).hasSizeGreaterThan(100);
    }

    @Test
    void theShippedBankSitsEveryPaperWholeAtLeastOnce() {
        // The M2 promise: every printed section of all four papers can be sat in
        // the app — assembled from the bank and as a library paper (408 is
        // assembled only). Coverage measures exactly what the assembler can do.
        List<ContentPack> packs = PackSource.select(PackSource.loadClasspath(mapper)).stream()
                .map(PackSource.Loaded::pack).toList();
        Coverage.Report report = Coverage.of(syllabus, PackValidator.validate(syllabus, packs).content());
        assertThat(report.papers()).allSatisfy(paper ->
                assertThat(paper.wholePapers()).as(paper.subject()).isGreaterThanOrEqualTo(1));
        assertThat(report.papers()).filteredOn(paper -> !paper.subject().equals("cs408"))
                .allSatisfy(paper -> assertThat(paper.libraryPapers()).as(paper.subject()).isGreaterThanOrEqualTo(1));
    }

    @Test
    void theQuestionsOfAGroupShareTheModuleOfTheirFirstKaodian() {
        ContentPack.Item stray = new ContentPack.Item("q-stray", "english1", "english1.reading-a", "single_choice",
                "g-1", List.of("english1.foundation.vocab.core"), 2, null, null, null, "stem", null,
                List.of("a", "b", "c", "d"), "A", null, null);
        PackValidator.Result result = PackValidator.validate(syllabus, List.of(pack(
                List.of(group("g-1")), List.of(grouped("q-1", "g-1"), stray), List.of())));
        assertThat(result.errors()).singleElement().asString()
                .contains("'g-1'").contains("english1.foundation").contains("english1.reading");
    }

    @Test
    void aTextThatNumbersItsQuestionsNumbersEveryOneOnceAndInOrder() {
        assertThat(clozeErrors("A **[1]** b **[2]** c.", "Blank **[1]**", "Blank **[2]**")).isEmpty();
        assertThat(clozeErrors("A **[1]** b c.", "Blank **[1]**", "Blank **[2]**"))
                .singleElement().asString().contains("**[2]** must appear exactly once");
        assertThat(clozeErrors("A **[2]** b **[1]** c.", "Blank **[1]**", "Blank **[2]**"))
                .singleElement().asString().contains("**[2]** must appear exactly once, after **[1]**");
        assertThat(clozeErrors("A **[1]** b **[1]** **[2]**.", "Blank **[1]**", "Blank **[2]**"))
                .singleElement().asString().contains("**[1]** must appear exactly once");
        assertThat(clozeErrors("A **[1]** b **[2]** c.", "Blank **[1]**", "Blank **[3]**"))
                .singleElement().asString().contains("'q-2'").contains("must name **[2]**");
        assertThat(clozeErrors("A **[1]** b **[2]** c **[3]**.", "Blank **[1]**", "Blank **[2]**"))
                .singleElement().asString().contains("marks **[3]** but has only 2 questions");
        // Translation texts number their sentences instead of blanks.
        assertThat(clozeErrors("**(1)** *One.* **(2)** *Two.*", "译 **(1)**", "译 **(2)**")).isEmpty();
        assertThat(clozeErrors("No markers at all.", "Blank one", "Blank two")).isEmpty();
    }

    private static List<String> clozeErrors(String passage, String firstStem, String secondStem) {
        ContentPack.Group text = new ContentPack.Group("g-c", "english1", "english1.cloze", null, passage, null);
        List<ContentPack.Item> blanks = List.of(clozeItem("q-1", firstStem), clozeItem("q-2", secondStem));
        return PackValidator.validate(syllabus, List.of(pack(List.of(text), blanks, List.of()))).errors();
    }

    private static ContentPack.Item clozeItem(String key, String stem) {
        return new ContentPack.Item(key, "english1", "english1.cloze", "single_choice", "g-c",
                List.of("english1.cloze.skills.word"), 2, null, null, null, stem, null,
                List.of("a", "b", "c", "d"), "A", null, null);
    }

    @Test
    void aValidPackWithAGroupAndAPaperPasses() {
        PackValidator.Result result = PackValidator.validate(syllabus, List.of(pack(
                List.of(group("g-1")),
                List.of(choice("q-1", "cs408.ds.tree.binary"), grouped("q-2", "g-1"), grouped("q-3", "g-1")),
                List.of())));
        assertThat(result.errors()).isEmpty();
        assertThat(result.content().groups().getFirst().members()).containsExactly("q-2", "q-3");
        assertThat(result.content().items()).extracting(PackValidator.ItemEntry::groupSeq).containsExactly(0, 1, 2);
        // A uniform section fixes the worth even when the item omits it.
        assertThat(result.content().items().get(1).validated().score()).isEqualByComparingTo("2");
    }

    @Test
    void keysArePermanentGlobalIdentities() {
        PackValidator.Result result = PackValidator.validate(syllabus, List.of(pack(
                List.of(group("same")),
                List.of(choice("same", "cs408.ds.tree.binary"), choice("Bad Key", "cs408.ds.tree.binary")),
                List.of())));
        assertThat(result.errors()).anyMatch(e -> e.contains("'same'") && e.contains("already used"));
        assertThat(result.errors()).anyMatch(e -> e.contains("'Bad Key'") && e.contains("malformed key"));
    }

    @Test
    void groupsAreWholeConsistentAndShared() {
        ContentPack.Item ownPassage = new ContentPack.Item("q-own", "english1", "english1.reading-a",
                "single_choice", "g-1", List.of("english1.reading.types.detail"), 2, null, null, null, "stem",
                "my own passage", List.of("a", "b", "c", "d"), "A", null, null);
        ContentPack.Item wrongSection = new ContentPack.Item("q-sec", "english1", "english1.cloze", "single_choice",
                "g-1", List.of("english1.cloze.skills.logic"), 2, null, null, null, "stem", null,
                List.of("a", "b", "c", "d"), "A", null, null);
        PackValidator.Result result = PackValidator.validate(syllabus, List.of(pack(
                List.of(group("g-1"), group("g-lonely")),
                List.of(grouped("q-1", "g-1"), grouped("q-2", "g-unknown"), ownPassage, wrongSection,
                        grouped("q-3", "g-lonely")),
                List.of())));
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown group 'g-unknown'"));
        assertThat(result.errors()).anyMatch(e -> e.contains("'q-own'") && e.contains("none of its own"));
        assertThat(result.errors()).anyMatch(e -> e.contains("'q-sec'") && e.contains("subject and section"));
        assertThat(result.errors()).anyMatch(e -> e.contains("'g-1'") && e.contains("at least two"));
        assertThat(result.errors()).anyMatch(e -> e.contains("'g-lonely'") && e.contains("at least two"));
    }

    @Test
    void worthAndTypeFollowThePrintedSection() {
        ContentPack.Item wrongWorth = new ContentPack.Item("q-worth", "cs408", "cs408.choice", "single_choice", null,
                List.of("cs408.ds.tree.binary"), 2, 3.0, null, null, "stem", null, List.of("a", "b", "c", "d"), "A",
                null, null);
        ContentPack.Item noWorth = new ContentPack.Item("q-none", "cs408", "cs408.comprehensive", "open", null,
                List.of("cs408.ds.tree.binary"), 3, null, null, null, "stem", null, null, "answer", null, null);
        ContentPack.Item wrongType = new ContentPack.Item("q-type", "math1", "math1.fill", "single_choice", null,
                List.of("math1.linear.matrix.rank"), 2, null, null, null, "stem", null, List.of("a", "b"), "A", null,
                null);
        PackValidator.Result result = PackValidator.validate(syllabus,
                List.of(pack(List.of(), List.of(wrongWorth, noWorth, wrongType), List.of())));
        assertThat(result.errors()).anyMatch(e -> e.contains("'q-worth'") && e.contains("is worth 2.0"));
        assertThat(result.errors()).anyMatch(e -> e.contains("'q-none'") && e.contains("needs a score"));
        assertThat(result.errors()).anyMatch(e -> e.contains("'q-type'") && e.contains("fill_blank questions"));
    }

    @Test
    void aLibraryPaperMatchesThePrintedPaper() {
        List<ContentPack.Item> items = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            items.add(choice("c-" + i, "cs408.ds.tree.binary"));
            keys.add("c-" + i);
        }
        ContentPack.Paper good = new ContentPack.Paper("p-good", "cs408", "mock", "选择题专项",
                null, List.of(new ContentPack.PaperSection("cs408.choice", keys)));
        ContentPack.Paper short1 = new ContentPack.Paper("p-short", "cs408", "mock", "少一题", null,
                List.of(new ContentPack.PaperSection("cs408.choice", keys.subList(0, 39))));
        ContentPack.Paper unknown = new ContentPack.Paper("p-unknown", "cs408", "mock", "未知题", null,
                List.of(new ContentPack.PaperSection("cs408.choice",
                        java.util.stream.Stream.concat(keys.subList(0, 39).stream(), java.util.stream.Stream.of("nope"))
                                .toList())));
        ContentPack.Paper yearless = new ContentPack.Paper("p-year", "cs408", "past_paper", "真题", null,
                List.of(new ContentPack.PaperSection("cs408.choice", keys)));
        ContentPack.Paper mockYear = new ContentPack.Paper("p-mock-year", "cs408", "mock", "模拟", 2024,
                List.of(new ContentPack.PaperSection("cs408.choice", keys)));

        PackValidator.Result result = PackValidator.validate(syllabus,
                List.of(pack(List.of(), items, List.of(good, short1, unknown, yearless, mockYear))));

        assertThat(result.errors()).anyMatch(e -> e.contains("'p-short'") && e.contains("prints 40"));
        assertThat(result.errors()).anyMatch(e -> e.contains("'p-unknown'") && e.contains("unknown question 'nope'"));
        assertThat(result.errors()).anyMatch(e -> e.contains("'p-year'") && e.contains("needs a year"));
        assertThat(result.errors()).anyMatch(e -> e.contains("'p-mock-year'") && e.contains("no year"));
        assertThat(result.content().papers()).singleElement().satisfies(paper -> {
            assertThat(paper.paper().key()).isEqualTo("p-good");
            assertThat(paper.whole()).isFalse();
            assertThat(paper.fullScore()).isEqualTo(80.0);
            assertThat(paper.durationMinutes()).isEqualTo(80); // the section's suggested minutes
        });
    }

    @Test
    void aPaperTakesGroupsWholeAndInOrder() {
        List<ContentPack.Item> items = new ArrayList<>();
        List<ContentPack.Group> groups = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int g = 1; g <= 4; g++) {
            groups.add(readingGroup("r-" + g));
            for (int i = 1; i <= 5; i++) {
                items.add(readingItem("r-" + g + "-" + i, "r-" + g));
                keys.add("r-" + g + "-" + i);
            }
        }
        List<String> broken = new ArrayList<>(keys);
        java.util.Collections.swap(broken, 0, 1);
        ContentPack.Paper ok = new ContentPack.Paper("p-ok", "english1", "mock", "阅读", null,
                List.of(new ContentPack.PaperSection("english1.reading-a", keys)));
        ContentPack.Paper bad = new ContentPack.Paper("p-bad", "english1", "mock", "乱序", null,
                List.of(new ContentPack.PaperSection("english1.reading-a", broken)));

        PackValidator.Result result = PackValidator.validate(syllabus, List.of(pack(groups, items, List.of(ok, bad))));

        assertThat(result.errors()).singleElement().asString().contains("'p-bad'").contains("whole and in order");
        assertThat(result.content().papers()).extracting(paper -> paper.paper().key()).containsExactly("p-ok");
    }

    // --- fixtures ---------------------------------------------------------------------

    private static ContentPack pack(List<ContentPack.Group> groups, List<ContentPack.Item> items,
                                    List<ContentPack.Paper> papers) {
        return new ContentPack("test-pack", "测试", 1, null, null, groups, items, papers);
    }

    private static ContentPack.Item choice(String key, String point) {
        return new ContentPack.Item(key, "cs408", "cs408.choice", "single_choice", null, List.of(point), 2, null,
                "原创", null, "题干 " + key, null, List.of("甲", "乙", "丙", "丁"), "A", null, "解析");
    }

    private static ContentPack.Group group(String key) {
        return new ContentPack.Group(key, "english1", "english1.reading-a", null, "A passage.", null);
    }

    private static ContentPack.Item grouped(String key, String group) {
        return readingItem(key, group);
    }

    private static ContentPack.Group readingGroup(String key) {
        return group(key);
    }

    private static ContentPack.Item readingItem(String key, String group) {
        return new ContentPack.Item(key, "english1", "english1.reading-a", "single_choice", group,
                List.of("english1.reading.types.detail"), 2, null, null, null, "Question " + key, null,
                List.of("a", "b", "c", "d"), "A", null, null);
    }
}
