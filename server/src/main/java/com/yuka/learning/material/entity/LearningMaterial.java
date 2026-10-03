package com.yuka.learning.material.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * A single piece of reference content on the candidate's shelf. Exactly one of
 * {@code sourceUrl} (external content) or {@code storageKey} (uploaded
 * content, resolved via the future StorageService) is expected to be set —
 * both stay null until upload/link flows are implemented.
 */
@Getter
@Setter
@TableName("learning_materials")
public class LearningMaterial extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The syllabus node this material serves (e.g. {@code cs408} for a textbook,
     * {@code math1.linear.eigen} for a lecture); null = general reference. Update
     * strategy ALWAYS so un-anchoring persists via {@code updateById}.
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nodeCode;

    /** Logical FK → users.id — denormalized for per-user queries without a join. */
    private Long userId;

    private String title;

    private MaterialType type;

    private String description;

    /** External location for LINK / ARTICLE / VIDEO materials. */
    private String sourceUrl;

    /** Opaque key in the storage backend for uploaded files (StorageService, future). */
    private String storageKey;

    /** Size in bytes for uploaded files; null for external content. */
    private Long sizeBytes;
}
