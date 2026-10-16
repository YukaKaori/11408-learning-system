package com.yuka.learning.paper.dto;

/**
 * @param score   points decided so far (graded answers plus the scores given)
 * @param pending answers that still need the candidate's score
 */
public record ScoringProgressResponse(double score, int pending) {
}
