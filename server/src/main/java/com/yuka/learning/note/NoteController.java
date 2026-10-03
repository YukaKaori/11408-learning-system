package com.yuka.learning.note;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.note.dto.BacklinkResponse;
import com.yuka.learning.note.dto.CreateNoteRequest;
import com.yuka.learning.note.dto.NoteResponse;
import com.yuka.learning.note.dto.UpdateNoteRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public ApiResponse<List<NoteResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(noteService.list(principal.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoteResponse> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.success(noteService.get(principal.id(), id));
    }

    @GetMapping("/{id}/backlinks")
    public ApiResponse<List<BacklinkResponse>> backlinks(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @PathVariable Long id) {
        return ApiResponse.success(noteService.backlinks(principal.id(), id));
    }

    @PostMapping
    public ApiResponse<NoteResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @Valid @RequestBody CreateNoteRequest request) {
        return ApiResponse.success(noteService.create(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<NoteResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable Long id, @Valid @RequestBody UpdateNoteRequest request) {
        return ApiResponse.success(noteService.update(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        noteService.delete(principal.id(), id);
        return ApiResponse.success();
    }
}
