package com.yuka.learning.calendar;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Calendar module error codes — reserved range 160000–169999. */
public enum CalendarErrorCode implements ErrorCode {

    SESSION_NOT_FOUND(160000, "Study session not found", HttpStatus.NOT_FOUND),
    SESSION_ACCESS_DENIED(160001, "Study session does not belong to the current user", HttpStatus.FORBIDDEN),
    SESSION_TIME_INVALID(160002, "Study session must end after it starts", HttpStatus.BAD_REQUEST),
    SESSION_WINDOW_INVALID(160003, "Query window requires from < to", HttpStatus.BAD_REQUEST),
    /** Stop or discard with no timer running (already stopped elsewhere). */
    FOCUS_NOT_RUNNING(160004, "No focus timer is running", HttpStatus.CONFLICT),
    /** The stated end is not after the start, or lies in the future. */
    FOCUS_END_INVALID(160005, "Focus end must be after its start and not in the future", HttpStatus.BAD_REQUEST),
    /** A timer left running past the ceiling needs an explicit end — the client asks when it really ended. */
    FOCUS_TOO_LONG(160006, "Focus session exceeds 12 hours; state when it ended", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    CalendarErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
