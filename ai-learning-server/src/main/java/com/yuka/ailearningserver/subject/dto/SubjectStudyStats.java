package com.yuka.ailearningserver.subject.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Per-subject derived study metrics, aggregated from {@code study_sessions}.
 * A mutable bean (not a record) so MyBatis populates it by column-aliased
 * setters without a constructor mapping.
 */
@Getter
@Setter
public class SubjectStudyStats {

    /** Grouping key; may be null for unclassified sessions. */
    private Long subjectId;

    /** Total studied minutes across all sessions of this subject. */
    private Long studyMinutes;

    /** Most recent session end for this subject. */
    private LocalDateTime lastStudiedAt;
}
