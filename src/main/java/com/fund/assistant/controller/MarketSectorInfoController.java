package com.fund.assistant.controller;

import com.fund.assistant.service.MarketSectorInfoService;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.MarketSectorRealtimeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/market")
public class MarketSectorInfoController {

    @Autowired
    private MarketSectorInfoService marketSectorInfoService;

    /**
     * 获取行业板块涨跌排行（前20）
     */
    @GetMapping("/sector/ranking")
    public Result<List<MarketSectorRealtimeVO>> getSectorRanking() {
        List<MarketSectorRealtimeVO> list = marketSectorInfoService.getSectorRanking();
        return Result.success(list);
    }

    /**
     * 从东方财富同步板块数据
     */
    @PostMapping("/sector/sync")
    public Result<Void> syncSectorData() {
        marketSectorInfoService.syncSectorData();
        return Result.success();
    }
}
