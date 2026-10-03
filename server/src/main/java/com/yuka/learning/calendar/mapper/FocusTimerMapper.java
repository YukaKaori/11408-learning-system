package com.yuka.learning.calendar.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuka.learning.calendar.entity.FocusTimer;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface FocusTimerMapper extends BaseMapper<FocusTimer> {

    /**
     * Physically removes the candidate's running timer. A real {@code DELETE},
     * not the inherited {@code @TableLogic} soft delete: the row is unique per
     * user, so a tombstone would block the next timer from ever starting (the
     * same documented exception as {@code note_links}, V7).
     *
     * @return 1 when a timer was running, 0 otherwise
     */
    @Delete("DELETE FROM focus_timers WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
