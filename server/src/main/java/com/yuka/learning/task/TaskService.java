package com.yuka.learning.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.OwnershipGuard;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.task.dto.CreateTaskRequest;
import com.yuka.learning.task.dto.TaskResponse;
import com.yuka.learning.task.dto.UpdateTaskRequest;
import com.yuka.learning.task.entity.LearningTask;
import com.yuka.learning.task.entity.TaskPriority;
import com.yuka.learning.task.entity.TaskStatus;
import com.yuka.learning.task.mapper.LearningTaskMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Learning-task CRUD. {@code completedAt} is owned by the status transition
 * (set when a task becomes {@code done}, cleared when it leaves {@code done})
 * and is never written directly by clients.
 */
@Service
public class TaskService {

    private final LearningTaskMapper taskMapper;
    private final Syllabus syllabus;

    public TaskService(LearningTaskMapper taskMapper, Syllabus syllabus) {
        this.taskMapper = taskMapper;
        this.syllabus = syllabus;
    }

    public List<TaskResponse> list(Long userId, String status, Long dueBefore, String nodeCode) {
        LambdaQueryWrapper<LearningTask> query = new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId);
        if (status != null) {
            query.eq(LearningTask::getStatus, parseStatus(status));
        }
        if (dueBefore != null) {
            query.le(LearningTask::getDueAt, toLocalDateTime(dueBefore));
        }
        if (nodeCode != null && !nodeCode.isBlank()) {
            // A scope, not an exact match: tasks anchored anywhere beneath it.
            String scope = syllabus.require(nodeCode.trim()).code();
            query.and(q -> q.eq(LearningTask::getNodeCode, scope)
                    .or().likeRight(LearningTask::getNodeCode, scope + "."));
        }
        query.orderByAsc(LearningTask::getStatus)
                .orderByAsc(LearningTask::getDueAt)
                .orderByDesc(LearningTask::getCreatedAt);
        return taskMapper.selectList(query).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public TaskResponse get(Long userId, Long id) {
        return TaskResponse.from(requireOwned(userId, id));
    }

    public TaskResponse create(Long userId, CreateTaskRequest request) {
        LearningTask task = new LearningTask();
        task.setUserId(userId);
        task.setNodeCode(syllabus.resolve(request.nodeCode()));
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(TaskStatus.TODO);
        task.setPriority(request.priority() != null ? parsePriority(request.priority()) : TaskPriority.MEDIUM);
        task.setDueAt(toLocalDateTime(request.dueAt()));
        taskMapper.insert(task);
        return TaskResponse.from(task);
    }

    public TaskResponse update(Long userId, Long id, UpdateTaskRequest request) {
        LearningTask task = requireOwned(userId, id);
        if (request.title() != null && !request.title().isBlank()) {
            task.setTitle(request.title());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.status() != null) {
            TaskStatus next = parseStatus(request.status());
            if (next == TaskStatus.DONE && task.getStatus() != TaskStatus.DONE) {
                task.setCompletedAt(LocalDateTime.now());
            } else if (next != TaskStatus.DONE && task.getStatus() == TaskStatus.DONE) {
                task.setCompletedAt(null);
            }
            task.setStatus(next);
        }
        if (request.priority() != null) {
            task.setPriority(parsePriority(request.priority()));
        }
        if (request.dueAt() != null) {
            task.setDueAt(request.dueAt() == 0 ? null : toLocalDateTime(request.dueAt()));
        }
        if (request.nodeCode() != null) {
            task.setNodeCode(syllabus.resolve(request.nodeCode()));
        }
        taskMapper.updateById(task);
        return TaskResponse.from(task);
    }

    public void delete(Long userId, Long id) {
        LearningTask task = requireOwned(userId, id);
        taskMapper.deleteById(task.getId());
    }

    private LearningTask requireOwned(Long userId, Long id) {
        return OwnershipGuard.require(taskMapper.selectById(id), LearningTask::getUserId, userId,
                TaskErrorCode.TASK_NOT_FOUND, TaskErrorCode.TASK_ACCESS_DENIED);
    }

    private static TaskStatus parseStatus(String value) {
        return switch (value) {
            case "todo" -> TaskStatus.TODO;
            case "inProgress" -> TaskStatus.IN_PROGRESS;
            case "done" -> TaskStatus.DONE;
            default -> throw new BusinessException(TaskErrorCode.TASK_STATUS_INVALID);
        };
    }

    private static TaskPriority parsePriority(String value) {
        return switch (value) {
            case "low" -> TaskPriority.LOW;
            case "medium" -> TaskPriority.MEDIUM;
            case "high" -> TaskPriority.HIGH;
            default -> throw new BusinessException(TaskErrorCode.TASK_PRIORITY_INVALID);
        };
    }

    private static LocalDateTime toLocalDateTime(Long epochMilli) {
        return epochMilli != null
                ? Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null;
    }
}
