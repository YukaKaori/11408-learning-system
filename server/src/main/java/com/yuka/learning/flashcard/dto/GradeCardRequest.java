package com.yuka.learning.flashcard.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * A single review grade posted for a card.
 *
 * @param rating the four-button FSRS grade: 1=again, 2=hard, 3=good, 4=easy.
 *               Bounds are enforced here (Bean Validation → 400) so the service
 *               only ever converts a value the scheduler accepts.
 */
public record GradeCardRequest(@Min(1) @Max(4) int rating) {
}
