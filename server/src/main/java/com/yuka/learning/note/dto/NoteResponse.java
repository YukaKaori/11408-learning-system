package com.yuka.learning.note.dto;

import com.yuka.learning.note.entity.Note;

import java.time.ZoneId;

public record NoteResponse(String id, String nodeCode, String title, String content, boolean pinned,
                            long updatedAt) {

    public static NoteResponse from(Note note) {
        return new NoteResponse(
                String.valueOf(note.getId()),
                note.getNodeCode(),
                note.getTitle(),
                note.getContent(),
                Boolean.TRUE.equals(note.getPinned()),
                note.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
}
