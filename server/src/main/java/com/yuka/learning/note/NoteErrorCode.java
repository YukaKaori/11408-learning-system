package com.yuka.learning.note;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Note module error codes — reserved range 130000–139999. */
public enum NoteErrorCode implements ErrorCode {

    NOTE_NOT_FOUND(130000, "Note not found", HttpStatus.NOT_FOUND),
    NOTE_ACCESS_DENIED(130001, "Note does not belong to the current user", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    NoteErrorCode(int code, String message, HttpStatus httpStatus) {
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
