package com.fund.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 * 大盘指数基础信息表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("market_index_info")
public class MarketIndexInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 指数代码
     */
    @TableField("index_code")
    private String indexCode;

    /**
     * 指数名称
     */
    @TableField("index_name")
    private String indexName;

    /**
     * 市场类型：1=A股 2=港股 3=美股
     */
    @TableField("market_type")
    private Byte marketType;

    /**
     * 最新点位
     */
    @TableField("latest_point")
    private BigDecimal latestPoint;

    /**
     * 最新涨跌幅(%)
     */
    @TableField("latest_change_rate")
    private BigDecimal latestChangeRate;

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
