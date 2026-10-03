package com.yuka.learning.auth.token;

import com.yuka.learning.user.entity.User;

/**
 * Issues, rotates and validates the token pair that backs stateless authentication.
 * <p>
 * Access tokens are self-contained JWTs — validated by signature only, never against
 * the database. Refresh tokens are opaque random values stored hashed server-side,
 * rotated on every use, with reuse detection revoking the whole session chain.
 * <p>
 * This is the seam for future token work (OAuth2 issuance, MFA step-up claims,
 * session management): callers depend on this interface, not on the JWT library.
 */
public interface TokenService {

    /** Issues a fresh access + refresh token pair after successful authentication. */
    IssuedTokens issue(User user, ClientInfo client);

    /**
     * Exchanges a refresh token for a new pair (rotation). The presented token is
     * revoked; presenting an already-rotated token is treated as theft and revokes
     * every live token of the user.
     */
    IssuedTokens rotate(String rawRefreshToken, ClientInfo client);

    /** Revokes a single refresh token (logout). Unknown or revoked tokens are ignored. */
    void revoke(String rawRefreshToken);

    /** Revokes every live refresh token of a user ("sign out everywhere", password change). */
    void revokeAllForUser(Long userId);

    /**
     * Parses and validates an access token.
     *
     * @throws com.yuka.learning.common.exception.BusinessException with
     *         {@code TOKEN_EXPIRED} or {@code TOKEN_INVALID}
     */
    AccessTokenClaims parseAccessToken(String accessToken);

    /** Client metadata captured at issuance, for auditing and future session management. */
    record ClientInfo(String ip, String userAgent) {
    }

    /** Result of {@link #issue} / {@link #rotate}. */
    record IssuedTokens(String accessToken, long expiresInSeconds, String refreshToken, User user) {
    }

    /** Claims the rest of the application may rely on. */
    record AccessTokenClaims(Long userId, String username) {
    }
}
