package com.yuka.ailearningserver.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuka.ailearningserver.subject.dto.SubjectStudyStats;
import com.yuka.ailearningserver.subject.entity.Subject;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SubjectMapper extends BaseMapper<Subject> {

    /**
     * Study minutes and last-studied time per subject for one user, derived from
     * {@code study_sessions}. Duration is computed in SQL (never stored), so this
     * is the single source of truth for a subject's study time. Subjects with no
     * sessions simply do not appear in the result and default to zero / null.
     */
    @Select("""
            SELECT subject_id                                                 AS subjectId,
                   COALESCE(SUM(TIMESTAMPDIFF(MINUTE, starts_at, ends_at)), 0) AS studyMinutes,
                   MAX(ends_at)                                               AS lastStudiedAt
            FROM study_sessions
            WHERE user_id = #{userId}
              AND deleted = 0
              AND subject_id IS NOT NULL
            GROUP BY subject_id
            """)
    List<SubjectStudyStats> studyStatsByUser(@Param("userId") Long userId);
}
