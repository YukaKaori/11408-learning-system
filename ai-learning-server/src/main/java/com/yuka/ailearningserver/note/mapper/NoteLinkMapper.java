package com.yuka.ailearningserver.note.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuka.ailearningserver.note.entity.NoteLink;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface NoteLinkMapper extends BaseMapper<NoteLink> {

    /**
     * Physically deletes every link row for a source note. Used by the
     * rebuild-on-save flow (Phase 16 Step 2): the note's links are wiped and
     * re-extracted on each write. This is a real {@code DELETE}, not the
     * inherited {@code @TableLogic} soft delete — a derived index must not
     * accumulate tombstones.
     */
    @Delete("DELETE FROM note_links WHERE source_note_id = #{sourceNoteId}")
    int deleteBySourceNoteId(@Param("sourceNoteId") Long sourceNoteId);

    /**
     * Reverts every link that resolved to {@code targetNoteId} back to a
     * dangling state ({@code target_note_id = NULL}). Called when that note is
     * deleted so inbound links stop pointing at a gone id and re-resolve by
     * title (via {@link #findBacklinkSourceIds}) if the note is later recreated.
     * Raw index-maintenance SQL — like {@link #deleteBySourceNoteId} it bypasses
     * audit fill, which a derived index does not need.
     */
    @Update("UPDATE note_links SET target_note_id = NULL WHERE target_note_id = #{targetNoteId}")
    int detachTargetNoteId(@Param("targetNoteId") Long targetNoteId);

    /**
     * Source-note ids that link to the given note, newest-updated first — the
     * backlinks query (Phase 16 Step 2). A source matches either by resolved id
     * ({@code target_note_id = noteId}) or, for a link made before the target
     * existed, by a dangling row whose title still matches ({@code target_note_id
     * IS NULL AND lower(target_title) = lower(title)}). The note itself is
     * excluded so a self-link is never its own backlink. Scoped to the owner.
     */
    @Select("""
            SELECT n.id
            FROM note_links l
            JOIN notes n ON n.id = l.source_note_id AND n.deleted = 0
            WHERE l.deleted = 0
              AND l.user_id = #{userId}
              AND l.source_note_id <> #{noteId}
              AND (l.target_note_id = #{noteId}
                   OR (l.target_note_id IS NULL AND LOWER(l.target_title) = LOWER(#{title})))
            GROUP BY n.id
            ORDER BY MAX(n.updated_at) DESC, n.id DESC
            """)
    List<Long> findBacklinkSourceIds(@Param("userId") Long userId,
                                     @Param("noteId") Long noteId,
                                     @Param("title") String title);
}
