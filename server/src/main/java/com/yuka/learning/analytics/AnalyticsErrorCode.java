package com.yuka.learning.analytics;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Analytics module error codes — reserved range 180000–189999. */
public enum AnalyticsErrorCode implements ErrorCode {

    ANALYTICS_RANGE_INVALID(180000, "Analytics window must be between 1 and 90 days", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    AnalyticsErrorCode(int code, String message, HttpStatus httpStatus) {
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
