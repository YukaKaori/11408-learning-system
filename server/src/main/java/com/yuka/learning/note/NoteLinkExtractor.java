package com.yuka.learning.note;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, deterministic extraction of {@code [[wiki-link]]} targets from a note's
 * markdown. The source of truth is {@code notes.content}; {@link NoteService}
 * feeds the result of this class into the derived {@code note_links} index on
 * every save (Phase 16 Step 2).
 *
 * <p>Deliberately a raw-text regex, not a markdown AST parse — the Phase 16
 * contract fixes the pattern {@code \[\[([^\]]+)\]\]}. A consequence is that a
 * {@code [[...]]} sequence inside a fenced or inline code block is also
 * extracted; this is a known, accepted limitation that keeps extraction
 * deterministic and content-blind.
 */
public final class NoteLinkExtractor {

    /** {@code [[ inner ]]} where inner is any non-empty run without a {@code ]}. */
    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    /** Any run of whitespace (incl. newlines/tabs) collapsed to a single space. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Matches {@code notes.title} / {@code note_links.target_title} column width. */
    private static final int MAX_TITLE_LENGTH = 255;

    private NoteLinkExtractor() {
    }

    /**
     * @param content raw markdown (may be {@code null})
     * @return the distinct normalized link targets in first-occurrence order;
     * duplicates are folded case-insensitively (the first display form wins)
     */
    public static List<String> extract(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        // lower-cased key -> first-seen display form; insertion order preserved.
        Map<String, String> distinct = new LinkedHashMap<>();
        Matcher matcher = WIKI_LINK.matcher(content);
        while (matcher.find()) {
            String normalized = WHITESPACE.matcher(matcher.group(1).trim()).replaceAll(" ");
            if (normalized.isEmpty() || normalized.length() > MAX_TITLE_LENGTH) {
                continue; // empty [[  ]] or a title that can never match a note
            }
            distinct.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }
        return List.copyOf(distinct.values());
    }
}
