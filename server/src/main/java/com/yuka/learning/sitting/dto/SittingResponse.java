package com.yuka.learning.sitting.dto;

import java.util.List;

/**
 * A sitting on the wire.
 *
 * @param title    the mock paper's name or a 真题's custom label; null = name the 真题 by its year
 * @param sections what each attempted section earned and was worth; empty when only a total was recorded
 * @param complete whether it covered the whole paper — only complete sittings estimate a paper
 */
public record SittingResponse(String id, String subject, String kind, String title, Integer paperYear,
                              String satOn, Integer durationMinutes, List<Section> sections, double score,
                              double fullScore, boolean complete, String note, long createdAt) {

    public record Section(String code, double score, double full) {
    }
}
