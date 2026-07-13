package com.yuka.ailearningserver.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.ailearningserver.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * A single entry on the workspace event timeline — a durable record that
 * something meaningful happened (see {@link ActivityType}). Recorded as a side
 * effect of domain writes by {@code ActivityService.record}; never edited by
 * the user.
 *
 * <p>{@code type} is stored as the {@link ActivityType} {@code name()} string;
 * the enum lives only at the service boundary so the entity never needs a
 * type handler.
 */
@Getter
@Setter
@TableName("activity_events")
public class ActivityEvent extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner). */
    private Long userId;

    /** {@link ActivityType} name. */
    private String type;

    /** Logical FK → subjects.id; null when the event is not subject-scoped. */
    private Long subjectId;

    /** Id of the entity the event is about (note/task/deck/session/…); null if none. */
    private Long refId;

    /** Human-readable label snapshot, e.g. the note title at the time. */
    private String title;
}
