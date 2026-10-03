package com.yuka.learning.ai.stream;

/** Persistence hook invoked by {@link SseRelay} once a relay finishes, one way or another. */
public interface RelayCallback {

    /** Provider finished normally or was cancelled by the client. */
    void onFinished(String fullText, boolean cancelled);

    /** Provider failed. {@code partialText} holds whatever tokens were emitted before the failure. */
    void onFailed(String partialText, Throwable error);
}
