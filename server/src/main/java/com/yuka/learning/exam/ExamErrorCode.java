package com.yuka.learning.exam;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Exam module error codes — reserved range 210000–219999. */
public enum ExamErrorCode implements ErrorCode {

    /** A client referenced a syllabus node code that does not exist in the loaded syllabus. */
    NODE_NOT_FOUND(210000, "Syllabus node not found", HttpStatus.BAD_REQUEST),
    /** A client referenced a paper that is not one of the four 11408 subjects. */
    SUBJECT_INVALID(210001, "Exam subject is invalid", HttpStatus.BAD_REQUEST),
    /** Target year, exam date or a target score is out of range. */
    PROFILE_INVALID(210002, "Exam profile is invalid", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ExamErrorCode(int code, String message, HttpStatus httpStatus) {
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
