package com.example.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ai.entity.Material;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface MaterialMapper extends BaseMapper<Material> {

    @Update("UPDATE `material` SET `deleted` = 0, `status` = 1, `update_time` = CURRENT_TIMESTAMP "
            + "WHERE `live_session_id` = #{sessionId}")
    int reviveByLiveSessionId(@Param("sessionId") Long sessionId);
}