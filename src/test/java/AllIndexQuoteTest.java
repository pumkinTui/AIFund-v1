import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 全量大盘指数行情接口测试类
 * 覆盖：A股、港股、美股
 * 数据源：新浪财经、腾讯财经
 */
public class AllIndexQuoteTest {

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("全量大盘指数行情接口测试");
        System.out.println("==============================================\n");

        // A股
        testSinaAStock();       // 新浪A股
        testTencentAStock();    // 腾讯A股

        // 港股
        testSinaHKStock();      // 新浪港股
        testTencentHKStock();   // 腾讯港股

        // 美股
        testSinaUSStock();      // 新浪美股
        testTencentUSStock();   // 腾讯美股

        System.out.println("\n==============================================");
        System.out.println("测试结束。");
        System.out.println("推荐：新浪接口最稳定，优先使用。");
    }

    // ==================== A股 ====================

    /**
     * 新浪 A股：上证、深证、创业板、科创50
     */
    public static void testSinaAStock() {
        System.out.println("【新浪-A股】sh000001,sz399001,sz399006,sh000688");
        try {
            String url = "https://hq.sinajs.cn/list=sh000001,sz399001,sz399006,sh000688";
            String body = httpGet(url, "GBK", "https://finance.sina.com.cn/");
            String[] lines = body.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] f = extractFields(line);
                if (f.length < 4) continue;
                String name = f[0];
                String price = f[3];
                String change = String.format("%.2f", Double.parseDouble(price) - Double.parseDouble(f[2]));
                String pct = String.format("%.2f%%", (Double.parseDouble(price) - Double.parseDouble(f[2])) / Double.parseDouble(f[2]) * 100);
                System.out.println("  " + name + "：" + price + "  涨跌：" + change + "  涨跌幅：" + pct);
            }
            System.out.println("  ✅ 新浪A股可用\n");
        } catch (Exception e) {
            System.out.println("  ❌ 失败：" + e.getMessage() + "\n");
        }
    }

    /**
     * 腾讯 A股：上证、深证、创业板、科创50
     */
    public static void testTencentAStock() {
        System.out.println("【腾讯-A股】sh000001,sz399001,sz399006,sh000688");
        try {
            String url = "http://qt.gtimg.cn/q=sh000001,sz399001,sz399006,sh000688";
            String body = httpGet(url, "GBK", null);
            String[] lines = body.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] f = extractFields(line);
                if (f.length < 5) continue;
                String name = f[1];
                String price = f[3];
                String change = f[31];
                String pct = f[32] + "%";
                System.out.println("  " + name + "：" + price + "  涨跌：" + change + "  涨跌幅：" + pct);
            }
            System.out.println("  ✅ 腾讯A股可用\n");
        } catch (Exception e) {
            System.out.println("  ❌ 失败：" + e.getMessage() + "\n");
        }
    }

    // ==================== 港股 ====================

    /**
     * 新浪 港股：恒生、国企、恒生科技
     */
    public static void testSinaHKStock() {
        System.out.println("【新浪-港股】rt_hkHSI,rt_hkHSCEI,rt_hkHSTECH");
        try {
            String url = "https://hq.sinajs.cn/list=rt_hkHSI,rt_hkHSCEI,rt_hkHSTECH";
            String body = httpGet(url, "GBK", "https://finance.sina.com.cn/");
            String[] lines = body.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] f = extractFields(line);
                if (f.length < 6) continue;
                String name = f[0];
                String price = f[1];
                String pct = f[2] + "%";
                System.out.println("  " + name + "：" + price + "  涨跌幅：" + pct);
            }
            System.out.println("  ✅ 新浪港股可用\n");
        } catch (Exception e) {
            System.out.println("  ❌ 失败：" + e.getMessage() + "\n");
        }
    }

    /**
     * 腾讯 港股：恒生、国企、恒生科技
     */
    public static void testTencentHKStock() {
        System.out.println("【腾讯-港股】hkHSI,hkHSCEI,hkHSTECH");
        try {
            String url = "http://qt.gtimg.cn/q=hkHSI,hkHSCEI,hkHSTECH";
            String body = httpGet(url, "GBK", null);
            String[] lines = body.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] f = extractFields(line);
                if (f.length < 5) continue;
                String name = f[1];
                String price = f[3];
                String pct = f[32] + "%";
                System.out.println("  " + name + "：" + price + "  涨跌幅：" + pct);
            }
            System.out.println("  ✅ 腾讯港股可用\n");
        } catch (Exception e) {
            System.out.println("  ❌ 失败：" + e.getMessage() + "\n");
        }
    }

    // ==================== 美股 ====================

    /**
     * 新浪 美股：道琼斯、标普500、纳斯达克
     */
    public static void testSinaUSStock() {
        System.out.println("【新浪-美股】int_dji,int_sp500,int_ixic");
        try {
            String url = "https://hq.sinajs.cn/list=int_dji,int_sp500,int_ixic";
            String body = httpGet(url, "GBK", "https://finance.sina.com.cn/");
            String[] lines = body.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] f = extractFields(line);
                if (f.length < 4) continue;
                String name = f[0];
                String price = f[1];
                String pct = f[2] + "%";
                System.out.println("  " + name + "：" + price + "  涨跌幅：" + pct);
            }
            System.out.println("  ✅ 新浪美股可用\n");
        } catch (Exception e) {
            System.out.println("  ❌ 失败：" + e.getMessage() + "\n");
        }
    }

    /**
     * 腾讯 美股：道琼斯、标普500、纳斯达克
     */
    public static void testTencentUSStock() {
        System.out.println("【腾讯-美股】usDJI,usINX,usIXIC");
        try {
            String url = "http://qt.gtimg.cn/q=usDJI,usINX,usIXIC";
            String body = httpGet(url, "GBK", null);
            String[] lines = body.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] f = extractFields(line);
                if (f.length < 5) continue;
                String name = f[1];
                String price = f[3];
                String pct = f[32] + "%";
                System.out.println("  " + name + "：" + price + "  涨跌幅：" + pct);
            }
            System.out.println("  ✅ 腾讯美股可用\n");
        } catch (Exception e) {
            System.out.println("  ❌ 失败：" + e.getMessage() + "\n");
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 通用 HTTP GET 请求
     */
    private static String httpGet(String urlStr, String charset, String referer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        if (referer != null) {
            conn.setRequestProperty("Referer", referer);
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), charset));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    /**
     * 提取双引号内的字段，按逗号分割
     */
    private static String[] extractFields(String line) {
        int start = line.indexOf("\"");
        int end = line.lastIndexOf("\"");
        if (start == -1 || end == -1 || start == end) return new String[0];
        return line.substring(start + 1, end).split(",");
    }
}