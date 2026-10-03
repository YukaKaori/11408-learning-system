/**
 * Per-user product preferences (theme, locale, daily study goal). Absence of
 * a row means "defaults" — GET synthesizes them, PUT creates the row lazily.
 * localStorage remains the FOUC-safe boot path on the frontend; the server
 * value reconciles after {@code /me} and wins.
 * Reserved error-code range: 200000–209999.
 */
package com.yuka.learning.preference;
