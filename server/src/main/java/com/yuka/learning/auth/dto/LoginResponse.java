package com.yuka.learning.auth.dto;

/**
 * @param expiresIn access-token lifetime in seconds — the client schedules its
 *                  refresh from this, never by decoding the JWT.
 */
public record LoginResponse(String accessToken, String refreshToken, long expiresIn, AuthUserResponse user) {
}
