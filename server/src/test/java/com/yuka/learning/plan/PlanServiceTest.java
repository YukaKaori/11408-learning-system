package com.yuka.learning.plan;

import com.yuka.learning.calendar.StudySessionService;
import com.yuka.learning.calendar.dto.CreateStudySessionRequest;
import com.yuka.learning.exam.ExamCalendar;
import com.yuka.learning.exam.ExamPhase;
import com.yuka.learning.exam.ExamProfileService;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.dto.UpdateExamProfileRequest;
import com.yuka.learning.plan.dto.PlanResponse;
import com.yuka.learning.preference.PreferenceService;
import com.yuka.learning.preference.dto.UpdatePreferencesRequest;
import com.yuka.learning.sitting.SittingService;
import com.yuka.learning.sitting.dto.SaveSittingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plan read model over real rows: the day divided per the phase, tilted by
 * sittings against targets, time spent bucketed per paper by the caller's day
 * and week, the weekly cadence, and the exam timetable.
 */
@SpringBootTest
@ActiveProfiles("test")
class PlanServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final long MINUTE = 60_000L;

    @Autowired
    private PlanService planService;
    @Autowired
    private StudySessionService sessionService;
    @Autowired
    private SittingService sittingService;
    @Autowired
    private ExamProfileService profileService;
    @Autowired
    private PreferenceService preferenceService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate today;
    private int latestYear;
    private long now;

    @BeforeEach
    void cleanTables() {
        for (String table : List.of("study_sessions", "paper_sittings", "exam_profiles", "user_preferences")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        today = LocalDate.now(ZONE);
        latestYear = ExamCalendar.nextTargetYear(today) - 1;
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
    }

    @Test
    void aNewCandidateGetsAFullStudyDayDividedByThePhase() {
        PlanResponse plan = planService.plan(USER, ZONE);
        ExamPhase phase = plan.exam().phase();

        assertThat(plan.dailyMinutes()).isEqualTo(480);
        assertThat(plan.papers()).extracting(PlanResponse.Paper::subject)
                .containsExactly("politics", "english1", "math1", "cs408");
        assertThat(plan.papers().stream().mapToInt(PlanResponse.Paper::dailyMinutes).sum()).isEqualTo(480);
        for (PlanResponse.Paper paper : plan.papers()) {
            ExamSubject subject = ExamSubject.fromCode(paper.subject());
            assertThat(paper.baseShare()).isEqualTo(PlanModel.base(phase, subject));
            assertThat(paper.share()).isEqualTo(paper.baseShare()); // no evidence, no tilt
            assertThat(paper.gap()).isNull();
            assertThat(paper.estimate()).isNull();
            assertThat(paper.weekPlannedMinutes()).isEqualTo(paper.dailyMinutes() * 7);
            assertThat(paper.weeklySittings()).isEqualTo(PlanModel.weeklySittings(phase, subject));
        }
        assertThat(plan.phases()).hasSize(4);
        assertThat(plan.phases().stream().filter(PlanResponse.Phase::current).count()).isEqualTo(1);
    }

    @Test
    void theStudyDayIsTheCandidatesOwn() {
        preferenceService.update(USER, new UpdatePreferencesRequest(null, null, 600));

        PlanResponse plan = planService.plan(USER, ZONE);

        assertThat(plan.dailyMinutes()).isEqualTo(600);
        assertThat(plan.papers().stream().mapToInt(PlanResponse.Paper::dailyMinutes).sum()).isEqualTo(600);
        assertThat(planService.plan(OTHER_USER, ZONE).dailyMinutes()).isEqualTo(480);
    }

    @Test
    void sittingsAgainstTargetsTiltTheDayTowardTheWiderGap() {
        profileService.update(USER, new UpdateExamProfileRequest(latestYear + 1, null,
                new UpdateExamProfileRequest.Targets(null, null, 120, 120)), ZONE);
        sittingService.create(USER, new SaveSittingRequest("math1", "past_paper", null, latestYear, today, 180,
                null, 90.0, null), ZONE);  // 30 short
        sittingService.create(USER, new SaveSittingRequest("cs408", "past_paper", null, latestYear, today, 180,
                null, 125.0, null), ZONE); // above target

        PlanResponse plan = planService.plan(USER, ZONE);
        PlanResponse.Paper math = plan.papers().get(2);
        PlanResponse.Paper cs = plan.papers().get(3);

        assertThat(math.estimate().score()).isEqualTo(90);
        assertThat(math.gap()).isEqualTo(0.2);
        assertThat(cs.gap()).isZero();
        assertThat(math.share()).isGreaterThan(math.baseShare());
        assertThat(cs.share()).isLessThan(cs.baseShare());
        assertThat(math.sittingsThisWeek()).isEqualTo(1);
        assertThat(plan.papers().get(0).sittingsThisWeek()).isZero();
    }

    @Test
    void timeSpentIsBucketedPerPaperOnTheDayASessionEnds() {
        session(USER, "math1.linear", now - MINUTE, 60);
        session(USER, "cs408", now - 2 * MINUTE, 30);
        session(USER, null, now - 3 * MINUTE, 15);
        session(USER, "math1", now - 9 * 24 * 60 * MINUTE, 120);   // last week: neither today nor this week
        session(OTHER_USER, "math1", now - MINUTE, 45);
        sessionService.create(USER, new CreateStudySessionRequest("planned", "politics",
                now + 60 * MINUTE, now + 120 * MINUTE));             // not studied yet

        PlanResponse plan = planService.plan(USER, ZONE);

        assertThat(plan.papers()).extracting(PlanResponse.Paper::todayMinutes).containsExactly(0, 0, 60, 30);
        assertThat(plan.papers()).extracting(PlanResponse.Paper::weekMinutes).containsExactly(0, 0, 60, 30);
        assertThat(plan.unclassified().todayMinutes()).isEqualTo(15);
        assertThat(plan.today()).isEqualTo(today.toString());
        assertThat(LocalDate.parse(plan.weekStart()).getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(LocalDate.parse(plan.weekEnd())).isEqualTo(LocalDate.parse(plan.weekStart()).plusDays(6));
    }

    @Test
    void theExamTimetableIsTheTwoDaysAsPrinted() {
        PlanResponse plan = planService.plan(USER, ZONE);
        LocalDate dayOne = LocalDate.parse(plan.exam().examDate());

        assertThat(plan.examDays()).extracting(PlanResponse.ExamSlot::subject)
                .containsExactly("politics", "english1", "math1", "cs408");
        assertThat(plan.examDays()).extracting(PlanResponse.ExamSlot::day).containsExactly(1, 1, 2, 2);
        assertThat(plan.examDays()).extracting(PlanResponse.ExamSlot::date).containsExactly(
                dayOne.toString(), dayOne.toString(), dayOne.plusDays(1).toString(), dayOne.plusDays(1).toString());
        assertThat(plan.examDays()).extracting(PlanResponse.ExamSlot::startTime)
                .containsExactly("08:30", "14:00", "08:30", "14:00");
        assertThat(plan.examDays()).extracting(PlanResponse.ExamSlot::endTime)
                .containsExactly("11:30", "17:00", "11:30", "17:00");
    }

    private void session(Long userId, String nodeCode, long endsAt, int minutes) {
        sessionService.create(userId, new CreateStudySessionRequest(null, nodeCode, endsAt - minutes * MINUTE, endsAt));
    }
}
