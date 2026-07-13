package com.yuka.ailearningserver.subject;

import com.yuka.ailearningserver.auth.security.AuthenticatedUser;
import com.yuka.ailearningserver.common.api.ApiResponse;
import com.yuka.ailearningserver.subject.dto.CreateSubjectRequest;
import com.yuka.ailearningserver.subject.dto.SubjectResponse;
import com.yuka.ailearningserver.subject.dto.UpdateSubjectRequest;
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
@RequestMapping("/api/v1/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public ApiResponse<List<SubjectResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(subjectService.list(principal.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<SubjectResponse> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable Long id) {
        return ApiResponse.success(subjectService.get(principal.id(), id));
    }

    @PostMapping
    public ApiResponse<SubjectResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody CreateSubjectRequest request) {
        return ApiResponse.success(subjectService.create(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SubjectResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable Long id,
                                               @Valid @RequestBody UpdateSubjectRequest request) {
        return ApiResponse.success(subjectService.update(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        subjectService.delete(principal.id(), id);
        return ApiResponse.success();
    }
}
