package com.yuka.learning.mastery;

import com.yuka.learning.exam.ExamPhase;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusLoader;
import com.yuka.learning.question.entity.AttemptResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recommendations are explainable arithmetic over the snapshot: only
 * practisable 考点, weighted by exam score, pushed by need and open mistakes,
 * damped when just practised — and the balance between new ground and known
 * weakness shifts with the phase of the year.
 */
class RecommendationServiceTest {

    private static final String HEAVY_UNTESTED = "cs408.os.process.sync";     // weight 3, 35-point module
    private static final String WEAK = "cs408.os.memory.replacement";         // weight 3, same module
    private static final String LIGHT = "cs408.os.overview.structure";         // weight 1

    private static Syllabus syllabus;
    private static RecommendationService service;

    @BeforeAll
    static void load() {
        syllabus = SyllabusLoader.load(JsonMapper.builder().build());
        service = new RecommendationService(null, null, syllabus);
    }

    @Test
    void onlyPractisablePointsAreRecommended() {
        MasterySnapshot snapshot = snapshot(Map.of(), Map.of(LIGHT, 2L), Map.of());

        List<RecommendationService.Focus> focus = service.rank(snapshot, ExamPhase.INTENSIVE, null, 10);

        assertThat(focus).extracting(RecommendationService.Focus::nodeCode).containsExactly(LIGHT);
        assertThat(focus.getFirst().reason()).isEqualTo(RecommendationService.Reason.UNTESTED);
    }

    @Test
    void earlyInTheYearNewGroundOutranksAKnownWeakness() {
        MasterySnapshot snapshot = weakVersusUntested(LocalDateTime.now().minusDays(5));
        List<RecommendationService.Focus> focus = service.rank(snapshot, ExamPhase.FOUNDATION, null, 2);
        assertThat(focus).extracting(RecommendationService.Focus::nodeCode).containsExactly(HEAVY_UNTESTED, WEAK);
    }

    @Test
    void inTheSprintAKnownWeaknessOutranksNewGround() {
        MasterySnapshot snapshot = weakVersusUntested(LocalDateTime.now().minusDays(5));
        List<RecommendationService.Focus> focus = service.rank(snapshot, ExamPhase.SPRINT, null, 2);
        assertThat(focus).extracting(RecommendationService.Focus::nodeCode).containsExactly(WEAK, HEAVY_UNTESTED);
        assertThat(focus.getFirst().reason()).isEqualTo(RecommendationService.Reason.WEAK);
    }

    @Test
    void whatWasJustPractisedStepsBack() {
        MasterySnapshot justNow = weakVersusUntested(LocalDateTime.now().minusHours(1));
        List<RecommendationService.Focus> focus = service.rank(justNow, ExamPhase.SPRINT, null, 2);
        assertThat(focus).extracting(RecommendationService.Focus::nodeCode).containsExactly(HEAVY_UNTESTED, WEAK);
    }

    @Test
    void openMistakesRaiseAPointAndSayWhy() {
        Map<String, MasteryModel.Evidence> evidence = new HashMap<>();
        evidence.put(LIGHT, evidenceAt(LocalDateTime.now().minusDays(3), AttemptResult.CORRECT, AttemptResult.WRONG));
        MasterySnapshot snapshot = snapshot(evidence, Map.of(LIGHT, 1L), Map.of(LIGHT, 2));

        RecommendationService.Focus focus = service.rank(snapshot, ExamPhase.INTENSIVE, null, 1).getFirst();
        assertThat(focus.reason()).isEqualTo(RecommendationService.Reason.MISTAKES);
        assertThat(focus.mistakes()).isEqualTo(2);
    }

    @Test
    void aScopeKeepsRecommendationsInsideIt() {
        MasterySnapshot snapshot = snapshot(Map.of(),
                Map.of(HEAVY_UNTESTED, 1L, "math1.linear.eigen.eigen", 1L), Map.of());
        assertThat(service.rank(snapshot, ExamPhase.INTENSIVE, "math1", 10))
                .extracting(RecommendationService.Focus::nodeCode).containsExactly("math1.linear.eigen.eigen");
    }

    /** The weak 考点 was practised at {@code lastPractised}; the heavy one never. */
    private static MasterySnapshot weakVersusUntested(LocalDateTime lastPractised) {
        Map<String, MasteryModel.Evidence> evidence = new HashMap<>();
        evidence.put(WEAK, evidenceAt(lastPractised, AttemptResult.WRONG, AttemptResult.WRONG, AttemptResult.CORRECT));
        return snapshot(evidence, Map.of(HEAVY_UNTESTED, 3L, WEAK, 3L), Map.of());
    }

    private static MasteryModel.Evidence evidenceAt(LocalDateTime at, AttemptResult... results) {
        MasteryModel.Evidence e = new MasteryModel.Evidence();
        for (AttemptResult result : results) {
            e.add(result, at, LocalDateTime.now());
        }
        return e;
    }

    private static MasterySnapshot snapshot(Map<String, MasteryModel.Evidence> evidence, Map<String, Long> available,
                                            Map<String, Integer> mistakes) {
        return MasterySnapshot.build(syllabus, evidence, available, mistakes);
    }
}
