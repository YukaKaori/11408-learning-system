package com.yuka.learning.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuka.learning.question.entity.Question;
import com.yuka.learning.question.entity.QuestionPoint;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * Active questions the user may practise within a syllabus scope: library
     * content plus the user's own, tagged with the scope node or anything
     * beneath it. Codes are hierarchical and contain no LIKE wildcards
     * ({@code Syllabus} enforces {@code [a-z0-9.-]}), so the prefix match is exact.
     */
    @Select("""
            SELECT DISTINCT q.id
            FROM questions q
            JOIN question_points qp ON qp.question_id = q.id
            WHERE q.deleted = 0
              AND q.status = 0
              AND (q.user_id IS NULL OR q.user_id = #{userId})
              AND (qp.node_code = #{scope} OR qp.node_code LIKE CONCAT(#{scope}, '.%'))
            """)
    List<Long> findVisibleIdsInScope(@Param("userId") Long userId, @Param("scope") String scope);

    /**
     * Every tag of every active question visible to the user — the bank's shape,
     * from which the mastery model counts each question once per node.
     */
    @Select("""
            SELECT qp.question_id AS question_id, qp.node_code AS node_code
            FROM question_points qp
            JOIN questions q ON q.id = qp.question_id
            WHERE q.deleted = 0
              AND q.status = 0
              AND (q.user_id IS NULL OR q.user_id = #{userId})
            """)
    List<QuestionPoint> findVisibleTags(@Param("userId") Long userId);
}
