package com.yuka.learning.paper.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * The whole answer sheet, as it stands — saved repeatedly while the paper is
 * sat, and once more with the hand-in. Replacing the sheet (rather than
 * patching it) makes every save idempotent: a retried request cannot undo a
 * later one's answer.
 *
 * @param answers by question id; a question absent from the map is unanswered
 */
public record SaveAnswersRequest(@NotNull @Size(max = 200) Map<String, @Valid Entry> answers) {

    /**
     * @param response letters for choice questions, the value for a fill-blank, text otherwise
     * @param flagged  marked for review (标记)
     * @param seconds  time spent on the question so far, measured by the client
     */
    public record Entry(@Size(max = 20000) String response, Boolean flagged, @Min(0) @Max(36000) Integer seconds) {
    }
}
