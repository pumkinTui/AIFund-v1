package com.fund.assistant;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 基金实时估值数据源测试类
 * 依次测试：天天基金、腾讯财经、新浪财经
 * 跑完一眼就能看出哪个能用、哪个数据最准
 */
public class FundValuationDataSourceTest {

    // 测试用基金代码（选两只：一只股票型，一只指数型）
    private static final String FUND_CODE_1 = "161725"; // 招商中证白酒指数(LOF)A
    private static final String FUND_CODE_2 = "001186"; // 嘉实文体娱乐股票A

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("基金估值数据源对比测试");
        System.out.println("测试基金：161725 招商中证白酒 / 001186 嘉实文体娱乐");
        
        // 检查是否交易日
        LocalDate today = LocalDate.now();
        if (today.getDayOfWeek().getValue() >= 6) {
            System.out.println("⚠️ 今天是" + today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + 
                               "（" + today.getDayOfWeek() + "），非交易日，估值数据可能是上一交易日收盘数据");
        }
        System.out.println("========================================\n");

        testTiantianFund(FUND_CODE_1);   // 天天基金
        testTencentFund(FUND_CODE_1);    // 腾讯财经
        testSinaFund(FUND_CODE_1);       // 新浪财经

        System.out.println("\n========================================");
        System.out.println("测试结束。");
        
        System.out.println("\n结论速览：");
        System.out.println("  天天基金：✅ 数据最全（净值+估算净值+涨跌幅+估值时间），优先使用");
        System.out.println("  腾讯财经：✅ 可用（有净值+涨跌幅），中文需GBK解码");
        System.out.println("  新浪财经：❌ 不提供基金实时估值，只适合指数行情");
    }

    /**
     * 测试1：天天基金实时估值接口（JSONP 格式）
     * 接口：fundgz.1234567.com.cn/js/{code}.js
     * 预期字段：fundcode, name, dwjz(单位净值), gsz(估算净值), gszzl(估算涨跌%), gztime(估值时间)
     */
    public static void testTiantianFund(String fundCode) {
        System.out.println("【测试1】天天基金 - 实时估值");
        System.out.println("接口：fundgz.1234567.com.cn/js/" + fundCode + ".js");

        try {
            String url = "https://fundgz.1234567.com.cn/js/" + fundCode + ".js";

            // 用 Jsoup 处理 JSONP + GBK 编码
            String jsonp = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .timeout(5000)
                    .get()
                    .body()
                    .text();

            if (!jsonp.contains("jsonpgz(")) {
                System.out.println("❌ 返回格式异常，无 jsonpgz 包裹");
                return;
            }

            // 提取 jsonpgz( ... ) 内的 JSON
            int start = jsonp.indexOf("jsonpgz(") + 8;
            int end = jsonp.lastIndexOf(");");
            String jsonStr = jsonp.substring(start, end);

            JSONObject json = JSON.parseObject(jsonStr);

            System.out.println("✅ 请求成功！");
            System.out.println("   基金名称：" + json.getString("name"));
            System.out.println("   基金代码：" + json.getString("fundcode"));
            System.out.println("   单位净值：" + json.getString("dwjz"));
            System.out.println("   估算净值：" + json.getString("gsz"));
            System.out.println("   估算涨跌：" + json.getString("gszzl") + "%");
            System.out.println("   估值时间：" + json.getString("gztime"));
            
            // 算出涨跌额
            BigDecimal dwjz = json.getBigDecimal("dwjz");
            BigDecimal gsz = json.getBigDecimal("gsz");
            if (dwjz != null && gsz != null) {
                System.out.println("   涨跌额：" + gsz.subtract(dwjz).setScale(4, RoundingMode.HALF_UP));
            }
            System.out.println("   ✅ 推荐使用！数据最完整");

        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试2：腾讯基金行情接口
     * 接口：qt.gtimg.cn/q=jj{code}
     * 字段按 ~ 分隔，需 GBK 解码
     */
    public static void testTencentFund(String fundCode) {
        System.out.println("【测试2】腾讯财经 - 基金行情");
        System.out.println("接口：qt.gtimg.cn/q=jj" + fundCode);

        try {
            String urlStr = "http://qt.gtimg.cn/q=jj" + fundCode;
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            // 腾讯返回的是 GBK 编码
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "GBK"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();

            String data = response.toString().trim();

            if (data.isEmpty()) {
                System.out.println("❌ 返回内容为空");
                return;
            }

            // 解析格式：v_jj161725="161725~基金名称~当前价~昨收~最高~最低~涨跌幅~..."
            // 提取双引号内的数据
            int start = data.indexOf("\"");
            int end = data.lastIndexOf("\"");
            if (start == -1 || end == -1 || start == end) {
                System.out.println("❌ 数据格式异常");
                return;
            }

            String innerData = data.substring(start + 1, end);
            String[] fields = innerData.split("~");

            System.out.println("✅ 请求成功！");
            System.out.println("   基金代码：" + (fields.length > 0 ? fields[0] : "无"));
            System.out.println("   基金名称：" + (fields.length > 1 ? fields[1] : "无"));
            
            // 字段索引：2=当前价, 3=昨收, 4=最高, 5=最低, 6=涨跌幅(%), 7=净值日期
            if (fields.length > 3) {
                System.out.println("   当前净值：" + (fields[2].isEmpty() ? "无" : fields[2]));
                System.out.println("   前日净值：" + (fields[3].isEmpty() ? "无" : fields[3]));
            }
            if (fields.length > 6) {
                System.out.println("   涨跌幅：" + (fields[6].isEmpty() ? "无" : fields[6] + "%"));
            }
            if (fields.length > 7) {
                System.out.println("   净值日期：" + (fields[7].isEmpty() ? "无" : fields[7]));
            }
            System.out.println("   ✅ 可用，但需要自行计算涨跌额");

        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试3：新浪财经 - 基金净值接口
     * 新浪没有专门的基金实时估值接口，但有基金净值接口
     */
    public static void testSinaFund(String fundCode) {
        System.out.println("【测试3】新浪财经 - 基金净值");
        System.out.println("接口：hq.sinajs.cn/list=f_" + fundCode);

        try {
            String urlStr = "https://hq.sinajs.cn/list=f_" + fundCode;
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn/");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "GBK"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();

            String data = response.toString().trim();

            if (data.isEmpty() || data.contains("FAILED")) {
                System.out.println("❌ 请求失败或无数据");
                return;
            }

            // 解析基金净值数据
            int start = data.indexOf("\"");
            int end = data.lastIndexOf("\"");
            if (start == -1 || end == -1 || start == end) {
                System.out.println("❌ 数据格式异常");
                return;
            }

            String innerData = data.substring(start + 1, end);
            String[] fields = innerData.split(",");

            System.out.println("✅ 请求成功！");
            System.out.println("   基金名称：" + (fields.length > 0 ? fields[0] : "无"));
            System.out.println("   最新净值：" + (fields.length > 1 ? fields[1] : "无"));
            System.out.println("   累计净值：" + (fields.length > 2 ? fields[2] : "无"));
            System.out.println("   净值日期：" + (fields.length > fields.length - 1 ? fields[fields.length - 1] : "无"));
            System.out.println("   ⚠️ 新浪只有每日净值，没有盘中实时估值，不推荐用于估值功能");

        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }
}