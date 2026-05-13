package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.service.DailyProfitCacheService;
import com.fund.assistant.service.UserDailyProfitService;
import com.fund.assistant.service.UserFundDailyProfitService;
import com.fund.assistant.entity.UserDailyProfit;
import com.fund.assistant.mapper.UserDailyProfitMapper;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.UserDailyProfitVO;
import com.fund.assistant.vo.UserFundDailyProfitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class UserDailyProfitServiceImpl extends ServiceImpl<UserDailyProfitMapper, UserDailyProfit>
        implements UserDailyProfitService {

    @Autowired
    private UserFundDailyProfitService userFundDailyProfitService;

    @Autowired
    private DailyProfitCacheService dailyProfitCacheService;

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    // 获取用户某天收益概览
    // 计算并返回 用户今日总收益、总收益率、总资产、总投入、持仓基金数
    // 按用户隔离的内存短时缓存，避免同一时刻多次请求因估值波动算出不同结果
    private final Map<Long, UserDailyProfitVO> memCache = new ConcurrentHashMap<>();
    private final Map<Long, Long> memCacheTime = new ConcurrentHashMap<>();
    private static final long OVERVIEW_CACHE_MS = 30000;

    @Override
    public UserDailyProfitVO getDailyProfitOverview() {
        Long userId = UserContext.getUserId();
        // 内存缓存：同一用户30秒内返回同一份数据
        Long lastTime = memCacheTime.get(userId);
        if (lastTime != null && System.currentTimeMillis() - lastTime < OVERVIEW_CACHE_MS) {
            UserDailyProfitVO mem = memCache.get(userId);
            if (mem != null) return mem;
        }
        // Redis缓存兜底
        UserDailyProfitVO cached = dailyProfitCacheService.getCachedOverview(userId);
        if (cached != null) {
            memCache.put(userId, cached);
            memCacheTime.put(userId, System.currentTimeMillis());
            return cached;
        }
        // 获取所有持仓基金的实时收益列表
        List<UserFundDailyProfitVO> fundProfitList = userFundDailyProfitService.getAllFundDailyProfitList();

        UserDailyProfitVO vo = new UserDailyProfitVO();
        vo.setProfitDate(LocalDate.now());
        vo.setFundProfitList(fundProfitList);
        // 情况1：用户没有任何持仓
        if (fundProfitList.isEmpty()) {
            vo.setTotalProfit(BigDecimal.ZERO);
            vo.setTotalProfitRate(BigDecimal.ZERO);
            vo.setTotalAsset(BigDecimal.ZERO);
            vo.setTotalCostAmount(BigDecimal.ZERO);
            vo.setHoldFundCount(0);

            // 存入Redis缓存
            dailyProfitCacheService.cacheOverview(userId, vo);
            memCache.put(userId, vo);
            memCacheTime.put(userId, System.currentTimeMillis());

            return vo;
        }
        // 情况2：用户有持仓，开始汇总计算
        //从零开始累加
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalAsset = BigDecimal.ZERO;
        BigDecimal totalCostAmount = BigDecimal.ZERO;

        for (UserFundDailyProfitVO fundProfit : fundProfitList) {
            totalProfit = totalProfit.add(fundProfit.getDailyProfit());
            totalAsset = totalAsset.add(fundProfit.getMarketValue());
            totalCostAmount = totalCostAmount.add(fundProfit.getTotalCostAmount());
        }

        totalProfit = totalProfit.setScale(SCALE, ROUNDING_MODE);
        totalAsset = totalAsset.setScale(SCALE, ROUNDING_MODE);
        totalCostAmount = totalCostAmount.setScale(SCALE, ROUNDING_MODE);
        vo.setTotalProfit(totalProfit);
        vo.setTotalAsset(totalAsset);
        vo.setTotalCostAmount(totalCostAmount);
        vo.setHoldFundCount(fundProfitList.size());

        // 计算总收益率
        //zhi有总资产大于0才能计算
        if (totalAsset.compareTo(BigDecimal.ZERO) > 0) {
            // 总资产 - 今日收益 = 昨日总资产
            BigDecimal totalProfitRate = totalProfit.divide(totalAsset.subtract(totalProfit), 4, ROUNDING_MODE)
                    .multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE);
            vo.setTotalProfitRate(totalProfitRate);
        } else {
            vo.setTotalProfitRate(BigDecimal.ZERO);
        }
        // 把总览数据存入Redis缓存
        dailyProfitCacheService.cacheOverview(userId, vo);
        memCache.put(userId, vo);
        memCacheTime.put(userId, System.currentTimeMillis());

        return vo;
    }

    // 获取用户某段时间的收益列表  从数据库中直接查  不计算了
    @Override
    public List<UserDailyProfit> getHistoryList(Long userId, LocalDate startDate, LocalDate endDate) {
        return list(new LambdaQueryWrapper<UserDailyProfit>()
                .eq(UserDailyProfit::getUserId, userId)
                .between(UserDailyProfit::getProfitDate, startDate, endDate)
                .orderByDesc(UserDailyProfit::getProfitDate));
    }
}
