import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class StockQuoteFetcher {

    public static String getTencentStockQuote(String stockCode) {
        String urlStr = String.format("https://web.sqt.gtimg.cn/q=%s", stockCode);
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            // 伪装请求头，降低被反爬虫拦截的概率
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            conn.setRequestProperty("Referer", "https://gu.qq.com/");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "GBK")
            );
            String line;
            StringBuilder result = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();
            conn.disconnect();

            return result.toString(); // 返回原始GBK数据，需要手工按分隔符解析
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        // 示例：平安银行代码 sz000001
        String data = getTencentStockQuote("sz000001");
        System.out.println(data);
    }
}