package com.fund.assistant.controller;

import com.fund.assistant.dto.FundBuyResultDTO;
import com.fund.assistant.dto.FundHoldBuyDTO;
import com.fund.assistant.dto.FundHoldSellDTO;
import com.fund.assistant.service.UserFundHoldService;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.UserFundHoldVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户持仓表 前端控制器
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Slf4j
@RestController
@RequestMapping("/user/hold")
public class UserFundHoldController {

    @Autowired
    private UserFundHoldService userFundHoldService;

    /**
     * 基金买入
     */
    @PostMapping("/buy")
    public Result<FundBuyResultDTO> buyFund(@Validated @RequestBody FundHoldBuyDTO dto) {
        log.info("用户买入基金的信息：{}",dto);
        FundBuyResultDTO result = userFundHoldService.buyFund(dto);
        return Result.success(result);
    }

    /**
     * 基金卖出
     */
    @PostMapping("/sell")
    public Result<Void> sellFund(@Validated @RequestBody FundHoldSellDTO dto) {
        log.info("用户卖出基金的信息：{}",dto);
        userFundHoldService.sellFund(dto);
        return Result.success();
    }

    /**
     * 查询当前用户所有持仓（按分组）
     */
    @GetMapping("/list")
    public Result<List<UserFundHoldVO>> getHoldList(@RequestParam(required = false) Long groupId) {
        log.info("查询用户持仓分组id：{}",groupId);
        List<UserFundHoldVO> holdList = userFundHoldService.getHoldList(groupId);
        return Result.success(holdList);
    }

    /**
     * 查询单只基金的持仓详情
     */
    @GetMapping("/detail/{fundCode}")
    public Result<UserFundHoldVO> getHoldDetail(@PathVariable String fundCode) {
        log.info("查询单只基金的持仓代码：{}",fundCode);
        UserFundHoldVO holdDetail = userFundHoldService.getHoldDetail(fundCode);
        return Result.success(holdDetail);
    }

}
