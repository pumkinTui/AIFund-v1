package com.fund.assistant.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.entity.MarketIndexInfo;
import com.fund.assistant.mapper.MarketIndexInfoMapper;
import com.fund.assistant.service.MarketIndexInfoService;
import com.fund.assistant.util.MarketTimeUtils;
import com.fund.assistant.vo.MarketIndexRealtimeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MarketIndexInfoServiceImpl extends ServiceImpl<MarketIndexInfoMapper, MarketIndexInfo>
        implements MarketIndexInfoService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${api.sina.a-stock-url}")
    private String aStockUrl;

    @Value("${api.sina.hk-stock-url}")
    private String hkStockUrl;

    @Value("${api.sina.us-stock-url}")
    private String usStockUrl;

    private static final String REDIS_KEY = "index:realtime";
    private static final int SCALE = 4;

    @Override
    public List<MarketIndexRealtimeVO> getRealtimeQuotes() {
        try {
            String json = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (json != null) return JSON.parseArray(json, MarketIndexRealtimeVO.class);
        } catch (Exception e) {
            log.warn("读取大盘缓存失败，{}", e.getMessage());
        }
        if (MarketTimeUtils.isAStockTradingTime() || MarketTimeUtils.isUSStockTradingTime()) {
            return fetchAllAndCache();
        }
        return List.of();
    }

    @Override
    public void refreshRealtimeQuotes() {
        fetchAllAndCache();
    }

    @Override
    public void refreshChinaIndices() {
        List<MarketIndexRealtimeVO> china = new ArrayList<>();
        china.addAll(fetchFromSina(aStockUrl, "A股"));
        china.addAll(fetchFromSina(hkStockUrl, "港股"));
        if (china.isEmpty()) return;
        List<MarketIndexRealtimeVO> existing = getCachedQuotes();
        List<MarketIndexRealtimeVO> usOnly = existing.stream()
                .filter(v -> "美股".equals(v.getMarketType())).collect(Collectors.toList());
        china.addAll(usOnly);
        cacheQuotes(china);
    }

    @Override
    public void refreshUSIndices() {
        List<MarketIndexRealtimeVO> us = new ArrayList<>(fetchFromSina(usStockUrl, "美股"));
        if (us.isEmpty()) return;
        List<MarketIndexRealtimeVO> existing = getCachedQuotes();
        List<MarketIndexRealtimeVO> chinaOnly = existing.stream()
                .filter(v -> !"美股".equals(v.getMarketType())).collect(Collectors.toList());
        us.addAll(chinaOnly);
        cacheQuotes(us);
    }

    private List<MarketIndexRealtimeVO> getCachedQuotes() {
        try {
            String json = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (json != null) return JSON.parseArray(json, MarketIndexRealtimeVO.class);
        } catch (Exception e) { /* ignore */ }
        return List.of();
    }

    private void cacheQuotes(List<MarketIndexRealtimeVO> quotes) {
        if (quotes.isEmpty()) return;
        try {
            String jsonStr = JSON.toJSONString(quotes);
            long ttl = 14 * 24 * 3600;
            if (ttl > 0) {
                stringRedisTemplate.opsForValue().set(REDIS_KEY, jsonStr, ttl, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("大盘数据写入Redis缓存失败：{}", e.getMessage());
        }
    }

    private List<MarketIndexRealtimeVO> fetchAllAndCache() {
        List<MarketIndexRealtimeVO> all = new ArrayList<>();
        all.addAll(fetchFromSina(aStockUrl, "A股"));
        all.addAll(fetchFromSina(hkStockUrl, "港股"));
        all.addAll(fetchFromSina(usStockUrl, "美股"));
        cacheQuotes(all);
        return all;
    }

    private List<MarketIndexRealtimeVO> fetchFromSina(String urlStr, String marketType) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            log.info("请求新浪{}指数：{}", marketType, urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn/");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            log.info("新浪{}指数响应码：{}", marketType, code);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "GBK"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String raw = sb.toString();
            if (raw.isEmpty()) {
                log.warn("新浪{}指数返回空", marketType);
                return List.of();
            }

            List<MarketIndexRealtimeVO> list = new ArrayList<>();
            String[] lines = raw.split(";");
            for (String ln : lines) {
                if (!ln.contains("=") || !ln.contains(",")) continue;
                try {
                    String[] parts = ln.split("=");
                    if (parts.length < 2) continue;
                    String codePart = parts[0].replace("var hq_str_", "").trim();
                    String dataStr = parts[1].replace("\"", "").trim();
                    String[] fields = dataStr.split(",");
                    if (fields.length < 4) continue;

                    String indexName = fields[0];
                    BigDecimal currentPoint, preClose;
                    if (codePart.startsWith("int_")) {
                        // 国际指数格式: 名称,当前点位,涨跌额,涨跌幅,...
                        currentPoint = new BigDecimal(fields[1]);
                        BigDecimal changePoint = new BigDecimal(fields[2]);
                        preClose = currentPoint.subtract(changePoint);
                    } else {
                        // A股/港股格式: 名称,今开,昨收,当前,...
                        preClose = new BigDecimal(fields[2]);
                        currentPoint = new BigDecimal(fields[3]);
                    }
                    BigDecimal changePoint = currentPoint.subtract(preClose);
                    BigDecimal changeRate = changePoint
                            .divide(preClose, SCALE + 2, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);

                    MarketIndexRealtimeVO vo = new MarketIndexRealtimeVO();
                    vo.setIndexCode(codePart);
                    vo.setIndexName(indexName);
                    vo.setCurrentPoint(currentPoint);
                    vo.setChangeRate(changeRate);
                    vo.setChangePoint(changePoint);
                    vo.setMarketType(marketType);
                    list.add(vo);
                } catch (Exception e) {
                    log.warn("单条指数解析失败：{}", e.getMessage());
                }
            }
            log.info("新浪{}指数解析完成，共 {} 条", marketType, list.size());
            return list;
        } catch (Exception e) {
            log.warn("获取新浪{}指数失败：{}", marketType, e.getMessage());
            return List.of();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
