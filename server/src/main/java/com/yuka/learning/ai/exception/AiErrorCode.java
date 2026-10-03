package com.yuka.learning.ai.exception;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * AI module error codes — reserved range 190000–199999.
 * <p>
 * Sub-ranges: 1900-0x provider/config, 1900-1x conversation ownership.
 * The frontend maps these to i18n message keys, same pattern as auth's codes.
 */
public enum AiErrorCode implements ErrorCode {

    PROVIDER_NOT_CONFIGURED(190000, "AI provider is not configured", HttpStatus.SERVICE_UNAVAILABLE),
    PROVIDER_UNAVAILABLE(190001, "AI provider is unavailable", HttpStatus.BAD_GATEWAY),
    PROVIDER_TIMEOUT(190002, "AI provider timed out", HttpStatus.GATEWAY_TIMEOUT),
    PROVIDER_AUTH_FAILED(190003, "AI provider rejected the API key", HttpStatus.BAD_GATEWAY),
    RATE_LIMITED(190004, "AI provider rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    QUOTA_EXCEEDED(190005, "AI provider quota exceeded", HttpStatus.PAYMENT_REQUIRED),
    INVALID_MODEL(190006, "Configured AI model is invalid", HttpStatus.BAD_REQUEST),
    STREAM_INTERRUPTED(190007, "AI response stream was interrupted", HttpStatus.INTERNAL_SERVER_ERROR),
    CONTEXT_TOO_LARGE(190008, "Learning context is too large for the model", HttpStatus.BAD_REQUEST),
    GENERATION_PARSE_FAILED(190009, "AI generation output could not be parsed", HttpStatus.INTERNAL_SERVER_ERROR),
    CONVERSATION_NOT_FOUND(190010, "Conversation not found", HttpStatus.NOT_FOUND),
    CONVERSATION_ACCESS_DENIED(190011, "Conversation does not belong to the current user", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    AiErrorCode(int code, String message, HttpStatus httpStatus) {
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
