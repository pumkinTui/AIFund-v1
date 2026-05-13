package com.fund.assistant.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.entity.MarketSectorInfo;
import com.fund.assistant.mapper.MarketSectorInfoMapper;
import com.fund.assistant.service.MarketSectorInfoService;
import com.fund.assistant.vo.MarketSectorRealtimeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MarketSectorInfoServiceImpl extends ServiceImpl<MarketSectorInfoMapper, MarketSectorInfo>
        implements MarketSectorInfoService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${api.eastmoney.sector-url}")
    private String sectorUrl;

    private static final String REDIS_KEY = "sector:ranking";

    private long secondsUntil1530() {
        LocalTime now = LocalTime.now();
        LocalTime closeTime = LocalTime.of(15, 30);
        if (now.isAfter(closeTime)) return 0;
        return Duration.between(now, closeTime).getSeconds();
    }

    @Override
    public List<MarketSectorRealtimeVO> getSectorRanking() {
        try {
            String json = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (json != null) {
                return JSON.parseArray(json, MarketSectorRealtimeVO.class);
            }
        } catch (Exception e) {
            log.warn("读取板块缓存失败，{}", e.getMessage());
        }

        return fetchAndCache();
    }

    @Override
    public void refreshSectorRanking() {
        fetchAndCache();
    }

    private List<MarketSectorRealtimeVO> fetchAndCache() {
        try {
            // 从数据库 market_sector_info 表读取板块行情，按涨跌幅倒序
            LambdaQueryWrapper<MarketSectorInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(MarketSectorInfo::getDailyChangeRate);
            List<MarketSectorInfo> list = this.list(wrapper);

            if (list.isEmpty()) {
                log.warn("market_sector_info 表无数据");
                return List.of();
            }

            List<MarketSectorRealtimeVO> voList = list.stream().map(entity -> {
                MarketSectorRealtimeVO vo = new MarketSectorRealtimeVO();
                vo.setSectorCode(entity.getSectorCode());
                vo.setSectorName(entity.getSectorName());
                vo.setChangeRate(entity.getDailyChangeRate());
                return vo;
            }).collect(Collectors.toList());

            // 写入 Redis
            String jsonStr = JSON.toJSONString(voList);
            long ttl = secondsUntil1530();
            if (ttl > 0) {
                stringRedisTemplate.opsForValue().set(REDIS_KEY, jsonStr, ttl, TimeUnit.SECONDS);
            }

            return voList;
        } catch (Exception e) {
            log.warn("获取板块排行失败，{}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void syncSectorData() {
        try {
            log.info("开始从东方财富同步板块数据");
            String resp = restTemplate.getForObject(sectorUrl, String.class);
            if (resp == null || resp.isEmpty()) {
                log.warn("东方财富板块接口返回空");
                return;
            }
            JSONObject json = JSON.parseObject(resp);
            JSONObject data = json.getJSONObject("data");
            if (data == null) {
                log.warn("板块数据 data 字段为空: {}", resp.substring(0, Math.min(200, resp.length())));
                return;
            }
            JSONArray diff = data.getJSONArray("diff");
            if (diff == null || diff.isEmpty()) {
                log.warn("板块数据 diff 数组为空");
                return;
            }
            for (int i = 0; i < diff.size(); i++) {
                JSONObject item = diff.getJSONObject(i);
                String code = item.getString("f12");
                String name = item.getString("f14");
                BigDecimal changeRate = item.getBigDecimal("f3");

                if (code == null || name == null) continue;

                // upsert: 根据 sector_code 查是否存在
                MarketSectorInfo exist = this.getOne(new LambdaQueryWrapper<MarketSectorInfo>()
                        .eq(MarketSectorInfo::getSectorCode, code));
                if (exist != null) {
                    exist.setSectorName(name);
                    exist.setDailyChangeRate(changeRate);
                    exist.setUpdateTime(LocalDateTime.now());
                    this.updateById(exist);
                } else {
                    MarketSectorInfo entity = new MarketSectorInfo();
                    entity.setSectorCode(code);
                    entity.setSectorName(name);
                    entity.setDailyChangeRate(changeRate);
                    entity.setCreateTime(LocalDateTime.now());
                    entity.setUpdateTime(LocalDateTime.now());
                    this.save(entity);
                }
            }
            log.info("板块数据同步完成，共 {} 条", diff.size());
            // 同步后刷新Redis
            refreshSectorRanking();
        } catch (Exception e) {
            log.error("同步板块数据失败: {}", e.getMessage(), e);
        }
    }
}
