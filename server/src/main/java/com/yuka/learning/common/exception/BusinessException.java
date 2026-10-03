package com.yuka.learning.common.exception;

import com.yuka.learning.common.api.ErrorCode;

/**
 * Thrown by service-layer code for expected business failures.
 * Translated to the response envelope by {@link GlobalExceptionHandler}.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
