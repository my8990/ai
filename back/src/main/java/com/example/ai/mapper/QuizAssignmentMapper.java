package com.example.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ai.entity.QuizAssignment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface QuizAssignmentMapper extends BaseMapper<QuizAssignment> {

    /**
     * 物理删除发布记录，避免逻辑删除行继续占用 (quiz_id, course_id) 唯一键。
     */
    @Delete("DELETE FROM quiz_assignment WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}