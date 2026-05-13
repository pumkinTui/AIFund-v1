package com.fund.assistant.controller;

import com.fund.assistant.entity.UserFundDailyProfit;
import com.fund.assistant.service.UserFundDailyProfitService;
import com.fund.assistant.util.Result;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.UserFundDailyProfitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 单只基金每日收益明细表 前端控制器
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Slf4j
@RestController
@RequestMapping("/user/fund-daily-profit")
public class UserFundDailyProfitController {

    @Autowired
    private UserFundDailyProfitService userFundDailyProfitService;

    @GetMapping("/{fundCode}")
    public Result<UserFundDailyProfitVO> getFundDailyProfit(@PathVariable String fundCode) {
        log.info("查询基金 {} 当日实时收益", fundCode);
        UserFundDailyProfitVO vo = userFundDailyProfitService.getFundDailyProfit(fundCode);
        return Result.success(vo);
    }

    @GetMapping("/all")
    public Result<List<UserFundDailyProfitVO>> getAllFundDailyProfit(
            @RequestParam(required = false) Long groupId) {
        log.info("查询持仓基金当日实时收益，分组ID：{}", groupId);
        List<UserFundDailyProfitVO> list = userFundDailyProfitService.getAllFundDailyProfitList(groupId);
        return Result.success(list);
    }

    @GetMapping("/history")
    public Result<List<UserFundDailyProfit>> getFundDailyProfitHistory(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate profitDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        Long userId = UserContext.getUserId();
        LocalDate s = startDate != null ? startDate : profitDate;
        LocalDate e = endDate != null ? endDate : profitDate;
        log.info("查询历史基金每日收益明细，范围：{} ~ {}", s, e);
        List<UserFundDailyProfit> list = userFundDailyProfitService.getHistoryList(userId, s, e);
        return Result.success(list);
    }
}
