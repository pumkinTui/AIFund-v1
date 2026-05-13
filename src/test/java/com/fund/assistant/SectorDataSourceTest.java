package com.fund.assistant;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 行业板块资金流向 - 可用数据源全量测试
 * 依次测试：腾讯概念板块、同花顺凤凰证券API、东方财富行业资金流、新浪板块行情
 */
public class SectorDataSourceTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("行业板块资金流向 - 全量数据源测试\n");

        testTencentConceptBoard();       // 腾讯概念板块
        testEastMoneySectorFlow();       // 东方财富行业资金流
        testSinaIndustryAPI();           // 新浪行业板块
        testDoupandSector();             // DouPand 行业板块

        System.out.println("\n========================================");
        System.out.println("测试结束。请选用有效的接口。");
    }

    /**
     * 测试1：腾讯概念板块行情
     * 接口：qt.gtimg.cn/q=ptBK0121,ptBK0479,ptBK0533
     */
    public static void testTencentConceptBoard() {
        System.out.println("【测试1】腾讯财经 - 概念板块行情");
        System.out.println("接口：qt.gtimg.cn/q=ptBK0121（券商）,ptBK0479（白酒）,ptBK0533（半导体）");
        try {
            String urlStr = "http://qt.gtimg.cn/q=ptBK0121,ptBK0479,ptBK0533";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "GBK"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            conn.disconnect();

            String body = response.toString().trim();
            if (body.isEmpty() || body.contains("none_match")) {
                System.out.println("❌ 返回内容为空或无匹配");
            } else {
                System.out.println("✅ 请求成功！返回数据：");
                System.out.println(body.substring(0, Math.min(600, body.length())));
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试2：东方财富行业资金流
     * 接口：push2.eastmoney.com/api/qt/clist/get (Jsoup)
     */
    public static void testEastMoneySectorFlow() {
        System.out.println("【测试2】东方财富 - 行业资金流（Jsoup）");
        System.out.println("接口：push2.eastmoney.com/api/qt/clist/get");

        try {
            // 先尝试 jsoup
            String urlStr = "https://push2.eastmoney.com/api/qt/clist/get" +
                    "?pn=1&pz=5&po=1&np=1&fs=m:90+t:2&fields=f12,f14,f3,f62";
            try {
                String body = Jsoup.connect(urlStr)
                        .ignoreContentType(true)
                        .timeout(5000)
                        .get()
                        .body()
                        .text();
                System.out.println("✅ Jsoup 请求成功！返回数据：");
                System.out.println(body);
                return;
            } catch (Exception ignored) {
                // Jsoup 失败，下面用 HttpURLConnection 再试
            }

            // 再试试 HttpURLConnection
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setRequestProperty("Referer", "https://data.eastmoney.com/");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                System.out.println("✅ HttpURLConnection 请求成功！返回数据：");
                System.out.println(response.toString());
            } catch (Exception e) {
                System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("❌ 外部异常：" + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试3：新浪行业板块行情
     * 接口：vip.stock.finance.sina.com.cn/quotes_service/api/...
     */
    public static void testSinaIndustryAPI() {
        System.out.println("【测试3】新浪财经 - 行业板块行情");
        System.out.println("接口：vip.stock.finance.sina.com.cn/q/go.php/vIndustryRank/...");

        try {
            // 新浪行业板块行情接口（返回JSON）
            String urlStr = "https://vip.stock.finance.sina.com.cn/q/go.php/vIndustryRank/knd/sort/1/display/1/handel/1/daily/1/";

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn/");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();

            String body = response.toString().trim();
            if (body.isEmpty()) {
                System.out.println("❌ 返回内容为空");
            } else {
                System.out.println("✅ 请求成功！返回数据（前500字符）：");
                System.out.println(body.substring(0, Math.min(500, body.length())));
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试4：DouPand 行业板块
     * 接口：doupand.com
     */
    public static void testDoupandSector() {
        System.out.println("【测试4】DouPand - 申万行业板块行情");
        System.out.println("接口：https://doupand.com/api/swindex/...");

        try {
            // DouPand 申万行业指数接口
            String urlStr = "https://www.doupand.com/api/v1/swindex/daily?ts_code=801001.SI";

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();

            String body = response.toString().trim();
            if (body.isEmpty()) {
                System.out.println("❌ 返回内容为空");
            } else {
                System.out.println("✅ 请求成功！返回数据（前500字符）：");
                System.out.println(body.substring(0, Math.min(500, body.length())));
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }
}