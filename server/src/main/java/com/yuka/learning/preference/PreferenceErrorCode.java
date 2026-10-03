package com.yuka.learning.preference;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Preference module error codes — reserved range 200000–209999. */
public enum PreferenceErrorCode implements ErrorCode {

    PREFERENCE_THEME_INVALID(200000, "Unknown theme preference", HttpStatus.BAD_REQUEST),
    PREFERENCE_LOCALE_INVALID(200001, "Unsupported locale preference", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    PreferenceErrorCode(int code, String message, HttpStatus httpStatus) {
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
