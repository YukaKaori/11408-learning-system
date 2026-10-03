package com.yuka.learning.common.api;

/**
 * Unified response envelope for every REST endpoint.
 * <p>
 * {@code code} is a business code ({@code 0} = success), independent of the HTTP status.
 * Clients branch on {@code code}; the HTTP status carries transport semantics.
 */
public record ApiResponse<T>(int code, String message, T data, long timestamp) {

    private static final int SUCCESS_CODE = 0;
    private static final String SUCCESS_MESSAGE = "OK";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, System.currentTimeMillis());
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.message());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null, System.currentTimeMillis());
    }
}
