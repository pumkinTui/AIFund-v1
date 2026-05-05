package com.fund.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 股票基础信息表
 * </p>
 *
 * @author jhshen
 * @since 2026-05-05
 */
@Getter
@Setter
@TableName("stock_base_info")
public class StockBaseInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 股票名称
     */
    private String stockName;

    /**
     * 所属市场：SH=上交所 SZ=深交所 HK=港股 US=美股
     */
    private String stockMarket;

    /**
     * 所属行业
     */
    private String industry;

    /**
     * 上市日期
     */
    private LocalDate listingDate;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
