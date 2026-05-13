package com.fund.assistant.mapper;

import com.fund.assistant.entity.AiChatHistory;
import com.fund.assistant.vo.AiChatSessionVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AiChatHistoryMapper extends BaseMapper<AiChatHistory> {

    @Select("SELECT session_id AS sessionId, " +
            "COALESCE(MAX(session_name), MIN(question)) AS sessionName, " +
            "MIN(create_time) AS createTime, MAX(create_time) AS updateTime, " +
            "COUNT(*) AS messageCount " +
            "FROM ai_chat_history " +
            "WHERE user_id = #{userId} AND del_flag = 0 " +
            "GROUP BY session_id " +
            "ORDER BY MAX(create_time) DESC")
    List<AiChatSessionVO> selectSessionsByUserId(@Param("userId") Long userId);
}
