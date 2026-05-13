package com.fund.assistant.service;

import com.fund.assistant.entity.UserDailyProfit;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.vo.UserDailyProfitVO;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 用户每日收益表 服务类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public interface UserDailyProfitService extends IService<UserDailyProfit> {

    /**
     * 获取当前用户当日实时收益总览
     */
    UserDailyProfitVO getDailyProfitOverview();

    /**
     * 查询历史每日收益总览列表  按日期倒序
     */
    List<UserDailyProfit> getHistoryList(Long userId, LocalDate startDate, LocalDate endDate);
}
