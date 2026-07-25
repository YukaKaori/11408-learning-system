package com.yuka.ailearningserver.note.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.ailearningserver.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * One [[wiki-link]] a source note references — a row in the derived link graph
 * index (Phase 16). The source of truth is the markdown in {@code notes.content};
 * this table is rebuilt on save so backlinks are a single indexed query rather
 * than a scan of every note.
 *
 * <p>{@code targetNoteId} is null for a <em>dangling</em> link (the referenced
 * title matches no note the user owns yet); it resolves once a source note is
 * saved after the target exists, and backlink queries also resolve dangling
 * links by title. Because the index is rebuilt via a physical delete + insert,
 * the inherited {@code deleted} flag is carried only for uniformity and stays 0.
 */
@Getter
@Setter
@TableName("note_links")
public class NoteLink extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner, denormalized for scoping). */
    private Long userId;

    /** Logical FK → notes.id — the note containing the {@code [[...]]} reference. */
    private Long sourceNoteId;

    /** Logical FK → notes.id — the resolved target; null = dangling link. */
    private Long targetNoteId;

    /** Raw {@code [[text]]} reference; matches {@code notes.title} length. */
    private String targetTitle;
}
