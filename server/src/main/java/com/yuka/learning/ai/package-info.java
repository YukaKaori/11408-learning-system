/**
 * AI learning engine — provider-abstracted AiService, DeepSeek integration,
 * SSE streaming, conversation persistence, and the context/prompt pipeline
 * that powers AI Tutor, Subject/Notes/Flashcard/Analytics generation.
 *
 * <p>Business code (controllers/services outside this package) never talks to
 * a vendor SDK directly — everything goes through {@link
 * com.yuka.learning.ai.provider.AiProvider}. Reserved error-code
 * range: 190000–199999. See {@code docs/ai-engine.md}.
 */
package com.yuka.learning.ai;
