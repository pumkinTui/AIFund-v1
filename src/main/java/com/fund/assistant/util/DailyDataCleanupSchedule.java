package com.fund.assistant.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 午夜清理当日估值和行情缓存，为下一个交易日做准备
 */
@Slf4j
@Component
public class DailyDataCleanupSchedule {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 每天 00:30 执行：清理前一日估值 timeline、latest 缓存、行情缓存
     */
    @Scheduled(cron = "0 30 0 * * *")
    public void cleanupDailyCache() {
        log.info("午夜清理：开始清除当日估值和行情缓存");

        try {
            // 清理估值 timeline 缓存（当日分时线），latest 保留（官方净值供非交易时段查询）
            Set<String> timelineKeys = stringRedisTemplate.keys("valuation:timeline:*");
            if (timelineKeys != null && !timelineKeys.isEmpty()) {
                stringRedisTemplate.delete(timelineKeys);
                log.info("清理估值 timeline 缓存：{} 条", timelineKeys.size());
            }

            // 清理板块排行缓存
            Set<String> sectorKeys = stringRedisTemplate.keys("sector:ranking:*");
            if (sectorKeys != null && !sectorKeys.isEmpty()) {
                stringRedisTemplate.delete(sectorKeys);
                log.info("清理板块排行缓存：{} 条", sectorKeys.size());
            }

            log.info("午夜清理完成");
        } catch (Exception e) {
            log.error("午夜清理缓存失败", e);
        }
    }
}
