package com.yuka.ailearningserver.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.ailearningserver.activity.dto.ActivityResponse;
import com.yuka.ailearningserver.activity.entity.ActivityEvent;
import com.yuka.ailearningserver.activity.entity.ActivityType;
import com.yuka.ailearningserver.activity.mapper.ActivityEventMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The write side and read side of the workspace event timeline.
 *
 * <p>{@link #record} is called as a side effect of domain writes (subject
 * created, task completed, …). It is deliberately best-effort and total: a
 * failure to log an activity must never fail the business operation, so callers
 * invoke it after the primary write has succeeded and it swallows nothing the
 * database would reject (the row is always well-formed).
 */
@Service
public class ActivityService {

    /** Hard cap on a single timeline page — the workspace only ever shows a handful. */
    private static final int MAX_TIMELINE = 50;

    private final ActivityEventMapper activityEventMapper;

    public ActivityService(ActivityEventMapper activityEventMapper) {
        this.activityEventMapper = activityEventMapper;
    }

    /** Record an event. {@code subjectId}/{@code refId}/{@code title} may be null. */
    public void record(Long userId, ActivityType type, Long subjectId, Long refId, String title) {
        ActivityEvent event = new ActivityEvent();
        event.setUserId(userId);
        event.setType(type.name());
        event.setSubjectId(subjectId);
        event.setRefId(refId);
        event.setTitle(truncate(title));
        activityEventMapper.insert(event);
    }

    /** Most recent events first, capped for the workspace timeline. */
    public List<ActivityResponse> recent(Long userId, int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_TIMELINE);
        return activityEventMapper.selectList(new LambdaQueryWrapper<ActivityEvent>()
                        .eq(ActivityEvent::getUserId, userId)
                        .orderByDesc(ActivityEvent::getCreatedAt)
                        .last("LIMIT " + capped))
                .stream()
                .map(ActivityResponse::from)
                .toList();
    }

    private static String truncate(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.strip();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }
}
