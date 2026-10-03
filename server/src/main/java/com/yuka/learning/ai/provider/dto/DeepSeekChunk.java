package com.yuka.learning.ai.provider.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** One {@code data:} line of a DeepSeek streaming response. Unknown fields are ignored on purpose. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeepSeekChunk(List<Choice> choices) {

    public String firstDeltaContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).delta() == null) {
            return null;
        }
        return choices.get(0).delta().content();
    }

    public String firstFinishReason() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).finishReason();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Delta delta, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(String content) {
    }
}
