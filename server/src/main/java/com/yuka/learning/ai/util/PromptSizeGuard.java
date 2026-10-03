package com.yuka.learning.ai.util;

import com.yuka.learning.ai.exception.AiErrorCode;
import com.yuka.learning.common.exception.BusinessException;

public final class PromptSizeGuard {

    private PromptSizeGuard() {
    }

    public static void ensureWithinLimit(int totalChars, int maxChars) {
        if (totalChars > maxChars) {
            throw new BusinessException(AiErrorCode.CONTEXT_TOO_LARGE,
                    "Prompt is %d characters, exceeds the %d limit".formatted(totalChars, maxChars));
        }
    }
}
