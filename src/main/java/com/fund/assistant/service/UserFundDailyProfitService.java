package com.fund.assistant.service;

import com.fund.assistant.entity.UserFundDailyProfit;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.vo.UserFundDailyProfitVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 单只基金每日收益明细表 服务类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public interface UserFundDailyProfitService extends IService<UserFundDailyProfit> {

    /**
     * 获取当前用户某只基金的  当日实时收益明细
     */
    UserFundDailyProfitVO getFundDailyProfit(String fundCode);

    /**
     * 获取当前用户所有持仓基金的当日实时收益明细列表（全部持仓，不区分分组）
     */
    List<UserFundDailyProfitVO> getAllFundDailyProfitList();

    /**
     * 获取当前用户指定分组下持仓基金的当日实时收益明细列表
     * @param groupId 持仓分组ID，null 表示全部分组
     */
    List<UserFundDailyProfitVO> getAllFundDailyProfitList(Long groupId);

    /**
     * 查询历史每日基金收益明细
     */
    List<UserFundDailyProfit> getHistoryList(Long userId, LocalDate startDate, LocalDate endDate);

    /**
     * 用官方净值重算单只基金当日收益（收盘后持久化用）
     */
    UserFundDailyProfitVO rebuildWithOfficialNAV(UserFundHold hold,
                                                  BigDecimal officialNAV,
                                                  BigDecimal preCloseNAV,
                                                  BigDecimal officialChangeRate);
}
