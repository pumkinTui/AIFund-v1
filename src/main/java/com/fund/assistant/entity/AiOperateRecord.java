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
 * AI指令操作记录表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("ai_operate_record")
public class AiOperateRecord implements Serializable {

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
     * 操作类型：1=加仓 2=减仓 3=定投配置 4=发布帖子
     */
    @TableField("operate_type")
    private Byte operateType;

    /**
     * 操作方案详情
     */
    @TableField("operate_content")
    private String operateContent;

    /**
     * 用户是否确认：0=未确认 1=已确认 2=已取消
     */
    @TableField("is_confirmed")
    private Byte isConfirmed;

    /**
     * 是否执行完成：0=未执行 1=成功 2=失败
     */
    @TableField("is_executed")
    private Byte isExecuted;

    /**
     * 执行失败原因
     */
    @TableField("fail_reason")
    private String failReason;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
