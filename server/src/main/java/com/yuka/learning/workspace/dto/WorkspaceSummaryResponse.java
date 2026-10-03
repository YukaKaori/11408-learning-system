package com.yuka.learning.workspace.dto;

import com.yuka.learning.ai.dto.ConversationSummaryResponse;
import com.yuka.learning.analytics.dto.ActivityDayResponse;
import com.yuka.learning.calendar.dto.StudySessionResponse;
import com.yuka.learning.note.entity.Note;
import com.yuka.learning.practice.dto.PracticeSummaryResponse;
import com.yuka.learning.task.dto.TaskResponse;

import java.time.ZoneId;
import java.util.List;

/**
 * Everything Today's Ledger renders, in one round trip (one loading state on
 * the frontend). Section DTOs are reused from their owning modules — the
 * workspace is a read-model façade and must not redefine their wire shapes.
 * Notes are the exception: the ledger only needs a title line, so
 * {@link RecentNote} deliberately omits the (potentially large) content.
 */
public record WorkspaceSummaryResponse(
        Stats stats,
        List<PracticeSummaryResponse> continuePractice,
        List<TaskResponse> upcomingTasks,
        List<ConversationSummaryResponse> recentConversations,
        List<RecentNote> recentNotes,
        List<StudySessionResponse> todaySessions,
        List<ActivityDayResponse> weekActivity) {

    /**
     * Headline figures. {@code dailyGoalMinutes} comes from preferences (or its
     * default); {@code dueCards} is the live actionable review count;
     * {@code dueMistakes} the mistake redos due today.
     */
    public record Stats(
            int streakDays,
            int studiedTodayMinutes,
            int dailyGoalMinutes,
            int dueCards,
            int dueMistakes) {
    }

    /** Slim note row for the ledger list — no content payload. */
    public record RecentNote(String id, String nodeCode, String title, long updatedAt) {

        public static RecentNote from(Note note) {
            return new RecentNote(
                    String.valueOf(note.getId()),
                    note.getNodeCode(),
                    note.getTitle(),
                    note.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
    }
}
