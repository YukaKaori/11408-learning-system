package com.yuka.learning.material;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Material module error codes — reserved range 120000–129999. */
public enum MaterialErrorCode implements ErrorCode {

    MATERIAL_NOT_FOUND(120000, "Material not found", HttpStatus.NOT_FOUND),
    MATERIAL_ACCESS_DENIED(120001, "Material does not belong to the current user", HttpStatus.FORBIDDEN),
    MATERIAL_TYPE_INVALID(120002, "Unknown material type", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    MaterialErrorCode(int code, String message, HttpStatus httpStatus) {
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
