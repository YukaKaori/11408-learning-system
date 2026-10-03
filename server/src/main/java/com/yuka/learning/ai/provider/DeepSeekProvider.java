package com.yuka.learning.ai.provider;

import com.yuka.learning.ai.exception.AiErrorCode;
import com.yuka.learning.ai.provider.dto.DeepSeekChatRequest;
import com.yuka.learning.ai.provider.dto.DeepSeekChunk;
import com.yuka.learning.ai.provider.dto.DeepSeekMessage;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * {@link AiProvider} implementation for DeepSeek's OpenAI-compatible
 * {@code /chat/completions} endpoint. Always requests {@code stream: true} —
 * one-shot generation use-cases get their "single answer" by having their
 * {@link ChatStreamListener} concatenate tokens instead of the provider
 * running a separate non-streaming code path.
 * <p>
 * Retries (bounded, exponential backoff) apply only to failures before the
 * first token is emitted. Once tokens have started flowing, a failure is
 * reported as {@link AiErrorCode#STREAM_INTERRUPTED} — never retried, since
 * that would duplicate already-delivered content.
 */
@Slf4j
@Component
public class DeepSeekProvider implements AiProvider {

    private static final int MAX_ATTEMPTS = 3;
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties.Ai.DeepSeek config;

    public DeepSeekProvider(RestClient aiRestClient, ObjectMapper objectMapper, AppProperties properties) {
        this.restClient = aiRestClient;
        this.objectMapper = objectMapper;
        this.config = properties.ai() != null ? properties.ai().deepseek() : null;
    }

    @Override
    public String id() {
        return "deepseek";
    }

    @Override
    public boolean isConfigured() {
        return config != null && config.apiKey() != null && !config.apiKey().isBlank()
                && config.baseUrl() != null && !config.baseUrl().isBlank();
    }

    @Override
    public void chat(ChatRequest request, ChatStreamListener listener) {
        if (!isConfigured()) {
            listener.onError(new BusinessException(AiErrorCode.PROVIDER_NOT_CONFIGURED));
            return;
        }
        DeepSeekChatRequest wire = toWireRequest(request);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                listener.onComplete("cancelled");
                return;
            }
            try {
                attemptOnce(wire, listener);
                return;
            } catch (PreStreamFailure failure) {
                if (!failure.retryable || attempt == MAX_ATTEMPTS) {
                    listener.onError(failure.toBusinessException());
                    return;
                }
                log.warn("DeepSeek attempt {}/{} failed, retrying: {}", attempt, MAX_ATTEMPTS, failure.getMessage());
                sleepBackoff(attempt);
            } catch (MidStreamFailure failure) {
                listener.onError(failure.toBusinessException());
                return;
            }
        }
    }

    private void attemptOnce(DeepSeekChatRequest wire, ChatStreamListener listener) {
        boolean[] emittedAny = {false};
        try {
            restClient.post()
                    .uri(config.baseUrl() + CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(wire)
                    .exchange((clientRequest, clientResponse) -> {
                        if (!clientResponse.getStatusCode().is2xxSuccessful()) {
                            throw httpStatusFailure(clientResponse.getStatusCode().value(), clientResponse.getBody());
                        }
                        readStream(clientResponse.getBody(), listener, emittedAny);
                        return null;
                    });
        } catch (PreStreamFailure | MidStreamFailure ex) {
            throw ex;
        } catch (Exception ex) {
            if (emittedAny[0]) {
                throw new MidStreamFailure(AiErrorCode.STREAM_INTERRUPTED, "DeepSeek stream interrupted", ex);
            }
            boolean timeout = isTimeout(ex);
            throw new PreStreamFailure(
                    timeout ? AiErrorCode.PROVIDER_TIMEOUT : AiErrorCode.PROVIDER_UNAVAILABLE,
                    "DeepSeek request failed: " + ex.getMessage(), true, ex);
        }
    }

    private void readStream(InputStream body, ChatStreamListener listener, boolean[] emittedAny) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    listener.onComplete("cancelled");
                    return;
                }
                if (line.isBlank() || !line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if ("[DONE]".equals(payload)) {
                    listener.onComplete("stop");
                    return;
                }
                DeepSeekChunk chunk;
                try {
                    chunk = objectMapper.readValue(payload, DeepSeekChunk.class);
                } catch (Exception parseEx) {
                    log.warn("Failed to parse DeepSeek chunk, skipping: {}", payload);
                    continue;
                }
                String delta = chunk.firstDeltaContent();
                if (delta != null && !delta.isEmpty()) {
                    emittedAny[0] = true;
                    listener.onToken(delta);
                }
                String finish = chunk.firstFinishReason();
                if (finish != null) {
                    listener.onComplete(finish);
                    return;
                }
            }
            // Body closed without an explicit [DONE]/finish_reason — treat as a normal end.
            listener.onComplete("stop");
        }
    }

    private PreStreamFailure httpStatusFailure(int status, InputStream errorBody) {
        String body = readSafely(errorBody);
        log.warn("DeepSeek returned HTTP {}: {}", status, truncate(body));
        AiErrorCode code = switch (status) {
            case 401, 403 -> AiErrorCode.PROVIDER_AUTH_FAILED;
            case 429 -> AiErrorCode.RATE_LIMITED;
            case 402 -> AiErrorCode.QUOTA_EXCEEDED;
            case 400, 404 -> AiErrorCode.INVALID_MODEL;
            default -> AiErrorCode.PROVIDER_UNAVAILABLE;
        };
        return new PreStreamFailure(code, "DeepSeek HTTP " + status, status >= 500, null);
    }

    private DeepSeekChatRequest toWireRequest(ChatRequest request) {
        List<DeepSeekMessage> messages = request.messages().stream()
                .map(turn -> new DeepSeekMessage(turn.role().name().toLowerCase(Locale.ROOT), turn.content()))
                .toList();
        return new DeepSeekChatRequest(config.model(), messages, config.temperature(), config.topP(),
                config.maxTokens(), true);
    }

    private static boolean isTimeout(Throwable ex) {
        for (Throwable cursor = ex; cursor != null; cursor = cursor.getCause()) {
            String name = cursor.getClass().getSimpleName();
            if (name.contains("Timeout")) {
                return true;
            }
        }
        return false;
    }

    private static String readSafely(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private static String truncate(String text) {
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Duration.ofMillis(300L * (1L << (attempt - 1))));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Failure before any token was emitted — may be retried. */
    private static final class PreStreamFailure extends RuntimeException {
        private final AiErrorCode code;
        private final boolean retryable;

        PreStreamFailure(AiErrorCode code, String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.retryable = retryable;
        }

        BusinessException toBusinessException() {
            return new BusinessException(code, getMessage());
        }
    }

    /** Failure after at least one token was emitted — never retried. */
    private static final class MidStreamFailure extends RuntimeException {
        private final AiErrorCode code;

        MidStreamFailure(AiErrorCode code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        BusinessException toBusinessException() {
            return new BusinessException(code, getMessage());
        }
    }
}
