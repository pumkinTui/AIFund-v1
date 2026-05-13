package com.fund.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 * AI对话历史记录表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("ai_chat_history")
public class AiChatHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 会话ID，用于关联多轮对话
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 会话名称，由第一条消息自动生成
     */
    @TableField("session_name")
    private String sessionName;

    /**
     * 用户提问内容
     */
    @TableField("question")
    private String question;

    /**
     * AI回答内容
     */
    @TableField("answer")
    private String answer;

    /**
     * 用户上传的图片URL
     */
    @TableField("image_url")
    private String imageUrl;

    /**
     * 逻辑删除标记：0=正常 1=已删除
     */
    @TableField("del_flag")
    private Byte delFlag;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
