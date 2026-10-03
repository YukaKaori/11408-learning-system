package com.yuka.learning.auth.security;

/**
 * The principal placed in the SecurityContext for a validated access token.
 * Carries only what the token itself proves — anything else must be loaded
 * from the database by the service that needs it.
 */
public record AuthenticatedUser(Long id, String username) {
}
