package com.yuka.learning.auth;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Auth module error codes — reserved range 100000–109999.
 * <p>
 * Sub-ranges: 1000xx credentials/account, 1000-1x access token, 1000-2x refresh token.
 * The frontend branches on these codes (e.g. only access-token failures trigger a
 * silent refresh), so codes are contract — never renumber.
 */
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(100000, "Invalid username or password", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(100001, "Account is locked", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(100002, "Account is disabled", HttpStatus.FORBIDDEN),

    TOKEN_EXPIRED(100010, "Access token has expired", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(100011, "Access token is invalid", HttpStatus.UNAUTHORIZED),

    REFRESH_TOKEN_INVALID(100020, "Refresh token is invalid", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED(100021, "Refresh token has expired", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED(100022, "Refresh token has already been used", HttpStatus.UNAUTHORIZED);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    AuthErrorCode(int code, String message, HttpStatus httpStatus) {
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
