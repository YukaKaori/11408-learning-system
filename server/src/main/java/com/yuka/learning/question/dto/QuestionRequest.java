package com.yuka.learning.question.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A question the candidate writes — typically one they got wrong in a book or a
 * paper mock and want in their mistake book. Shape rules beyond these
 * annotations (options only for choice types, answer letters that name real
 * options, 考点 inside the paper) are enforced by {@code QuestionRules}, the
 * same validator the library content passes.
 *
 * @param answer the reference answer: letters for choice questions, the value for
 *               a fill-blank, a model answer for open questions
 * @param accept extra accepted forms of a fill-blank value, e.g. {@code 0.5} for {@code 1/2}
 */
public record QuestionRequest(
        @NotBlank String subject,
        String section,
        @NotBlank String type,
        @NotBlank @Size(max = 20000) String stem,
        @Size(max = 20000) String passage,
        @Size(max = 8) List<@Size(max = 2000) String> options,
        @NotBlank @Size(max = 20000) String answer,
        @Size(max = 8) List<@Size(max = 128) String> accept,
        @Size(max = 20000) String analysis,
        @Min(1) @Max(5) Integer difficulty,
        Double score,
        @Size(max = 128) String source,
        Integer sourceYear,
        @NotEmpty @Size(max = 6) List<String> points) {
}
