package com.yuka.learning.paper.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Self-scores for answers the grader cannot judge — set (a number) or cleared
 * (null), by question id. Only the questions named are touched.
 */
public record SaveScoresRequest(@NotNull @Size(max = 200) Map<String, Double> points) {
}
