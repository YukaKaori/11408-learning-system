package com.yuka.ailearningserver.activity.dto;

import com.yuka.ailearningserver.activity.entity.ActivityEvent;

import java.time.ZoneId;

/**
 * One timeline entry. {@code type} is the {@link com.yuka.ailearningserver.activity.entity.ActivityType}
 * name; the frontend maps it to an icon and a localized label.
 */
public record ActivityResponse(String id, String type, String subjectId, String refId, String title,
                               long createdAt) {

    public static ActivityResponse from(ActivityEvent event) {
        return new ActivityResponse(
                String.valueOf(event.getId()),
                event.getType(),
                event.getSubjectId() != null ? String.valueOf(event.getSubjectId()) : null,
                event.getRefId() != null ? String.valueOf(event.getRefId()) : null,
                event.getTitle(),
                event.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
}
