package com.fund.assistant.util;

import com.fund.assistant.service.FundRealtimeValuationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FundValuationSchedule {

    @Autowired
    private FundRealtimeValuationService fundRealtimeValuationService;

    /**
     * 批量基金估值
     * 盘中的交易时间：每15分钟执行一次（周一到周五 9:30-15:00）
     */
    @Scheduled(cron = "0 */15 9-15 * * MON-FRI")
    public void batchValuation() {
        if (!MarketTimeUtils.isAStockTradingTime()) {
            log.info("非交易时段，跳过批量估值");
            return;
        }
        log.info("开始批量基金估值");
        try {
            fundRealtimeValuationService.batchCalculateAllFundValuation();
            log.info("批量基金估值完成");
        } catch (Exception e) {
            log.error("批量基金估值失败", e);
        }
    }
}