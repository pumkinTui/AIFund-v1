package com.fund.assistant.controller;

import com.fund.assistant.service.FundRealtimeValuationService;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.FundRealtimeValuationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 基金实时估值结果表 前端控制器
 * </p>
 *
 * @author jhshen
 * @since 2026-05-05
 */
@Slf4j
@RestController
@RequestMapping("/fund/realtime-valuation")
public class FundRealtimeValuationController {

    @Autowired
    private FundRealtimeValuationService fundRealtimeValuationService;

    /**
     * 实时计算并返回单只基金估值
     */
    @GetMapping("/calculate/{fundCode}")
    public Result<FundRealtimeValuationVO> calculateValuation(@PathVariable String fundCode) {
        log.info("要计算的基金代码是：{}",fundCode);
        FundRealtimeValuationVO vo = fundRealtimeValuationService.calculateFundRealtimeValuation(fundCode);
        return Result.success(vo);
    }

    /**
     * 查询单只基金最新估值（优先查缓存）
     */
    @GetMapping("/latest/{fundCode}")
    public Result<FundRealtimeValuationVO> getLatestValuation(@PathVariable String fundCode) {
        FundRealtimeValuationVO vo = fundRealtimeValuationService.getLatestValuationByFundCode(fundCode);
        return Result.success(vo);
    }

    /**
     * 查询用户自选基金实时估值列表
     */
    @GetMapping("/favorite")
    public Result<List<FundRealtimeValuationVO>> getFavoriteValuationList() {
        List<FundRealtimeValuationVO> list = fundRealtimeValuationService.getFavoriteFundValuationList();
        return Result.success(list);
    }

    /**
     * 查询用户持仓基金实时估值列表
     */
    @GetMapping("/hold")
    public Result<List<FundRealtimeValuationVO>> getHoldValuationList() {
        List<FundRealtimeValuationVO> list = fundRealtimeValuationService.getHoldFundValuationList();
        return Result.success(list);
    }

    /**
     * 手动触发全量基金估值（定时任务用）
     */
    @PostMapping("/batch-calculate")
    public Result<Void> batchCalculateValuation() {
        fundRealtimeValuationService.batchCalculateAllFundValuation();
        return Result.success();
    }

    /**
     * 用户主动刷新某只基金的实时估值
     * 不走缓存，强制重算，同时更新 Redis 中的走势数据
     */
    @PostMapping("/refresh/{fundCode}")
    public Result<FundRealtimeValuationVO> refreshValuation(@PathVariable String fundCode) {
        log.info("用户主动刷新基金估值：{}", fundCode);
        FundRealtimeValuationVO vo = fundRealtimeValuationService.calculateFundRealtimeValuation(fundCode);
        return Result.success(vo);
    }

    /**
     * 获取某只基金当天走势图数据
     * 从 Redis 读取全天每15分钟的估值快照，按时间正序返回
     */
    @GetMapping("/timeline/{fundCode}")
    public Result<List<FundRealtimeValuationVO>> getTimeline(@PathVariable String fundCode) {
        List<FundRealtimeValuationVO> list = fundRealtimeValuationService.getTimeline(fundCode);
        return Result.success(list);
    }
}
