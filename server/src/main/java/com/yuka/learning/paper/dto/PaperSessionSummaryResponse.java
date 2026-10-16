package com.yuka.learning.paper.dto;

/**
 * A paper sitting without its questions — the lists on 模考 and Today.
 *
 * @param status   {@code in_progress} | {@code scoring} | {@code completed}
 * @param score    null while in progress; points decided so far while scoring; the total once completed
 * @param pending  answers awaiting the candidate's score
 * @param answered answers on the sheet
 */
public record PaperSessionSummaryResponse(String id, String paperId, String subject, String kind, String title,
                                          Integer paperYear, String status, boolean whole, int timeLimitMinutes,
                                          long startedAt, long deadlineAt, Long submittedAt, Long completedAt,
                                          double fullScore, Double score, int pending, int answered, int total,
                                          String sittingId) {
}
