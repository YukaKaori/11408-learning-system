package com.yuka.learning.paper.dto;

/**
 * @param savedAt    epoch ms the sheet was stored
 * @param serverNow  the server clock, so the countdown never trusts the client's
 * @param deadlineAt epoch ms the paper must be handed in by
 * @param answered   questions with an answer on the stored sheet
 */
public record SaveAnswersResponse(long savedAt, long serverNow, long deadlineAt, int answered) {
}
