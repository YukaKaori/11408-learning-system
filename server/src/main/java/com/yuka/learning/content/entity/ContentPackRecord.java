package com.yuka.learning.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * The live version of one content pack — what a deployment is actually
 * serving, and the floor below which a pack is refused (an older jar must not
 * silently revert newer content shipped through the packs directory).
 */
@Getter
@Setter
@TableName("content_packs")
public class ContentPackRecord extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The pack id declared in the file. */
    private String pack;

    private String title;

    private Integer version;

    /** SHA-256 of the canonical pack. */
    private String contentHash;

    private Integer questionCount;

    private Integer groupCount;

    private Integer paperCount;

    /** {@code classpath} | {@code directory}. */
    private String source;

    /** When this version became live. */
    private LocalDateTime importedAt;
}
