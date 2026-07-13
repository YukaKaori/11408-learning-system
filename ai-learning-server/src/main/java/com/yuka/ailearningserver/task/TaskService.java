package com.yuka.ailearningserver.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.ailearningserver.activity.ActivityService;
import com.yuka.ailearningserver.activity.entity.ActivityType;
import com.yuka.ailearningserver.common.exception.BusinessException;
import com.yuka.ailearningserver.task.dto.CreateTaskRequest;
import com.yuka.ailearningserver.task.dto.TaskResponse;
import com.yuka.ailearningserver.task.dto.UpdateTaskRequest;
import com.yuka.ailearningserver.task.entity.LearningTask;
import com.yuka.ailearningserver.task.entity.TaskPriority;
import com.yuka.ailearningserver.task.entity.TaskStatus;
import com.yuka.ailearningserver.task.mapper.LearningTaskMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Learning-task CRUD. Two lifecycle rules live here and nowhere else:
 * {@code completedAt} is stamped the moment a task first becomes {@code DONE}
 * (and cleared if it is re-opened), and crossing into {@code DONE} emits a
 * {@link ActivityType#TASK_COMPLETED} timeline event exactly once.
 */
@Service
public class TaskService {

    private final LearningTaskMapper taskMapper;
    private final ActivityService activityService;

    public TaskService(LearningTaskMapper taskMapper, ActivityService activityService) {
        this.taskMapper = taskMapper;
        this.activityService = activityService;
    }

    public List<TaskResponse> list(Long userId, String status, Long subjectId, Long dueFrom, Long dueTo) {
        LambdaQueryWrapper<LearningTask> query = new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId);
        if (status != null && !status.isBlank()) {
            query.eq(LearningTask::getStatus, parseStatus(status));
        }
        if (subjectId != null) {
            query.eq(LearningTask::getSubjectId, subjectId);
        }
        if (dueFrom != null) {
            query.ge(LearningTask::getDueAt, toLocalDateTime(dueFrom));
        }
        if (dueTo != null) {
            query.le(LearningTask::getDueAt, toLocalDateTime(dueTo));
        }
        query.orderByAsc(LearningTask::getDueAt).orderByDesc(LearningTask::getCreatedAt);
        return taskMapper.selectList(query).stream().map(TaskResponse::from).toList();
    }

    public TaskResponse create(Long userId, CreateTaskRequest request) {
        LearningTask task = new LearningTask();
        task.setUserId(userId);
        task.setSubjectId(request.subjectId());
        task.setTitle(request.title().strip());
        task.setDescription(request.description());
        task.setStatus(TaskStatus.TODO);
        task.setPriority(request.priority() != null ? parsePriority(request.priority()) : TaskPriority.MEDIUM);
        task.setDueAt(toLocalDateTime(request.dueAt()));
        taskMapper.insert(task);
        return TaskResponse.from(task);
    }

    public TaskResponse update(Long userId, Long id, UpdateTaskRequest request) {
        LearningTask task = requireOwned(userId, id);
        boolean wasDone = task.getStatus() == TaskStatus.DONE;
        if (request.subjectId() != null) {
            task.setSubjectId(request.subjectId());
        }
        if (request.title() != null && !request.title().isBlank()) {
            task.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.priority() != null) {
            task.setPriority(parsePriority(request.priority()));
        }
        if (Boolean.TRUE.equals(request.clearDueAt())) {
            task.setDueAt(null);
        } else if (request.dueAt() != null) {
            task.setDueAt(toLocalDateTime(request.dueAt()));
        }
        if (request.status() != null) {
            TaskStatus next = parseStatus(request.status());
            task.setStatus(next);
            task.setCompletedAt(next == TaskStatus.DONE ? LocalDateTime.now() : null);
        }
        taskMapper.updateById(task);
        boolean nowDone = task.getStatus() == TaskStatus.DONE;
        if (!wasDone && nowDone) {
            activityService.record(userId, ActivityType.TASK_COMPLETED, task.getSubjectId(), task.getId(),
                    task.getTitle());
        }
        return TaskResponse.from(task);
    }

    public void delete(Long userId, Long id) {
        LearningTask task = requireOwned(userId, id);
        taskMapper.deleteById(task.getId());
    }

    private static LocalDateTime toLocalDateTime(Long epochMillis) {
        return epochMillis != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                : null;
    }

    private static TaskStatus parseStatus(String token) {
        return switch (token) {
            case "todo" -> TaskStatus.TODO;
            case "inProgress" -> TaskStatus.IN_PROGRESS;
            case "done" -> TaskStatus.DONE;
            default -> throw new BusinessException(TaskErrorCode.TASK_INVALID_STATUS);
        };
    }

    private static TaskPriority parsePriority(String token) {
        try {
            return TaskPriority.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(TaskErrorCode.TASK_INVALID_PRIORITY);
        }
    }

    private LearningTask requireOwned(Long userId, Long id) {
        LearningTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(TaskErrorCode.TASK_NOT_FOUND);
        }
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(TaskErrorCode.TASK_ACCESS_DENIED);
        }
        return task;
    }
}
