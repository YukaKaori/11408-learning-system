package com.yuka.learning.calendar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.calendar.dto.FocusResponse;
import com.yuka.learning.calendar.dto.FocusResultResponse;
import com.yuka.learning.calendar.dto.StartFocusRequest;
import com.yuka.learning.calendar.dto.StopFocusRequest;
import com.yuka.learning.calendar.dto.StudySessionResponse;
import com.yuka.learning.calendar.entity.FocusTimer;
import com.yuka.learning.calendar.entity.StudySession;
import com.yuka.learning.calendar.mapper.FocusTimerMapper;
import com.yuka.learning.calendar.mapper.StudySessionMapper;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.syllabus.Syllabus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * The study timer — how a candidate's hours become measured instead of
 * remembered. A 考研 year is spent mostly away from the screen (books, paper,
 * lectures), so the system cannot observe study; it can only make recording
 * it one tap. The timer is that tap.
 *
 * <p>One timer per candidate, kept server-side so it survives a closed tab and
 * follows the candidate across devices. Starting while one runs is a
 * <em>switch</em> — the running one is saved first — because moving from 数学
 * to 408 is the most common thing a candidate does with it. Stopping writes an
 * ordinary {@link StudySession}: the timer adds a way to record time, never a
 * second kind of time.
 *
 * <p>Two honesty rules: under a minute is a mis-tap and is dropped, not
 * written; past {@link #CEILING} the timer was almost certainly forgotten, so
 * stopping it requires the candidate to say when they really stopped rather
 * than recording a night's sleep as study.
 */
@Service
public class FocusService {

    /** A session longer than this cannot be recorded without an explicit end. */
    static final Duration CEILING = Duration.ofHours(12);

    /** Shorter than this is a mis-tap, not study. */
    static final Duration MINIMUM = Duration.ofMinutes(1);

    /** Client clocks run a little ahead; an end this close to "now" is clamped, not rejected. */
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(2);

    private final FocusTimerMapper timerMapper;
    private final StudySessionMapper sessionMapper;
    private final Syllabus syllabus;

    public FocusService(FocusTimerMapper timerMapper, StudySessionMapper sessionMapper, Syllabus syllabus) {
        this.timerMapper = timerMapper;
        this.sessionMapper = sessionMapper;
        this.syllabus = syllabus;
    }

    /** The running timer, or null. */
    public FocusResponse current(Long userId) {
        FocusTimer timer = find(userId);
        return timer == null ? null : FocusResponse.from(timer, now());
    }

    /**
     * Starts a timer, saving the running one first (a switch).
     *
     * @throws BusinessException {@code FOCUS_TOO_LONG} when the running timer is
     *                           past the ceiling — it must be stopped with an
     *                           explicit end before anything new starts
     */
    @Transactional
    public FocusResultResponse start(Long userId, StartFocusRequest request) {
        String nodeCode = syllabus.resolve(request.nodeCode());
        LocalDateTime now = now();
        StudySessionResponse saved = null;
        FocusTimer running = find(userId);
        if (running != null) {
            saved = close(running, now);
        }

        FocusTimer timer = new FocusTimer();
        timer.setUserId(userId);
        timer.setNodeCode(nodeCode);
        timer.setTitle(request.title() == null || request.title().isBlank() ? null : request.title().trim());
        timer.setStartedAt(now);
        try {
            timerMapper.insert(timer);
        } catch (DuplicateKeyException e) {
            // A concurrent start won (a double tap): its timer is the running one.
            return new FocusResultResponse(current(userId), saved);
        }
        return new FocusResultResponse(FocusResponse.from(timer, now), saved);
    }

    /** Stops the timer and records what it measured. */
    @Transactional
    public FocusResultResponse stop(Long userId, StopFocusRequest request) {
        FocusTimer running = find(userId);
        if (running == null) {
            throw new BusinessException(CalendarErrorCode.FOCUS_NOT_RUNNING);
        }
        LocalDateTime now = now();
        LocalDateTime endsAt = now;
        if (request != null && request.endsAt() != null) {
            // A stated end must lie inside the timed span; "now" always does.
            endsAt = toLocal(request.endsAt());
            if (!endsAt.isAfter(running.getStartedAt()) || endsAt.isAfter(now.plus(CLOCK_TOLERANCE))) {
                throw new BusinessException(CalendarErrorCode.FOCUS_END_INVALID);
            }
            if (endsAt.isAfter(now)) {
                endsAt = now;
            }
        }
        return new FocusResultResponse(null, close(running, endsAt));
    }

    /** Throws the running timer away without recording anything. */
    @Transactional
    public void discard(Long userId) {
        if (timerMapper.deleteByUserId(userId) == 0) {
            throw new BusinessException(CalendarErrorCode.FOCUS_NOT_RUNNING);
        }
    }

    /**
     * Ends a timer at {@code endsAt}: removes it and writes the session it
     * measured, or nothing for a mis-tap (a stop in the same second as the
     * start measures zero, which is a mis-tap too).
     */
    private StudySessionResponse close(FocusTimer timer, LocalDateTime endsAt) {
        Duration measured = Duration.between(timer.getStartedAt(), endsAt);
        if (measured.compareTo(CEILING) > 0) {
            throw new BusinessException(CalendarErrorCode.FOCUS_TOO_LONG);
        }
        timerMapper.deleteByUserId(timer.getUserId());
        if (measured.compareTo(MINIMUM) < 0) {
            return null;
        }
        StudySession session = new StudySession();
        session.setUserId(timer.getUserId());
        session.setNodeCode(timer.getNodeCode());
        session.setTitle(timer.getTitle());
        session.setStartsAt(timer.getStartedAt());
        session.setEndsAt(endsAt);
        sessionMapper.insert(session);
        return StudySessionResponse.from(session);
    }

    private FocusTimer find(Long userId) {
        return timerMapper.selectOne(new LambdaQueryWrapper<FocusTimer>().eq(FocusTimer::getUserId, userId));
    }

    /**
     * Whole seconds: DATETIME columns round fractions on persist, and a start
     * stored a fraction later than it was taken would make the first elapsed
     * figure negative.
     */
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private static LocalDateTime toLocal(long epochMilli) {
        return Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault()).toLocalDateTime()
                .truncatedTo(ChronoUnit.SECONDS);
    }
}
