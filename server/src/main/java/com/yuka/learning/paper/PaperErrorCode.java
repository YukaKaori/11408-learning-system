package com.yuka.learning.paper;

import com.yuka.learning.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** Paper error codes — reserved range 260000–269999. */
public enum PaperErrorCode implements ErrorCode {

    PAPER_NOT_FOUND(260000, "Paper not found", HttpStatus.NOT_FOUND),
    PAPER_ACCESS_DENIED(260001, "Paper does not belong to the current user", HttpStatus.FORBIDDEN),
    /** A section that is not printed on this paper, or listed twice. */
    PAPER_SECTION_INVALID(260002, "Section does not belong to this paper", HttpStatus.BAD_REQUEST),
    /** The bank cannot fill every slot of the requested sections yet. */
    PAPER_CONTENT_SHORT(260003, "The question bank cannot fill this paper yet", HttpStatus.CONFLICT),
    PAPER_SESSION_NOT_FOUND(260004, "Paper session not found", HttpStatus.NOT_FOUND),
    PAPER_SESSION_ACCESS_DENIED(260005, "Paper session does not belong to the current user", HttpStatus.FORBIDDEN),
    /** Another paper is being sat — one at a time. */
    PAPER_SESSION_ACTIVE(260006, "Another paper is in progress", HttpStatus.CONFLICT),
    /** The action does not fit the session's state (answering after submission, scoring before it…). */
    PAPER_SESSION_STATE(260007, "The paper is not in a state that allows this", HttpStatus.CONFLICT),
    /** Answers sent after the deadline (and its grace) are not accepted. */
    PAPER_TIME_UP(260008, "Time is up for this paper", HttpStatus.CONFLICT),
    /** An answer to a question that is not on this paper. */
    PAPER_ANSWER_INVALID(260009, "Answer does not belong to this paper", HttpStatus.BAD_REQUEST),
    /** A self-score outside the question's worth, off the half-point grid, or for a question the grader decided. */
    PAPER_SCORE_INVALID(260010, "Score is not a valid mark for this question", HttpStatus.BAD_REQUEST),
    /** Completing before every self-scored question has its mark. */
    PAPER_SCORING_INCOMPLETE(260011, "Some answers still need your score", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    PaperErrorCode(int code, String message, HttpStatus httpStatus) {
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
