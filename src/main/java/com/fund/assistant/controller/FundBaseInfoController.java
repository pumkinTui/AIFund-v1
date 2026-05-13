package com.fund.assistant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fund.assistant.dto.FundQueryDTO;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.service.FundBaseInfoService;
import com.fund.assistant.util.DailyProfitPersistenceSchedule;
import com.fund.assistant.util.FundNavSyncSchedule;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.FundBaseInfoVO;
import com.fund.assistant.vo.FundNetValueVO;
import com.fund.assistant.vo.FundStockHoldVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * fund信息
 */
@Slf4j
@RestController
@RequestMapping("/fund")
public class FundBaseInfoController {

    @Autowired
    private FundBaseInfoService fundBaseInfoService;

    @Autowired
    private FundNavSyncSchedule fundNavSyncSchedule;

    @Autowired
    private DailyProfitPersistenceSchedule dailyProfitPersistenceSchedule;



    /**
     * 分页查询基金列表（支持搜索）
     */
    @PostMapping("/list")
    public Result<IPage<FundBaseInfoVO>> getFundList(@RequestBody FundQueryDTO dto) {
        log.info("要查询的基金信息为：{}",dto);
        IPage<FundBaseInfoVO> fundList = fundBaseInfoService.getFundList(dto);
        return Result.success(fundList);
    }

    /**
     * 根据基金代码查询基金详情
     */
    @GetMapping("/detail/{fundCode}")
    public Result<FundBaseInfoVO> getFundDetail(@PathVariable String fundCode) {
        log.info("基金代码为：{}",fundCode);
        FundBaseInfoVO fundDetail = fundBaseInfoService.getFundDetail(fundCode);
        return Result.success(fundDetail);
    }

    /**
     * 查询基金历史净值
     */
    @GetMapping("/net-value/{fundCode}")
    public Result<List<FundNetValueVO>> getFundNetValueHistory(
            @PathVariable String fundCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        log.info("基金代码为：{} 开始时间为：{} 结束时间为：{}",fundCode,startDate,endDate);
        List<FundNetValueVO> netValueList = fundBaseInfoService.getFundNetValueHistory(fundCode, startDate, endDate);
        return Result.success(netValueList);
    }

    /**
     * 查询基金前十重仓股
     */
    @GetMapping("/stock-hold/{fundCode}")
    public Result<List<FundStockHoldVO>> getFundStockHold(@PathVariable String fundCode) {
        log.info("基金代码为:{}",fundCode);
        List<FundStockHoldVO> stockHoldList = fundBaseInfoService.getFundStockHold(fundCode);
        return Result.success(stockHoldList);
    }

    /**
     * 同步单只基金基础信息
     */
    @PostMapping("/sync/{fundCode}")
    public Result<FundBaseInfo> syncFundBaseInfo(@PathVariable String fundCode) {
        FundBaseInfo fund = fundBaseInfoService.syncFundBaseInfo(fundCode);
        return Result.success(fund);
    }

    /**
     * 手动触发收盘后净值同步（回补遗漏的净值）
     */
    @PostMapping("/admin/sync-official-nav")
    public Result<String> manualSyncOfficialNav() {
        fundNavSyncSchedule.syncOfficialNavs();
        return Result.success("净值同步已触发，请查看日志");
    }

    /**
     * 手动触发每日收益持久化
     */
    @PostMapping("/admin/persist-daily-profit")
    public Result<String> manualPersistDailyProfit() {
        dailyProfitPersistenceSchedule.persistDailyProfitToDb();
        return Result.success("收益持久化已触发，请查看日志");
    }

    /**
     * 批量同步基金基础信息
     */
    @PostMapping("/batch-sync")
    public Result<Void> batchSyncFundBaseInfo(@RequestBody List<String> fundCodeList) {
        fundBaseInfoService.batchSyncFundBaseInfo(fundCodeList);
        return Result.success();
    }
}