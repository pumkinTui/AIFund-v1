package com.fund.assistant.util;

import com.fund.assistant.service.FundInvestPlanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FundInvestPlanSchedule {

    @Autowired
    private FundInvestPlanService fundInvestPlanService;

    /**
     * 每天上午10点执行到期定投计划
     */
    @Scheduled(cron = "0 0 10 * * ?")
    public void executeDuePlans() {
        log.info("定时任务：开始执行到期定投计划");
        try {
            fundInvestPlanService.batchExecuteDuePlans();
            log.info("定时任务：到期定投计划执行完成");
        } catch (Exception e) {
            log.error("定时任务：到期定投计划执行失败", e);
        }
    }
}