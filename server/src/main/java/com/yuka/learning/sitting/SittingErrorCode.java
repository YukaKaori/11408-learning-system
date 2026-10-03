package com.yuka.learning.sitting;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Sitting error codes — reserved range 250000–259999. */
public enum SittingErrorCode implements ErrorCode {

    SITTING_NOT_FOUND(250000, "Sitting not found", HttpStatus.NOT_FOUND),
    SITTING_ACCESS_DENIED(250001, "Sitting does not belong to the current user", HttpStatus.FORBIDDEN),
    SITTING_KIND_INVALID(250002, "Sitting kind must be past_paper or mock", HttpStatus.BAD_REQUEST),
    /** A section that is not part of this paper, or listed twice. */
    SITTING_SECTION_INVALID(250003, "Section does not belong to this paper", HttpStatus.BAD_REQUEST),
    /** A score below zero or above what the section (or the paper) is worth, or no score at all. */
    SITTING_SCORE_INVALID(250004, "Score is outside what the paper or section is worth", HttpStatus.BAD_REQUEST),
    /** A 真题 needs a year that exists for this paper; a mock paper needs a name. */
    SITTING_PAPER_INVALID(250005, "A past paper needs a valid year; a mock paper needs a name", HttpStatus.BAD_REQUEST),
    SITTING_DATE_INVALID(250006, "A sitting cannot be dated in the future", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    SittingErrorCode(int code, String message, HttpStatus httpStatus) {
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
