import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 天天基金实时估值接口测试（Jsoup版，解决乱码）
 */
public class TiantianFundTest {

    private static final String FUND_CODE = "011103";

    public static void main(String[] args) {
        try {
            String url = "https://fundgz.1234567.com.cn/js/" + FUND_CODE + ".js";

            // 用 Jsoup 发送请求，自动处理编码
            Document doc = Jsoup.connect(url)
                    .ignoreContentType(true)   // 忽略Content-Type，Jsoup会自动识别
                    .timeout(5000)
                    .get();

            // 获取响应正文
            String jsonp = doc.body().text();
            System.out.println("原始返回数据：\n" + jsonp + "\n");

            // 提取 jsonpgz( ... ) 内的 JSON 字符串
            if (!jsonp.contains("jsonpgz(")) {
                System.out.println("返回格式异常");
                return;
            }
            int start = jsonp.indexOf("jsonpgz(") + 8;
            int end = jsonp.lastIndexOf(");");
            String jsonStr = jsonp.substring(start, end);

            // 解析 JSON
            JSONObject json = JSON.parseObject(jsonStr);

            System.out.println("=== 基金实时估值 ===");
            System.out.println("基金名称：" + json.getString("name"));
            System.out.println("基金代码：" + json.getString("fundcode"));
            System.out.println("净值日期：" + json.getString("jzrq"));
            System.out.println("单位净值：" + json.getString("dwjz"));
            System.out.println("实时估算净值：" + json.getString("gsz"));
            System.out.println("估算涨跌幅：" + json.getString("gszzl") + "%");
            System.out.println("估值时间：" + json.getString("gztime"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}