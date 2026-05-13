package com.fund.assistant.controller;

import com.fund.assistant.entity.UserDailyProfit;
import com.fund.assistant.service.UserDailyProfitService;
import com.fund.assistant.util.Result;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.UserDailyProfitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 用户每日收益表 前端控制器
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Slf4j
@RestController
@RequestMapping("/user/daily-profit")
public class UserDailyProfitController {

    @Autowired
    private UserDailyProfitService userDailyProfitService;

    @GetMapping("/overview")
    public Result<UserDailyProfitVO> getDailyProfitOverview() {
        log.info("查询用户当日实时收益总览");
        UserDailyProfitVO vo = userDailyProfitService.getDailyProfitOverview();
        return Result.success(vo);
    }

    @GetMapping("/history")
    public Result<List<UserDailyProfit>> getDailyProfitHistory(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        log.info("查询历史每日收益总览，开始日期：{}，结束日期：{}", startDate, endDate);
        Long userId = UserContext.getUserId();
        List<UserDailyProfit> list = userDailyProfitService.getHistoryList(userId, startDate, endDate);
        return Result.success(list);
    }
}
