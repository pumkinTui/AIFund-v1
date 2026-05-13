package com.fund.assistant.controller;

import com.fund.assistant.service.MarketIndexInfoService;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.MarketIndexRealtimeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/market")
public class MarketIndexInfoController {

    @Autowired
    private MarketIndexInfoService marketIndexInfoService;

    /**
     * 获取大盘指数实时行情（上证、深证、创业板、科创50）
     */
    @GetMapping("/index/realtime")
    public Result<List<MarketIndexRealtimeVO>> getRealtimeQuotes() {
        List<MarketIndexRealtimeVO> list = marketIndexInfoService.getRealtimeQuotes();
        return Result.success(list);
    }
}
