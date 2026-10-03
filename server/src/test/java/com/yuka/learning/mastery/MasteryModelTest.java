package com.yuka.learning.mastery;

import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusLoader;
import com.yuka.learning.question.entity.AttemptResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The learning model, reproducible by hand: decaying, smoothed evidence per
 * 考点, and aggregates weighted by what each 考点 is worth on the day.
 */
class MasteryModelTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 20, 0);
    private static Syllabus syllabus;

    @BeforeAll
    static void load() {
        syllabus = SyllabusLoader.load(JsonMapper.builder().build());
    }

    @Test
    void littleEvidenceIsWeakEvidence() {
        assertThat(evidence(AttemptResult.WRONG).mastery()).isCloseTo(0.25, within(1e-9));
        assertThat(evidence(AttemptResult.CORRECT).mastery()).isCloseTo(0.75, within(1e-9));
        assertThat(evidence(AttemptResult.PARTIAL).mastery()).isCloseTo(0.5, within(1e-9));
        // One lucky answer is "developing", never "mastered".
        assertThat(evidence(AttemptResult.CORRECT).level()).isEqualTo(MasteryModel.Level.DEVELOPING);
    }

    @Test
    void fourRecentCorrectAnswersAreMastery() {
        MasteryModel.Evidence e = evidence(AttemptResult.CORRECT, AttemptResult.CORRECT,
                AttemptResult.CORRECT, AttemptResult.CORRECT);
        assertThat(e.mastery()).isCloseTo(0.9, within(1e-9));
        assertThat(e.level()).isEqualTo(MasteryModel.Level.MASTERED);
        assertThat(e.attempts()).isEqualTo(4);
        assertThat(e.correct()).isEqualTo(4);
    }

    @Test
    void evidenceHalvesEveryThirtyDays() {
        assertThat(MasteryModel.decay(NOW.minusDays(30), NOW)).isCloseTo(0.5, within(1e-9));
        assertThat(MasteryModel.decay(NOW.minusDays(60), NOW)).isCloseTo(0.25, within(1e-9));

        MasteryModel.Evidence old = new MasteryModel.Evidence();
        old.add(AttemptResult.CORRECT, NOW.minusDays(30), NOW);
        // (0.5 × 1 + ½) / (0.5 + 1): a month-old success counts for less.
        assertThat(old.mastery()).isCloseTo(2.0 / 3.0, within(1e-9));
        assertThat(old.lastAttemptAt()).isEqualTo(NOW.minusDays(30));
    }

    @Test
    void anUntestedPointIsUnknownNotZero() {
        MasteryModel.Evidence none = new MasteryModel.Evidence();
        assertThat(none.level()).isEqualTo(MasteryModel.Level.UNTESTED);
        assertThat(none.attempts()).isZero();
    }

    @Test
    void readinessWeighsPointsByExamScoreAndCountsUntestedAsNotSecured() {
        // Two 考点 of 进程管理, both mastered-level evidence; the rest untested.
        MasteryModel.Evidence strong = evidence(AttemptResult.CORRECT, AttemptResult.CORRECT,
                AttemptResult.CORRECT, AttemptResult.CORRECT);
        MasterySnapshot snapshot = MasterySnapshot.build(syllabus,
                Map.of("cs408.os.process.sync", strong, "cs408.os.process.ipc", strong),
                Map.of("cs408.os.process.sync", 3L), Map.of("cs408.os.process.sync", 1));

        MasterySnapshot.NodeStats chapter = snapshot.of("cs408.os.process");
        List<String> points = syllabus.pointsUnder("cs408.os.process");
        double total = points.stream().mapToDouble(p -> syllabus.require(p).examShare()).sum();
        double tested = syllabus.require("cs408.os.process.sync").examShare()
                + syllabus.require("cs408.os.process.ipc").examShare();

        assertThat(chapter.coverage()).isCloseTo(tested / total, within(1e-9));
        assertThat(chapter.readiness()).isCloseTo(0.9 * tested / total, within(1e-9));
        assertThat(chapter.available()).isEqualTo(3);
        assertThat(chapter.mistakes()).isEqualTo(1);
        // A heavily-examined 考点 (weight 3) moves readiness more than a light one (weight 1).
        assertThat(syllabus.require("cs408.os.process.sync").examShare())
                .isEqualTo(3 * syllabus.require("cs408.os.process.ipc").examShare());

        MasterySnapshot.NodeStats paper = snapshot.of("cs408");
        assertThat(paper.readiness()).isLessThan(chapter.readiness());
        assertThat(paper.attempts()).isEqualTo(8);
        assertThat(snapshot.of("cs408.os.process.sync").mastery()).isCloseTo(0.9, within(1e-9));
        assertThat(snapshot.of("cs408.os.process.concepts").mastery()).isNull();
    }

    @Test
    void aggregatesCountEachQuestionMistakeAndAnswerOnce() {
        // One question tests two 考点 of 进程管理. It is evidence for both 考点, and
        // available at both — but it is one question of the chapter (and of 408),
        // one mistake, and one answer; the counts must match the lists they open.
        List<String> tags = List.of("cs408.os.process.sync", "cs408.os.process.ipc");
        MasteryModel.Evidence wrong = evidence(AttemptResult.WRONG);
        NodeCounts counts = new NodeCounts(syllabus).question(tags).mistake(tags).attempt(tags, false);
        MasterySnapshot snapshot = MasterySnapshot.build(syllabus,
                Map.of("cs408.os.process.sync", wrong, "cs408.os.process.ipc", wrong), counts);

        for (String point : tags) {
            assertThat(snapshot.of(point).available()).as(point).isEqualTo(1);
            assertThat(snapshot.of(point).attempts()).as(point).isEqualTo(1);
        }
        for (String aggregate : List.of("cs408.os.process", "cs408.os", "cs408")) {
            MasterySnapshot.NodeStats stats = snapshot.of(aggregate);
            assertThat(stats.available()).as(aggregate).isEqualTo(1);
            assertThat(stats.mistakes()).as(aggregate).isEqualTo(1);
            assertThat(stats.attempts()).as(aggregate).isEqualTo(1);
            assertThat(stats.correct()).as(aggregate).isZero();
        }
        assertThat(snapshot.of("math1").available()).isZero();
    }

    private static MasteryModel.Evidence evidence(AttemptResult... results) {
        MasteryModel.Evidence e = new MasteryModel.Evidence();
        for (AttemptResult result : results) {
            e.add(result, NOW, NOW);
        }
        return e;
    }
}
