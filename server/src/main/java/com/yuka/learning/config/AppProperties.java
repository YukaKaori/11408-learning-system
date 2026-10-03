package com.yuka.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Typed access to all custom {@code app.*} configuration.
 * Add new settings here instead of scattering {@code @Value} lookups.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Security security, Ai ai, Flashcard flashcard) {

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * Spaced-repetition tuning. Only the operator-facing dial lives here; the FSRS
     * weight vector and target retention are algorithm internals (see
     * {@code ReviewParameters}) that the roadmap never asks a user to touch.
     *
     * @param newCardsPerDay maximum brand-new cards introduced into the due queue
     *                       per user per (client-zone) day. Caps the first queue of
     *                       a freshly migrated deck so it is studyable, not overwhelming.
     */
    public record Flashcard(int newCardsPerDay) {
    }

    public record Security(Jwt jwt, PasswordPolicy passwordPolicy) {

        /**
         * @param secret          HMAC-SHA256 signing key, at least 32 bytes. Injected via the
         *                        {@code JWT_SECRET} environment variable outside local dev.
         * @param issuer          {@code iss} claim, verified on every parse.
         * @param accessTokenTtl  lifetime of access tokens (short — minutes).
         * @param refreshTokenTtl lifetime of refresh tokens (long — days); also the maximum
         *                        length of a login session without re-authentication.
         */
        public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
        }

        /**
         * Password policy applied when passwords are created or changed (registration and
         * password-reset arrive in a later phase; login never re-validates policy).
         */
        public record PasswordPolicy(int minLength) {
        }
    }

    /**
     * @param provider       active provider id, e.g. {@code deepseek}. Business code never
     *                       branches on this — it only selects which {@code AiProvider} bean
     *                       is wired in {@code AiConfig}.
     * @param maxPromptChars hard ceiling on the assembled prompt (system + context + history +
     *                       input) before a request is rejected as {@code CONTEXT_TOO_LARGE}.
     */
    public record Ai(String provider, DeepSeek deepseek, int maxPromptChars) {

        /**
         * @param apiKey            secret, {@code DEEPSEEK_API_KEY} only — never hardcoded, never
         *                          logged. Blank means the provider is unconfigured; calls fail
         *                          fast with {@code PROVIDER_NOT_CONFIGURED} rather than at
         *                          startup, so the rest of the app keeps working without a key.
         * @param baseUrl           DeepSeek's OpenAI-compatible API root.
         * @param model             e.g. {@code deepseek-chat}.
         * @param temperature       sampling temperature.
         * @param topP              nucleus sampling parameter.
         * @param maxTokens         max tokens generated per response.
         * @param timeout           per-request connect+read timeout.
         * @param streamingEnabled  whether chat responses stream (SSE) or return in one shot.
         */
        public record DeepSeek(String apiKey, String baseUrl, String model, double temperature, double topP,
                               int maxTokens, Duration timeout, boolean streamingEnabled) {
        }
    }
}
