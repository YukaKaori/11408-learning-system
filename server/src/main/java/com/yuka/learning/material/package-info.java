/**
 * Learning materials — the candidate's reference shelf (教材, 视频课, 真题 PDFs,
 * articles, links), each optionally anchored to a syllabus node. Since V8 the
 * retired {@code subject_id} is no longer read or written.
 *
 * <p>Upload/ingest goes through the
 * {@code infrastructure} {@code StorageService} seam when implemented (the
 * entity carries a {@code storageKey} for that future); AI ingestion
 * (chunking/embedding for retrieval) is a Phase 6+ concern layered on top —
 * never inside — this package. Reserved error-code range: 120000–129999.
 */
package com.yuka.learning.material;
