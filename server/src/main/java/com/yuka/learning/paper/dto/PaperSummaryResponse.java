package com.yuka.learning.paper.dto;

import java.util.List;

/**
 * A paper as the catalog lists it — its shape, never its questions (those are
 * revealed only by sitting it).
 *
 * @param sections the printed sections it covers, in order
 * @param whole    every printed section — only a whole paper estimates the paper
 * @param mine     assembled by the candidate (otherwise library content)
 * @param history  what the candidate has done with it
 */
public record PaperSummaryResponse(String id, String subject, String kind, String title, Integer paperYear,
                                   List<String> sections, boolean whole, int questionCount, double fullScore,
                                   int durationMinutes, boolean mine, History history, long createdAt) {

    /**
     * @param sat           completed sittings of this paper
     * @param best          the best completed score, or null
     * @param last          the latest completed score, or null
     * @param openSessionId a sitting of it in progress or awaiting scoring, or null
     */
    public record History(int sat, Double best, Double last, String openSessionId) {

        public static final History NONE = new History(0, null, null, null);
    }
}
