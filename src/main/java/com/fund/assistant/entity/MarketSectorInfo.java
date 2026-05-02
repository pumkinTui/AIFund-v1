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
 * 行业板块资金&行情表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("market_sector_info")
public class MarketSectorInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 板块代码
     */
    @TableField("sector_code")
    private String sectorCode;

    /**
     * 板块名称
     */
    @TableField("sector_name")
    private String sectorName;

    /**
     * 当日涨跌幅(%)
     */
    @TableField("daily_change_rate")
    private BigDecimal dailyChangeRate;

    /**
     * 资金净流入/流出(亿元)：正=流入 负=流出
     */
    @TableField("capital_flow")
    private BigDecimal capitalFlow;

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
