package com.fund.assistant;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 数据源接口可用性测试类
 * 依次测试：新浪指数、腾讯基金、天天基金估值、东方财富板块
 * 哪个能用，哪个不能用，一眼就能看出来
 */
public class DataSourceTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("开始测试各数据源接口...\n");

        testSinaIndex();       // 新浪指数
        testTencentFund();     // 腾讯基金
        testTiantianFund();    // 天天基金估值
        testEastMoneySector(); // 东方财富板块

        System.out.println("\n========================================");
        System.out.println("测试结束。");
    }

    /**
     * 测试1：新浪财经指数接口
     * 预期：上证指数 + 深证成指 + 创业板指 + 科创50
     */
    public static void testSinaIndex() {
        System.out.println("【测试1】新浪财经 - 指数行情");
        System.out.println("接口：hq.sinajs.cn/list=sh000001,sz399001,sz399006,sh000688");
        try {
            String urlStr = "https://hq.sinajs.cn/list=sh000001,sz399001,sz399006,sh000688";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
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

            System.out.println("✅ 请求成功！返回数据：");
            System.out.println(response.toString().trim());
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试2：腾讯基金行情接口
     * 预期：基金的实时行情数据
     */
    public static void testTencentFund() {
        System.out.println("【测试2】腾讯财经 - 基金行情");
        System.out.println("接口：qt.gtimg.cn/q=jj000001,jj161725");
        try {
            String urlStr = "http://qt.gtimg.cn/q=jj000001,jj161725";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            conn.disconnect();

            System.out.println("✅ 请求成功！返回数据：");
            System.out.println(response.toString().trim());
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试3：天天基金实时估值接口（JSONP 格式）
     * 预期：包含 gztime / gsz / gszzl / dwjz 等字段
     */
    public static void testTiantianFund() {
        System.out.println("【测试3】天天基金 - 实时估值");
        System.out.println("接口：fundgz.1234567.com.cn/js/161725.js");
        try {
            String url = "https://fundgz.1234567.com.cn/js/161725.js";
            String jsonp = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .timeout(5000)
                    .get()
                    .body()
                    .text();

            // 提取 jsonpgz( ... ) 内的 JSON
            if (!jsonp.contains("jsonpgz(")) {
                System.out.println("❌ 返回格式异常，无 jsonpgz 包裹");
                return;
            }
            int start = jsonp.indexOf("jsonpgz(") + 8;
            int end = jsonp.lastIndexOf(");");
            String jsonStr = jsonp.substring(start, end);

            JSONObject json = JSON.parseObject(jsonStr);
            System.out.println("✅ 请求成功！关键字段：");
            System.out.println("   基金名称：" + json.getString("name"));
            System.out.println("   基金代码：" + json.getString("fundcode"));
            System.out.println("   单位净值：" + json.getString("dwjz"));
            System.out.println("   估算净值：" + json.getString("gsz"));
            System.out.println("   估算涨跌：" + json.getString("gszzl") + "%");
            System.out.println("   估值时间：" + json.getString("gztime"));
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试4：东方财富行业板块资金流向
     * 预期：前20个行业板块的涨跌幅 + 主力净流入
     */
    public static void testEastMoneySector() {
        System.out.println("【测试4】东方财富 - 板块资金流向");
        System.out.println("接口：push2.eastmoney.com/api/qt/clist/get");
        try {
            String urlStr = "https://push2.eastmoney.com/api/qt/clist/get" +
                    "?pn=1&pz=5&po=1&np=1&fs=m:90+t:2&fields=f12,f14,f3,f62";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", "https://quote.eastmoney.com/");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            conn.disconnect();

            System.out.println("✅ 请求成功！返回数据（前5条）：");
            System.out.println(response.toString().trim());
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }
}