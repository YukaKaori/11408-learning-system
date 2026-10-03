package com.yuka.learning.common;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Resolves the caller's timezone for "which day is it?" computations.
 *
 * <p>Instants are stored and compared absolutely, so a card is due the moment
 * {@code due_at <= now} regardless of zone. Timezone only matters for
 * <em>day-bucketing</em> — the daily new-card cap, "reviewed today", and
 * retention-by-day. Clients send their IANA zone (e.g. {@code Asia/Shanghai})
 * in the {@link #HEADER} header; a missing or unparseable value falls back to
 * the server default so the request never fails on a bad header.
 */
public final class ClientZone {

    /** Request header carrying the caller's IANA timezone id. */
    public static final String HEADER = "X-Client-Timezone";

    private ClientZone() {
    }

    public static ZoneId resolve(String header) {
        if (header != null && !header.isBlank()) {
            try {
                return ZoneId.of(header.trim());
            } catch (DateTimeException ignored) {
                // fall through to the server default
            }
        }
        return ZoneId.systemDefault();
    }

    /**
     * The zone of the HTTP request being served on this thread, or the server
     * default outside a request. For deep callers — the AI context pipeline
     * composes an exam countdown several layers below the controller — where
     * threading a {@code ZoneId} through every signature would add noise
     * without adding safety. Controllers that own a day-bucketed contract keep
     * reading the header explicitly.
     */
    public static ZoneId current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
            return resolve(servlet.getRequest().getHeader(HEADER));
        }
        return ZoneId.systemDefault();
    }

    /**
     * The caller's "today" as a half-open instant range, expressed in the
     * system-zone {@link LocalDateTime} space every datetime column stores.
     *
     * <p>This is the single definition of a day boundary. Review day-bucketing
     * (the new-card cap, "reviewed today") and the workspace's today-sessions
     * window both read it, so the same request can never disagree with itself
     * about which day it is — the defect Phase 17 fixes in
     * {@code WorkspaceService}, which previously bucketed on the server's
     * {@code LocalDate.now()} while due counts bucketed on the client's.
     *
     * @param date  the caller's local date — carried so a response can report
     *              which day it bucketed by without recomputing it (and
     *              possibly landing on the other side of midnight)
     * @param start start of the caller's today, inclusive
     * @param end   start of the caller's tomorrow, exclusive
     */
    public record DayRange(LocalDate date, LocalDateTime start, LocalDateTime end) {
    }

    /** {@code [startOfToday, startOfTomorrow)} in {@code zone}. */
    public static DayRange today(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        return new DayRange(
                today,
                toSystemLocal(today.atStartOfDay(zone).toInstant()),
                toSystemLocal(today.plusDays(1).atStartOfDay(zone).toInstant()));
    }

    private static LocalDateTime toSystemLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
