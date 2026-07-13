package com.yuka.ailearningserver.activity.entity;

/**
 * The kinds of events that appear on the workspace timeline. Persisted as the
 * enum {@code name()} string in {@code activity_events.type} — string, not
 * tinyint, so new event kinds never require a migration and rows stay
 * self-describing when read straight from the database.
 */
public enum ActivityType {

    SUBJECT_CREATED,
    SUBJECT_COMPLETED,
    MATERIAL_UPLOADED,
    FLASHCARDS_GENERATED,
    REVIEW_FINISHED,
    AI_CONVERSATION,
    TASK_COMPLETED,
    NOTE_CREATED,
    SESSION_FINISHED,
}
