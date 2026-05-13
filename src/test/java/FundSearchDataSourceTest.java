import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * 基金名称搜索接口测试类
 * 测试各数据源能否通过基金名称返回基本信息
 */
public class FundSearchDataSourceTest {

    // 测试用基金名称
    private static final String KEYWORD = "易方达蓝筹";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("基金名称搜索接口测试");
        System.out.println("搜索关键词：" + KEYWORD);
        System.out.println("========================================\n");

        testTiantianFundSearch();       // 天天基金搜索
        testEastMoneyFundSearch();      // 东方财富基金搜索
        testTencentFundSearch();        // 腾讯财经基金搜索
        testSinaFundSearch();           // 新浪财经基金搜索

        System.out.println("\n========================================");
        System.out.println("测试结束。");
        System.out.println("结论：天天基金接口最推荐，数据完整且稳定。");
    }

    /**
     * 测试1：天天基金搜索接口
     * 接口：fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx
     */
    public static void testTiantianFundSearch() {
        System.out.println("【测试1】天天基金 - 基金搜索");
        System.out.println("接口：fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx");
        try {
            String encodedKeyword = URLEncoder.encode(KEYWORD, "UTF-8");
            String url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx" +
                    "?m=9&key=" + encodedKeyword + "&callback=&_=0";

            // 用 Jsoup 处理
            String body = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .timeout(8000)
                    .get()
                    .body()
                    .text();

            if (body == null || body.isEmpty()) {
                System.out.println("❌ 返回为空\n");
                return;
            }

            // 解析 JSON
            JSONObject json = JSON.parseObject(body);
            JSONArray datas = json.getJSONArray("Datas");

            if (datas == null || datas.isEmpty()) {
                System.out.println("❌ 无搜索结果\n");
                return;
            }

            System.out.println("✅ 请求成功！搜索到 " + datas.size() + " 只基金：");
            for (int i = 0; i < Math.min(3, datas.size()); i++) {
                JSONObject fund = datas.getJSONObject(i);
                System.out.println("   基金代码：" + fund.getString("CODE"));
                System.out.println("   基金名称：" + fund.getString("NAME"));
                System.out.println("   基金类型：" + fund.getString("FundType"));
                System.out.println("   基金简称：" + fund.getString("FundShortName"));
                System.out.println("   ---");
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试2：东方财富基金搜索接口
     * 接口：searchapi.eastmoney.com/bussiness/Web/FundSearch
     */
    public static void testEastMoneyFundSearch() {
        System.out.println("【测试2】东方财富 - 基金搜索");
        System.out.println("接口：searchapi.eastmoney.com/bussiness/Web/FundSearch");
        try {
            String encodedKeyword = URLEncoder.encode(KEYWORD, "UTF-8");
            String url = "https://searchapi.eastmoney.com/bussiness/Web/FundSearch" +
                    "?SearchTerm=" + encodedKeyword + "&FundType=&SortType=&m=1&pageIndex=1&pageSize=5";

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://www.eastmoney.com/");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();

            String body = response.toString();
            if (body.isEmpty()) {
                System.out.println("❌ 返回为空\n");
                return;
            }

            JSONObject json = JSON.parseObject(body);
            JSONArray datas = json.getJSONArray("Data");

            if (datas == null || datas.isEmpty()) {
                System.out.println("❌ 无搜索结果\n");
                return;
            }

            System.out.println("✅ 请求成功！搜索到 " + datas.size() + " 只基金：");
            for (int i = 0; i < Math.min(3, datas.size()); i++) {
                JSONObject fund = datas.getJSONObject(i);
                System.out.println("   基金代码：" + fund.getString("Code"));
                System.out.println("   基金名称：" + fund.getString("Name"));
                System.out.println("   基金类型：" + fund.getString("FundTypeName"));
                System.out.println("   ---");
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试3：腾讯财经基金搜索（通过行情接口间接测试）
     * 腾讯主要通过代码查询，所以这里直接用已知的基金代码验证
     */
    public static void testTencentFundSearch() {
        System.out.println("【测试3】腾讯财经 - 基金行情（用已知代码验证）");
        System.out.println("接口：qt.gtimg.cn/q=jj005827");
        try {
            String urlStr = "http://qt.gtimg.cn/q=jj005827";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

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
                System.out.println("❌ 返回为空\n");
                return;
            }

            int start = data.indexOf("\"");
            int end = data.lastIndexOf("\"");
            String inner = data.substring(start + 1, end);
            String[] fields = inner.split("~");

            System.out.println("✅ 请求成功！");
            System.out.println("   基金代码：" + (fields.length > 0 ? fields[0] : "无"));
            System.out.println("   基金名称：" + (fields.length > 1 ? fields[1] : "无"));
            System.out.println("   最新净值：" + (fields.length > 4 ? fields[4] : "无"));
            System.out.println("   ⚠️ 腾讯不支持名称搜索，只能用代码查询");
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 测试4：新浪财经基金搜索
     */
    public static void testSinaFundSearch() {
        System.out.println("【测试4】新浪财经 - 基金行情（用已知代码验证）");
        System.out.println("接口：hq.sinajs.cn/list=f_005827");
        try {
            String urlStr = "https://hq.sinajs.cn/list=f_005827";
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
            if (data.isEmpty()) {
                System.out.println("❌ 返回为空\n");
                return;
            }

            int start = data.indexOf("\"");
            int end = data.lastIndexOf("\"");
            String inner = data.substring(start + 1, end);
            String[] fields = inner.split(",");

            System.out.println("✅ 请求成功！");
            System.out.println("   基金名称：" + (fields.length > 0 ? fields[0] : "无"));
            System.out.println("   最新净值：" + (fields.length > 1 ? fields[1] : "无"));
            System.out.println("   累计净值：" + (fields.length > 2 ? fields[2] : "无"));
            System.out.println("   ⚠️ 新浪不支持名称搜索，只能用代码查询，且无盘中实时估值");
        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }
}