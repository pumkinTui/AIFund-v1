import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 天天基金持仓数据解析测试类（修正版）
 * 先打印页面原始结构，再精确提取前十重仓股
 */
public class TiantianHoldStockParser {

    // 测试用基金代码
    private static final String FUND_CODE = "005827"; // 易方达蓝筹

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("天天基金持仓数据解析测试（修正版）");
        System.out.println("测试基金代码：" + FUND_CODE);
        System.out.println("==============================================\n");

        parseHoldStock(FUND_CODE);
    }

    /**
     * 解析天天基金持仓接口返回的表格数据
     */
    public static void parseHoldStock(String fundCode) {
        System.out.println("接口：fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=" + fundCode);
        try {
            String url = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx"
                    + "?type=jjcc&code=" + fundCode
                    + "&topline=10";

            // 用 Jsoup 获取页面
            Document doc = Jsoup.connect(url)
                    .header("Referer", "https://fundf10.eastmoney.com/")
                    .ignoreContentType(true)
                    .timeout(8000)
                    .get();

            // ====== 方法1：直接解析页面中的表格 ======
            // 先尝试找页面里的所有表格
            Elements tables = doc.select("table");
            System.out.println("页面中找到 " + tables.size() + " 个表格");

            if (!tables.isEmpty()) {
                for (int i = 0; i < tables.size(); i++) {
                    Element table = tables.get(i);
                    Elements rows = table.select("tr");
                    System.out.println("  表格" + (i + 1) + " 有 " + rows.size() + " 行");

                    // 如果有超过3行的表格，很可能就是持仓数据表格
                    if (rows.size() >= 3) {
                        System.out.println("\n✅ 找到候选表格（表格" + (i + 1) + "），开始解析：");
                        parseTableRows(rows);
                        return;
                    }
                }
            }

            // ====== 方法2：如果方法1没找到，打印页面body的部分HTML用于调试 ======
            System.out.println("\n⚠️ 方法1未找到表格，打印页面body前2000字符用于调试：");
            String bodyText = doc.body().html();
            System.out.println(bodyText.substring(0, Math.min(2000, bodyText.length())));

        } catch (Exception e) {
            System.out.println("❌ 请求失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 解析表格行数据
     */
    private static void parseTableRows(Elements rows) {
        // 打印前2行（含表头）的所有td，用于确认数据在哪一列
        System.out.println("\n===== 调试：前2行的所有 td 内容 =====");
        int debugRows = Math.min(2, rows.size());
        for (int i = 0; i < debugRows; i++) {
            Elements tds = rows.get(i).select("td");
            System.out.println("\n第" + (i + 1) + "行（共" + tds.size() + "列）：");
            for (int j = 0; j < tds.size(); j++) {
                System.out.println("  td[" + j + "] = \"" + tds.get(j).text().trim() + "\"");
            }
            // 如果td为空，可能是th标签
            Elements ths = rows.get(i).select("th");
            if (tds.isEmpty() && !ths.isEmpty()) {
                System.out.println("  （这是表头行，使用 th 标签）：");
                for (int j = 0; j < ths.size(); j++) {
                    System.out.println("  th[" + j + "] = \"" + ths.get(j).text().trim() + "\"");
                }
            }
        }

        // 正式解析数据行（跳过表头）
        System.out.println("\n===== 正式解析结果 =====");
        System.out.println(String.format("%-4s %-12s %-20s %-10s",
                "序号", "股票代码", "股票名称", "持仓占比"));

        int count = 0;
        for (int i = 1; i < rows.size(); i++) {  // 跳过第1行（表头）
            Elements tds = rows.get(i).select("td");
            if (tds.isEmpty()) continue;

            String seq = "";
            String stockCode = "";
            String stockName = "";
            String ratio = "";

            // 尝试按照标准表格结构解析：第1列序号、第2列代码、第3列名称、第7列占比
            if (tds.size() >= 7) {
                seq = tds.get(0).text().trim();       // 第1列：序号
                stockCode = tds.get(1).text().trim(); // 第2列：股票代码
                stockName = tds.get(2).text().trim(); // 第3列：股票名称
                ratio = tds.get(6).text().trim();     // 第7列：持仓占比
            }
            // 如果列数不对，尝试取所有 td 的文本
            else {
                for (int j = 0; j < tds.size(); j++) {
                    String text = tds.get(j).text().trim();
                    if (text.matches("\\d+")) {
                        if (seq.isEmpty()) seq = text;
                        else if (stockCode.isEmpty()) stockCode = text;
                    } else if (text.contains("%")) {
                        ratio = text;
                    } else if (!text.isEmpty() && !text.matches("\\d+")) {
                        if (stockName.isEmpty()) stockName = text;
                    }
                }
            }

            // 清洗持仓占比
            String ratioNum = ratio.replace("%", "").replace("--", "").trim();

            if (!stockCode.isEmpty()) {
                count++;
                System.out.println(String.format("%-4s %-12s %-20s %-10s",
                        count, stockCode, stockName, ratioNum + "%"));
            }
        }

        if (count == 0) {
            System.out.println("⚠️ 未能解析到持仓数据，请查看上方调试信息中的表格结构");
        } else {
            System.out.println("\n✅ 成功解析 " + count + " 只重仓股");
        }
    }
}