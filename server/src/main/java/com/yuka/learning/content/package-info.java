/**
 * Content — the question bank's library as versioned packs, and the pipeline
 * that turns them into rows.
 *
 * <p>A pack ({@link com.yuka.learning.content.ContentPack}, format v2) holds
 * question groups, questions and library papers. Packs ship in the jar and,
 * optionally, in a packs directory on the server, so content can ship without
 * a code release ({@link com.yuka.learning.content.PackSource}). One pure
 * validator ({@link com.yuka.learning.content.PackValidator}) guards the boot
 * import, the test suite and the offline check
 * ({@link com.yuka.learning.content.ContentCheck}); the importer
 * ({@link com.yuka.learning.content.ContentImporter}) applies the packs
 * idempotently and records the live version of each in {@code content_packs}.
 *
 * <p>Owns {@code content_packs} (V11). Writes the library rows of
 * {@code questions}, {@code question_groups} and {@code papers} — the only
 * writer of library content. No error-code range: content problems are boot
 * failures, never client errors. The authoring guide is
 * {@code content/README.md} at the repository root.
 */
package com.yuka.learning.content;
