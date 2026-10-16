package com.yuka.learning.paper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 组卷 — a new paper from the bank.
 *
 * @param sections the printed sections to include; null or empty = the whole paper
 */
public record AssemblePaperRequest(@NotBlank String subject, @Size(max = 8) List<String> sections) {
}
