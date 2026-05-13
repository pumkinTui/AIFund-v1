package com.fund.assistant.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.entity.FundPendingTrade;
import com.fund.assistant.mapper.FundPendingTradeMapper;
import com.fund.assistant.service.UserFundHoldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class PendingTradeConfirmSchedule {

    @Autowired
    private UserFundHoldService userFundHoldService;

    @Autowired
    private FundPendingTradeMapper fundPendingTradeMapper;

    @Scheduled(cron = "0 0 16 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void confirmPendingTrades() {
        log.info("开始处理今日待确认交易");
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<FundPendingTrade> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundPendingTrade::getConfirmDate, today);
        wrapper.eq(FundPendingTrade::getStatus, 0);
        List<FundPendingTrade> pendingList = fundPendingTradeMapper.selectList(wrapper);

        if (pendingList.isEmpty()) {
            log.info("今日无待确认交易");
            return;
        }

        int success = 0, fail = 0;
        for (FundPendingTrade trade : pendingList) {
            try {
                userFundHoldService.confirmPendingTrade(trade);
                success++;
                log.info("交易确认成功，ID：{}，基金：{}", trade.getId(), trade.getFundCode());
            } catch (Exception e) {
                fail++;
                log.error("交易确认失败，ID：{}，错误：", trade.getId(), e);
            }
        }

        log.info("今日待确认交易处理完成，共处理：{} 条，成功：{}，失败：{}", pendingList.size(), success, fail);
    }
}
