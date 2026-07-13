package com.yuka.ailearningserver.task;

import com.yuka.ailearningserver.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Task module error codes — reserved range 160000–169999. */
public enum TaskErrorCode implements ErrorCode {

    TASK_NOT_FOUND(160000, "Task not found", HttpStatus.NOT_FOUND),
    TASK_ACCESS_DENIED(160001, "Task does not belong to the current user", HttpStatus.FORBIDDEN),
    TASK_INVALID_STATUS(160002, "Unknown task status", HttpStatus.BAD_REQUEST),
    TASK_INVALID_PRIORITY(160003, "Unknown task priority", HttpStatus.BAD_REQUEST);

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
