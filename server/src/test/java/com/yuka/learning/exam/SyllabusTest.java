package com.yuka.learning.exam;

import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.syllabus.KnowledgeNode;
import com.yuka.learning.exam.syllabus.NodeKind;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;
import com.yuka.learning.exam.syllabus.SyllabusLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The shipped syllabus content, and the rules {@link Syllabus#of} enforces on
 * any content. Framework-free: the syllabus is loaded exactly as the
 * application loads it, minus Spring.
 */
class SyllabusTest {

    private static Syllabus syllabus;

    @BeforeAll
    static void load() {
        syllabus = SyllabusLoader.load(JsonMapper.builder().build());
    }

    // --- the shipped content ----------------------------------------------------

    @Test
    void theFourPapersAreLoadedInExamOrderWithTheirOfficialStructure() {
        assertThat(syllabus.subjectDocuments()).extracting(SyllabusContent.Subject::code)
                .containsExactly("politics", "english1", "math1", "cs408");
        assertThat(syllabus.subjectDocuments()).extracting(SyllabusContent.Subject::paperCode)
                .containsExactly("101", "201", "301", "408");
        assertThat(syllabus.subjectDocuments()).extracting(SyllabusContent.Subject::fullScore)
                .containsExactly(100, 100, 150, 150);
        assertThat(syllabus.blueprint().exam()).isEqualTo("11408");
    }

    @Test
    void thePapersKeepTheTwoDayTimetableAndTheirPastPaperRanges() {
        assertThat(syllabus.subjectDocuments()).extracting(SyllabusContent.Subject::examDay)
                .containsExactly(1, 1, 2, 2);
        assertThat(syllabus.subjectDocuments()).extracting(SyllabusContent.Subject::startTime)
                .containsExactly("08:30", "14:00", "08:30", "14:00");
        // 408 was introduced in 2009 and 英语（一） in 2010 — no 真题 exists before them.
        assertThat(syllabus.subject(ExamSubject.CS_408).pastPaperFirstYear()).isEqualTo(2009);
        assertThat(syllabus.subject(ExamSubject.ENGLISH_1).pastPaperFirstYear()).isEqualTo(2010);
    }

    @Test
    void cs408ModulesCarryTheirStatedScores() {
        assertThat(syllabus.require("cs408").childCodes())
                .containsExactly("cs408.ds", "cs408.co", "cs408.os", "cs408.cn");
        assertThat(List.of("cs408.ds", "cs408.co", "cs408.os", "cs408.cn"))
                .extracting(code -> syllabus.require(code).examShare())
                .containsExactly(45.0, 45.0, 35.0, 25.0);
    }

    @Test
    void everyPaperDividesItsFullScoreAmongItsPoints() {
        for (ExamSubject subject : ExamSubject.values()) {
            double points = syllabus.pointsUnder(subject.code()).stream()
                    .mapToDouble(code -> syllabus.require(code).examShare())
                    .sum();
            assertThat(points).as(subject.code()).isCloseTo(syllabus.require(subject.code()).examShare(),
                    within(1e-6));
        }
    }

    @Test
    void englishFoundationsCarryNoDirectScoreButAreStillPoints() {
        KnowledgeNode vocab = syllabus.require("english1.foundation.vocab.core");
        assertThat(vocab.isPoint()).isTrue();
        assertThat(vocab.examShare()).isZero();
        assertThat(vocab.weight()).isEqualTo(3);
    }

    @Test
    void pathsLabelsAndSubtreesFollowTheHierarchy() {
        String sync = "cs408.os.process.sync";
        assertThat(syllabus.path(sync)).extracting(KnowledgeNode::kind)
                .containsExactly(NodeKind.SUBJECT, NodeKind.MODULE, NodeKind.CHAPTER, NodeKind.POINT);
        assertThat(syllabus.label(sync)).isEqualTo("操作系统 › 进程管理 › 同步与互斥：锁、信号量与条件变量");
        assertThat(syllabus.label("cs408")).isEqualTo("计算机学科专业基础");
        assertThat(syllabus.pointsUnder("cs408.os.process")).contains(sync).allMatch(code -> code.startsWith("cs408.os.process."));
        assertThat(syllabus.pointsUnder(sync)).containsExactly(sync);
        assertThat(Syllabus.within(sync, "cs408.os")).isTrue();
        assertThat(Syllabus.within("cs408.osx", "cs408.os")).isFalse(); // a prefix is not a parent
        assertThat(ExamSubject.ofNode(sync)).isEqualTo(ExamSubject.CS_408);
    }

    @Test
    void resolveTreatsBlankAsUnanchoredAndRejectsUnknownCodes() {
        assertThat(syllabus.resolve(null)).isNull();
        assertThat(syllabus.resolve("  ")).isNull();
        assertThat(syllabus.resolve(" math1.linear ")).isEqualTo("math1.linear");
        assertThatThrownBy(() -> syllabus.resolve("math1.topology"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ExamErrorCode.NODE_NOT_FOUND));
    }

    @Test
    void everySectionCarriesItsSuggestedTimeAndTheSectionsFillThePaper() {
        for (SyllabusContent.Subject paper : syllabus.subjectDocuments()) {
            assertThat(paper.sections().stream().mapToInt(SyllabusContent.Section::minutes).sum())
                    .as(paper.code()).isEqualTo(paper.durationMinutes());
        }
        assertThat(syllabus.section("cs408.choice")).get()
                .extracting(SyllabusContent.Section::minutes).isEqualTo(80);
        assertThat(syllabus.section("nope")).isEmpty();
    }

    @Test
    void theSlotsReproduceTheRealPaperStructure() {
        // 408 选择: 11 数据结构 · 11 组成原理 · 10 操作系统 · 8 计算机网络.
        assertThat(syllabus.section("cs408.choice").orElseThrow().slots())
                .extracting(SyllabusContent.Slot::scope, SyllabusContent.Slot::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("cs408.ds", 11),
                        org.assertj.core.groups.Tuple.tuple("cs408.co", 11),
                        org.assertj.core.groups.Tuple.tuple("cs408.os", 10),
                        org.assertj.core.groups.Tuple.tuple("cs408.cn", 8));
        // 408 综合: the points per module are the module scores minus the choice questions.
        assertThat(syllabus.section("cs408.comprehensive").orElseThrow().slots())
                .extracting(SyllabusContent.Slot::total)
                .containsExactly(23.0, 23.0, 15.0, 9.0);
        // English reading is sat as whole texts.
        assertThat(syllabus.section("english1.reading-a").orElseThrow().slots().getFirst().group()).isEqualTo(5);
        assertThat(syllabus.section("english1.cloze").orElseThrow().slots().getFirst().group()).isEqualTo(20);
    }

    @Test
    void politicsSlotsReproduceTheModuleScoresExactly() {
        // Single 1 point, multiple 2 points, analysis 10 points per question.
        SyllabusContent.Subject politics = syllabus.subject(ExamSubject.POLITICS);
        java.util.Map<String, Double> byModule = new java.util.HashMap<>();
        for (SyllabusContent.Section section : politics.sections()) {
            for (SyllabusContent.Slot slot : section.slots()) {
                byModule.merge(slot.scope(), section.score() * slot.count(), Double::sum);
            }
        }
        for (SyllabusContent.Module module : politics.modules()) {
            assertThat(byModule.get(module.code())).as(module.code()).isEqualTo((double) module.score());
        }
    }

    @Test
    void positionsFollowTreePreOrder() {
        assertThat(syllabus.position("cs408.ds")).isLessThan(syllabus.position("cs408.ds.basics.complexity"));
        assertThat(syllabus.position("cs408.ds.sort.analysis")).isLessThan(syllabus.position("cs408.co"));
        assertThat(syllabus.position("unknown")).isEqualTo(Integer.MAX_VALUE);
    }

    // --- the rules, on synthetic content -------------------------------------------

    @Test
    void sectionMinutesThatDoNotFillThePaperFailTheBoot() {
        List<SyllabusContent.Subject> subjects = withCs408Section(new SyllabusContent.Section("cs408.choice", "单选",
                "single_choice", 75, 2.0, 150, 120, List.of(new SyllabusContent.Slot("cs408.ds", 75, null, null))));
        assertThatThrownBy(() -> Syllabus.of(blueprint(), subjects))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minutes");
    }

    @Test
    void slotsThatDoNotHoldTheSectionFailTheBoot() {
        assertThatThrownBy(() -> Syllabus.of(blueprint(), withCs408Section(
                choice(List.of(new SyllabusContent.Slot("cs408.ds", 74, null, null))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slots hold 74");
        assertThatThrownBy(() -> Syllabus.of(blueprint(), withCs408Section(
                choice(List.of(new SyllabusContent.Slot("politics.m", 75, null, null))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a module or chapter of cs408");
        assertThatThrownBy(() -> Syllabus.of(blueprint(), withCs408Section(
                choice(List.of(new SyllabusContent.Slot("cs408.ds", 75, 100.0, null))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is worth 150");
        assertThatThrownBy(() -> Syllabus.of(blueprint(), withCs408Section(
                choice(List.of(new SyllabusContent.Slot("cs408.ds", 75, null, 4))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("group size");
    }

    @Test
    void variableScoreSlotsMustDeclareTotalsThatFillTheSection() {
        assertThatThrownBy(() -> Syllabus.of(blueprint(), withCs408Section(new SyllabusContent.Section(
                "cs408.comprehensive", "综合", "open", 2, null, 150, 180,
                List.of(new SyllabusContent.Slot("cs408.ds", 2, null, null))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("needs a total");
        assertThatThrownBy(() -> Syllabus.of(blueprint(), withCs408Section(new SyllabusContent.Section(
                "cs408.comprehensive", "综合", "open", 2, null, 150, 180,
                List.of(new SyllabusContent.Slot("cs408.ds", 2, 140.0, null))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slots are worth 140");
    }

    @Test
    void aCodeThatIsNotADirectChildFailsTheBoot() {
        assertThatThrownBy(() -> Syllabus.of(blueprint(), subjectsWithPoint("cs408.ds.basics.x.deeper", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("direct child");
    }

    @Test
    void aWeightOutsideOneToThreeFailsTheBoot() {
        assertThatThrownBy(() -> Syllabus.of(blueprint(), subjectsWithPoint("cs408.ds.basics.x", 4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("weight");
    }

    @Test
    void moduleScoresThatDoNotSumToThePaperFailTheBoot() {
        List<SyllabusContent.Subject> subjects = subjectsWithPoint("cs408.ds.basics.x", 1);
        SyllabusContent.Subject cs = subjects.get(3);
        SyllabusContent.Subject broken = new SyllabusContent.Subject(cs.code(), cs.paperCode(), cs.name(), 149,
                cs.durationMinutes(), cs.examDay(), cs.startTime(), cs.pastPaperFirstYear(), null,
                List.of(new SyllabusContent.Section("cs408.choice", "单选", "single_choice", 1, null, 149, 180,
                        List.of(new SyllabusContent.Slot("cs408.ds", 1, 149.0, null)))),
                cs.modules());
        List<SyllabusContent.Subject> replaced = List.of(subjects.get(0), subjects.get(1), subjects.get(2), broken);
        assertThatThrownBy(() -> Syllabus.of(blueprint(), replaced))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("module scores");
    }

    @Test
    void aPaperOnTheWrongExamDayFailsTheBoot() {
        List<SyllabusContent.Subject> subjects = subjectsWithPoint("cs408.ds.basics.x", 1);
        SyllabusContent.Subject cs = subjects.get(3);
        SyllabusContent.Subject dayOne = new SyllabusContent.Subject(cs.code(), cs.paperCode(), cs.name(),
                cs.fullScore(), cs.durationMinutes(), 1, cs.startTime(), cs.pastPaperFirstYear(), null,
                cs.sections(), cs.modules());
        assertThatThrownBy(() -> Syllabus.of(blueprint(), List.of(subjects.get(0), subjects.get(1),
                subjects.get(2), dayOne)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("day 2");
    }

    @Test
    void aMalformedStartTimeOrAFuturePastPaperYearFailsTheBoot() {
        List<SyllabusContent.Subject> subjects = subjectsWithPoint("cs408.ds.basics.x", 1);
        SyllabusContent.Subject cs = subjects.get(3);
        SyllabusContent.Subject badTime = new SyllabusContent.Subject(cs.code(), cs.paperCode(), cs.name(),
                cs.fullScore(), cs.durationMinutes(), cs.examDay(), "2pm", cs.pastPaperFirstYear(), null,
                cs.sections(), cs.modules());
        assertThatThrownBy(() -> Syllabus.of(blueprint(), List.of(subjects.get(0), subjects.get(1),
                subjects.get(2), badTime)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HH:mm");

        SyllabusContent.Subject futureYear = new SyllabusContent.Subject(cs.code(), cs.paperCode(), cs.name(),
                cs.fullScore(), cs.durationMinutes(), cs.examDay(), cs.startTime(), 2027, null,
                cs.sections(), cs.modules());
        assertThatThrownBy(() -> Syllabus.of(blueprint(), List.of(subjects.get(0), subjects.get(1),
                subjects.get(2), futureYear)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("past-paper year");
    }

    @Test
    void aBlueprintThatDoesNotListTheFourPapersFailsTheBoot() {
        SyllabusContent.Blueprint partial = new SyllabusContent.Blueprint("11408", "t", 2027,
                List.of("politics", "english1", "math1"));
        assertThatThrownBy(() -> Syllabus.of(partial, subjectsWithPoint("cs408.ds.basics.x", 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 408's single section in the minimal fixture: 75 choice questions, the whole 180 minutes. */
    private static SyllabusContent.Section choice(List<SyllabusContent.Slot> slots) {
        return new SyllabusContent.Section("cs408.choice", "单选", "single_choice", 75, 2.0, 150, 180, slots);
    }

    /** The minimal four papers with 408's section replaced. */
    private static List<SyllabusContent.Subject> withCs408Section(SyllabusContent.Section section) {
        List<SyllabusContent.Subject> subjects = subjectsWithPoint("cs408.ds.basics.x", 1);
        SyllabusContent.Subject cs = subjects.get(3);
        return List.of(subjects.get(0), subjects.get(1), subjects.get(2),
                new SyllabusContent.Subject(cs.code(), cs.paperCode(), cs.name(), cs.fullScore(),
                        cs.durationMinutes(), cs.examDay(), cs.startTime(), cs.pastPaperFirstYear(), null,
                        List.of(section), cs.modules()));
    }

    private static SyllabusContent.Blueprint blueprint() {
        return new SyllabusContent.Blueprint("11408", "t", 2027, List.of("politics", "english1", "math1", "cs408"));
    }

    /** Four minimal papers; 408's single 考点 carries the given code and weight. */
    private static List<SyllabusContent.Subject> subjectsWithPoint(String cs408Point, int weight) {
        return List.of(
                minimal("politics", "politics.m.c.p", 1),
                minimal("english1", "english1.m.c.p", 1),
                minimal("math1", "math1.m.c.p", 1),
                new SyllabusContent.Subject("cs408", "408", "408", 150, 180, 2, "14:00", 2009, null,
                        List.of(choice(List.of(new SyllabusContent.Slot("cs408.ds", 75, null, null)))),
                        List.of(new SyllabusContent.Module("cs408.ds", "数据结构", 150, List.of(
                                new SyllabusContent.Chapter("cs408.ds.basics", "基本概念", List.of(
                                        new SyllabusContent.Point(cs408Point, "考点", weight))))))));
    }

    private static SyllabusContent.Subject minimal(String code, String point, int weight) {
        String module = point.substring(0, point.indexOf(".c.p"));
        int day = code.equals("politics") || code.equals("english1") ? 1 : 2;
        return new SyllabusContent.Subject(code, "x", code, 100, 180, day, "08:30", 2010, null,
                List.of(new SyllabusContent.Section(code + ".s", "s", "open", 1, null, 100, 180,
                        List.of(new SyllabusContent.Slot(module, 1, 100.0, null)))),
                List.of(new SyllabusContent.Module(module, "m", 100, List.of(
                        new SyllabusContent.Chapter(module + ".c", "c", List.of(
                                new SyllabusContent.Point(point, "p", weight)))))));
    }
}
