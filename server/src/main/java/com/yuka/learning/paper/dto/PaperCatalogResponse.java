package com.yuka.learning.paper.dto;

import java.util.List;

/**
 * Everything a candidate can sit for one paper: library papers, their own
 * assembled papers, and what the bank can assemble right now.
 *
 * @param library  library papers (packs), in authored order
 * @param mine     the candidate's own papers, newest first
 * @param assembly what 组卷 can make from the bank today
 */
public record PaperCatalogResponse(String subject, List<PaperSummaryResponse> library,
                                   List<PaperSummaryResponse> mine, Assembly assembly) {

    /**
     * @param whole    every section can be assembled — a whole paper is available
     * @param sections per printed section, in order
     */
    public record Assembly(boolean whole, int wholeMinutes, List<SectionAvailability> sections) {
    }

    /**
     * @param minutes   the suggested time — a section paper's time limit
     * @param shortages the slots the bank cannot fill yet (empty when available)
     */
    public record SectionAvailability(String code, boolean available, int minutes, List<Shortage> shortages) {
    }

    /**
     * @param needed    questions (or texts) the slot takes
     * @param available what the bank has for it
     * @param texts     whether the slot is counted in texts (reading, cloze) rather than questions
     */
    public record Shortage(String scope, int needed, int available, boolean texts) {
    }
}
