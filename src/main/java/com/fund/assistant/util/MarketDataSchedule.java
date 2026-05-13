package com.fund.assistant.util;

import com.fund.assistant.service.MarketIndexInfoService;
import com.fund.assistant.service.MarketSectorInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 大盘指数行情定时刷新
 * A股/港股：仅 A 股交易时段每 15 分钟刷新
 * 美股：仅美股交易时段（北京时间 21:30-04:00）每 15 分钟刷新
 * 每次刷新覆盖 Redis 原值，48h TTL
 */
@Slf4j
@Component
public class MarketDataSchedule {

    @Autowired
    private MarketIndexInfoService marketIndexInfoService;

    @Autowired
    private MarketSectorInfoService marketSectorInfoService;

    /**
     * A股/港股指数 + 板块排行：仅 A 股交易时段
     */
    @Scheduled(cron = "0 */15 9-15 * * MON-FRI")
    public void refreshChinaMarketData() {
        if (!MarketTimeUtils.isAStockTradingTime()) return;
        try {
            marketIndexInfoService.refreshChinaIndices();
            marketSectorInfoService.refreshSectorRanking();
        } catch (Exception e) {
            log.error("刷新A股/港股指数失败", e);
        }
    }

    /**
     * 美股指数：仅美股交易时段（北京时间 21:30-04:00）
     * 用两个 cron 覆盖跨午夜窗口
     */
    @Scheduled(cron = "0 */15 21-23 ? * MON-FRI")
    public void refreshUSEvening() {
        if (!MarketTimeUtils.isUSStockTradingTime()) return;
        try { marketIndexInfoService.refreshUSIndices(); } catch (Exception e) { log.error("刷新美股指数失败", e); }
    }

    @Scheduled(cron = "0 */15 0-4 ? * MON-FRI")
    public void refreshUSEarlyMorning() {
        if (!MarketTimeUtils.isUSStockTradingTime()) return;
        try { marketIndexInfoService.refreshUSIndices(); } catch (Exception e) { log.error("刷新美股指数失败", e); }
    }
}
