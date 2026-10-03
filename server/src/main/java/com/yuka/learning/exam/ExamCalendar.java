package com.yuka.learning.exam;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Pure date arithmetic for the national exam. No clock of its own — every
 * function takes "today" — so it is trivially testable across year boundaries.
 *
 * <p><strong>The estimate.</strong> The first round of the national
 * postgraduate entrance exam is held over a weekend in the December before the
 * admission year. From 2015 to 2025 day one fell, every year, on the Saturday
 * between December 20 and 26 (2025-12-20, 2024-12-21, 2023-12-23, 2022-12-24,
 * 2021-12-25, 2020-12-26 …). The official date is announced each autumn; until
 * a candidate confirms it, the system uses this rule and says that it is an
 * estimate rather than presenting a guess as a fact.
 */
public final class ExamCalendar {

    private ExamCalendar() {
    }

    /**
     * Day one of the exam for a 考研年份.
     *
     * @param targetYear the admission year, e.g. 2027 for the exam sat in December 2026
     */
    public static LocalDate estimatedExamDate(int targetYear) {
        return LocalDate.of(targetYear - 1, 12, 20).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }

    /**
     * The 考研年份 of the next exam that has not finished yet. Day two is still
     * part of this year's exam, so the target only rolls over the day after it.
     */
    public static int nextTargetYear(LocalDate today) {
        int candidate = today.getYear() + 1;
        LocalDate dayTwo = estimatedExamDate(candidate).plusDays(1);
        return today.isAfter(dayTwo) ? candidate + 1 : candidate;
    }

    /** Whole days from {@code today} to {@code examDate}; negative once it has passed. */
    public static long daysUntil(LocalDate today, LocalDate examDate) {
        return ChronoUnit.DAYS.between(today, examDate);
    }

    /** Whether a confirmed exam date is plausible for a 考研年份 (autumn before, through January). */
    public static boolean plausibleExamDate(int targetYear, LocalDate examDate) {
        LocalDate earliest = LocalDate.of(targetYear - 1, 9, 1);
        LocalDate latest = LocalDate.of(targetYear, 1, 31);
        return !examDate.isBefore(earliest) && !examDate.isAfter(latest);
    }
}
