package com.fund.assistant.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.entity.FundNetValueHistory;
import com.fund.assistant.entity.UserDailyProfit;
import com.fund.assistant.entity.UserFundDailyProfit;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.mapper.FundNetValueHistoryMapper;
import com.fund.assistant.mapper.UserDailyProfitMapper;
import com.fund.assistant.mapper.UserFundDailyProfitMapper;
import com.fund.assistant.mapper.UserFundHoldMapper;
import com.fund.assistant.service.UserFundDailyProfitService;
import com.fund.assistant.vo.UserFundDailyProfitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 收盘后将当日收益持久化到数据库
 * 流程：从Redis读取当日缓存 → 用官方净值重算 → 写入 user_daily_profit 和 user_fund_daily_profit → 清理Redis缓存
 * 执行时间：交易日 21:00（官方净值通常在 20:00-22:00 公布）
 */
@Slf4j
@Component
public class DailyProfitPersistenceSchedule {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserDailyProfitMapper userDailyProfitMapper;

    @Autowired
    private UserFundDailyProfitMapper userFundDailyProfitMapper;

    @Autowired
    private UserFundHoldMapper userFundHoldMapper;

    @Autowired
    private FundNetValueHistoryMapper fundNetValueHistoryMapper;

    @Autowired
    private UserFundDailyProfitService userFundDailyProfitService;

    private static final String KEY_PREFIX = "daily_profit:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Scheduled(cron = "0 35 21,22 ? * MON-FRI")
    @Transactional(rollbackFor = Exception.class)
    public void persistDailyProfitToDb() {
        String todayStr = LocalDate.now().format(DATE_FMT);
        log.info("收盘后将当日收益持久化到数据库开始，日期：{}", todayStr);

        try {
            // 从 Redis 获取所有有今日收益数据的用户
            Set<String> overviewKeys = stringRedisTemplate.keys(KEY_PREFIX + todayStr + ":overview:*");
            if (overviewKeys == null || overviewKeys.isEmpty()) {
                log.info("Redis中无今日收益数据，跳过批量写入");
                return;
            }

            LocalDate today = LocalDate.now();
            int successCount = 0;

            for (String key : overviewKeys) {
                String userIdStr = key.substring(key.lastIndexOf(":") + 1);
                Long userId;
                try {
                    userId = Long.parseLong(userIdStr);
                } catch (NumberFormatException e) {
                    log.warn("解析用户ID失败，key: {}", key);
                    continue;
                }

                try {
                    boolean success = persistUserDailyProfitWithOfficialNAV(userId, today, todayStr);
                    if (success) {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("持久化用户 {} 每日收益失败: {}", userId, e.getMessage(), e);
                }
            }

            log.info("收盘后收益持久化完成，共处理 {} / {} 位用户", successCount, overviewKeys.size());
        } catch (Exception e) {
            log.error("收盘后批量写入每日收益数据异常", e);
        }
    }

    /**
     * 用官方净值重算并持久化单个用户的当日收益
     */
    private boolean persistUserDailyProfitWithOfficialNAV(Long userId, LocalDate today, String todayStr) {
        // 1. 优先从 Redis 获取基金收益列表，没有则从持仓表查
        String fundListKey = KEY_PREFIX + todayStr + ":funds:" + userId;
        String fundListJson = stringRedisTemplate.opsForValue().get(fundListKey);
        List<UserFundDailyProfitVO> cachedFundList = null;

        if (fundListJson != null) {
            cachedFundList = com.alibaba.fastjson.JSON.parseArray(fundListJson, UserFundDailyProfitVO.class);
        }

        if (cachedFundList == null || cachedFundList.isEmpty()) {
            // Redis 无缓存，从持仓表直接获取基金代码列表
            List<UserFundHold> holds = userFundHoldMapper.selectList(
                    new LambdaQueryWrapper<UserFundHold>()
                            .eq(UserFundHold::getUserId, userId)
                            .gt(UserFundHold::getHoldShares, BigDecimal.ZERO)
                            .select(UserFundHold::getFundCode));
            if (holds.isEmpty()) {
                log.warn("用户 {} 无持仓，跳过收益持久化", userId);
                return false;
            }
            cachedFundList = holds.stream().map(h -> {
                UserFundDailyProfitVO vo = new UserFundDailyProfitVO();
                vo.setFundCode(h.getFundCode());
                return vo;
            }).distinct().collect(java.util.stream.Collectors.toList());
            log.info("用户 {} Redis缓存为空，从持仓表获取 {} 只基金", userId, cachedFundList.size());
        }

        // 3. 逐只基金用官方净值重算
        List<UserFundDailyProfit> entityList = new ArrayList<>();
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        boolean hasUnpublishedNAV = false;

        for (UserFundDailyProfitVO cached : cachedFundList) {
            String fundCode = cached.getFundCode();

            //  获取当日官方净值
            FundNetValueHistory officialNAV = fundNetValueHistoryMapper.selectOne(
                    new LambdaQueryWrapper<FundNetValueHistory>()
                            .eq(FundNetValueHistory::getFundCode, fundCode)
                            .eq(FundNetValueHistory::getNetValueDate, today)
                            .last("LIMIT 1"));

            if (officialNAV == null) {
                log.warn("基金 {} 当日官方净值尚未公布，跳过此基金", fundCode);
                hasUnpublishedNAV = true;
                continue;
            }

            //  获取前一日官方净值
            FundNetValueHistory preCloseNAV = fundNetValueHistoryMapper.selectOne(
                    new LambdaQueryWrapper<FundNetValueHistory>()
                            .eq(FundNetValueHistory::getFundCode, fundCode)
                            .lt(FundNetValueHistory::getNetValueDate, today)
                            .orderByDesc(FundNetValueHistory::getNetValueDate)
                            .last("LIMIT 1"));

            if (preCloseNAV == null) {
                log.warn("基金 {} 前一日净值不存在，跳过此基金", fundCode);
                continue;
            }

            //  获取用户持仓（持仓份额、成本）
            UserFundHold hold = userFundHoldMapper.selectOne(
                    new LambdaQueryWrapper<UserFundHold>()
                            .eq(UserFundHold::getUserId, userId)
                            .eq(UserFundHold::getFundCode, fundCode)
                            .gt(UserFundHold::getHoldShares, BigDecimal.ZERO)
                            .last("LIMIT 1"));

            if (hold == null) {
                log.warn("用户 {} 无基金 {} 持仓，跳过", userId, fundCode);
                continue;
            }

            //  用官方净值重新计算
            UserFundDailyProfitVO rebuilt = userFundDailyProfitService.rebuildWithOfficialNAV(
                    hold,
                    officialNAV.getUnitNetValue(),
                    preCloseNAV.getUnitNetValue(),
                    officialNAV.getDailyChangeRate());

            //  组装实体准备写入
            UserFundDailyProfit entity = new UserFundDailyProfit();
            entity.setUserId(userId);
            entity.setFundCode(fundCode);
            entity.setProfitDate(today);
            entity.setDailyProfit(rebuilt.getDailyProfit());
            entity.setDailyProfitRate(rebuilt.getDailyProfitRate());
            entity.setHoldMarketValue(rebuilt.getMarketValue());
            entityList.add(entity);

            totalProfit = totalProfit.add(rebuilt.getDailyProfit() != null ? rebuilt.getDailyProfit() : BigDecimal.ZERO);
            totalMarketValue = totalMarketValue.add(rebuilt.getMarketValue() != null ? rebuilt.getMarketValue() : BigDecimal.ZERO);
            totalCost = totalCost.add(hold.getTotalCostAmount() != null ? hold.getTotalCostAmount() : BigDecimal.ZERO);
        }

        if (entityList.isEmpty()) {
            log.warn("用户 {} 所有基金官方净值均未公布，暂不写入", userId);
            // 不删 Redis，下次运行（22:35）继续等待
            return false;
        }

        // 4. 写入 user_fund_daily_profit（先删旧数据，再批量插入）
        userFundDailyProfitMapper.delete(new LambdaQueryWrapper<UserFundDailyProfit>()
                .eq(UserFundDailyProfit::getUserId, userId)
                .eq(UserFundDailyProfit::getProfitDate, today));
        for (UserFundDailyProfit entity : entityList) {
            entity.setCreateTime(LocalDateTime.now());
            userFundDailyProfitMapper.insert(entity);
        }

        // 5. 写入 user_daily_profit（总收益）
        BigDecimal totalProfitRate = totalCost.compareTo(BigDecimal.ZERO) > 0
                ? totalProfit.divide(totalCost, SCALE, ROUNDING_MODE).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        userDailyProfitMapper.delete(new LambdaQueryWrapper<UserDailyProfit>()
                .eq(UserDailyProfit::getUserId, userId)
                .eq(UserDailyProfit::getProfitDate, today));

        UserDailyProfit dailyProfitEntity = new UserDailyProfit();
        dailyProfitEntity.setUserId(userId);
        dailyProfitEntity.setProfitDate(today);
        dailyProfitEntity.setDailyTotalProfit(totalProfit);
        dailyProfitEntity.setDailyProfitRate(totalProfitRate);
        dailyProfitEntity.setTotalAsset(totalMarketValue);
        dailyProfitEntity.setCreateTime(LocalDateTime.now());
        dailyProfitEntity.setUpdateTime(LocalDateTime.now());
        userDailyProfitMapper.insert(dailyProfitEntity);

        // 6. 清理 Redis 缓存（仅当全部基金净值都已公布才删除，否则保留给下次运行）
        if (!hasUnpublishedNAV) {
            String overviewKey = KEY_PREFIX + todayStr + ":overview:" + userId;
            String fundItemKey = KEY_PREFIX + todayStr + ":fund:" + userId + ":*";
            stringRedisTemplate.delete(List.of(overviewKey, fundListKey));
            Set<String> fundItemKeys = stringRedisTemplate.keys(fundItemKey);
            if (fundItemKeys != null && !fundItemKeys.isEmpty()) {
                stringRedisTemplate.delete(fundItemKeys);
            }
            log.info("用户 {} 收益持久化完成：{} 只基金，总收益 {}，缓存已清理", userId, entityList.size(), totalProfit);
        } else {
            log.info("用户 {} 部分基金净值未公布，保留 Redis 缓存等下次运行（已持久化 {}/{} 只）",
                    userId, entityList.size(), cachedFundList.size());
        }
        return true;
    }
}
