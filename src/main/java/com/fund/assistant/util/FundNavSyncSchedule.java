package com.fund.assistant.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.mapper.UserFundHoldMapper;
import com.fund.assistant.service.FundBaseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 收盘后从东方财富同步基金官方净值
 * 20:30 起每 30 分钟重试，直到净值公布
 * 同步后更新 fund_net_value_history + fund_base_info，为 DailyProfitPersistenceSchedule 提供数据基础
 */
@Slf4j
@Component
public class FundNavSyncSchedule {

    @Autowired
    private UserFundHoldMapper userFundHoldMapper;

    @Autowired
    private FundBaseInfoService fundBaseInfoService;

    /**
     * 交易日 20:30 / 21:30 / 22:30 各执行一次
     * 拉取所有用户持仓基金的最新净值
     */
    @Scheduled(cron = "0 30 20,21,22 ? * MON-FRI")
    public void syncOfficialNavs() {
        log.info("定时任务：收盘后同步基金官方净值开始");

        // 获取所有用户持仓的去重基金代码
        List<String> fundCodes = userFundHoldMapper.selectList(
                new LambdaQueryWrapper<UserFundHold>()
                        .gt(UserFundHold::getHoldShares, BigDecimal.ZERO)
                        .select(UserFundHold::getFundCode)
        ).stream()
                .map(UserFundHold::getFundCode)
                .distinct()
                .collect(Collectors.toList());

        if (fundCodes.isEmpty()) {
            log.info("无持仓基金需要同步");
            return;
        }

        int synced = 0, pending = 0;
        for (String fundCode : fundCodes) {
            try {
                if (fundBaseInfoService.syncLatestOfficialNav(fundCode)) {
                    synced++;
                } else {
                    pending++;
                }
            } catch (Exception e) {
                pending++;
                log.error("同步基金 {} 净值异常：{}", fundCode, e.getMessage());
            }
        }

        log.info("收盘后净值同步完成：已公布 {} 只，待公布 {} 只，共 {} 只", synced, pending, fundCodes.size());
    }
}
