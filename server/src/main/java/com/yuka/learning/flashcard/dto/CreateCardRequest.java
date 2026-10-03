package com.yuka.learning.flashcard.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCardRequest(@NotBlank String front, @NotBlank String back) {
}
