/**
 * Learning analytics — derived, read-only views over the other domains:
 * study time (study_sessions), streaks (session days), task completion,
 * AI usage. Implemented in Phase 7 as on-the-fly aggregation
 * ({@code AnalyticsService}); it owns NO tables — if aggregation ever becomes
 * measurably expensive, materialized summary tables are added HERE by a new
 * migration, never by widening the source domains.
 *
 * <p>Reserved error-code range: 180000–189999. (Range 190000–199999 is
 * reserved for the Phase 6 {@code ai} module.)
 */
package com.yuka.learning.analytics;
