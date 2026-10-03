package com.yuka.learning.material.dto;

import com.yuka.learning.material.entity.LearningMaterial;

import java.time.ZoneId;

public record MaterialResponse(String id, String nodeCode, String title, String type, String description,
                               String sourceUrl, Long sizeBytes, long createdAt) {

    public static MaterialResponse from(LearningMaterial material) {
        return new MaterialResponse(
                String.valueOf(material.getId()),
                material.getNodeCode(),
                material.getTitle(),
                material.getType().name().toLowerCase(),
                material.getDescription(),
                material.getSourceUrl(),
                material.getSizeBytes(),
                material.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
}
