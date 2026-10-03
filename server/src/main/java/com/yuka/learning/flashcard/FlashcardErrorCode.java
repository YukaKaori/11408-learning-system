package com.yuka.learning.flashcard;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Flashcard module error codes — reserved range 140000–149999. */
public enum FlashcardErrorCode implements ErrorCode {

    DECK_NOT_FOUND(140000, "Flashcard deck not found", HttpStatus.NOT_FOUND),
    DECK_ACCESS_DENIED(140001, "Flashcard deck does not belong to the current user", HttpStatus.FORBIDDEN),
    CARD_NOT_FOUND(140002, "Flashcard not found", HttpStatus.NOT_FOUND),
    CARD_ACCESS_DENIED(140003, "Flashcard does not belong to the current user", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    FlashcardErrorCode(int code, String message, HttpStatus httpStatus) {
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
