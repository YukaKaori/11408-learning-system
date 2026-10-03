package com.yuka.learning.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuka.learning.question.entity.QuestionAttempt;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface QuestionAttemptMapper extends BaseMapper<QuestionAttempt> {

    /**
     * Every attempt since {@code since}, fanned out to each node its question is
     * tagged with — the raw evidence of the mastery model. One row per
     * (attempt, tag); a question tagged with two 考点 counts as evidence for both,
     * and {@code attempt_id} lets counts see it as one answer.
     */
    @Select("""
            SELECT a.id AS attempt_id, qp.node_code AS node_code, a.result AS result, a.attempted_at AS attempted_at
            FROM question_attempts a
            JOIN question_points qp ON qp.question_id = a.question_id
            WHERE a.user_id = #{userId}
              AND a.deleted = 0
              AND a.attempted_at >= #{since}
            """)
    List<PointAttemptRow> findPointAttemptsSince(@Param("userId") Long userId,
                                                 @Param("since") LocalDateTime since);
}
