package com.yuka.learning.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuka.learning.question.entity.QuestionPoint;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface QuestionPointMapper extends BaseMapper<QuestionPoint> {

    /**
     * Physically deletes a question's tags before they are rebuilt — a real
     * {@code DELETE}, not the inherited soft delete, because a derived
     * association must not accumulate tombstones (same rule as
     * {@code NoteLinkMapper.deleteBySourceNoteId}).
     */
    @Delete("DELETE FROM question_points WHERE question_id = #{questionId}")
    int deleteByQuestionId(@Param("questionId") Long questionId);
}
