package com.yuka.learning.calendar.dto;

import jakarta.validation.constraints.Size;

/**
 * Starts the study timer. {@code nodeCode}, when present, must be a syllabus
 * node — usually a paper ({@code math1}), sometimes a chapter or 考点; it is
 * what the plan attributes the time to.
 */
public record StartFocusRequest(String nodeCode, @Size(max = 255) String title) {
}
