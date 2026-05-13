package com.fund.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 基金基础信息表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("fund_base_info")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FundBaseInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 基金代码
     */
    @TableField("fund_code")
    private String fundCode;

    /**
     * 基金全称
     */
    @TableField("fund_name")
    private String fundName;

    /**
     * 基金简称
     */
    @TableField("fund_short_name")
    private String fundShortName;

    /**
     * 基金类型：股票型/指数型/混合型/债券型
     */
    @TableField("fund_type")
    private String fundType;

    /**
     * 所属板块：光伏/半导体/CPO/纳斯达克
     */
    @TableField("fund_plate")
    private String fundPlate;

    /**
     * 风险等级：1=低 2=中低 3=中 4=中高 5=高
     */
    @TableField("risk_level")
    private Byte riskLevel;

    /**
     * 基金经理
     */
    @TableField("fund_manager")
    private String fundManager;

    /**
     * 基金公司
     */
    @TableField("fund_company")
    private String fundCompany;

    /**
     * 成立日期
     */
    @TableField("establish_date")
    private LocalDate establishDate;

    /**
     * 最新单位净值
     */
    @TableField("latest_net_value")
    private BigDecimal latestNetValue;

    /**
     * 最新涨跌幅(%)
     */
    @TableField("latest_change_rate")
    private BigDecimal latestChangeRate;

    /**
     * 股票仓位比例(%)
     */
    @TableField("stock_position_ratio")
    private BigDecimal stockPositionRatio;

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
