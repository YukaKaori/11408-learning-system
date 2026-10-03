package com.yuka.learning.sitting;

import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamCalendar;
import com.yuka.learning.exam.ExamProfileService;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.dto.UpdateExamProfileRequest;
import com.yuka.learning.sitting.dto.SaveSittingRequest;
import com.yuka.learning.sitting.dto.SittingOverviewResponse;
import com.yuka.learning.sitting.dto.SittingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sittings: validated against the paper as the syllabus describes it, totals
 * computed server-side, owned per candidate, and summarized into estimate,
 * section profile, trajectory and the 真题 shelf.
 */
@SpringBootTest
@ActiveProfiles("test")
class SittingServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Autowired
    private SittingService sittingService;
    @Autowired
    private ExamProfileService profileService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate today;
    private int latestYear;

    @BeforeEach
    void cleanTables() {
        for (String table : List.of("paper_sittings", "exam_profiles")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        today = LocalDate.now(ZONE);
        latestYear = ExamCalendar.nextTargetYear(today) - 1;
    }

    @Test
    void aPastPaperWithSectionsIsTotalledServerSideInPrintedOrder() {
        SittingResponse sitting = sittingService.create(USER, pastPaper("cs408", latestYear, today,
                List.of(section("cs408.comprehensive", 48.5), section("cs408.choice", 64))), ZONE);

        assertThat(sitting.kind()).isEqualTo("past_paper");
        assertThat(sitting.title()).isNull();
        assertThat(sitting.paperYear()).isEqualTo(latestYear);
        assertThat(sitting.score()).isEqualTo(112.5);
        assertThat(sitting.fullScore()).isEqualTo(150);
        assertThat(sitting.complete()).isTrue();
        assertThat(sitting.sections()).extracting(SittingResponse.Section::code)
                .containsExactly("cs408.choice", "cs408.comprehensive");
        assertThat(sitting.sections()).extracting(SittingResponse.Section::full).containsExactly(80.0, 70.0);
        assertThat(sittingService.list(USER, null, 10)).extracting(SittingResponse::id).containsExactly(sitting.id());
        assertThat(sittingService.list(OTHER_USER, null, 10)).isEmpty();
    }

    @Test
    void aSectionDrillIsOutOfWhatItCoveredAndIsNotAWholePaper() {
        SittingResponse drill = sittingService.create(USER, pastPaper("cs408", latestYear, today,
                List.of(section("cs408.choice", 66))), ZONE);

        assertThat(drill.score()).isEqualTo(66);
        assertThat(drill.fullScore()).isEqualTo(80);
        assertThat(drill.complete()).isFalse();
    }

    @Test
    void aTotalAloneIsAWholePaper() {
        SittingResponse mock = sittingService.create(USER, new SaveSittingRequest("math1", "mock", "  模拟卷 · 第 3 套 ",
                null, today, 175, null, 118.0, "  计算失误两处  "), ZONE);

        assertThat(mock.title()).isEqualTo("模拟卷 · 第 3 套");
        assertThat(mock.score()).isEqualTo(118);
        assertThat(mock.fullScore()).isEqualTo(150);
        assertThat(mock.complete()).isTrue();
        assertThat(mock.sections()).isEmpty();
        assertThat(mock.note()).isEqualTo("计算失误两处");
    }

    @Test
    void thePaperIdentityIsValidated() {
        // A mock paper needs a name.
        assertRejected(new SaveSittingRequest("math1", "mock", " ", null, today, null, null, 100.0, null),
                SittingErrorCode.SITTING_PAPER_INVALID);
        // 408 did not exist before 2009, and next year's paper has not been set.
        assertRejected(pastPaper("cs408", 2008, today, List.of(section("cs408.choice", 60))),
                SittingErrorCode.SITTING_PAPER_INVALID);
        assertRejected(pastPaper("cs408", latestYear + 1, today, List.of(section("cs408.choice", 60))),
                SittingErrorCode.SITTING_PAPER_INVALID);
        assertRejected(new SaveSittingRequest("math1", "exam", "x", null, today, null, null, 100.0, null),
                SittingErrorCode.SITTING_KIND_INVALID);
        assertRejected(pastPaper("cs408", latestYear, today.plusDays(1), List.of(section("cs408.choice", 60))),
                SittingErrorCode.SITTING_DATE_INVALID);
    }

    @Test
    void scoresCannotExceedWhatTheSectionOrPaperIsWorth() {
        assertRejected(pastPaper("cs408", latestYear, today, List.of(section("cs408.choice", 81))),
                SittingErrorCode.SITTING_SCORE_INVALID);
        assertRejected(pastPaper("cs408", latestYear, today, List.of(section("cs408.choice", -1))),
                SittingErrorCode.SITTING_SCORE_INVALID);
        assertRejected(new SaveSittingRequest("english1", "past_paper", null, latestYear, today, null, null,
                101.0, null), SittingErrorCode.SITTING_SCORE_INVALID);
        assertRejected(new SaveSittingRequest("english1", "past_paper", null, latestYear, today, null, null,
                null, null), SittingErrorCode.SITTING_SCORE_INVALID);
        // A section of another paper, or the same section twice.
        assertRejected(pastPaper("cs408", latestYear, today, List.of(section("math1.choice", 40))),
                SittingErrorCode.SITTING_SECTION_INVALID);
        assertRejected(pastPaper("cs408", latestYear, today,
                        List.of(section("cs408.choice", 40), section("cs408.choice", 40))),
                SittingErrorCode.SITTING_SECTION_INVALID);
    }

    @Test
    void sittingsAreTheirOwnersAndAnUpdateReplacesTheRecord() {
        SittingResponse sitting = sittingService.create(USER, pastPaper("cs408", latestYear, today,
                List.of(section("cs408.choice", 60))), ZONE);
        Long id = Long.valueOf(sitting.id());

        assertThatThrownBy(() -> sittingService.delete(OTHER_USER, id))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SittingErrorCode.SITTING_ACCESS_DENIED));

        SittingResponse updated = sittingService.update(USER, id, pastPaper("cs408", latestYear - 1, today,
                List.of(section("cs408.choice", 70), section("cs408.comprehensive", 50))), ZONE);
        assertThat(updated.paperYear()).isEqualTo(latestYear - 1);
        assertThat(updated.score()).isEqualTo(120);
        assertThat(updated.complete()).isTrue();

        sittingService.delete(USER, id);
        assertThat(sittingService.list(USER, null, 10)).isEmpty();
        assertThatThrownBy(() -> sittingService.delete(USER, id))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SittingErrorCode.SITTING_NOT_FOUND));
    }

    @Test
    void theOverviewEstimatesFromWholePapersAndProfilesEverySection() {
        profileService.update(USER, new UpdateExamProfileRequest(latestYear + 1, null,
                new UpdateExamProfileRequest.Targets(null, null, null, 120)), ZONE);
        sittingService.create(USER, pastPaper("cs408", latestYear - 1, today.minusDays(1),
                List.of(section("cs408.choice", 60), section("cs408.comprehensive", 40))), ZONE);
        sittingService.create(USER, pastPaper("cs408", latestYear, today,
                List.of(section("cs408.choice", 70), section("cs408.comprehensive", 50))), ZONE);
        sittingService.create(USER, pastPaper("cs408", latestYear, today,
                List.of(section("cs408.choice", 74))), ZONE); // a drill: profile only

        SittingOverviewResponse overview = sittingService.overview(USER, ZONE);
        SittingOverviewResponse.Paper cs = overview.papers().get(3);

        assertThat(overview.latestPaperYear()).isEqualTo(latestYear);
        assertThat(overview.papers()).extracting(SittingOverviewResponse.Paper::subject)
                .containsExactly("politics", "english1", "math1", "cs408");
        assertThat(cs.target()).isEqualTo(120);
        assertThat(cs.total()).isEqualTo(3);
        assertThat(cs.estimate().sittings()).isEqualTo(2);
        assertThat(cs.estimate().score()).isBetween(100.0, 120.0);
        assertThat(cs.estimate().low()).isEqualTo(100);
        assertThat(cs.estimate().high()).isEqualTo(120);
        assertThat(cs.estimate().latest().score()).isEqualTo(120);
        // Trajectory: whole papers only, oldest first.
        assertThat(cs.trend()).extracting(SittingOverviewResponse.Point::score).containsExactly(100.0, 120.0);
        // Both sections profiled; the drill counts towards choice.
        assertThat(cs.sections()).extracting(SittingOverviewResponse.Section::code)
                .containsExactly("cs408.choice", "cs408.comprehensive");
        assertThat(cs.sections().get(0).sittings()).isEqualTo(3);
        assertThat(cs.sections().get(1).sittings()).isEqualTo(2);
        // An unsat paper has no estimate and untested sections, not zeros.
        SittingOverviewResponse.Paper math = overview.papers().get(2);
        assertThat(math.estimate()).isNull();
        assertThat(math.sections()).allSatisfy(section -> assertThat(section.rate()).isNull());
    }

    @Test
    void theShelfListsRecentYearsAndKeepsOlderOnesThatWereSat() {
        sittingService.create(USER, pastPaper("cs408", 2009, today,
                List.of(section("cs408.choice", 60), section("cs408.comprehensive", 45))), ZONE);
        sittingService.create(USER, pastPaper("cs408", latestYear, today.minusDays(3),
                List.of(section("cs408.choice", 58), section("cs408.comprehensive", 40))), ZONE);
        sittingService.create(USER, pastPaper("cs408", latestYear, today,
                List.of(section("cs408.choice", 70))), ZONE); // 二刷, choice only

        SittingOverviewResponse.PastPapers shelf = sittingService.overview(USER, ZONE).papers().get(3).pastPapers();

        assertThat(shelf.firstYear()).isEqualTo(2009);
        assertThat(shelf.lastYear()).isEqualTo(latestYear);
        assertThat(shelf.years().getFirst().year()).isEqualTo(latestYear);
        assertThat(shelf.years()).extracting(SittingOverviewResponse.Year::year).contains(2009);
        SittingOverviewResponse.Year newest = shelf.years().getFirst();
        assertThat(newest.times()).isEqualTo(2);
        assertThat(newest.best()).isEqualTo(98);        // whole papers only
        assertThat(newest.latest()).isEqualTo(70);      // the latest attempt, as recorded
        assertThat(newest.latestFull()).isEqualTo(80);
        SittingOverviewResponse.Year unsat = shelf.years().get(1);
        assertThat(unsat.times()).isZero();
        assertThat(unsat.best()).isNull();
    }

    @Test
    void countByPaperCountsSittingsInTheWindow() {
        sittingService.create(USER, pastPaper("cs408", latestYear, today, List.of(section("cs408.choice", 60))), ZONE);
        sittingService.create(USER, pastPaper("cs408", latestYear - 1, today.minusDays(10),
                List.of(section("cs408.choice", 60))), ZONE);
        sittingService.create(USER, new SaveSittingRequest("math1", "mock", "卷一", null, today, null, null,
                100.0, null), ZONE);

        Map<ExamSubject, Integer> counts = sittingService.countByPaper(USER, today.minusDays(6), today);

        assertThat(counts).containsEntry(ExamSubject.CS_408, 1).containsEntry(ExamSubject.MATH_1, 1)
                .doesNotContainKey(ExamSubject.POLITICS);
    }

    private void assertRejected(SaveSittingRequest request, SittingErrorCode code) {
        assertThatThrownBy(() -> sittingService.create(USER, request, ZONE))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static SaveSittingRequest pastPaper(String subject, int year, LocalDate satOn,
                                                List<SaveSittingRequest.SectionScore> sections) {
        return new SaveSittingRequest(subject, "past_paper", null, year, satOn, 170, sections, null, null);
    }

    private static SaveSittingRequest.SectionScore section(String code, double score) {
        return new SaveSittingRequest.SectionScore(code, score);
    }
}
