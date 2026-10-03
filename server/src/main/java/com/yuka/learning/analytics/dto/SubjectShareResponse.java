package com.yuka.learning.analytics.dto;

/**
 * Study minutes attributed to one exam paper over the requested window.
 * A null {@code subject} row carries the minutes of sessions not anchored to
 * the syllabus; percentages are left to the client so the raw minutes stay
 * honest. The client owns each paper's name and accent.
 *
 * @param subject an {@code ExamSubject} code, or null for unanchored time
 */
public record SubjectShareResponse(String subject, long minutes) {
}
