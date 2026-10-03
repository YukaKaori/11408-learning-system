package com.yuka.learning.task;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.task.dto.CreateTaskRequest;
import com.yuka.learning.task.dto.TaskResponse;
import com.yuka.learning.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * All filters are optional: {@code status} wire name, {@code dueBefore} epoch ms,
     * {@code nodeCode} a syllabus scope (the node and everything beneath it).
     */
    @GetMapping
    public ApiResponse<List<TaskResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long dueBefore,
                                                @RequestParam(required = false) String nodeCode) {
        return ApiResponse.success(taskService.list(principal.id(), status, dueBefore, nodeCode));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @PathVariable Long id) {
        return ApiResponse.success(taskService.get(principal.id(), id));
    }

    @PostMapping
    public ApiResponse<TaskResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.success(taskService.create(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TaskResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable Long id,
                                            @Valid @RequestBody UpdateTaskRequest request) {
        return ApiResponse.success(taskService.update(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        taskService.delete(principal.id(), id);
        return ApiResponse.success();
    }
}
