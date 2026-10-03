package com.yuka.learning.sitting;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The paper estimate, by hand: only whole papers estimate a paper, recent ones
 * weigh more, old ones stop counting, and section drills still inform the
 * section profile.
 */
class ScoreEstimateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    @Test
    void noWholePaperMeansNoEstimate() {
        assertThat(ScoreEstimate.estimate(List.of(), 150, TODAY)).isNull();
        ScoreEstimate.Sat drill = new ScoreEstimate.Sat(TODAY, 62, 80, false,
                List.of(new ScoreEstimate.SectionScore("cs408.choice", 62, 80)));
        assertThat(ScoreEstimate.estimate(List.of(drill), 150, TODAY)).isNull();
    }

    @Test
    void sameDaySittingsAverageAndRangeIsReported() {
        ScoreEstimate.PaperEstimate estimate = ScoreEstimate.estimate(List.of(
                whole(TODAY, 100, 150), whole(TODAY, 120, 150)), 150, TODAY);

        assertThat(estimate.score()).isCloseTo(110, within(1e-9));
        assertThat(estimate.sittings()).isEqualTo(2);
        assertThat(estimate.low()).isCloseTo(100, within(1e-9));
        assertThat(estimate.high()).isCloseTo(120, within(1e-9));
    }

    @Test
    void aMonthOldPaperCountsHalfAsMuchAsTodaysOne() {
        // Weights 1 (today) and ½ (30 days ago): (1·120 + ½·90) / 1.5 = 110.
        ScoreEstimate.PaperEstimate estimate = ScoreEstimate.estimate(List.of(
                whole(TODAY, 120, 150), whole(TODAY.minusDays(30), 90, 150)), 150, TODAY);

        assertThat(estimate.score()).isCloseTo(110, within(1e-9));
    }

    @Test
    void sittingsOlderThanTheWindowStopCounting() {
        ScoreEstimate.PaperEstimate estimate = ScoreEstimate.estimate(List.of(
                whole(TODAY.minusDays(10), 100, 150),
                whole(TODAY.minusDays(ScoreEstimate.WINDOW_DAYS + 1), 40, 150)), 150, TODAY);

        assertThat(estimate.sittings()).isEqualTo(1);
        assertThat(estimate.score()).isCloseTo(100, within(1e-9));
        assertThat(ScoreEstimate.estimate(List.of(whole(TODAY.minusDays(200), 40, 150)), 150, TODAY)).isNull();
    }

    @Test
    void scoresAreComparedAsRatesOfWhatEachRecordWasOutOf() {
        // A record kept out of 100 still estimates a 150-point paper on its own scale.
        ScoreEstimate.PaperEstimate estimate = ScoreEstimate.estimate(List.of(whole(TODAY, 80, 100)), 150, TODAY);

        assertThat(estimate.score()).isCloseTo(120, within(1e-9));
    }

    @Test
    void sectionDrillsInformTheSectionProfileButNotTheEstimate() {
        ScoreEstimate.Sat drill = new ScoreEstimate.Sat(TODAY, 60, 80, false,
                List.of(new ScoreEstimate.SectionScore("cs408.choice", 60, 80)));
        ScoreEstimate.Sat paper = new ScoreEstimate.Sat(TODAY, 110, 150, true, List.of(
                new ScoreEstimate.SectionScore("cs408.choice", 70, 80),
                new ScoreEstimate.SectionScore("cs408.comprehensive", 40, 70)));

        List<ScoreEstimate.SectionProfile> profile = ScoreEstimate.sections(List.of(drill, paper),
                List.of("cs408.choice", "cs408.comprehensive"), TODAY);

        assertThat(profile.get(0).rate()).isCloseTo((60 / 80.0 + 70 / 80.0) / 2, within(1e-9));
        assertThat(profile.get(0).sittings()).isEqualTo(2);
        assertThat(profile.get(1).rate()).isCloseTo(40 / 70.0, within(1e-9));
        assertThat(ScoreEstimate.estimate(List.of(drill, paper), 150, TODAY).sittings()).isEqualTo(1);
    }

    @Test
    void anUntestedSectionHasNoRateRatherThanZero() {
        List<ScoreEstimate.SectionProfile> profile = ScoreEstimate.sections(List.of(whole(TODAY, 120, 150)),
                List.of("cs408.choice"), TODAY);

        assertThat(profile.getFirst().rate()).isNull();
        assertThat(profile.getFirst().sittings()).isZero();
    }

    private static ScoreEstimate.Sat whole(LocalDate satOn, double score, double full) {
        return new ScoreEstimate.Sat(satOn, score, full, true, List.of());
    }
}
