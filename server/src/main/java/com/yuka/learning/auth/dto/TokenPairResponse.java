package com.yuka.learning.auth.dto;

/** Result of a refresh-token rotation. */
public record TokenPairResponse(String accessToken, String refreshToken, long expiresIn) {
}
