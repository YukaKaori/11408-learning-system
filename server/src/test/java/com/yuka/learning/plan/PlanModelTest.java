package com.yuka.learning.plan;

import com.yuka.learning.exam.ExamPhase;
import com.yuka.learning.exam.ExamSubject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The plan's arithmetic, by hand: base splits per phase, the gap tilt, the
 * neutral treatment of papers without evidence, block rounding that always
 * adds up to the day, and phase dates that agree with {@link ExamPhase#of}.
 */
class PlanModelTest {

    private static final LocalDate EXAM = LocalDate.of(2026, 12, 26);

    @Test
    void everyActivePhaseSplitsTheWholeDayAndPoliticsGrowsTowardTheExam() {
        for (ExamPhase phase : List.of(ExamPhase.FOUNDATION, ExamPhase.INTENSIVE, ExamPhase.PAST_PAPERS,
                ExamPhase.SPRINT)) {
            double sum = 0;
            for (ExamSubject subject : ExamSubject.values()) {
                sum += PlanModel.base(phase, subject);
            }
            assertThat(sum).as(phase.name()).isCloseTo(1.0, within(1e-9));
        }
        assertThat(PlanModel.base(ExamPhase.FOUNDATION, ExamSubject.POLITICS))
                .isLessThan(PlanModel.base(ExamPhase.INTENSIVE, ExamSubject.POLITICS));
        assertThat(PlanModel.base(ExamPhase.PAST_PAPERS, ExamSubject.POLITICS))
                .isLessThan(PlanModel.base(ExamPhase.SPRINT, ExamSubject.POLITICS));
    }

    @Test
    void withoutEvidenceTheDayFollowsThePhaseBase() {
        List<PlanModel.Allocation> allocations = PlanModel.allocate(ExamPhase.PAST_PAPERS, 480, inputs(
                null, null, null, null));

        assertThat(allocations).extracting(PlanModel.Allocation::gap).containsOnlyNulls();
        double[] expected = {0.20, 0.20, 0.30, 0.30};
        for (int i = 0; i < expected.length; i++) {
            assertThat(allocations.get(i).share()).isCloseTo(expected[i], within(1e-9));
        }
        // 480 minutes = 96 blocks: 19.2 / 19.2 / 28.8 / 28.8 → the two .8s win the spare blocks.
        assertThat(allocations).extracting(PlanModel.Allocation::dailyMinutes)
                .containsExactly(95, 95, 145, 145);
    }

    @Test
    void aPaperFurtherFromItsTargetGainsTimeAndOneAtTargetKeepsItsBase() {
        // 数学一 30 short on 150 (gap 0.2); 408 already above target (gap 0).
        List<PlanModel.Allocation> allocations = PlanModel.allocate(ExamPhase.PAST_PAPERS, 600, List.of(
                new PlanModel.PaperInput(ExamSubject.POLITICS, 100, null, null),
                new PlanModel.PaperInput(ExamSubject.ENGLISH_1, 100, null, null),
                new PlanModel.PaperInput(ExamSubject.MATH_1, 150, 120, 90.0),
                new PlanModel.PaperInput(ExamSubject.CS_408, 150, 110, 118.0)));

        PlanModel.Allocation math = allocations.get(2);
        PlanModel.Allocation cs = allocations.get(3);
        assertThat(math.gap()).isCloseTo(0.2, within(1e-9));
        assertThat(cs.gap()).isZero();
        // Weights: politics/english take the mean gap (0.1) → .22 each; math .36; 408 .30. Σ = 1.10.
        assertThat(math.share()).isCloseTo(0.36 / 1.10, within(1e-9));
        assertThat(cs.share()).isCloseTo(0.30 / 1.10, within(1e-9));
        assertThat(math.share()).isGreaterThan(math.base());
        assertThat(cs.share()).isLessThan(cs.base());
        assertThat(allocations.stream().mapToInt(PlanModel.Allocation::dailyMinutes).sum()).isEqualTo(600);
    }

    @Test
    void theGapIsClampedSoOneDisasterCannotTakeTheWholeDay() {
        List<PlanModel.Allocation> allocations = PlanModel.allocate(ExamPhase.SPRINT, 480, List.of(
                new PlanModel.PaperInput(ExamSubject.POLITICS, 100, 70, 65.0),
                new PlanModel.PaperInput(ExamSubject.ENGLISH_1, 100, 70, 68.0),
                new PlanModel.PaperInput(ExamSubject.MATH_1, 150, 140, -400.0),
                new PlanModel.PaperInput(ExamSubject.CS_408, 150, 120, 115.0)));

        assertThat(allocations.get(2).gap()).isEqualTo(1.0);
        assertThat(allocations.get(2).share()).isLessThan(0.5);
    }

    @Test
    void blocksAlwaysAddUpToTheDayAndTheOddMinutesGoToTheLargestShare() {
        int[] minutes = PlanModel.blocks(new double[]{0.1, 0.2, 0.3, 0.4}, 457);

        assertThat(minutes[0] + minutes[1] + minutes[2] + minutes[3]).isEqualTo(457);
        assertThat(minutes[3] % PlanModel.BLOCK_MINUTES).isEqualTo(2);
        for (int i = 0; i < 3; i++) {
            assertThat(minutes[i] % PlanModel.BLOCK_MINUTES).isZero();
        }
    }

    @Test
    void afterTheExamThereIsNothingToDivide() {
        List<PlanModel.Allocation> allocations = PlanModel.allocate(ExamPhase.FINISHED, 480, inputs(
                null, null, null, null));

        assertThat(allocations).extracting(PlanModel.Allocation::dailyMinutes).containsOnly(0);
        assertThat(PlanModel.weeklySittings(ExamPhase.FINISHED, ExamSubject.MATH_1)).isZero();
    }

    @Test
    void wholePapersAreOnlyCalledForOnceThePastPaperPhaseBegins() {
        for (ExamSubject subject : ExamSubject.values()) {
            assertThat(PlanModel.weeklySittings(ExamPhase.FOUNDATION, subject)).isZero();
            assertThat(PlanModel.weeklySittings(ExamPhase.INTENSIVE, subject)).isZero();
            assertThat(PlanModel.weeklySittings(ExamPhase.PAST_PAPERS, subject)).isPositive();
        }
        assertThat(PlanModel.weeklySittings(ExamPhase.PAST_PAPERS, ExamSubject.CS_408)).isEqualTo(2);
        assertThat(PlanModel.weeklySittings(ExamPhase.SPRINT, ExamSubject.POLITICS)).isEqualTo(2);
    }

    @Test
    void phaseDatesAgreeWithTheCountdownRule() {
        List<PlanModel.PhaseSpan> timeline = PlanModel.timeline(EXAM);

        assertThat(timeline).extracting(PlanModel.PhaseSpan::phase).containsExactly(ExamPhase.FOUNDATION,
                ExamPhase.INTENSIVE, ExamPhase.PAST_PAPERS, ExamPhase.SPRINT);
        assertThat(timeline.getFirst().start()).isNull();
        for (PlanModel.PhaseSpan span : timeline) {
            assertThat(ExamPhase.of(daysTo(span.end()))).as(span.phase() + " end").isEqualTo(span.phase());
            if (span.start() != null) {
                assertThat(ExamPhase.of(daysTo(span.start()))).as(span.phase() + " start").isEqualTo(span.phase());
                assertThat(ExamPhase.of(daysTo(span.start().minusDays(1)))).isNotEqualTo(span.phase());
            }
        }
        // The sprint runs through day two of the exam.
        assertThat(timeline.getLast().end()).isEqualTo(EXAM.plusDays(1));
    }

    private static long daysTo(LocalDate date) {
        return java.time.temporal.ChronoUnit.DAYS.between(date, EXAM);
    }

    private static List<PlanModel.PaperInput> inputs(Double politics, Double english, Double math, Double cs) {
        return List.of(
                new PlanModel.PaperInput(ExamSubject.POLITICS, 100, 70, politics),
                new PlanModel.PaperInput(ExamSubject.ENGLISH_1, 100, 70, english),
                new PlanModel.PaperInput(ExamSubject.MATH_1, 150, 120, math),
                new PlanModel.PaperInput(ExamSubject.CS_408, 150, 120, cs));
    }
}
