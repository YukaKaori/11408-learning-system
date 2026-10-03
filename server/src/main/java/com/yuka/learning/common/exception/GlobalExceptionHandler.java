package com.yuka.learning.common.exception;

import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.common.api.CommonErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single translation point from exceptions to the response envelope.
 * Controllers and services never build error responses themselves.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("Business error {}: {}", ex.getErrorCode().code(), ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().httpStatus())
                .body(ApiResponse.failure(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(CommonErrorCode.VALIDATION_FAILED.message());
        return ResponseEntity.status(CommonErrorCode.VALIDATION_FAILED.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.VALIDATION_FAILED, detail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParams(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .orElse(CommonErrorCode.VALIDATION_FAILED.message());
        return ResponseEntity.status(CommonErrorCode.VALIDATION_FAILED.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.VALIDATION_FAILED, detail));
    }

    /**
     * A body that cannot be read at all — malformed JSON, a date that is not a
     * date. The client's fault, so a 400, never the generic 500 below.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(CommonErrorCode.BAD_REQUEST.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.BAD_REQUEST, "Request body is malformed"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(CommonErrorCode.VALIDATION_FAILED.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.VALIDATION_FAILED,
                        ex.getParameterName() + ": required parameter is missing"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(CommonErrorCode.VALIDATION_FAILED.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.VALIDATION_FAILED,
                        ex.getName() + ": invalid value"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(CommonErrorCode.NOT_FOUND.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(CommonErrorCode.METHOD_NOT_ALLOWED.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR));
    }
}
