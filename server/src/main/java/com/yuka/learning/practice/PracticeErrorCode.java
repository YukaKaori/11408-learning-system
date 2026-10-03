package com.yuka.learning.practice;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Practice error codes — reserved range 230000–239999. */
public enum PracticeErrorCode implements ErrorCode {

    SESSION_NOT_FOUND(230000, "Practice session not found", HttpStatus.NOT_FOUND),
    SESSION_ACCESS_DENIED(230001, "Practice session does not belong to the current user", HttpStatus.FORBIDDEN),
    SESSION_CLOSED(230002, "Practice session is already finished", HttpStatus.CONFLICT),
    QUESTION_NOT_IN_SESSION(230003, "Question is not part of this practice session", HttpStatus.BAD_REQUEST),
    ALREADY_ANSWERED(230004, "Question was already answered in this session", HttpStatus.CONFLICT),
    /** The scope has nothing to draw — e.g. no due mistakes, or a 考点 the bank does not cover yet. */
    NO_QUESTIONS_AVAILABLE(230005, "No questions available for this practice", HttpStatus.NOT_FOUND),
    MODE_INVALID(230006, "Practice mode is invalid", HttpStatus.BAD_REQUEST),
    /** {@code topic} practice needs a syllabus node to draw from. */
    SCOPE_REQUIRED(230007, "Practice scope is required for this mode", HttpStatus.BAD_REQUEST),
    SELF_GRADE_INVALID(230008, "Self grade must be correct, partial or wrong", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    PracticeErrorCode(int code, String message, HttpStatus httpStatus) {
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
