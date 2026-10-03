package com.yuka.learning.note;

import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.note.dto.BacklinkResponse;
import com.yuka.learning.note.dto.CreateNoteRequest;
import com.yuka.learning.note.dto.UpdateNoteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The derived {@code note_links} index maintained by {@link NoteService} over
 * H2: extraction + resolution on save, dedup, dangling links, backlinks (incl.
 * title-fallback and self-exclusion), rebuild determinism, delete cleanup and
 * ownership. Pure extraction is covered by {@link NoteLinkExtractorTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
class NoteServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER = 2L;

    @Autowired
    private NoteService noteService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM note_links");
        jdbcTemplate.update("DELETE FROM notes");
    }

    // --- extraction & resolution -------------------------------------------

    @Test
    void createResolvesExistingTargetAndStoresDanglingAsNull() {
        Long target = create(USER, "Algorithms", "content");
        Long source = create(USER, "Study", "See [[Algorithms]] and [[Nonexistent]].");

        assertThat(rowCount(source)).isEqualTo(2);
        assertThat(targetOf(source, "Algorithms")).isEqualTo(target);
        assertThat(targetOf(source, "Nonexistent")).isNull(); // dangling
    }

    @Test
    void duplicateLinksInOneNoteProduceOneRow() {
        Long source = create(USER, "Src", "[[Java]] then [[java]] again [[JAVA]]");
        assertThat(rowCount(source)).isEqualTo(1);
    }

    @Test
    void rebuildIsDeterministicAndIdempotent() {
        Long source = create(USER, "Src", "[[Alpha]] [[Beta]]");
        assertThat(rowCount(source)).isEqualTo(2);

        // Re-saving identical content wipes and re-inserts the same rows.
        noteService.update(USER, source, new UpdateNoteRequest(null, "[[Alpha]] [[Beta]]", null, null));
        assertThat(rowCount(source)).isEqualTo(2);
    }

    @Test
    void updateRebuildsIndexFromNewContent() {
        Long target = create(USER, "Alpha", "a");
        Long source = create(USER, "Src", "[[Alpha]] [[Beta]]");
        assertThat(rowCount(source)).isEqualTo(2);

        // Drop Beta, keep Alpha, add a new dangling Gamma.
        noteService.update(USER, source, new UpdateNoteRequest(null, "[[Alpha]] [[Gamma]]", null, null));
        assertThat(rowCount(source)).isEqualTo(2);
        assertThat(targetOf(source, "Alpha")).isEqualTo(target);
        assertThat(targetOf(source, "Gamma")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_links WHERE source_note_id = ? AND target_title = 'Beta'",
                Integer.class, source)).isZero();
    }

    @Test
    void resolutionPrefersLatestUpdatedOnDuplicateTitles() {
        Long older = create(USER, "Dup", "first");
        Long newer = create(USER, "Dup", "second");
        Long source = create(USER, "Src", "[[Dup]]");

        assertThat(targetOf(source, "Dup")).isEqualTo(newer).isNotEqualTo(older);
    }

    // --- backlinks ----------------------------------------------------------

    @Test
    void backlinksReturnsResolvedSourcesNewestFirst() {
        Long target = create(USER, "Hub", "hub");
        Long a = create(USER, "A", "[[Hub]]");
        Long b = create(USER, "B", "[[Hub]]");

        List<BacklinkResponse> backlinks = noteService.backlinks(USER, target);
        assertThat(backlinks).extracting(BacklinkResponse::id)
                .containsExactly(String.valueOf(b), String.valueOf(a)); // newest-updated first
    }

    @Test
    void danglingLinkResolvesByTitleOnceTargetExists() {
        // B links to Hub before Hub exists → stored dangling.
        Long b = create(USER, "B", "[[Hub]]");
        Long target = create(USER, "Hub", "hub");

        // B's own row is still dangling (no cross-note rewrite)...
        assertThat(targetOf(b, "Hub")).isNull();
        // ...but the backlinks query resolves it by title.
        assertThat(noteService.backlinks(USER, target))
                .extracting(BacklinkResponse::id)
                .containsExactly(String.valueOf(b));
    }

    @Test
    void backlinksExcludesSelfLink() {
        Long selfRef = create(USER, "Recursive", "I mention [[Recursive]] myself.");
        assertThat(rowCount(selfRef)).isEqualTo(1);                 // stored faithfully
        assertThat(noteService.backlinks(USER, selfRef)).isEmpty(); // but not a backlink of itself
    }

    @Test
    void backlinksEnforcesOwnership() {
        Long note = create(USER, "Owned", "x");

        assertThatThrownBy(() -> noteService.backlinks(OTHER, note))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NoteErrorCode.NOTE_ACCESS_DENIED));

        assertThatThrownBy(() -> noteService.backlinks(USER, 999_999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NoteErrorCode.NOTE_NOT_FOUND));
    }

    // --- delete cleanup -----------------------------------------------------

    @Test
    void deleteRemovesOutgoingLinksAndDetachesInbound() {
        Long target = create(USER, "Target", "t");
        Long source = create(USER, "Source", "[[Target]] [[Ghost]]");
        assertThat(targetOf(source, "Target")).isEqualTo(target);

        // Deleting the source drops its outgoing rows entirely.
        noteService.delete(USER, source);
        assertThat(rowCount(source)).isZero();

        // Re-create the wiring, then delete the *target*: inbound reverts to dangling.
        Long source2 = create(USER, "Source2", "[[Target]]");
        assertThat(targetOf(source2, "Target")).isEqualTo(target);
        noteService.delete(USER, target);
        assertThat(targetOf(source2, "Target")).isNull(); // detached, not deleted

        // A new note with the same title now backlinks source2 via title-fallback.
        Long revived = create(USER, "Target", "again");
        assertThat(noteService.backlinks(USER, revived))
                .extracting(BacklinkResponse::id)
                .containsExactly(String.valueOf(source2));
    }

    // --- helpers ------------------------------------------------------------

    private Long create(Long userId, String title, String content) {
        return Long.valueOf(noteService.create(userId, new CreateNoteRequest(title, content, false, null)).id());
    }

    private int rowCount(Long sourceId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_links WHERE source_note_id = ?", Integer.class, sourceId);
    }

    private Long targetOf(Long sourceId, String title) {
        return jdbcTemplate.queryForObject(
                "SELECT target_note_id FROM note_links WHERE source_note_id = ? AND target_title = ?",
                Long.class, sourceId, title);
    }
}
