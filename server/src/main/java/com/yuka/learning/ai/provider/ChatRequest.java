package com.yuka.learning.ai.provider;

import java.util.List;

/**
 * Everything a provider needs for one exchange. Model/temperature/etc. are
 * NOT here — each provider owns its own configured defaults, so swapping
 * providers is a config change, never a caller change.
 */
public record ChatRequest(List<ChatTurn> messages) {
}
