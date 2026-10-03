package com.yuka.learning.ai.stream;

import com.yuka.learning.ai.exception.AiErrorCode;
import com.yuka.learning.ai.provider.AiProvider;
import com.yuka.learning.ai.provider.ChatRequest;
import com.yuka.learning.ai.provider.ChatStreamListener;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives one {@link AiProvider#chat} call on a virtual thread and relays
 * tokens to the browser as SSE events. This is the only place that touches
 * {@link SseEmitter} — {@code AiConversationService} deals in plain text.
 * <p>
 * Cancellation: a client abort makes the servlet container fail the next
 * {@code emitter.send}, which cancels the backing {@link Future} and
 * interrupts the provider thread. The provider notices the interrupt between
 * reads of the upstream stream (see {@code DeepSeekProvider}) — a stalled
 * connection with no further chunks won't unblock instantly, which is an
 * accepted tradeoff rather than reaching for lower-level socket control.
 */
@Slf4j
@Component
public class SseRelay {

    private static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(5);

    private final AiProvider aiProvider;
    private final ExecutorService streamingExecutor;

    public SseRelay(AiProvider aiProvider, ExecutorService aiStreamingExecutor) {
        this.aiProvider = aiProvider;
        this.streamingExecutor = aiStreamingExecutor;
    }

    public SseEmitter stream(ChatRequest request, RelayCallback callback) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        StringBuilder full = new StringBuilder();

        Future<?> future = streamingExecutor.submit(() -> aiProvider.chat(request, new ChatStreamListener() {
            @Override
            public void onToken(String delta) {
                full.append(delta);
                try {
                    emitter.send(SseEmitter.event().name("token").data(delta));
                } catch (IOException | IllegalStateException ex) {
                    Future<?> current = futureRef.get();
                    if (current != null) {
                        current.cancel(true);
                    }
                }
            }

            @Override
            public void onComplete(String finishReason) {
                boolean cancelled = "cancelled".equals(finishReason);
                completeEmitter(emitter, "done", finishReason);
                callback.onFinished(full.toString(), cancelled);
            }

            @Override
            public void onError(Throwable error) {
                BusinessException businessException = error instanceof BusinessException be
                        ? be
                        : new BusinessException(AiErrorCode.STREAM_INTERRUPTED);
                completeEmitter(emitter, "error",
                        ApiResponse.failure(businessException.getErrorCode(), businessException.getMessage()));
                log.warn("AI stream failed after {} chars", full.length(), error);
                callback.onFailed(full.toString(), error);
            }
        }));
        futureRef.set(future);

        emitter.onTimeout(() -> future.cancel(true));
        emitter.onError(ex -> future.cancel(true));
        return emitter;
    }

    private static void completeEmitter(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException ignored) {
            // Client already gone — nothing left to notify.
        }
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // Already completed by a timeout/error callback racing this one.
        }
    }
}
