package com.fund.assistant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fund.assistant.dto.FundQueryDTO;
import com.fund.assistant.service.FundBaseInfoService;
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

@Slf4j
@RestController
@RequestMapping("/fund")
public class FundBaseInfoController {

    @Autowired
    private FundBaseInfoService fundBaseInfoService;

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
}