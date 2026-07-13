package com.yuka.ailearningserver.material.dto;

import com.yuka.ailearningserver.material.entity.LearningMaterial;
import com.yuka.ailearningserver.material.entity.MaterialType;

import java.time.ZoneId;
import java.util.Locale;

/** Mirror of the frontend {@code LearningMaterial} type. */
public record MaterialResponse(String id, String subjectId, String title, String type, String description,
                               String sourceUrl, long addedAt) {

    public static MaterialResponse from(LearningMaterial material) {
        return new MaterialResponse(
                String.valueOf(material.getId()),
                String.valueOf(material.getSubjectId()),
                material.getTitle(),
                typeToken(material.getType()),
                material.getDescription(),
                material.getSourceUrl(),
                material.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private static String typeToken(MaterialType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
