package com.yuka.learning.material.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Kind of learning material, stored as {@code learning_materials.type}.
 * Drives which viewer the frontend opens and which future ingest pipeline
 * (text extraction, transcript, …) applies.
 */
public enum MaterialType {

    PDF(0),
    MARKDOWN(1),
    VIDEO(2),
    ARTICLE(3),
    LINK(4),
    DOCUMENT(5);

    @EnumValue
    private final int code;

    MaterialType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
