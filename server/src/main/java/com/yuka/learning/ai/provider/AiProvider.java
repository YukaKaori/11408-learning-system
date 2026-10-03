package com.yuka.learning.ai.provider;

/**
 * The one seam business code (services/controllers outside {@code ai.provider})
 * is allowed to depend on for talking to a model. Adding a new vendor means
 * adding a new implementation here and flipping {@code app.ai.provider} —
 * nothing else changes.
 */
public interface AiProvider {

    /** Stable id, e.g. {@code "deepseek"}. Matches {@code app.ai.provider}. */
    String id();

    /** True once required configuration (API key, etc.) is present. */
    boolean isConfigured();

    /**
     * Runs the exchange, invoking {@code listener} as tokens arrive. Blocking —
     * callers run this on a virtual thread. Never throws for provider-side
     * failures (network, HTTP error, parse failure); those are reported via
     * {@link ChatStreamListener#onError}.
     */
    void chat(ChatRequest request, ChatStreamListener listener);
}
