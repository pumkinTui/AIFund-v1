package com.fund.assistant.controller;

import com.fund.assistant.service.FundPendingTradeService;
import com.fund.assistant.util.Result;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.FundPendingTradeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user/pending-trade")
public class FundPendingTradeController {

    @Autowired
    private FundPendingTradeService fundPendingTradeService;

    @GetMapping("/list")
    public Result<List<FundPendingTradeVO>> getPendingList(@RequestParam(required = false) Byte tradeType) {
        Long userId = UserContext.getUserId();
        List<FundPendingTradeVO> list = fundPendingTradeService.getPendingList(userId, tradeType);
        return Result.success(list);
    }

    @PostMapping("/cancel/{tradeId}")
    public Result<Void> cancelPendingTrade(@PathVariable Long tradeId) {
        Long userId = UserContext.getUserId();
        fundPendingTradeService.cancelPendingTrade(userId, tradeId);
        return Result.success();
    }
}
