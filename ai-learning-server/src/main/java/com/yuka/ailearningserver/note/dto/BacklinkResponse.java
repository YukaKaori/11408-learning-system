package com.yuka.ailearningserver.note.dto;

import com.yuka.ailearningserver.note.entity.Note;

import java.time.ZoneId;

/**
 * A note that links to the note being viewed (Phase 16 backlinks panel).
 * Intentionally minimal — id + title + updatedAt is enough to render and
 * navigate the right-rail list; content is fetched only when the note is opened.
 */
public record BacklinkResponse(String id, String title, long updatedAt) {

    public static BacklinkResponse from(Note note) {
        return new BacklinkResponse(
                String.valueOf(note.getId()),
                note.getTitle(),
                note.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
}
