package com.yuka.learning.practice.dto;

/**
 * A practice session without its questions — the history list and the header
 * of a report.
 *
 * @param title      the scope's display snapshot (may be empty — the client
 *                   composes "mode · title")
 * @param answered   derived from the answer log
 * @param correct    derived from the answer log
 * @param finishedAt epoch ms, null while in progress
 */
public record PracticeSummaryResponse(String id, String mode, String subject, String nodeCode, String title,
                                      String status, int total, int answered, int correct, long startedAt,
                                      Long finishedAt) {
}
