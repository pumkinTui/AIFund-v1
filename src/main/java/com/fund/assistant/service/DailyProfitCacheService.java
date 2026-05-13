package com.fund.assistant.service;

import com.fund.assistant.vo.UserDailyProfitVO;
import com.fund.assistant.vo.UserFundDailyProfitVO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 今日收益 专用Redis缓存服务
 * 把用户实时计算的收益存到 Redis，下次直接读取，不用重复计算
 * 只缓存当天数据  午夜 0 点自动过期
 */
@Slf4j
@Service
public class DailyProfitCacheService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "daily_profit:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private String getDateStr() {
        return LocalDate.now().format(DATE_FMT);
    }

    /** 总览 key */
    private String overviewKey(Long userId) {
        return KEY_PREFIX + getDateStr() + ":overview:" + userId;
    }

    /** 基金列表 key */
    private String fundListKey(Long userId) {
        return KEY_PREFIX + getDateStr() + ":funds:" + userId;
    }

    /** 单只基金 key */
    private String fundKey(Long userId, String fundCode) {
        return KEY_PREFIX + getDateStr() + ":fund:" + userId + ":" + fundCode;
    }

    //计算到0点还有多少秒
    private long secondsUntilEndOfDay() {
        LocalTime now = LocalTime.now();
        LocalTime midnight = LocalTime.MAX;
        return Duration.between(now, midnight).getSeconds() + 1;
    }

    //缓存收益总览
    public void cacheOverview(Long userId, UserDailyProfitVO vo) {
        try {
            String key = overviewKey(userId);
            String json = JSON.toJSONString(vo);// 对象转 JSON 字符串
            // 存入 Redis，过期时间 = 当天剩余秒数
            stringRedisTemplate.opsForValue().set(key, json, secondsUntilEndOfDay(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存用户每日收益总览到Redis失败: {}", e.getMessage());
        }
    }

    //缓存所有基金收益明细列表
    public void cacheFundProfitList(Long userId, List<UserFundDailyProfitVO> list) {
        try {
            String key = fundListKey(userId);
            String json = JSON.toJSONString(list);
            stringRedisTemplate.opsForValue().set(key, json, secondsUntilEndOfDay(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存基金收益明细列表到Redis失败: {}", e.getMessage());
        }
    }

    //缓存单只基金收益明细
    public void cacheFundProfit(Long userId, String fundCode, UserFundDailyProfitVO vo) {
        try {
            String key = fundKey(userId, fundCode);
            String json = JSON.toJSONString(vo);
            stringRedisTemplate.opsForValue().set(key, json, secondsUntilEndOfDay(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存单只基金收益明细到Redis失败: {}", e.getMessage());
        }
    }

    //获取缓存的收益总览
    public UserDailyProfitVO getCachedOverview(Long userId) {
        try {
            String key = overviewKey(userId);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return JSON.parseObject(json, UserDailyProfitVO.class);// JSON 转回对象
        } catch (Exception e) {
            log.warn("从Redis读取用户每日收益总览失败: {}", e.getMessage());
            return null;
        }
    }

    //获取缓存的单只基金收益明细
    public UserFundDailyProfitVO getCachedFundProfit(Long userId, String fundCode) {
        try {
            String key = fundKey(userId, fundCode);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return JSON.parseObject(json, UserFundDailyProfitVO.class);
        } catch (Exception e) {
            log.warn("从Redis读取单只基金收益明细失败: {}", e.getMessage());
            return null;
        }
    }

    //获取缓存的基金收益明细列表
    public List<UserFundDailyProfitVO> getCachedFundProfitList(Long userId) {
        try {
            String key = fundListKey(userId);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return JSON.parseArray(json, UserFundDailyProfitVO.class);// JSON 转回列表
        } catch (Exception e) {
            log.warn("从Redis读取基金收益明细列表失败: {}", e.getMessage());
            return null;
        }
    }

    // ====================== 6. 删除今天缓存（刷新时用） ======================
/**
 * 作用：删除用户【今天】的所有收益缓存
 * 场景：用户点击【刷新】按钮，强制重新计算收益
 */
public void deleteTodayCache(Long userId) {
    try {
        String overviewKey = overviewKey(userId);
        String fundListKey = fundListKey(userId);
        stringRedisTemplate.delete(List.of(overviewKey, fundListKey));

        // 同时清理所有单只基金缓存（key 模式：daily_profit:yyyyMMdd:fund:userId:*）
        String fundItemPattern = fundKey(userId, "*");
        java.util.Set<String> fundItemKeys = stringRedisTemplate.keys(fundItemPattern);
        if (fundItemKeys != null && !fundItemKeys.isEmpty()) {
            stringRedisTemplate.delete(fundItemKeys);
        }
    } catch (Exception e) {
        log.warn("删除Redis缓存失败: {}", e.getMessage());
    }
}
}
