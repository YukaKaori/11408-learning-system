package com.yuka.learning.task;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Task module error codes — reserved range 150000–159999. */
public enum TaskErrorCode implements ErrorCode {

    TASK_NOT_FOUND(150000, "Task not found", HttpStatus.NOT_FOUND),
    TASK_ACCESS_DENIED(150001, "Task does not belong to the current user", HttpStatus.FORBIDDEN),
    TASK_STATUS_INVALID(150002, "Unknown task status", HttpStatus.BAD_REQUEST),
    TASK_PRIORITY_INVALID(150003, "Unknown task priority", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    TaskErrorCode(int code, String message, HttpStatus httpStatus) {
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
