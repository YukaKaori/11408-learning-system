package com.yuka.learning.mistake;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mistake book error codes — reserved range 240000–249999. */
public enum MistakeErrorCode implements ErrorCode {

    MISTAKE_NOT_FOUND(240000, "Mistake not found", HttpStatus.NOT_FOUND),
    MISTAKE_ACCESS_DENIED(240001, "Mistake does not belong to the current user", HttpStatus.FORBIDDEN),
    MISTAKE_CAUSE_INVALID(240002, "Mistake cause is invalid", HttpStatus.BAD_REQUEST),
    /** Capturing a mistake means recording a wrong or partial answer; "correct" is not a mistake. */
    MISTAKE_RESULT_INVALID(240003, "A captured mistake must be wrong or partial", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    MistakeErrorCode(int code, String message, HttpStatus httpStatus) {
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
