package com.yuka.learning.ai.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Wire shape of a DeepSeek (OpenAI-compatible) {@code /chat/completions} request. */
public record DeepSeekChatRequest(
        String model,
        List<DeepSeekMessage> messages,
        double temperature,
        @JsonProperty("top_p") double topP,
        @JsonProperty("max_tokens") int maxTokens,
        boolean stream) {
}
