package com.fund.assistant.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.config.SpringAiConfig;
import com.fund.assistant.dto.AiChatImageRequestDTO;
import com.fund.assistant.dto.AiChatRequestDTO;
import com.fund.assistant.dto.AiChatResponseDTO;
import com.fund.assistant.dto.FundHoldBuyDTO;
import com.fund.assistant.dto.FundHoldSellDTO;
import com.fund.assistant.dto.FundInvestPlanCreateDTO;
import com.fund.assistant.dto.GroupCreateDTO;
import com.fund.assistant.entity.AiAlertRule;
import com.fund.assistant.entity.AiChatHistory;
import com.fund.assistant.entity.AiOperateRecord;
import com.fund.assistant.entity.FundTradeRecord;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundUserGroup;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.AiAlertRuleMapper;
import com.fund.assistant.mapper.AiChatHistoryMapper;
import com.fund.assistant.mapper.AiOperateRecordMapper;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.FundTradeRecordMapper;
import com.fund.assistant.mapper.FundUserGroupMapper;
import com.fund.assistant.mapper.UserFundHoldMapper;
import com.fund.assistant.service.AiChatMemoryService;
import com.fund.assistant.service.AiChatService;
import com.fund.assistant.service.FundBaseInfoService;
import com.fund.assistant.service.FundInvestPlanService;
import com.fund.assistant.service.FundUserGroupService;
import com.fund.assistant.service.UserFundHoldService;
import com.fund.assistant.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI对话服务实现 - 基于Spring AI + DeepSeek
 * 包含：系统提示词注入、对话记忆、长期用户画像、操作指令解析与执行
 */
@Service
@Slf4j
public class AiChatServiceImpl implements AiChatService {

    @Autowired
    private AiChatHistoryMapper aiChatHistoryMapper;

    @Autowired
    private AiOperateRecordMapper aiOperateRecordMapper;

    @Autowired
    private AiAlertRuleMapper aiAlertRuleMapper;

    @Autowired
    private UserFundHoldMapper userFundHoldMapper;

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    @Autowired
    private AiChatMemoryService aiChatMemoryService;

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatMemory chatMemory;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashscopeApiKey;

    @Autowired
    private UserFundHoldService userFundHoldService;

    @Autowired
    private FundInvestPlanService fundInvestPlanService;

    @Autowired
    private FundUserGroupService fundUserGroupService;

    @Autowired
    private FundBaseInfoService fundBaseInfoService;

    @Autowired
    private FundTradeRecordMapper fundTradeRecordMapper;

    @Autowired
    private FundUserGroupMapper fundUserGroupMapper;

    private static final String[] FUND_KEYWORDS = {
            "基金", "理财", "收益", "估值", "定投", "加仓", "减仓", "持仓", "自选",
            "净值", "行情", "板块", "指数", "股票", "风险", "配置", "投资", "理财通",
            "余额宝", "零钱通", "市场", "大盘", "上证", "深证", "创业板", "科创板",
            "港股", "美股", "纳斯达克", "道琼斯", "恒生", "沪深", "A股", "牛市",
            "熊市", "震荡", "反弹", "回调", "分红", "费率", "手续费", "申购", "赎回",
            "C类", "A类", "ETF", "LOF", "FOF", "量化", "主动", "被动", "指数型",
            "股票型", "债券型", "混合型", "货币型", "半导体", "新能源", "消费",
            "医药", "科技", "军工", "白酒", "银行", "券商", "地产", "芯片", "AI",
            "人工智能", "光伏", "北向资金", "主力资金", "成交量", "换手率",
            "加仓", "买入", "减仓", "卖出", "赎回", "清仓", "止盈", "止损", "定投"
    };

    private static final String IMAGE_EXTRACTION_PROMPT = "你是一个基金APP截图OCR解析器，只负责从截图中提取原始数据，不做任何操作建议。\n\n"
            + "【你需要提取的字段】\n"
            + "- 基金名称(name)：字符串，不要带基金代码。\n"
            + "- 基金代码(fundCode)：字符串，统一为6位数字，如果截图里只给了名称没给代码，此字段留空。\n"
            + "- 操作类型(type)：整数，1=加仓/买入，2=减仓/卖出，3=定投配置，0=识别失败或仅持仓展示。\n"
            + "- 申购金额(investAmount)：数字，单位\"元\"。如果截图是卖出操作，此字段为 0，并填入下面的 sellShares。\n"
            + "- 卖出份额(sellShares)：数字，单位\"份\"。\n"
            + "- 卖出比例(sellRatio)：数字，例如 1/3=0.33，1/2=0.5，全部=1.0。\n"
            + "- 认购/申购费率(chargeRate)：数字，例如 0.15 表示 0.15% 费率。\n"
            + "- 定投周期(investCycle)：字符串，\"daily\"、\"weekly\"、\"biweekly\"、\"monthly\" 之一。\n"
            + "- 定投扣款日(cycleDay)：数字，每周几(1-7) / 每月几号(1-28)。\n"
            + "- 定投金额(investAmount)：数字，与上面 investAmount 字段相同，定投场景必须是定投金额。\n"
            + "- 分红方式(dividendType)：字符串，现金分红 / 红利再投资。\n"
            + "- 交易时间(transactionTime)：字符串，\"15:00前\" 或 \"15:00后\"。\n"
            + "- 图片中显示的金额(displayedAmount)：数字，直接截图里看到的数字（用于校验后端计算是否一致）。\n\n"
            + "【需要在返回时额外注意的上下文信息】\n"
            + "1. 如果截图里基金代码和名称同时存在，以代码为准来填充 fundCode。\n"
            + "2. 所有费率保留两位小数。\n"
            + "3. 如果截图不清晰或部分字段遮挡，不要胡乱猜测，在对应的字段里填 null 并在 summary 里说明。\n"
            + "4. 定投金额不要和单次加仓金额搞混。\n"
            + "5. 如果是持仓截图而非交易截图，type 填 0，并把截图中展示的所有基金代码放入 holdings 数组。\n"
            + "6. 卖出比例如果不准，可以结合卖出份额和持有份额计算，如果看不清楚不要猜。\n"
            + "7. 谨慎处理交易时间，很多APP会显示\"15:00前\"或\"15:00后\"，没有就填 null。\n\n"
            + "【JSON 输出格式】\n"
            + "在你的回答末尾附加下面的 JSON 代码块：\n"
            + "```json\n"
            + "{\n"
            + "  \"operation\": {\n"
            + "    \"type\": 1,\n"
            + "    \"summary\": \"加仓华夏成长混合 1000元\",\n"
            + "    \"params\": {\n"
            + "      \"fundCode\": \"000001\",\n"
            + "      \"fundName\": \"华夏成长混合\",\n"
            + "      \"investAmount\": 1000.00,\n"
            + "      \"sellShares\": 0,\n"
            + "      \"sellRatio\": null,\n"
            + "      \"chargeRate\": 0.15,\n"
            + "      \"investCycle\": null,\n"
            + "      \"cycleDay\": null,\n"
            + "      \"dividendType\": null,\n"
            + "      \"transactionTime\": \"15:00前\",\n"
            + "      \"displayedAmount\": 1000.00,\n"
            + "      \"holdings\": []\n"
            + "    }\n"
            + "  }\n"
            + "}\n"
            + "```\n\n"
            + "如果图片不清晰或无法识别，请在 JSON 里把 type 填 0，summary 填\"图片识别失败\"，不要强行猜测。";

    @Override
    public AiChatResponseDTO chat(AiChatRequestDTO request) {
        Long userId = UserContext.getUserId();
        String question = request.getQuestion().trim();
        log.info("用户 {} 发起AI对话，问题：{}", userId, question);

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
            log.info("新建会话，SessionId: {}", sessionId);
        }

        String answer;
        String fundContext = buildFundContext(question);
        String portfolioContext = (!Boolean.TRUE.equals(request.getFromImage()) && isFundRelated(question)) ? buildPortfolioContext(userId) : "";
        String memoryContext = aiChatMemoryService.buildMemoryContext(userId);
        answer = callDeepSeekModel(question, fundContext + "\n" + portfolioContext, memoryContext, sessionId);
        aiChatMemoryService.learnFromConversation(userId, question, answer);

        // 从 AI 回答中提取操作 JSON，保存操作记录
        AiChatResponseDTO response = new AiChatResponseDTO();
        String operationJson = extractOperationJson(answer);
        if (operationJson != null) {
            try {
                JSONObject opObj = JSON.parseObject(operationJson);
                JSONObject operation = opObj.getJSONObject("operation");
                int type = operation.getIntValue("type");
                String summary = operation.getString("summary");
                JSONObject params = operation.getJSONObject("params");

                // 存到 ai_operate_record 表
                AiOperateRecord record = new AiOperateRecord();
                record.setUserId(userId);
                record.setOperateType((byte) type);
                record.setOperateContent(params != null ? params.toJSONString() : "{}");
                record.setIsConfirmed((byte) 0);
                record.setIsExecuted((byte) 0);
                record.setCreateTime(LocalDateTime.now());
                record.setUpdateTime(LocalDateTime.now());
                aiOperateRecordMapper.insert(record);

                log.info("AI生成操作方案，类型：{}，摘要：{}，记录ID：{}", type, summary, record.getId());

                response.setHasOperation(true);
                response.setOperationId(record.getId());
                response.setOperationType((byte) type);
                response.setOperationSummary(summary);

                // 从回答中去掉 JSON 块，用户看到的是纯文本
                answer = cleanOperationJson(answer);
            } catch (Exception e) {
                log.warn("解析AI操作JSON失败，忽略：{}", e.getMessage());
            }
        }

        // 保存聊天记录（图片识别用displayQuestion，避免内部prompt泄露到前端）
        String displayQ = request.getDisplayQuestion() != null ? request.getDisplayQuestion() : question;
        saveChatLog(userId, sessionId, displayQ, answer, request.getImageUrl());

        response.setSessionId(sessionId);
        response.setQuestion(displayQ);
        response.setAnswer(answer);
        response.setResponseTime(LocalDateTime.now());

        return response;
    }

    /**
     * 从 AI 回答中提取 ```json ... ``` 块
     */
    private String extractOperationJson(String answer) {
        if (answer == null || answer.isEmpty()) {
            return null;
        }
        int start = answer.indexOf("```json");
        if (start == -1) {
            return null;
        }
        start += 7; // 跳过 ```json
        int end = answer.indexOf("```", start);
        if (end == -1) {
            return null;
        }
        return answer.substring(start, end).trim();
    }

    /**
     * 去掉回答末尾的 ```json ... ``` 块
     */
    private String cleanOperationJson(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer;
        }
        int start = answer.indexOf("```json");
        if (start == -1) {
            return answer;
        }
        return answer.substring(0, start).trim();
    }

    // 从用户问题中提取基金代码，查库并注入基金信息
    private String buildFundContext(String question) {
        if (question == null || question.isEmpty()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{6})\\b").matcher(question);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String code = m.group(1);
            try {
                FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                        new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, code));
                if (fund == null) {
                    try { fundBaseInfoService.syncFundBaseInfo(code); } catch (Exception e) { /* */ }
                    fund = fundBaseInfoMapper.selectOne(
                            new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, code));
                }
                if (fund != null) {
                    sb.append("【用户提到的基金" + code + "的真实信息】名称：" + fund.getFundName());
                    if (fund.getFundShortName() != null) sb.append("，简称：" + fund.getFundShortName());
                    if (fund.getFundType() != null) sb.append("，类型：" + fund.getFundType());
                    if (fund.getLatestNetValue() != null) sb.append("，最新净值：" + fund.getLatestNetValue());
                    sb.append("\n重要：以上是基金" + code + "的真实信息，你必须基于此信息回答，不要自己编造基金名称！\n");
                }
            } catch (Exception e) { /* */ }
        }
        return sb.toString();
    }

    /**
     * 构建用户持仓上下文
     */
    private String buildPortfolioContext(Long userId) {
        LambdaQueryWrapper<UserFundHold> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFundHold::getUserId, userId);
        wrapper.gt(UserFundHold::getHoldShares, BigDecimal.ZERO);
        List<UserFundHold> holdList = userFundHoldMapper.selectList(wrapper);

        if (holdList.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("【用户当前持仓信息】\n");

        BigDecimal totalValue = BigDecimal.ZERO;
        for (UserFundHold hold : holdList) {
            FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                    new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, hold.getFundCode()));

            String fundName = fund != null ? fund.getFundShortName() : hold.getFundCode();
            String fundType = fund != null ? fund.getFundType() : "未知";

            BigDecimal latestNetValue = fund != null && fund.getLatestNetValue() != null
                    ? fund.getLatestNetValue()
                    : BigDecimal.ZERO;
            BigDecimal marketValue = hold.getHoldShares().multiply(latestNetValue).setScale(2, RoundingMode.HALF_UP);
            totalValue = totalValue.add(marketValue);

            context.append(String.format("- %s(%s)：%s型，持有 %.2f 份，成本 %.2f 元，当前净值 %.4f\n",
                    fundName, hold.getFundCode(), fundType, hold.getHoldShares(), hold.getTotalCostAmount(), latestNetValue));
        }

        context.append(String.format("总持仓市值：%.2f 元\n", totalValue));
        context.append("请结合这些持仓信息，以及当前市场行情和用户的风格（如果没有就只结合市场行情）给用户提供有针对性的分析和建议。");
        return context.toString();
    }

    /**
     * 调用大模型 获取回答
     */
    private String callDeepSeekModel(String question, String portfolioContext,
            String memoryContext, String sessionId) {
        try {
            StringBuilder systemContext = new StringBuilder();
            systemContext.append(SpringAiConfig.WANGCAI_SYSTEM_PROMPT).append("\n\n");
            if (!portfolioContext.isEmpty()) {
                systemContext.append(portfolioContext).append("\n\n");
            }
            systemContext.append(memoryContext);

            String response = chatClient.prompt()
                    .system(spec -> spec.text(systemContext.toString()))
                    .user(question)
                    .advisors(a -> a
                            .param("chat_memory_conversation_id", sessionId)
                            .param("chat_memory_retrieve_size", 10))
                    .call()
                    .content();
            log.debug("DeepSeek响应：{}", response);
            return response;

        } catch (Exception e) {
            log.error("调用DeepSeek模型失败", e);
            return "哎呀，旺财这边网络有点卡顿，稍等一下再试试吧~ \n\n⚠️ 旺财提醒：以上内容仅供参考，不构成投资建议哦~";
        }
    }

    /**
     * 判断是否与基金理财相关
     */
    private boolean isFundRelated(String question) {
        String lower = question.toLowerCase();
        for (String keyword : FUND_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }



    /**
     * 保存对话记录到数据库
     */
    private void saveChatLog(Long userId, String sessionId, String question, String answer) {
        saveChatLog(userId, sessionId, question, answer, null);
    }

    private void saveChatLog(Long userId, String sessionId, String question, String answer, String imageUrl) {
        try {
            AiChatHistory history = new AiChatHistory();
            history.setUserId(userId);
            history.setSessionId(sessionId);
            history.setQuestion(question);
            history.setAnswer(answer);
            history.setImageUrl(imageUrl);
            history.setDelFlag((byte) 0);
            history.setCreateTime(LocalDateTime.now());

            Long count = aiChatHistoryMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatHistory>()
                            .eq(AiChatHistory::getSessionId, sessionId)
                            .eq(AiChatHistory::getUserId, userId)
                            .eq(AiChatHistory::getDelFlag, (byte) 0));
            if (count == 0 || count == null) {
                String name = (question != null && !question.isEmpty()) ? question : "[图片]";
                name = name.length() > 30 ? name.substring(0, 30) + "..." : name;
                history.setSessionName(name);
            }

            aiChatHistoryMapper.insert(history);
        } catch (Exception e) {
            log.error("保存对话记录失败", e);
        }
    }

    // ==================== 多模态图片识别 ====================

    // 从OCR提取数据构建操作JSON（Java保证格式正确）
    private JSONObject buildOperationFromOCR(String extractedData, String userPrompt) {
        try {
            // 提取JSON（处理```json包裹）
            String jsonStr = extractedData;
            int js = jsonStr.indexOf("```json"); int je = jsonStr.lastIndexOf("```");
            if (js != -1 && je > js) jsonStr = jsonStr.substring(js + 7, je).trim();
            int start = jsonStr.indexOf("{");
            int end = jsonStr.lastIndexOf("}");
            if (start == -1 || end == -1) return null;
            jsonStr = jsonStr.substring(start, end + 1);
            JSONObject ocr = JSON.parseObject(jsonStr);
            JSONArray items = ocr.getJSONArray("items");
            if (items == null || items.isEmpty()) return null;
            String screenshotType = ocr.getString("screenshotType");
            String platform = ocr.getString("platform");
            String userLower = userPrompt.toLowerCase();

            JSONObject operation = new JSONObject();
            JSONObject params = new JSONObject();

            // 判断操作类型
            if (userLower.contains("新建") || userLower.contains("创建") || userLower.contains("分组")) {
                operation.put("type", 6);
                // 从用户指令提取真实分组名
                String groupName = extractGroupName(userPrompt);
                if (groupName == null && platform != null) groupName = platform + "持仓";
                if (groupName == null) groupName = "导入持仓";
                params.put("groupName", groupName);
                JSONArray holdings = new JSONArray();
                for (int i = 0; i < items.size(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    JSONObject h = new JSONObject();
                    String code = item.getString("fundCode");
                    String fname = item.getString("fundName");
                    // 代码为空时按名称模糊查库
                    if ((code == null || code.isEmpty()) && fname != null && !fname.isEmpty()) {
                        String matched = matchFundCodeByName(fname);
                        h.put("fundCode", matched != null ? matched : "");
                    } else {
                        h.put("fundCode", code != null ? code : "");
                    }
                    h.put("fundName", fname != null ? fname : "");
                    Object amt = item.get("amount");
                    h.put("amount", amt != null ? amt : 0);
                    holdings.add(h);
                }
                params.put("holdings", holdings);
                operation.put("summary", "创建" + params.getString("groupName") + "分组，导入" + items.size() + "只基金");
            } else if (userLower.contains("买入") || userLower.contains("加仓")) {
                operation.put("type", 1);
                if (items.size() > 0) {
                    JSONObject item = items.getJSONObject(0);
                    params.put("fundCode", item.getString("fundCode") != null ? item.getString("fundCode") : "");
                    params.put("fundName", item.getString("fundName") != null ? item.getString("fundName") : "");
                    Object amt = item.get("amount");
                    params.put("investAmount", amt != null ? amt : 0);
                }
                operation.put("summary", "加仓操作");
            } else if (userLower.contains("卖出") || userLower.contains("减仓")) {
                operation.put("type", 2);
                if (items.size() > 0) {
                    JSONObject item = items.getJSONObject(0);
                    params.put("fundCode", item.getString("fundCode") != null ? item.getString("fundCode") : "");
                    params.put("fundName", item.getString("fundName") != null ? item.getString("fundName") : "");
                }
                operation.put("summary", "减仓操作");
            } else {
                operation.put("type", 0);
                operation.put("summary", "图片识别结果，包含" + items.size() + "只基金");
            }
            operation.put("params", params);
            JSONObject wrapper = new JSONObject();
            wrapper.put("operation", operation);
            log.info("Java自动生成操作JSON：{}", wrapper.toJSONString());
            return wrapper;
        } catch (Exception e) {
            log.warn("构建OCR操作JSON失败：{}", e.getMessage());
            return null;
        }
    }

    // 将Java生成的JSON注入为操作记录
    private void injectOperationFromJson(AiChatResponseDTO response, Long userId, String sessionId, JSONObject opJson) {
        try {
            JSONObject operation = opJson.getJSONObject("operation");
            int type = operation.getIntValue("type");
            String summary = operation.getString("summary");
            JSONObject params = operation.getJSONObject("params");

            AiOperateRecord record = new AiOperateRecord();
            record.setUserId(userId);
            record.setOperateType((byte) type);
            record.setOperateContent(params != null ? params.toJSONString() : "{}");
            record.setIsConfirmed((byte) 0);
            record.setIsExecuted((byte) 0);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            aiOperateRecordMapper.insert(record);

            response.setHasOperation(true);
            response.setOperationId(record.getId());
            response.setOperationType((byte) type);
            response.setOperationSummary(summary);
            log.info("已将Java生成的JSON注入为操作记录，ID：{}，类型：{}", record.getId(), type);
        } catch (Exception e) {
            log.warn("注入操作JSON失败：{}", e.getMessage());
        }
    }

    // 从用户指令提取分组名
    private String extractGroupName(String prompt) {
        if (prompt == null) return null;
        // 模式1: "分组名称(为|是|叫)xxx" / "分组名xxx" — 匹配到逗号或空格或中文标点为止
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("分组名(?:称|字)?(?:为|是|叫|：|:)?\\s*([^，,\\s。；;、]{1,15})");
        java.util.regex.Matcher m1 = p1.matcher(prompt);
        if (m1.find()) {
            String name = m1.group(1).trim();
            if (!name.isEmpty() && name.length() < 20) return name;
        }
        // 模式2: "新建xxx分组"/"创建xxx分组"
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("(?:新建|创建|建个?|弄个?)(.{1,10})(?:持仓)?(?:分组|自选)");
        java.util.regex.Matcher m2 = p2.matcher(prompt);
        if (m2.find()) {
            String name = m2.group(1).trim();
            if (!name.isEmpty() && name.length() < 15) return name;
        }
        return null;
    }

    // 确保基金信息已入库
    private void ensureFundExists(String fundCode) {
        try {
            FundBaseInfo exist = fundBaseInfoMapper.selectOne(
                    new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, fundCode));
            if (exist == null) {
                log.info("基金{}不在本地库，尝试同步", fundCode);
                try {
                    fundBaseInfoService.syncFundBaseInfo(fundCode);
                } catch (Exception e) {
                    log.warn("同步基金{}失败，创建最小记录：{}", fundCode, e.getMessage());
                    FundBaseInfo fb = FundBaseInfo.builder()
                            .fundCode(fundCode)
                            .fundName("基金" + fundCode)
                            .fundShortName("基金" + fundCode)
                            .createTime(java.time.LocalDateTime.now())
                            .build();
                    fundBaseInfoMapper.insert(fb);
                }
            }
        } catch (Exception e) { log.warn("ensureFundExists失败：{}", e.getMessage()); }
    }

    // 根据基金名称模糊查询fund_code，找不到则尝试同步
    private String matchFundCodeByName(String fundName) {
        try {
            FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                    new LambdaQueryWrapper<FundBaseInfo>().like(FundBaseInfo::getFundShortName, fundName));
            if (fund == null) fund = fundBaseInfoMapper.selectOne(
                    new LambdaQueryWrapper<FundBaseInfo>().like(FundBaseInfo::getFundName, fundName));
            if (fund != null) {
                log.info("基金名称模糊匹配成功：{} → {}", fundName, fund.getFundCode());
                return fund.getFundCode();
            }
            // 数据库中不存在，尝试从东方财富搜索同步
            log.info("基金{}不在本地库，尝试从东方财富搜索同步", fundName);
            try {
                String searchUrl = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=9&key="
                        + java.net.URLEncoder.encode(fundName, "UTF-8") + "&callback=&_=0";
                org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
                String resp = rt.getForObject(searchUrl, String.class);
                if (resp != null && resp.contains("\"CODE\"")) {
                    // m=9返回格式：Datas数组，字段CODE/NAME等
                    JSONObject wrapper = JSON.parseObject(resp);
                    JSONArray arr = wrapper.getJSONArray("Datas");
                    if (arr != null && !arr.isEmpty()) {
                        JSONObject first = arr.getJSONObject(0);
                        String code = first.getString("CODE");
                        if (code != null && code.length() == 6) {
                            log.info("天天基金搜索成功：{} → {}", fundName, code);
                            try { fundBaseInfoService.syncFundBaseInfo(code); return code; } catch (Exception e2) { log.warn("同步基金{}失败", code); }
                        }
                    }
                } else if (resp != null && resp.contains("\"Code\"")) {
                    // m=1返回格式：数组
                    JSONArray arr = JSON.parseArray(resp);
                    if (arr != null && !arr.isEmpty()) {
                        JSONObject first = arr.getJSONObject(0);
                        String code = first.getString("Code");
                        if (code != null && code.length() == 6) {
                            // 找到了代码，同步基金信息
                            log.info("东方财富搜索成功：{} → {}", fundName, code);
                            try {
                                fundBaseInfoService.syncFundBaseInfo(code);
                                return code;
                            } catch (Exception e2) { log.warn("同步基金{}失败", code); }
                        }
                    }
                }
            } catch (Exception e3) { log.warn("东方财富搜索基金{}失败：{}", fundName, e3.getMessage()); }
        } catch (Exception e) { log.warn("基金名称匹配失败：{}", e.getMessage()); }
        return null;
    }

    private String buildExtractionPrompt() {
        return "你是截图解析器。基金代码(6位数字)通常在基金名旁边务必提取。输出JSON：\n"
                + "```json\n"
                + "{\"description\":\"图片内容的一句话描述\",\"platform\":\"平台名或null\",\"screenshotType\":\"持仓截图/交易截图/其他\",\"items\":[{\"fundName\":\"完整基金名\",\"fundCode\":\"6位代码或null\",\"action\":\"买入/卖出/持仓\",\"amount\":金额或null,\"shares\":份额或null}]}\n"
                + "```";
    }

    @Override
    public void chatWithImageStream(AiChatImageRequestDTO request, SseEmitter emitter) {
        String sessionId = request.getSessionId() != null && !request.getSessionId().isEmpty()
                ? request.getSessionId() : UUID.randomUUID().toString().replace("-", "");
        String userPrompt = request.getUserPrompt() != null ? request.getUserPrompt() : "请分析这张截图";
        try {
            String extractedData = callQwenVL(request.getImageUrl());
            log.info("流式图片识别-通义千问提取结果：{}", extractedData);
            boolean hasFundData = extractedData != null && extractedData.contains("\"fundName\"");
            JSONObject opJson = hasFundData ? buildOperationFromOCR(extractedData, userPrompt) : null;
            String deepSeekPrompt = userPrompt + "\n\n【截图OCR数据】\n" + extractedData;
            if (opJson != null) {
                deepSeekPrompt += "\n\n系统已生成操作方案，你只需用友好语气告诉用户即将执行什么。";
            } else {
                deepSeekPrompt += "\n\n这张图没有基金相关数据，请用自然友好的语气回应图片内容，然后巧妙地引导用户聊聊基金理财（比如问问用户的投资偏好、最近关注什么板块等）。";
            }
            AiChatRequestDTO chatReq = new AiChatRequestDTO();
            chatReq.setSessionId(sessionId);
            chatReq.setQuestion(deepSeekPrompt);
            chatReq.setFromImage(true);
            chatReq.setDisplayQuestion(userPrompt);
            chatReq.setImageUrl(request.getImageUrl());
            if (opJson != null) {
                // 先发meta事件（sessionId）
                JSONObject meta = new JSONObject();
                meta.put("sessionId", sessionId);
                emitter.send(SseEmitter.event().name("meta").data(meta.toJSONString()));
            }
            chatStream(chatReq, emitter);
            // 注入操作JSON
            if (opJson != null) {
                Long userId = UserContext.getUserId();
                injectOperationFromJsonEmitter(emitter, userId, opJson);
            }
        } catch (Exception e) {
            log.error("流式图片识别失败：{}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().data("{\"content\":\"抱歉，图片识别失败，请稍后重试。\"}"));
                emitter.complete();
            } catch (IOException ignored) {}
        }
    }

    // 通过SSE发送操作事件（不依赖AiChatResponseDTO）
    private void injectOperationFromJsonEmitter(SseEmitter emitter, Long userId, JSONObject opJson) {
        try {
            JSONObject operation = opJson.getJSONObject("operation");
            int type = operation.getIntValue("type");
            String summary = operation.getString("summary");
            JSONObject params = operation.getJSONObject("params");
            AiOperateRecord record = new AiOperateRecord();
            record.setUserId(userId);
            record.setOperateType((byte) type);
            record.setOperateContent(params != null ? params.toJSONString() : "{}");
            record.setIsConfirmed((byte) 0);
            record.setIsExecuted((byte) 0);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            aiOperateRecordMapper.insert(record);
            JSONObject opEvent = new JSONObject();
            opEvent.put("hasOperation", true);
            opEvent.put("operationId", record.getId());
            opEvent.put("operationType", type);
            opEvent.put("operationSummary", summary);
            emitter.send(SseEmitter.event().name("operation").data(opEvent.toJSONString()));
            log.info("流式图片识别生成操作方案，ID：{}，类型：{}", record.getId(), type);
        } catch (Exception e) {
            log.warn("注入操作JSON失败：{}", e.getMessage());
        }
    }

    private String callQwenVL(String imageUrl) {
        JSONObject reqBody = new JSONObject();
        reqBody.put("model", "qwen-vl-max");
        reqBody.put("temperature", 0.1);
        reqBody.put("max_tokens", 1024);
        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", buildExtractionPrompt());
        messages.add(sysMsg);
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        JSONArray userContent = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", "请提取这张截图中的基金数据");
        userContent.add(textPart);
        JSONObject imgPart = new JSONObject();
        imgPart.put("type", "image_url");
        JSONObject imgUrl = new JSONObject();
        imgUrl.put("url", imageUrl);
        imgPart.put("image_url", imgUrl);
        userContent.add(imgPart);
        userMsg.put("content", userContent);
        messages.add(userMsg);
        reqBody.put("messages", messages);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + dashscopeApiKey);
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(reqBody.toJSONString(), headers);
        org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
        org.springframework.http.ResponseEntity<String> resp = rt.postForEntity(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", entity, String.class);
        JSONObject respJson = JSON.parseObject(resp.getBody());
        return respJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }

    @Override
    public AiChatResponseDTO chatWithImage(AiChatImageRequestDTO request) {
        Long userId = UserContext.getUserId();
        String imageUrl = request.getImageUrl();
        String userPrompt = request.getUserPrompt() != null ? request.getUserPrompt() : "请分析这张基金截图";
        log.info("用户 {} 发起图片识别请求，图片URL：{}，提示词：{}", userId, imageUrl, userPrompt);

        // ========== 核心修复：sessionId 绝对不能二次赋值 ==========
        String sessionId;
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        } else {
            sessionId = request.getSessionId();
        }

        try {
            // 第一步：通义千问提取图片原始数据
            JSONObject reqBody = new JSONObject();
            reqBody.put("model", "qwen-vl-max");
            reqBody.put("temperature", 0.1);
            reqBody.put("max_tokens", 1024);
            JSONArray messages = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", buildExtractionPrompt());
            messages.add(sysMsg);
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            JSONArray userContent = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", "请提取这张截图中的基金数据");
            userContent.add(textPart);
            JSONObject imgPart = new JSONObject();
            imgPart.put("type", "image_url");
            JSONObject imgUrl = new JSONObject();
            imgUrl.put("url", imageUrl);
            imgPart.put("image_url", imgUrl);
            userContent.add(imgPart);
            userMsg.put("content", userContent);
            messages.add(userMsg);
            reqBody.put("messages", messages);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + dashscopeApiKey);
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(reqBody.toJSONString(), headers);

            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            org.springframework.http.ResponseEntity<String> resp = rt.postForEntity(
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", entity, String.class);
            JSONObject respJson = JSON.parseObject(resp.getBody());
            String extractedData = respJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            log.info("通义千问图片提取结果：{}", extractedData);

            boolean hasFundData = extractedData != null && extractedData.contains("\"fundName\"");
            JSONObject opJson = hasFundData ? buildOperationFromOCR(extractedData, userPrompt) : null;
            String opJsonStr = opJson != null ? opJson.toJSONString() : "";

            // 第三步：DeepSeek只负责写友好的文字回复
            String deepSeekPrompt = userPrompt + "\n\n【截图OCR数据】\n" + extractedData;
            if (opJson != null) {
                deepSeekPrompt += "\n\n系统已根据OCR数据生成操作方案，你只需用友好语气告诉用户即将执行什么，不要输出JSON。";
            } else {
                deepSeekPrompt += "\n\n这张图不含基金数据。请根据OCR中的description字段自然回应用户发的图片内容，然后巧妙引导用户聊基金理财。不要提'识别为空'或'无法识别'。";
            }
            AiChatRequestDTO chatReq = new AiChatRequestDTO();
            chatReq.setSessionId(sessionId);
            chatReq.setQuestion(deepSeekPrompt);
            chatReq.setFromImage(true);
            chatReq.setDisplayQuestion(userPrompt);
            chatReq.setImageUrl(imageUrl);

            AiChatResponseDTO result = chat(chatReq);
            // 始终用Java生成的JSON（保证正确），替换DeepSeek可能误生成的
            if (opJson != null) {
                injectOperationFromJson(result, userId, sessionId, opJson);
            }
            return result;
        } catch (Exception e) {
            log.error("调用通义千问多模态模型失败: {}", e.getMessage(), e);
            AiChatResponseDTO response = new AiChatResponseDTO();
            response.setSessionId(sessionId);
            response.setQuestion("[图片] " + (request.getUserPrompt() != null ? request.getUserPrompt() : "识别截图"));
            response.setAnswer("抱歉，图片识别失败，请稍后重试。");
            response.setResponseTime(LocalDateTime.now());
            return response;
        }
    }

    /**
     * 根据图片URL自动检测MIME类型（已废弃，保留防编译错误）
     */
    private MimeType detectMimeType(String imageUrl) {
        if (imageUrl == null) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        String lowerUrl = imageUrl.toLowerCase();
        if (lowerUrl.startsWith("data:image/")) {
            int endIndex = lowerUrl.indexOf(";");
            if (endIndex == -1) {
                endIndex = lowerUrl.indexOf(",");
            }
            if (endIndex != -1) {
                String mimePart = lowerUrl.substring(5, endIndex);
                return MimeType.valueOf(mimePart);
            }
        }
        if (lowerUrl.endsWith(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (lowerUrl.endsWith(".webp")) {
            return MimeType.valueOf("image/webp");
        }
        if (lowerUrl.endsWith(".gif")) {
            return MimeTypeUtils.IMAGE_GIF;
        }
        if (lowerUrl.endsWith(".bmp")) {
            return MimeType.valueOf("image/bmp");
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }

    @Override
    public List<AiChatHistory> getHistory(String sessionId) {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<AiChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatHistory::getUserId, userId);
        wrapper.eq(AiChatHistory::getDelFlag, (byte) 0);
        if (sessionId != null && !sessionId.isEmpty()) {
            wrapper.eq(AiChatHistory::getSessionId, sessionId);
        }
        wrapper.orderByDesc(AiChatHistory::getCreateTime);
        return aiChatHistoryMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHistory(Long historyId) {
        Long userId = UserContext.getUserId();
        AiChatHistory history = aiChatHistoryMapper.selectById(historyId);
        if (history == null || !history.getUserId().equals(userId)) {
            throw new BusinessException("记录不存在");
        }
        history.setDelFlag((byte) 1);
        aiChatHistoryMapper.updateById(history);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearAllHistory() {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<AiChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatHistory::getUserId, userId);
        wrapper.eq(AiChatHistory::getDelFlag, (byte) 0);
        List<AiChatHistory> historyList = aiChatHistoryMapper.selectList(wrapper);
        for (AiChatHistory history : historyList) {
            history.setDelFlag((byte) 1);
            aiChatHistoryMapper.updateById(history);
        }
    }

    /**
     * 用户二次确认 AI 给出的操作
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmOperation(Long operationId, boolean confirm) {
        Long userId = UserContext.getUserId();
        AiOperateRecord record = aiOperateRecordMapper.selectById(operationId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException("操作记录不存在");
        }
        if (record.getIsConfirmed() != null && record.getIsConfirmed() != 0) {
            throw new BusinessException("该操作已处理");
        }

        if (confirm) {
            record.setIsConfirmed((byte) 1);
            boolean success = executeOperation(record);
            record.setIsExecuted(success ? (byte) 1 : (byte) 2);
            if (!success) {
                record.setFailReason("操作执行失败");
            }
        } else {
            record.setIsConfirmed((byte) 2);
            record.setIsExecuted((byte) 0);
        }

        record.setUpdateTime(LocalDateTime.now());
        aiOperateRecordMapper.updateById(record);
        return confirm;
    }

    /**
     * 真正执行AI建议的操作
     * 根据 operate_type 分发到不同的业务方法
     */
    private boolean executeOperation(AiOperateRecord record) {
        try {
            log.info("执行AI操作，类型：{}，内容：{}", record.getOperateType(), record.getOperateContent());

            JSONObject params = JSON.parseObject(record.getOperateContent());

            switch (record.getOperateType()) {
                case 1:
                    return executeBuy(params);
                case 2:
                    return executeSell(params);
                case 3:
                    return executeCreateInvestPlan(params);
                case 4:
                    return executeCreateAlertRule(params);
                case 5:
                    return executeBatch(params);
                case 6:
                    return executeImportHoldings(params);
                default:
                    log.warn("未知操作类型：{}", record.getOperateType());
                    return true;
            }
        } catch (Exception e) {
            log.error("执行AI操作失败", e);
            return false;
        }
    }

    // ==================== 各操作类型的执行逻辑 ====================

    /**
     * 执行加仓买入
     */
    private boolean executeBuy(JSONObject params) {
        String fundCode = params.getString("fundCode");
        BigDecimal investAmount = params.getBigDecimal("investAmount");
        BigDecimal chargeRate = params.getBigDecimal("chargeRate");

        if (fundCode == null || investAmount == null) {
            log.error("加仓参数不完整：fundCode={}, investAmount={}", fundCode, investAmount);
            return false;
        }

        // 确保基金信息已入库
        ensureFundExists(fundCode);

        // 查用户默认持仓分组
        Long userId = UserContext.getUserId();
        Long defaultGroupId = fundUserGroupService.getOrCreateDefaultHoldGroup(userId);

        FundHoldBuyDTO dto = new FundHoldBuyDTO();
        dto.setFundCode(fundCode);
        dto.setGroupId(defaultGroupId);
        dto.setInvestAmount(investAmount);
        dto.setChargeRate(chargeRate != null ? chargeRate : BigDecimal.ZERO);
        // 默认按15点前处理（AI操作在盘中发生）
        dto.setTradeTimeFlag((byte) 1);

        userFundHoldService.buyFund(dto);
        log.info("AI加仓成功：基金{}，金额{}", fundCode, investAmount);
        return true;
    }

    /**
     * 执行减仓卖出
     */
    private boolean executeSell(JSONObject params) {
        String fundCode = params.getString("fundCode");
        Boolean sellAll = params.getBoolean("sellAll");
        BigDecimal sellShares = params.getBigDecimal("sellShares");
        BigDecimal sellRatio = params.getBigDecimal("sellRatio");
        BigDecimal chargeRate = params.getBigDecimal("chargeRate");

        if (fundCode == null) {
            log.error("减仓参数不完整：fundCode为空");
            return false;
        }

        Long userId = UserContext.getUserId();

        // 查用户对该基金的持仓
        UserFundHold hold = userFundHoldMapper.selectOne(
                new LambdaQueryWrapper<UserFundHold>()
                        .eq(UserFundHold::getUserId, userId)
                        .eq(UserFundHold::getFundCode, fundCode)
        );
        if (hold == null || hold.getHoldShares().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("减仓失败：用户没有持仓基金 {}", fundCode);
            return false;
        }

        BigDecimal sharesToSell;
        if (Boolean.TRUE.equals(sellAll)) {
            sharesToSell = hold.getHoldShares();
        } else if (sellShares != null && sellShares.compareTo(BigDecimal.ZERO) > 0) {
            sharesToSell = sellShares;
        } else if (sellRatio != null && sellRatio.compareTo(BigDecimal.ZERO) > 0) {
            // 按比例卖出，比如 sellRatio=0.5 表示卖一半
            sharesToSell = hold.getHoldShares().multiply(sellRatio)
                    .setScale(4, RoundingMode.HALF_UP);
        } else {
            log.error("减仓参数不完整：未指定卖出份额或比例");
            return false;
        }

        // 不能超过持仓份额
        if (sharesToSell.compareTo(hold.getHoldShares()) > 0) {
            sharesToSell = hold.getHoldShares();
        }

        FundHoldSellDTO dto = new FundHoldSellDTO();
        dto.setHoldId(hold.getId());
        dto.setSellShares(sharesToSell);
        dto.setChargeRate(chargeRate != null ? chargeRate : BigDecimal.ZERO);
        dto.setTradeTimeFlag((byte) 1);

        userFundHoldService.sellFund(dto);
        log.info("AI减仓成功：基金{}，卖出份额{}", fundCode, sharesToSell);
        return true;
    }

    /**
     * 执行创建定投计划
     */
    private boolean executeCreateInvestPlan(JSONObject params) {
        String fundCode = params.getString("fundCode");
        BigDecimal investAmount = params.getBigDecimal("investAmount");
        Integer investCycle = params.getInteger("investCycle");
        Integer cycleDay = params.getInteger("cycleDay");
        BigDecimal chargeRate = params.getBigDecimal("chargeRate");
        Long groupId = params.getLong("groupId");

        if (fundCode == null || investAmount == null || investCycle == null) {
            log.error("定投参数不完整：fundCode={}, investAmount={}, investCycle={}",
                    fundCode, investAmount, investCycle);
            return false;
        }

        Long userId = UserContext.getUserId();
        if (groupId == null) {
            groupId = fundUserGroupService.getOrCreateDefaultHoldGroup(userId);
        }

        FundInvestPlanCreateDTO dto = new FundInvestPlanCreateDTO();
        dto.setFundCode(fundCode);
        dto.setGroupId(groupId);
        dto.setInvestAmount(investAmount);
        dto.setChargeRate(chargeRate != null ? chargeRate : BigDecimal.ZERO);
        dto.setInvestCycle(investCycle.byteValue());
        dto.setCycleDay(cycleDay != null ? cycleDay.byteValue() : (byte) LocalDate.now().getDayOfMonth());
        dto.setStartDate(LocalDate.now());

        fundInvestPlanService.createPlan(dto);
        log.info("AI创建定投计划成功：基金{}，金额{}，周期{}", fundCode, investAmount, investCycle);
        return true;
    }

    /**
     * 执行创建止盈止损提醒规则
     */
    private boolean executeCreateAlertRule(JSONObject params) {
        String fundCode = params.getString("fundCode");
        BigDecimal threshold = params.getBigDecimal("threshold");
        String direction = params.getString("direction");

        if (fundCode == null || threshold == null || direction == null) {
            log.error("止盈止损参数不完整：fundCode={}, threshold={}, direction={}",
                    fundCode, threshold, direction);
            return false;
        }

        Long userId = UserContext.getUserId();

        // 写入 ai_alert_rule 表，后续由定时任务扫描触发
        AiAlertRule rule = new AiAlertRule();
        rule.setUserId(userId);
        rule.setFundCode(fundCode);
        rule.setThreshold(threshold);
        // direction: "止盈" → 1, "止损" → 2
        rule.setDirection("止盈".equals(direction) ? (byte) 1 : (byte) 2);
        rule.setIsTriggered((byte) 0);
        rule.setCreateTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());
        aiAlertRuleMapper.insert(rule);

        log.info("AI止盈止损提醒规则已保存：用户{}，基金{}，阈值{}%，方向{}，规则ID：{}",
                userId, fundCode, threshold, direction, rule.getId());
        return true;
    }

    // 执行创建分组并批量导入持仓
    private boolean executeImportHoldings(JSONObject params) {
        String groupName = params.getString("groupName");
        JSONArray holdings = params.getJSONArray("holdings");
        if (groupName == null || holdings == null || holdings.isEmpty()) {
            log.error("导入持仓参数不完整");
            return false;
        }
        Long userId = UserContext.getUserId();
        // 1. 创建或获取分组（已存在则复用）
        Long groupId;
        try {
            GroupCreateDTO gDto = new GroupCreateDTO();
            gDto.setGroupName(groupName);
            groupId = fundUserGroupService.createHoldGroup(gDto);
        } catch (Exception e) {
            FundUserGroup exist = fundUserGroupMapper.selectOne(
                    new LambdaQueryWrapper<FundUserGroup>()
                            .eq(FundUserGroup::getUserId, userId)
                            .eq(FundUserGroup::getGroupName, groupName)
                            .eq(FundUserGroup::getGroupType, (byte) 1));
            groupId = exist != null ? exist.getId() : fundUserGroupService.getOrCreateDefaultHoldGroup(userId);
        }
        log.info("导入持仓分组ID：{}", groupId);

        // 2. 直接写入user_fund_hold（不走buyFund，避免待确认交易）
        for (int i = 0; i < holdings.size(); i++) {
            JSONObject h = holdings.getJSONObject(i);
            String fundCode = h.getString("fundCode");
            String fundName = h.getString("fundName");
            BigDecimal amount = h.getBigDecimal("amount");

            // fundCode为空时尝试按名称查
            if (fundCode == null || fundCode.isEmpty()) {
                if (fundName != null && !fundName.isEmpty()) {
                    FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                            new LambdaQueryWrapper<FundBaseInfo>().like(FundBaseInfo::getFundName, fundName));
                    if (fund != null) fundCode = fund.getFundCode();
                }
                if (fundCode == null || fundCode.isEmpty()) {
                    log.warn("跳过无法匹配的基金：{}", fundName);
                    continue;
                }
            }
            try {
                // 确保基金信息已入库
                ensureFundExists(fundCode);
                // 查是否已在此分组有持仓
                UserFundHold existHold = userFundHoldMapper.selectOne(
                        new LambdaQueryWrapper<UserFundHold>()
                                .eq(UserFundHold::getUserId, userId)
                                .eq(UserFundHold::getFundCode, fundCode)
                                .eq(UserFundHold::getGroupId, groupId));
                if (existHold != null) {
                    log.info("基金{}已在分组{}中，跳过", fundCode, groupId);
                    continue;
                }
                // 查最新净值计算份额
                BigDecimal latestNav = BigDecimal.ZERO;
                FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                        new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, fundCode));
                if (fund != null && fund.getLatestNetValue() != null) latestNav = fund.getLatestNetValue();

                UserFundHold newHold = new UserFundHold();
                newHold.setUserId(userId);
                newHold.setFundCode(fundCode);
                newHold.setGroupId(groupId);
                if (latestNav.compareTo(BigDecimal.ZERO) > 0 && amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                    newHold.setHoldShares(amount.divide(latestNav, 4, RoundingMode.HALF_UP));
                    newHold.setCostPrice(latestNav);
                } else {
                    // NAV不可用时设holdShares为正值避免 >0 过滤
                    newHold.setHoldShares(amount != null && amount.compareTo(BigDecimal.ZERO) > 0 ? amount : BigDecimal.ONE);
                    newHold.setCostPrice(BigDecimal.ONE);
                }
                newHold.setTotalCostAmount(amount != null ? amount : BigDecimal.ZERO);
                newHold.setFrozenShares(BigDecimal.ZERO);
                newHold.setCreateTime(LocalDateTime.now());
                userFundHoldMapper.insert(newHold);
                log.info("AI批量导入持仓：基金{}，金额{}，分组{}", fundCode, amount, groupId);

                // 创建一条导入交易记录
                FundTradeRecord tradeRecord = new FundTradeRecord();
                tradeRecord.setUserId(userId);
                tradeRecord.setFundCode(fundCode);
                tradeRecord.setGroupId(groupId);
                tradeRecord.setTradeType((byte) 1);
                tradeRecord.setTradeAmount(amount != null ? amount : BigDecimal.ZERO);
                tradeRecord.setTradeShares(newHold.getHoldShares());
                tradeRecord.setChargeFee(BigDecimal.ZERO);
                tradeRecord.setTradeDate(LocalDate.now());
                tradeRecord.setImportType((byte) 1); // 图片识别导入
                fundTradeRecordMapper.insert(tradeRecord);
            } catch (Exception e) {
                log.warn("AI批量导入基金{}失败：{}", fundCode, e.getMessage());
            }
        }
        return true;
    }

    /**
     * 执行批量操作（一键清仓/调仓/分析报告）
     */
    private boolean executeBatch(JSONObject params) {
        JSONArray subOperations = params.getJSONArray("subOperations");
        if (subOperations != null && !subOperations.isEmpty()) {
            boolean allSuccess = true;
            for (int i = 0; i < subOperations.size(); i++) {
                JSONObject sub = subOperations.getJSONObject(i);
                int subType = sub.getIntValue("type");
                try {
                    boolean success = false;
                    switch (subType) {
                        case 2:
                            success = executeSell(sub);
                            break;
                        case 1:
                            success = executeBuy(sub);
                            break;
                        default:
                            success = true;
                    }
                    if (!success) {
                        allSuccess = false;
                        log.error("批量操作中子任务失败：索引{}，类型{}", i, subType);
                    }
                } catch (Exception e) {
                    allSuccess = false;
                    log.error("批量操作中子任务异常：索引{}，类型{}", i, subType, e);
                }
            }
            return allSuccess;
        }
        // 纯分析报告，没有子操作 → 直接成功（报告内容已在 AI 回答中）
        log.info("AI分析报告已生成");
        return true;
    }

    // ==================== 流式输出 ====================

    @Override
    public void chatStream(AiChatRequestDTO request, SseEmitter emitter) {
        Long userId;
        try {
            userId = UserContext.getUserId();
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().data("{\"error\":\"未登录，请先登录\"}"));
            } catch (IOException ignored) {}
            emitter.complete();
            return;
        }

        String question = request.getQuestion().trim();
        log.info("用户 {} 发起流式AI对话，问题：{}", userId, question);

        // ====================== 修复位置 START ======================
        String sessionId;
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
            log.info("新建流式会话，SessionId: {}", sessionId);
        } else {
            sessionId = request.getSessionId();
        }
        // ====================== 修复位置 END ======================

        String portfolioContext = isFundRelated(question) ? buildPortfolioContext(userId) : "";
        String memoryContext = aiChatMemoryService.buildMemoryContext(userId);

        StringBuilder systemContext = new StringBuilder();
        systemContext.append(SpringAiConfig.WANGCAI_SYSTEM_PROMPT).append("\n\n");
        if (!portfolioContext.isEmpty()) {
            systemContext.append(portfolioContext).append("\n\n");
        }
        systemContext.append(memoryContext);

        StringBuilder fullAnswer = new StringBuilder();

        // 先发送 sessionId，让前端知道会话ID
        try {
            emitter.send(SseEmitter.event().name("meta").data("{\"sessionId\":\"" + sessionId + "\"}"));
        } catch (IOException e) {
            log.error("发送sessionId失败", e);
            emitter.completeWithError(e);
            return;
        }

        chatClient.prompt()
                .system(spec -> spec.text(systemContext.toString()))
                .user(question)
                .advisors(a -> a
                        .param("chat_memory_conversation_id", sessionId)
                        .param("chat_memory_retrieve_size", 10))
                .stream()
                .content()
                .subscribe(
                        content -> {
                            fullAnswer.append(content);
                            try {
                                String jsonData = "{\"content\":" + com.alibaba.fastjson.JSON.toJSONString(content) + ",\"sessionId\":\"" + sessionId + "\"}";
                                emitter.send(SseEmitter.event().data(jsonData));
                            } catch (IOException e) {
                                log.error("流式发送失败", e);
                                throw new RuntimeException(e);
                            }
                        },
                        error -> {
                            log.error("流式输出异常", error);
                            try {
                                String errorJson = "{\"content\":\"哎呀，旺财这边网络有点卡顿，稍等一下再试试吧~ \\n\\n⚠️ 旺财提醒：以上内容仅供参考，不构成投资建议哦~\"}";
                                emitter.send(SseEmitter.event().data(errorJson));
                            } catch (IOException ignored) {}
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                String answer = fullAnswer.toString();

                                // 处理操作JSON
                                String operationJson = extractOperationJson(answer);
                                if (operationJson != null) {
                                    try {
                                        JSONObject opObj = JSON.parseObject(operationJson);
                                        JSONObject operation = opObj.getJSONObject("operation");
                                        int type = operation.getIntValue("type");
                                        String summary = operation.getString("summary");
                                        JSONObject params = operation.getJSONObject("params");

                                        AiOperateRecord record = new AiOperateRecord();
                                        record.setUserId(userId);
                                        record.setOperateType((byte) type);
                                        record.setOperateContent(params != null ? params.toJSONString() : "{}");
                                        record.setIsConfirmed((byte) 0);
                                        record.setIsExecuted((byte) 0);
                                        record.setCreateTime(LocalDateTime.now());
                                        record.setUpdateTime(LocalDateTime.now());
                                        aiOperateRecordMapper.insert(record);

                                        log.info("AI流式生成操作方案，类型：{}，摘要：{}，记录ID：{}", type, summary, record.getId());

                                        // 发送操作确认事件
                                        JSONObject opEvent = new JSONObject();
                                        opEvent.put("hasOperation", true);
                                        opEvent.put("operationId", record.getId());
                                        opEvent.put("operationType", type);
                                        opEvent.put("operationSummary", summary);
                                        emitter.send(SseEmitter.event().name("operation").data(opEvent.toJSONString()));
                                    } catch (Exception e) {
                                        log.warn("解析AI流式操作JSON失败，忽略：{}", e.getMessage());
                                    }
                                }

                                emitter.send(SseEmitter.event().data("[DONE]"));

                                // 流完成后再保存到数据库
                                String cleanAnswer = cleanOperationJson(answer);
                                String displayQ = request.getDisplayQuestion() != null ? request.getDisplayQuestion() : question;
                                saveChatLog(userId, sessionId, displayQ, cleanAnswer, request.getImageUrl());
                                aiChatMemoryService.learnFromConversation(userId, question, cleanAnswer);
                            } catch (IOException e) {
                                log.error("流式完成处理失败", e);
                            }
                            emitter.complete();
                        }
                );
    }
}
