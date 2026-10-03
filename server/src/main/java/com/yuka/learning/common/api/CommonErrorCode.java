package com.yuka.learning.common.api;

import org.springframework.http.HttpStatus;

/**
 * Cross-cutting error codes shared by all modules.
 * Feature modules define their own {@link ErrorCode} enums in their reserved range.
 */
public enum CommonErrorCode implements ErrorCode {

    BAD_REQUEST(40000, "Bad request", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(40001, "Validation failed", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "Access denied", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "Resource not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(40500, "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(40900, "Resource conflict", HttpStatus.CONFLICT),
    INTERNAL_ERROR(50000, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    CommonErrorCode(int code, String message, HttpStatus httpStatus) {
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
