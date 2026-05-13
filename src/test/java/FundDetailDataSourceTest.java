import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 基金详情查询接口测试类
 * 测试各数据源能否通过基金代码返回基金名称、净值、涨跌幅等信息
 */
public class FundDetailDataSourceTest {

    // 测试用基金代码
    private static final String FUND_CODE = "005827"; // 易方达蓝筹

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("基金详情查询接口测试");
        System.out.println("测试基金代码：" + FUND_CODE);
        System.out.println("==============================================\n");

        testTiantianFund();       // 天天基金估值接口
        testTencentFund();        // 腾讯基金行情接口
        testSinaFund();           // 新浪基金行情接口
        testEastMoneyFund();      // 东方财富基金详情接口

        System.out.println("\n==============================================");
        System.out.println("测试结束。");
        System.out.println("推荐排序：天天基金 > 腾讯 > 新浪 > 东方财富");
        System.out.println("天天基金返回字段最全，包含实时估算涨跌幅");
    }

    /**
     * 测试1：天天基金实时估值接口（⭐最推荐）
     * 接口：fundgz.1234567.com.cn/js/{code}.js
     * 返回：基金名称、单位净值、估算净值、估算涨跌幅、估值时间
     */
    public static void testTiantianFund() {
        System.out.println("【测试1】天天基金 - 实时估值");
        System.out.println("接口：fundgz.1234567.com.cn/js/" + FUND_CODE + ".js");
        try {
            String url = "https://fundgz.1234567.com.cn/js/" + FUND_CODE + ".js";

            // 用 Jsoup 处理 JSONP + GBK 编码
            String jsonp = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .timeout(8000)
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
            System.out.println("   净值日期：" + json.getString("jzrq"));
            System.out.println("   ⭐ 返回字段最全，优先使用");

        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试2：腾讯基金行情接口
     * 接口：qt.gtimg.cn/q=jj{code}
     * 返回：基金代码、名称、净值、昨收、涨跌幅等（GBK编码，按~分隔）
     */
    public static void testTencentFund() {
        System.out.println("【测试2】腾讯财经 - 基金行情");
        System.out.println("接口：qt.gtimg.cn/q=jj" + FUND_CODE);
        try {
            String urlStr = "http://qt.gtimg.cn/q=jj" + FUND_CODE;
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

            // 解析格式：v_jj005827="005827~基金名称~...~涨跌幅~净值日期~"
            int start = data.indexOf("\"");
            int end = data.lastIndexOf("\"");
            if (start == -1 || end == -1 || start == end) {
                System.out.println("❌ 数据格式异常");
                return;
            }

            String innerData = data.substring(start + 1, end);
            String[] fields = innerData.split("~");

            System.out.println("✅ 请求成功！");
            System.out.println("   fields数组长度：" + fields.length);

            // 打印所有字段用于调试
            for (int i = 0; i < Math.min(fields.length, 30); i++) {
                System.out.println("   fields[" + i + "] = " + (fields[i].isEmpty() ? "(空)" : fields[i]));
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试3：新浪基金行情接口
     * 接口：hq.sinajs.cn/list=f_{code}
     * 返回：基金名称、最新净值、累计净值、净值日期等（GBK编码，按逗号分隔）
     */
    public static void testSinaFund() {
        System.out.println("【测试3】新浪财经 - 基金行情");
        System.out.println("接口：hq.sinajs.cn/list=f_" + FUND_CODE);
        try {
            String urlStr = "https://hq.sinajs.cn/list=f_" + FUND_CODE;
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
                response.append(line);
            }
            reader.close();
            conn.disconnect();

            String data = response.toString().trim();

            if (data.isEmpty() || data.contains("FAILED")) {
                System.out.println("❌ 请求失败或无数据");
                return;
            }

            // 解析基金净值数据：按逗号分隔
            int start = data.indexOf("\"");
            int end = data.lastIndexOf("\"");
            if (start == -1 || end == -1 || start == end) {
                System.out.println("❌ 数据格式异常");
                return;
            }

            String innerData = data.substring(start + 1, end);
            String[] fields = innerData.split(",");

            System.out.println("✅ 请求成功！");
            System.out.println("   fields数组长度：" + fields.length);

            // fields数组结构：0=基金名称, 1=最新净值, 2=累计净值, 3=前一日净值, 4=前前日净值...
            for (int i = 0; i < Math.min(fields.length, 15); i++) {
                System.out.println("   fields[" + i + "] = " + (fields[i].isEmpty() ? "(空)" : fields[i]));
            }

        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试4：东方财富基金详情接口（备选）
     * 接口：fundgz.1234567.com.cn 的替代方案
     */
    public static void testEastMoneyFund() {
        System.out.println("【测试4】东方财富 - 基金详情");
        System.out.println("接口：api.fund.eastmoney.com/f10/lsjz");
        try {
            String url = "https://api.fund.eastmoney.com/f10/lsjz"
                    + "?fundCode=" + FUND_CODE
                    + "&pageIndex=1&pageSize=5";

            // 用 Jsoup 尝试
            String body = Jsoup.connect(url)
                    .header("Referer", "https://fund.eastmoney.com/")
                    .ignoreContentType(true)
                    .timeout(8000)
                    .get()
                    .body()
                    .text();

            if (body == null || body.isEmpty()) {
                System.out.println("❌ 返回内容为空");
                return;
            }

            System.out.println("✅ 请求成功！返回数据（前500字符）：");
            System.out.println(body.substring(0, Math.min(500, body.length())));

        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }
}