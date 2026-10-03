package com.yuka.learning.exam;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The exam calendar: the estimate rule against history, the target-year rollover, the phases. */
class ExamCalendarTest {

    @Test
    void theEstimateReproducesEveryFirstRoundDateFrom2015To2025() {
        // Day one of the national exam, 2016考研 … 2026考研 (sat in December of the prior year).
        assertThat(ExamCalendar.estimatedExamDate(2016)).isEqualTo(LocalDate.of(2015, 12, 26));
        assertThat(ExamCalendar.estimatedExamDate(2017)).isEqualTo(LocalDate.of(2016, 12, 24));
        assertThat(ExamCalendar.estimatedExamDate(2018)).isEqualTo(LocalDate.of(2017, 12, 23));
        assertThat(ExamCalendar.estimatedExamDate(2019)).isEqualTo(LocalDate.of(2018, 12, 22));
        assertThat(ExamCalendar.estimatedExamDate(2020)).isEqualTo(LocalDate.of(2019, 12, 21));
        assertThat(ExamCalendar.estimatedExamDate(2021)).isEqualTo(LocalDate.of(2020, 12, 26));
        assertThat(ExamCalendar.estimatedExamDate(2022)).isEqualTo(LocalDate.of(2021, 12, 25));
        assertThat(ExamCalendar.estimatedExamDate(2023)).isEqualTo(LocalDate.of(2022, 12, 24));
        assertThat(ExamCalendar.estimatedExamDate(2024)).isEqualTo(LocalDate.of(2023, 12, 23));
        assertThat(ExamCalendar.estimatedExamDate(2025)).isEqualTo(LocalDate.of(2024, 12, 21));
        assertThat(ExamCalendar.estimatedExamDate(2026)).isEqualTo(LocalDate.of(2025, 12, 20));
    }

    @Test
    void theTargetYearRollsOverTheDayAfterDayTwo() {
        LocalDate dayOne = ExamCalendar.estimatedExamDate(2027); // 2026-12-26
        assertThat(ExamCalendar.nextTargetYear(LocalDate.of(2026, 9, 29))).isEqualTo(2027);
        assertThat(ExamCalendar.nextTargetYear(dayOne)).isEqualTo(2027);
        assertThat(ExamCalendar.nextTargetYear(dayOne.plusDays(1))).isEqualTo(2027); // day two
        assertThat(ExamCalendar.nextTargetYear(dayOne.plusDays(2))).isEqualTo(2028);
        assertThat(ExamCalendar.nextTargetYear(LocalDate.of(2027, 1, 5))).isEqualTo(2028);
    }

    @Test
    void phasesFollowTheDaysLeft() {
        assertThat(ExamPhase.of(250)).isEqualTo(ExamPhase.FOUNDATION);
        assertThat(ExamPhase.of(181)).isEqualTo(ExamPhase.FOUNDATION);
        assertThat(ExamPhase.of(180)).isEqualTo(ExamPhase.INTENSIVE);
        assertThat(ExamPhase.of(91)).isEqualTo(ExamPhase.INTENSIVE);
        assertThat(ExamPhase.of(90)).isEqualTo(ExamPhase.PAST_PAPERS);
        assertThat(ExamPhase.of(31)).isEqualTo(ExamPhase.PAST_PAPERS);
        assertThat(ExamPhase.of(30)).isEqualTo(ExamPhase.SPRINT);
        assertThat(ExamPhase.of(0)).isEqualTo(ExamPhase.SPRINT);
        assertThat(ExamPhase.of(-1)).isEqualTo(ExamPhase.SPRINT); // day two is still the exam
        assertThat(ExamPhase.of(-2)).isEqualTo(ExamPhase.FINISHED);
        assertThat(ExamPhase.PAST_PAPERS.wire()).isEqualTo("past-papers");
    }

    @Test
    void aConfirmedDateMustBelongToItsTargetYear() {
        assertThat(ExamCalendar.plausibleExamDate(2027, LocalDate.of(2026, 12, 19))).isTrue();
        assertThat(ExamCalendar.plausibleExamDate(2027, LocalDate.of(2027, 1, 8))).isTrue();
        assertThat(ExamCalendar.plausibleExamDate(2027, LocalDate.of(2027, 12, 18))).isFalse();
        assertThat(ExamCalendar.plausibleExamDate(2027, LocalDate.of(2026, 6, 1))).isFalse();
    }
}
