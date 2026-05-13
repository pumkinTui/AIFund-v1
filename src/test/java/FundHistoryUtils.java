import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import java.sql.*;
import java.util.List;

public class FundHistoryUtils {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String REFERER = "http://fund.eastmoney.com/";

    public static List<JSONObject> getFundHistoryNetValue(String fundCode, int pageIndex, int pageSize) {
        String url = String.format("https://api.fund.eastmoney.com/f10/lsjz?fundCode=%s&pageIndex=%d&pageSize=%d", fundCode, pageIndex, pageSize);
        String response = HttpUtil.createGet(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .timeout(5000)
                .execute()
                .body();
        JSONObject json = JSONObject.parseObject(response);
        if (json.get("Data") == null) {
            System.out.println("API返回 Data=null，原始响应：" + response.substring(0, Math.min(200, response.length())));
            return java.util.Collections.emptyList();
        }
        return json.getJSONObject("Data").getJSONArray("LSJZList").toJavaList(JSONObject.class);
    }

    public static void main(String[] args) throws Exception {
        String fundCode = args.length > 0 ? args[0] : "005827";
        System.out.println("拉取基金 " + fundCode + " 的历史净值并写入数据库...");

        List<JSONObject> list = getFundHistoryNetValue(fundCode, 1, 100);
        if (list.isEmpty()) {
            System.out.println("未获取到数据！");
            return;
        }
        System.out.println("获取到 " + list.size() + " 条记录，开始写入数据库");

        // 直连数据库写入
        String jdbcUrl = "jdbc:mysql://localhost:3306/fund_valuation_assistant?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "8023")) {
            conn.setAutoCommit(false);
            PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO fund_net_value_history (fund_code, net_value_date, unit_net_value, cumulative_net_value, daily_change_rate, create_time) VALUES (?, ?, ?, ?, ?, NOW())");
            int count = 0;
            for (JSONObject item : list) {
                String date = item.getString("FSRQ");
                String dwjz = item.getString("DWJZ");
                String ljjz = item.getString("LJJZ");
                String jzzzl = item.getString("JZZZL");
                if (date == null || dwjz == null) continue;
                ps.setString(1, fundCode);
                ps.setDate(2, java.sql.Date.valueOf(date));
                ps.setBigDecimal(3, new java.math.BigDecimal(dwjz));
                ps.setBigDecimal(4, ljjz != null ? new java.math.BigDecimal(ljjz) : null);
                ps.setBigDecimal(5, jzzzl != null ? new java.math.BigDecimal(jzzzl) : null);
                ps.addBatch();
                count++;
            }
            int[] results = ps.executeBatch();
            conn.commit();
            System.out.println("成功写入 " + results.length + " 条净值记录！");
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
