package com.fund.assistant.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.entity.StockBaseInfo;
import com.fund.assistant.entity.StockRealtimeQuote;
import com.fund.assistant.mapper.StockBaseInfoMapper;
import com.fund.assistant.mapper.StockRealtimeQuoteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class StockQuoteSchedule {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StockBaseInfoMapper stockBaseInfoMapper;

    @Autowired
    private StockRealtimeQuoteMapper stockRealtimeQuoteMapper;

    /**
     * 股票实时行情同步（腾讯接口，GBK 处理）
     * 交易时间：每 30 秒执行一次（周一到周五 9:30-15:00）
     */
    @Scheduled(cron = "0/30 * 9-15 * * MON-FRI")
    public void syncStockQuote() {
        log.info("开始同步股票实时行情");
        try {
            List<StockBaseInfo> stockList = stockBaseInfoMapper.selectList(null);
            if (stockList.isEmpty()) {
                log.warn("暂无股票数据，跳过同步");
                return;
            }

            // 拼接带市场前缀的代码
            List<String> fullCodes = stockList.stream().map(s -> {
                String code = s.getStockCode();
                if (code.startsWith("6")) return "sh" + code;
                if (code.startsWith("0") || code.startsWith("3")) return "sz" + code;
                return code;
            }).collect(Collectors.toList());

            // 分批请求，每批 100 只
            int batchSize = 100;
            for (int i = 0; i < fullCodes.size(); i += batchSize) {
                int end = Math.min(i + batchSize, fullCodes.size());
                List<String> batch = fullCodes.subList(i, end);
                String url = "https://qt.gtimg.cn/q=" + String.join(",", batch);
                
                String result = restTemplate.getForObject(url, String.class);
                if (StringUtils.hasText(result)) {
                    parseAndSave(result);
                }
            }
            log.info("股票行情同步完成");
        } catch (Exception e) {
            log.error("股票行情同步失败", e);
        }
    }

    private void parseAndSave(String result) {
        String[] lines = result.split("\n");
        for (String line : lines) {
            if (!line.contains("~")) continue;
            try {
                String[] parts = line.split("=");
                if (parts.length < 2) continue;

                String fullCode = parts[0].replace("v_", "").trim();
                String stockCode = fullCode.replace("sh", "").replace("sz", "").trim();
                String data = parts[1].replace("\"", "").trim();
                String[] fields = data.split("~");
                if (fields.length < 32) continue;

                // 提取字段
                BigDecimal latestPrice = new BigDecimal(fields[3]);
                BigDecimal preClose = new BigDecimal(fields[4]);
                BigDecimal changeAmount = new BigDecimal(fields[9]);
                BigDecimal changeRate = new BigDecimal(fields[10]);
                BigDecimal high = new BigDecimal(fields[6]);
                BigDecimal low = new BigDecimal(fields[7]);
                Long volume = StringUtils.hasText(fields[19]) ? Long.parseLong(fields[19]) : 0L;
                BigDecimal amount = StringUtils.hasText(fields[20]) ? new BigDecimal(fields[20]) : BigDecimal.ZERO;

                // 更新或插入
                LambdaQueryWrapper<StockRealtimeQuote> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(StockRealtimeQuote::getStockCode, stockCode);
                StockRealtimeQuote exist = stockRealtimeQuoteMapper.selectOne(wrapper);

                LocalDateTime now = LocalDateTime.now();
                if (exist != null) {
                    exist.setLatestPrice(latestPrice);
                    exist.setPreClosePrice(preClose);
                    exist.setChangeAmount(changeAmount);
                    exist.setChangeRate(changeRate);
                    exist.setHighPrice(high);
                    exist.setLowPrice(low);
                    exist.setTradeVolume(volume);
                    exist.setTradeAmount(amount);
                    exist.setQuoteTime(now);
                    exist.setIsTrading((byte) 1);
                    stockRealtimeQuoteMapper.updateById(exist);
                } else {
                    StockRealtimeQuote quote = new StockRealtimeQuote();
                    quote.setStockCode(stockCode);
                    quote.setLatestPrice(latestPrice);
                    quote.setPreClosePrice(preClose);
                    quote.setChangeAmount(changeAmount);
                    quote.setChangeRate(changeRate);
                    quote.setHighPrice(high);
                    quote.setLowPrice(low);
                    quote.setTradeVolume(volume);
                    quote.setTradeAmount(amount);
                    quote.setQuoteTime(now);
                    quote.setIsTrading((byte) 1);
                    stockRealtimeQuoteMapper.insert(quote);
                }
            } catch (Exception e) {
                log.error("单只股票解析失败: {}", e.getMessage());
            }
        }
    }
}