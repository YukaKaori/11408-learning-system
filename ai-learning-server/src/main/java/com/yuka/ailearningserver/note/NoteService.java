package com.yuka.ailearningserver.note;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.ailearningserver.common.OwnershipGuard;
import com.yuka.ailearningserver.note.dto.BacklinkResponse;
import com.yuka.ailearningserver.note.dto.CreateNoteRequest;
import com.yuka.ailearningserver.note.dto.NoteResponse;
import com.yuka.ailearningserver.note.dto.UpdateNoteRequest;
import com.yuka.ailearningserver.note.entity.Note;
import com.yuka.ailearningserver.note.entity.NoteLink;
import com.yuka.ailearningserver.note.mapper.NoteLinkMapper;
import com.yuka.ailearningserver.note.mapper.NoteMapper;
import com.yuka.ailearningserver.subject.SubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NoteService {

    private final NoteMapper noteMapper;
    private final NoteLinkMapper noteLinkMapper;
    private final SubjectService subjectService;

    public NoteService(NoteMapper noteMapper, NoteLinkMapper noteLinkMapper, SubjectService subjectService) {
        this.noteMapper = noteMapper;
        this.noteLinkMapper = noteLinkMapper;
        this.subjectService = subjectService;
    }

    public List<NoteResponse> list(Long userId) {
        return noteMapper.selectList(new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .orderByDesc(Note::getPinned)
                        .orderByDesc(Note::getUpdatedAt))
                .stream()
                .map(NoteResponse::from)
                .toList();
    }

    public NoteResponse get(Long userId, Long id) {
        return NoteResponse.from(requireOwned(userId, id));
    }

    @Transactional
    public NoteResponse create(Long userId, CreateNoteRequest request) {
        Note note = new Note();
        note.setUserId(userId);
        note.setSubjectId(subjectService.resolveOwnedSubjectId(userId, request.subjectId()));
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setPinned(Boolean.TRUE.equals(request.pinned()));
        noteMapper.insert(note);
        rebuildLinks(note);
        return NoteResponse.from(note);
    }

    @Transactional
    public NoteResponse update(Long userId, Long id, UpdateNoteRequest request) {
        Note note = requireOwned(userId, id);
        if (request.title() != null && !request.title().isBlank()) {
            note.setTitle(request.title());
        }
        if (request.content() != null) {
            note.setContent(request.content());
        }
        if (request.pinned() != null) {
            note.setPinned(request.pinned());
        }
        if (request.subjectId() != null) {
            note.setSubjectId(subjectService.resolveOwnedSubjectId(userId, request.subjectId()));
        }
        noteMapper.updateById(note);
        rebuildLinks(note);
        return NoteResponse.from(note);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Note note = requireOwned(userId, id);
        noteMapper.deleteById(note.getId());
        // Derived index cleanup: drop this note's outgoing links, and revert
        // links that pointed *to* it back to dangling so they re-resolve by
        // title if the note is ever recreated.
        noteLinkMapper.deleteBySourceNoteId(note.getId());
        noteLinkMapper.detachTargetNoteId(note.getId());
    }

    /**
     * Notes that link to the given note, newest first. Ownership of the target
     * is verified first (reusing the note error codes); dangling links are
     * resolved by title so a link made before this note existed still appears.
     */
    public List<BacklinkResponse> backlinks(Long userId, Long id) {
        Note note = requireOwned(userId, id);
        List<Long> sourceIds = noteLinkMapper.findBacklinkSourceIds(userId, note.getId(), note.getTitle());
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Note> byId = noteMapper.selectBatchIds(sourceIds).stream()
                .collect(Collectors.toMap(Note::getId, Function.identity()));
        return sourceIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull) // preserve the mapper's newest-first order
                .map(BacklinkResponse::from)
                .toList();
    }

    /**
     * Rebuilds the derived {@code note_links} rows for one source note from its
     * current markdown: wipe (physical delete) + re-extract distinct targets +
     * resolve each title to an owned note (latest-updated wins, null = dangling).
     * Runs inside the write transaction so the note and its index commit together.
     */
    private void rebuildLinks(Note note) {
        noteLinkMapper.deleteBySourceNoteId(note.getId());
        List<String> targets = NoteLinkExtractor.extract(note.getContent());
        if (targets.isEmpty()) {
            return;
        }
        Map<String, Long> byTitle = resolveTitleIndex(note.getUserId());
        for (String title : targets) {
            NoteLink link = new NoteLink();
            link.setUserId(note.getUserId());
            link.setSourceNoteId(note.getId());
            link.setTargetTitle(title);
            link.setTargetNoteId(byTitle.get(title.toLowerCase(Locale.ROOT)));
            noteLinkMapper.insert(link);
        }
    }

    /** lower(title) -&gt; owning note id, latest-updated (then id-desc) winning ties. */
    private Map<String, Long> resolveTitleIndex(Long userId) {
        List<Note> notes = noteMapper.selectList(new LambdaQueryWrapper<Note>()
                .select(Note::getId, Note::getTitle, Note::getUpdatedAt)
                .eq(Note::getUserId, userId)
                .orderByDesc(Note::getUpdatedAt)
                .orderByDesc(Note::getId));
        Map<String, Long> byTitle = new HashMap<>();
        for (Note note : notes) {
            if (note.getTitle() != null) {
                byTitle.putIfAbsent(note.getTitle().toLowerCase(Locale.ROOT), note.getId());
            }
        }
        return byTitle;
    }

    private Note requireOwned(Long userId, Long id) {
        return OwnershipGuard.require(noteMapper.selectById(id), Note::getUserId, userId,
                NoteErrorCode.NOTE_NOT_FOUND, NoteErrorCode.NOTE_ACCESS_DENIED);
    }
}
