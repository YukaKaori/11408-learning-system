package com.yuka.learning.question;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Question bank error codes — reserved range 220000–229999. */
public enum QuestionErrorCode implements ErrorCode {

    QUESTION_NOT_FOUND(220000, "Question not found", HttpStatus.NOT_FOUND),
    QUESTION_ACCESS_DENIED(220001, "Question does not belong to the current user", HttpStatus.FORBIDDEN),
    /** Library content is shared by every candidate and is edited only through its content pack. */
    QUESTION_READ_ONLY(220002, "Library questions cannot be modified", HttpStatus.FORBIDDEN),
    /** The question breaks one of {@code QuestionRules}; the message names the rule. */
    QUESTION_INVALID(220003, "Question is invalid", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    QuestionErrorCode(int code, String message, HttpStatus httpStatus) {
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
