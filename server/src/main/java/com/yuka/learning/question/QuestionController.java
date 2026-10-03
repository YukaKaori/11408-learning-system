package com.yuka.learning.question;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.common.api.PageResponse;
import com.yuka.learning.question.dto.QuestionDetailResponse;
import com.yuka.learning.question.dto.QuestionRequest;
import com.yuka.learning.question.dto.QuestionResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The question bank. Deleting is deliberately absent: a candidate's own
 * questions leave the system through the mistake book they were captured into
 * ({@code DELETE /v1/mistakes/{id}}), and library questions leave through their
 * content pack.
 */
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /**
     * @param nodeCode optional syllabus scope (the node and everything beneath it)
     * @param origin   {@code library} or {@code mine}; omitted = both
     */
    @GetMapping
    public ApiResponse<PageResponse<QuestionResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @RequestParam(required = false) String nodeCode,
                                                            @RequestParam(required = false) String origin,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(questionService.list(principal.id(), nodeCode, origin, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable Long id) {
        return ApiResponse.success(questionService.detail(principal.id(), id));
    }

    @PostMapping
    public ApiResponse<QuestionResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @Valid @RequestBody QuestionRequest request) {
        return ApiResponse.success(questionService.create(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable Long id,
                                                @Valid @RequestBody QuestionRequest request) {
        return ApiResponse.success(questionService.update(principal.id(), id, request));
    }
}
