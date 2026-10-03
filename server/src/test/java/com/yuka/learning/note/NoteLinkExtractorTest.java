package com.yuka.learning.note;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure {@code [[wiki-link]]} extraction contract: determinism, dedup,
 * normalization and edge handling — no database, no Spring.
 */
class NoteLinkExtractorTest {

    @Test
    void nullOrBlankContentYieldsNoLinks() {
        assertThat(NoteLinkExtractor.extract(null)).isEmpty();
        assertThat(NoteLinkExtractor.extract("   ")).isEmpty();
        assertThat(NoteLinkExtractor.extract("no links here")).isEmpty();
    }

    @Test
    void extractsDistinctTargetsInFirstOccurrenceOrder() {
        List<String> targets = NoteLinkExtractor.extract("See [[Alpha]], then [[Beta]] and [[Gamma]].");
        assertThat(targets).containsExactly("Alpha", "Beta", "Gamma");
    }

    @Test
    void foldsDuplicatesCaseInsensitivelyKeepingFirstDisplayForm() {
        List<String> targets = NoteLinkExtractor.extract("[[Java]] ... [[java]] ... [[JAVA]]");
        assertThat(targets).containsExactly("Java"); // one row, first form wins
    }

    @Test
    void normalizesSurroundingAndInternalWhitespace() {
        List<String> targets = NoteLinkExtractor.extract("[[  Data   Structures \n and Algorithms ]]");
        assertThat(targets).containsExactly("Data Structures and Algorithms");
    }

    @Test
    void skipsEmptyAndOverlongTargets() {
        String tooLong = "x".repeat(256);
        List<String> targets = NoteLinkExtractor.extract("[[   ]] and [[" + tooLong + "]] and [[Real]]");
        assertThat(targets).containsExactly("Real");
    }

    @Test
    void isDeterministicForTheSameInput() {
        String content = "[[A]] [[b]] [[A]] [[C]]";
        assertThat(NoteLinkExtractor.extract(content)).isEqualTo(NoteLinkExtractor.extract(content));
        assertThat(NoteLinkExtractor.extract(content)).containsExactly("A", "b", "C");
    }
}
