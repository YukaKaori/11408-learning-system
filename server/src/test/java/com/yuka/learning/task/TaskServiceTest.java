package com.yuka.learning.task;

import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamErrorCode;
import com.yuka.learning.task.dto.CreateTaskRequest;
import com.yuka.learning.task.dto.TaskResponse;
import com.yuka.learning.task.dto.UpdateTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task behavior: per-user isolation, filters, the completedAt lifecycle owned
 * by status transitions, and validated optional syllabus anchoring.
 */
@SpringBootTest
@ActiveProfiles("test")
class TaskServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;

    @Autowired
    private TaskService taskService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        for (String table : List.of("learning_tasks")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @Test
    void crudRoundTrip() {
        TaskResponse created = taskService.create(USER,
                new CreateTaskRequest("Finish chapter 4", "exercises 1-10", null, null, null));
        assertThat(created.status()).isEqualTo("todo");
        assertThat(created.priority()).isEqualTo("medium");
        assertThat(created.dueAt()).isNull();
        assertThat(created.completedAt()).isNull();

        long due = Instant.now().plusSeconds(86400).toEpochMilli();
        TaskResponse updated = taskService.update(USER, Long.valueOf(created.id()),
                new UpdateTaskRequest("Finish chapter 5", null, "inProgress", "high", due, null));
        assertThat(updated.title()).isEqualTo("Finish chapter 5");
        assertThat(updated.status()).isEqualTo("inProgress");
        assertThat(updated.priority()).isEqualTo("high");
        assertThat(updated.dueAt()).isNotNull();

        assertThat(taskService.list(USER, null, null, null)).hasSize(1);
        taskService.delete(USER, Long.valueOf(created.id()));
        assertThat(taskService.list(USER, null, null, null)).isEmpty();
    }

    @Test
    void statusTransitionOwnsCompletedAt() {
        TaskResponse task = taskService.create(USER,
                new CreateTaskRequest("Review notes", null, null, null, null));
        Long id = Long.valueOf(task.id());

        TaskResponse done = taskService.update(USER, id,
                new UpdateTaskRequest(null, null, "done", null, null, null));
        assertThat(done.status()).isEqualTo("done");
        assertThat(done.completedAt()).isNotNull();

        TaskResponse reopened = taskService.update(USER, id,
                new UpdateTaskRequest(null, null, "todo", null, null, null));
        assertThat(reopened.status()).isEqualTo("todo");
        assertThat(reopened.completedAt()).isNull();
        // Cleared value must also be persisted, not just mutated in memory.
        assertThat(taskService.get(USER, id).completedAt()).isNull();
    }

    @Test
    void listFilters() {
        long tomorrow = Instant.now().plusSeconds(86400).toEpochMilli();
        long nextWeek = Instant.now().plusSeconds(7 * 86400).toEpochMilli();

        TaskResponse dueSoon = taskService.create(USER,
                new CreateTaskRequest("Due soon", null, "high", tomorrow, "math1.linear.eigen.similar"));
        taskService.create(USER, new CreateTaskRequest("Due later", null, null, nextWeek, null));
        TaskResponse backlog = taskService.create(USER,
                new CreateTaskRequest("Backlog", null, null, null, null));
        taskService.update(USER, Long.valueOf(backlog.id()),
                new UpdateTaskRequest(null, null, "done", null, null, null));

        assertThat(taskService.list(USER, "todo", null, null)).hasSize(2);
        assertThat(taskService.list(USER, "done", null, null)).hasSize(1);
        assertThat(taskService.list(USER, null, Instant.now().plusSeconds(2 * 86400).toEpochMilli(), null))
                .extracting(TaskResponse::id).containsExactly(dueSoon.id());
        // The anchor filter is a scope: a task on a 考点 is found from its module and paper.
        assertThat(taskService.list(USER, null, null, "math1.linear"))
                .extracting(TaskResponse::id).containsExactly(dueSoon.id());
        assertThat(taskService.list(USER, null, null, "math1"))
                .extracting(TaskResponse::id).containsExactly(dueSoon.id());
        assertThat(taskService.list(USER, null, null, "cs408")).isEmpty();
        assertThatThrownBy(() -> taskService.list(USER, "blocked", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TaskErrorCode.TASK_STATUS_INVALID));
    }

    @Test
    void dueAtClearSentinelUnschedules() {
        long due = Instant.now().plusSeconds(3600).toEpochMilli();
        TaskResponse task = taskService.create(USER,
                new CreateTaskRequest("Scheduled", null, null, due, null));
        assertThat(task.dueAt()).isNotNull();

        taskService.update(USER, Long.valueOf(task.id()),
                new UpdateTaskRequest(null, null, null, null, 0L, null));
        assertThat(taskService.get(USER, Long.valueOf(task.id())).dueAt()).isNull();
    }

    @Test
    void crossUserAccessIsDenied() {
        TaskResponse task = taskService.create(USER,
                new CreateTaskRequest("Private", null, null, null, null));
        assertThatThrownBy(() -> taskService.get(OTHER_USER, Long.valueOf(task.id())))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TaskErrorCode.TASK_ACCESS_DENIED));
        assertThat(taskService.list(OTHER_USER, null, null, null)).isEmpty();
    }

    @Test
    void syllabusAnchorIsValidatedAndClearable() {
        assertThatThrownBy(() -> taskService.create(USER,
                new CreateTaskRequest("Anchored", null, null, null, "politics.no-such-module")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ExamErrorCode.NODE_NOT_FOUND));

        // Any depth is a valid anchor: a whole paper as well as a single 考点.
        TaskResponse paper = taskService.create(USER,
                new CreateTaskRequest("Paper-wide", null, null, null, "english1"));
        assertThat(paper.nodeCode()).isEqualTo("english1");

        TaskResponse task = taskService.create(USER,
                new CreateTaskRequest("Anchored", null, null, null, "cs408.cn.transport.tcp-congestion"));
        assertThat(task.nodeCode()).isEqualTo("cs408.cn.transport.tcp-congestion");

        taskService.update(USER, Long.valueOf(task.id()),
                new UpdateTaskRequest(null, null, null, null, null, ""));
        assertThat(taskService.get(USER, Long.valueOf(task.id())).nodeCode()).isNull();
    }
}
