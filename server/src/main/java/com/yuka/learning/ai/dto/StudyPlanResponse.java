package com.yuka.learning.ai.dto;

import java.util.List;

/** Parsed directly from the model's JSON output — see {@code PromptTemplate.STUDY_PLAN}. Not persisted (Tasks stays mock-only this phase). */
public record StudyPlanResponse(
        List<String> dailyTasks,
        String weeklyPlan,
        String reviewSchedule,
        String estimatedCompletion,
        List<String> suggestions) {
}
