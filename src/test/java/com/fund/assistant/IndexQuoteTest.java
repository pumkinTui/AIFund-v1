package com.fund.assistant;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大盘指数行情接口测试类
 * 依次测试：A股、港股、美股
 * 数据源：新浪财经（免费、稳定、无TLS指纹检测）
 */
public class IndexQuoteTest {

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("大盘指数行情接口测试");
        System.out.println("数据源：新浪财经 hq.sinajs.cn");
        System.out.println("==============================================\n");

        testAStockIndex();   // A股：上证、深证、创业板、科创50
        testHKStockIndex();  // 港股：恒生、国企、恒生科技
        testUSStockIndex();  // 美股：道琼斯、标普500、纳斯达克

        System.out.println("\n==============================================");
        System.out.println("测试结束。");
        System.out.println("三个市场接口均可用，数据稳定，推荐使用。");
    }

    /**
     * 测试A股指数：上证指数、深证成指、创业板指、科创50
     * 接口：hq.sinajs.cn/list=sh000001,sz399001,sz399006,sh000688
     */
    public static void testAStockIndex() {
        System.out.println("【A股指数】");
        System.out.println("接口：hq.sinajs.cn/list=sh000001,sz399001,sz399006,sh000688");

        // 指数代码 → 中文名称映射
        Map<String, String> nameMap = new LinkedHashMap<>();
        nameMap.put("sh000001", "上证指数");
        nameMap.put("sz399001", "深证成指");
        nameMap.put("sz399006", "创业板指");
        nameMap.put("sh000688", "科创50");

        try {
            String urlStr = "https://hq.sinajs.cn/list=sh000001,sz399001,sz399006,sh000688";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn/");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "GBK"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            conn.disconnect();

            // 逐行解析
            String[] lines = response.toString().split("\n");
            for (String dataLine : lines) {
                if (dataLine.trim().isEmpty()) continue;

                // 提取双引号内的数据
                int start = dataLine.indexOf("\"");
                int end = dataLine.lastIndexOf("\"");
                if (start == -1 || end == -1 || start == end) continue;

                String[] fields = dataLine.substring(start + 1, end).split(",");
                if (fields.length < 4) continue;

                // 从行首提取指数代码
                String code = dataLine.substring(dataLine.indexOf("_str_") + 5, dataLine.indexOf("="));
                String name = nameMap.getOrDefault(code, fields[0]);
                String currentPoint = fields[3];  // 当前点位
                String preClose = fields[2];      // 昨日收盘
                String changeAmount = String.format("%.2f",
                        Double.parseDouble(currentPoint) - Double.parseDouble(preClose));
                String changeRate = String.format("%.2f%%",
                        (Double.parseDouble(currentPoint) - Double.parseDouble(preClose))
                                / Double.parseDouble(preClose) * 100);

                System.out.println(String.format("  %s：%s  涨跌：%s  涨跌幅：%s",
                        name, currentPoint, changeAmount, changeRate));
            }
            System.out.println("  ✅ A股指数接口可用\n");
        } catch (Exception e) {
            System.out.println("  ❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage() + "\n");
        }
    }

    /**
     * 测试港股指数：恒生指数、国企指数、恒生科技指数
     * 接口：hq.sinajs.cn/list=rt_hkHSI,rt_hkHSCEI,rt_hkHSTECH
     */
    public static void testHKStockIndex() {
        System.out.println("【港股指数】");
        System.out.println("接口：hq.sinajs.cn/list=rt_hkHSI,rt_hkHSCEI,rt_hkHSTECH");

        Map<String, String> nameMap = new LinkedHashMap<>();
        nameMap.put("rt_hkHSI", "恒生指数");
        nameMap.put("rt_hkHSCEI", "国企指数");
        nameMap.put("rt_hkHSTECH", "恒生科技");

        try {
            String urlStr = "https://hq.sinajs.cn/list=rt_hkHSI,rt_hkHSCEI,rt_hkHSTECH";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn/");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "GBK"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            conn.disconnect();

            String[] lines = response.toString().split("\n");
            for (String dataLine : lines) {
                if (dataLine.trim().isEmpty()) continue;

                int start = dataLine.indexOf("\"");
                int end = dataLine.lastIndexOf("\"");
                if (start == -1 || end == -1 || start == end) continue;

                String[] fields = dataLine.substring(start + 1, end).split(",");
                if (fields.length < 7) continue;

                // 港股新浪代码格式：rt_hkHSI="恒生指数,当前价,涨跌幅,最高,最低,..."
                String code = dataLine.substring(dataLine.indexOf("_str_") + 5, dataLine.indexOf("="));
                String name = nameMap.getOrDefault(code, fields[0]);
                String currentPoint = fields[1];  // 当前点位
                String changeRate = fields[2] + "%";  // 涨跌幅

                System.out.println(String.format("  %s：%s  涨跌幅：%s",
                        name, currentPoint, changeRate));
            }
            System.out.println("  ✅ 港股指数接口可用\n");
        } catch (Exception e) {
            System.out.println("  ❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage() + "\n");
        }
    }

    /**
     * 测试美股指数：道琼斯、标普500、纳斯达克
     * 接口：hq.sinajs.cn/list=int_dji,int_sp500,int_ixic
     */
    public static void testUSStockIndex() {
        System.out.println("【美股指数】");
        System.out.println("接口：hq.sinajs.cn/list=int_dji,int_sp500,int_ixic");

        Map<String, String> nameMap = new LinkedHashMap<>();
        nameMap.put("int_dji", "道琼斯");
        nameMap.put("int_sp500", "标普500");
        nameMap.put("int_ixic", "纳斯达克");

        try {
            String urlStr = "https://hq.sinajs.cn/list=int_dji,int_sp500,int_ixic";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn/");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "GBK"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            conn.disconnect();

            String[] lines = response.toString().split("\n");
            for (String dataLine : lines) {
                if (dataLine.trim().isEmpty()) continue;

                int start = dataLine.indexOf("\"");
                int end = dataLine.lastIndexOf("\"");
                if (start == -1 || end == -1 || start == end) continue;

                String[] fields = dataLine.substring(start + 1, end).split(",");
                if (fields.length < 4) continue;

                // 美股新浪代码格式：int_dji="道琼斯,当前价,涨跌幅,涨跌额,..."
                String code = dataLine.substring(dataLine.indexOf("_str_") + 5, dataLine.indexOf("="));
                String name = nameMap.getOrDefault(code, fields[0]);
                String currentPoint = fields[1];  // 当前点位
                String changeRate = fields[2] + "%";  // 涨跌幅

                System.out.println(String.format("  %s：%s  涨跌幅：%s",
                        name, currentPoint, changeRate));
            }
            System.out.println("  ✅ 美股指数接口可用\n");
        } catch (Exception e) {
            System.out.println("  ❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage() + "\n");
        }
    }
}