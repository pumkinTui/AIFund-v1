package com.fund.assistant;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 板块资金流向 —— 全量备选接口测试（剔除东方财富）
 */
public class SectorNoEastMoneyTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("板块资金流向数据源测试（不含东方财富）\n");

        testTencentSectorBoard();       // 腾讯概念板块行情
        testSinaIndustryRank();         // 新浪行业板块排行
        testAllTickAPI();               // AllTick 免费API（需注册 key，可先测试连通性）
        testDoupandSector();            // DouPand 申万行业

        System.out.println("\n========================================");
        System.out.println("测试结束。如果全部失败，推荐使用 Python+AkShare 或模拟数据。");
    }

    /**
     * 1. 腾讯概念板块行情（可能包含资金流向）
     * 昨天测试过 ptBK0121 等概念板块，这次加上更多概念代码
     */
    public static void testTencentSectorBoard() {
        System.out.println("【测试1】腾讯财经 - 概念板块行情（包含资金流向？）");
        System.out.println("接口：qt.gtimg.cn/q=ptBK0479,ptBK0533,ptBK1211,ptBK1386,ptBK1204");
        try {
            String urlStr = "http://qt.gtimg.cn/q=ptBK0479,ptBK0533,ptBK1211,ptBK1386,ptBK1204";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "GBK"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            conn.disconnect();

            String body = sb.toString().trim();
            if (body.isEmpty() || body.contains("none_match")) {
                System.out.println("❌ 无数据或 none_match");
            } else {
                System.out.println("✅ 有返回，前800字符：");
                System.out.println(body.substring(0, Math.min(800, body.length())));
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 2. 新浪行业板块排行（可能包含涨跌幅，不一定有资金流向）
     */
    public static void testSinaIndustryRank() {
        System.out.println("【测试2】新浪财经 - 行业板块排行");
        System.out.println("接口：vip.stock.finance.sina.com.cn/q/go.php/vIndustryRank/...");
        try {
            String urlStr = "https://vip.stock.finance.sina.com.cn/q/go.php/vIndustryRank/knd/sort/1/display/1/handel/1/daily/1/";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn/");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            String body = sb.toString().trim();
            if (body.isEmpty()) {
                System.out.println("❌ 返回为空");
            } else {
                System.out.println("✅ 请求成功，前500字符：");
                System.out.println(body.substring(0, Math.min(500, body.length())));
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 3. AllTick 免费API（需要注册获取 key，此处测试连通性）
     * 如果返回 "Unauthorized" 之类的，说明接口可用但需要 key
     */
    public static void testAllTickAPI() {
        System.out.println("【测试3】AllTick 免费板块资金流向 API（需注册）");
        System.out.println("接口：https://api.alltick.co/market/sector/flow");
        try {
            String urlStr = "https://api.alltick.co/market/sector/flow?country=cn&limit=5";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            String body = sb.toString().trim();
            System.out.println("返回：" + body);
            if (body.contains("Unauthorized") || body.contains("Missing")) {
                System.out.println("⚠️ 接口可用，需要注册 AllTick 并获取免费 API Key。");
            } else if (!body.isEmpty()) {
                System.out.println("✅ 直接返回了数据！");
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 4. DouPand 申万行业指数（可能有涨跌幅，但无资金流向）
     */
    public static void testDoupandSector() {
        System.out.println("【测试4】DouPand - 申万行业指数");
        System.out.println("接口：https://www.doupand.com/api/v1/swindex/daily?ts_code=801001.SI");
        try {
            String urlStr = "https://www.doupand.com/api/v1/swindex/daily?ts_code=801001.SI";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            String body = sb.toString().trim();
            if (body.isEmpty()) {
                System.out.println("❌ 返回为空");
            } else {
                System.out.println("✅ 请求成功，前500字符：");
                System.out.println(body.substring(0, Math.min(500, body.length())));
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }
}