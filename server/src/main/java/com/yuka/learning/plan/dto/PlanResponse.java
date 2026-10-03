package com.yuka.learning.plan.dto;

import com.yuka.learning.exam.ExamPhase;

import java.util.List;

/**
 * The plan for the caller's today and week.
 *
 * @param phases       the four phases with their dates (ISO; {@code start} null for the open-ended foundation)
 * @param examDays     the two exam days as a timetable, each paper with its date and times
 * @param dailyMinutes the candidate's daily study goal — the time the papers divide
 * @param weekStart    Monday of the caller's week (ISO)
 * @param weekEnd      Sunday of the caller's week (ISO)
 * @param papers       one per paper, in exam order
 * @param unclassified study time recorded without a paper — counted, never allocated
 */
public record PlanResponse(Exam exam, List<Phase> phases, List<ExamSlot> examDays, int dailyMinutes,
                           String today, String weekStart, String weekEnd, List<Paper> papers,
                           Unclassified unclassified) {

    public record Exam(int targetYear, String examDate, boolean estimated, long daysRemaining, ExamPhase phase) {
    }

    public record Phase(ExamPhase phase, String start, String end, boolean current) {
    }

    /** One paper's slot in the exam timetable. */
    public record ExamSlot(int day, String date, String subject, String startTime, String endTime) {
    }

    /**
     * @param estimate          whole-paper estimate from recent sittings, or null without evidence
     * @param baseShare         the phase's base share of the day
     * @param gap               (target − estimate) / full score, or null when either is missing
     * @param share             the share of the day after the gap tilt
     * @param dailyMinutes      planned minutes per day
     * @param todayMinutes      minutes studied today (sessions ended today)
     * @param weekPlannedMinutes planned minutes for the week (seven days)
     * @param weekMinutes       minutes studied this week so far
     * @param weeklySittings    whole papers a week the phase calls for
     * @param sittingsThisWeek  papers sat this week
     */
    public record Paper(String subject, double fullScore, Integer target, Estimate estimate, double baseShare,
                        Double gap, double share, int dailyMinutes, int todayMinutes, int weekPlannedMinutes,
                        int weekMinutes, int weeklySittings, int sittingsThisWeek) {
    }

    public record Estimate(double score, int sittings) {
    }

    public record Unclassified(int todayMinutes, int weekMinutes) {
    }
}
