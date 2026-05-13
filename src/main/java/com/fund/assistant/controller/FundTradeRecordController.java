package com.fund.assistant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fund.assistant.dto.TradeQueryDTO;
import com.fund.assistant.service.FundTradeRecordService;
import com.fund.assistant.util.Result;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.FundHoldProfitVO;
import com.fund.assistant.vo.TradeRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 基金交易记录表 前端控制器
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Slf4j
@RestController
@RequestMapping("/user/trade")
public class FundTradeRecordController {


    @Autowired
    private FundTradeRecordService fundTradeRecordService;

    /**
     * 分页查询交易记录（支持筛选）
     */
    @PostMapping("/page")
    public Result<IPage<TradeRecordVO>> getTradeRecordPage(@RequestBody TradeQueryDTO dto) {
        log.info("分页查询交易记录的信息为：{}",dto);
        IPage<TradeRecordVO> page = fundTradeRecordService.getTradeRecordPage(dto);
        return Result.success(page);
    }

    /**
     * 查询所有交易记录（不分页，用于导出）
     */
    @PostMapping("/list")
    public Result<List<TradeRecordVO>> getTradeRecordList(@RequestBody TradeQueryDTO dto) {
        log.info("分页查询交易记录的信息为：{}",dto);
        List<TradeRecordVO> list = fundTradeRecordService.getTradeRecordList(dto);
        return Result.success(list);
    }

    /**
     * 查询单只基金的收益明细
     */
    @GetMapping("/profit/{fundCode}")
    public Result<FundHoldProfitVO> getFundProfitDetail(@PathVariable String fundCode , @RequestParam(required = false) Long groupId) {
        log.info("要查询：{}只基金的收益明细",fundCode);
        FundHoldProfitVO profitDetail = fundTradeRecordService.getFundProfitDetail(fundCode,groupId);
        return Result.success(profitDetail);
    }

    /**
     * 查询所有持仓基金的收益明细
     */
    @GetMapping("/profit/all")
    public Result<List<FundHoldProfitVO>> getAllHoldProfitList() {
        List<FundHoldProfitVO> profitList = fundTradeRecordService.getAllHoldProfitList();
        return Result.success(profitList);
    }

    /**
     * 删除交易记录
     */
    @PostMapping("/delete/{tradeId}")
    public Result<Void> deleteTradeRecord(@PathVariable Long tradeId) {
        Long userId = UserContext.getUserId();
        fundTradeRecordService.deleteTradeRecord(userId, tradeId);
        return Result.success();
    }
}
