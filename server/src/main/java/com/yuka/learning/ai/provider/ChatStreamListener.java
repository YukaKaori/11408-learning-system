package com.yuka.learning.ai.provider;

/**
 * Sink for one {@link AiProvider#chat} call. Implementations decide what to
 * do with tokens — relay to an {@code SseEmitter} for live chat, or
 * concatenate into a single string for one-shot generation use-cases.
 */
public interface ChatStreamListener {

    /** A content delta arrived. Called zero or more times before {@link #onComplete}. */
    void onToken(String delta);

    /**
     * The exchange finished normally.
     *
     * @param finishReason provider-reported reason ({@code stop}, {@code length}), or
     *                      {@code "cancelled"} when the caller interrupted the exchange.
     */
    void onComplete(String finishReason);

    /**
     * The exchange failed. May fire after some tokens were already delivered via
     * {@link #onToken} — implementations must treat whatever was already emitted
     * as a partial/truncated result, not discard it.
     */
    void onError(Throwable error);
}
