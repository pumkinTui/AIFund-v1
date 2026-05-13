package com.fund.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.entity.AiChatHistory;
import com.fund.assistant.mapper.AiChatHistoryMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 用户长期记忆服务
 * 存储用户偏好、投资风格、投资习惯等关键信息
 */
@Service
@Slf4j
public class AiChatMemoryService {

    @Autowired
    private AiChatHistoryMapper aiChatHistoryMapper;

    private final Map<Long, UserProfile> userProfileMap = new ConcurrentHashMap<>();

    /**
     * 获取用户画像
     */
    public UserProfile getUserProfile(Long userId) {
        // 从内存缓存中获取这个用户的档案
        // 如果不存在，就自动创建一个新的
        return userProfileMap.computeIfAbsent(userId, k -> {
            // 用户档案不存在  新建 
            UserProfile profile = new UserProfile(); // 创建空的用户档案
            profile.setUserId(userId); // 设置用户ID
            loadFromHistory(userId, profile); // 从历史聊天记录里学习，把记忆加载进来
            return profile; // 返回建好的档案
        });
    }

    /**
     * 从历史对话中学习用户画像
     */
    private void loadFromHistory(Long userId, UserProfile profile) {
        LambdaQueryWrapper<AiChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatHistory::getUserId, userId);
        wrapper.eq(AiChatHistory::getDelFlag, (byte) 0);
        wrapper.orderByDesc(AiChatHistory::getCreateTime);
        wrapper.last("LIMIT 100");
        List<AiChatHistory> historyList = aiChatHistoryMapper.selectList(wrapper);

        for (AiChatHistory history : historyList) {
            extractPreference(history.getQuestion(), profile);
            extractPreference(history.getAnswer(), profile);
        }
    }

    /**
     * 从文本中提取用户偏好
     */
    private void extractPreference(String text, UserProfile profile) {
        if (text == null || text.isEmpty())
            return;

        String lower = text.toLowerCase();

        // 提取风险偏好
        if (lower.contains("保守") || lower.contains("稳健") || lower.contains("保本")) {
            profile.setRiskPreference("保守");
        } else if (lower.contains("激进") || lower.contains("高风险") || lower.contains("高收益")) {
            profile.setRiskPreference("激进");
        } else if (lower.contains("平衡") || lower.contains("均衡") || lower.contains("适中")) {
            profile.setRiskPreference("平衡");
        }

        // 提取投资风格
        if (lower.contains("长期") || lower.contains("长线") || lower.contains("价值投资")) {
            profile.setInvestStyle("长期投资");
        } else if (lower.contains("短期") || lower.contains("短线") || lower.contains("波段")) {
            profile.setInvestStyle("短期操作");
        } else if (lower.contains("定投")) {
            profile.setInvestStyle("定投为主");
        }

        // 提取偏好的基金类型
        if (lower.contains("指数"))
            profile.getLikeFundTypes().add("指数型");
        if (lower.contains("股票"))
            profile.getLikeFundTypes().add("股票型");
        if (lower.contains("债券") || lower.contains("债基"))
            profile.getLikeFundTypes().add("债券型");
        if (lower.contains("混合"))
            profile.getLikeFundTypes().add("混合型");
        if (lower.contains("货币"))
            profile.getLikeFundTypes().add("货币型");

        // 提取偏好的板块
        extractSector(text, profile);

        // 提取用户提到的其他重要信息
        extractImportantInfo(text, profile);
    }

    private void extractSector(String text, UserProfile profile) {
        String[] sectors = { "半导体", "光伏", "新能源", "消费", "医药", "医疗", "科技",
                "军工", "白酒", "食品", "银行", "证券", "地产", "汽车", "AI", "人工智能", "芯片" };
        for (String sector : sectors) {
            if (text.contains(sector)) {
                profile.getLikeSectors().add(sector);
            }
        }
    }

    private void extractImportantInfo(String text, UserProfile profile) {
        Pattern p = Pattern.compile("我[的是](\\S{2,20})");
        Matcher m = p.matcher(text);
        while (m.find()) {
            String info = m.group(1);
            if (info.length() < 20 && !info.contains("持仓") && !info.contains("基金")) {
                profile.getImportantInfo().add(info);
            }
        }
    }

    /**
     * 构建长期记忆文本，注入到每次对话的上下文中
     */
    public String buildMemoryContext(Long userId) {
        UserProfile profile = getUserProfile(userId);
        StringBuilder sb = new StringBuilder();

        sb.append("【旺财的记忆】\n");
        sb.append("你和我已经认识一段时间了，以下是我记住的关于你的信息：\n");

        boolean hasMemory = false;

        if (profile.getRiskPreference() != null) {
            sb.append("- 你的风险偏好是：").append(profile.getRiskPreference()).append("\n");
            hasMemory = true;
        }
        if (profile.getInvestStyle() != null) {
            sb.append("- 你的投资风格偏向：").append(profile.getInvestStyle()).append("\n");
            hasMemory = true;
        }
        if (!profile.getLikeFundTypes().isEmpty()) {
            sb.append("- 你比较关注的基金类型有：")
                    .append(String.join("、", profile.getLikeFundTypes())).append("\n");
            hasMemory = true;
        }
        if (!profile.getLikeSectors().isEmpty()) {
            sb.append("- 你关注的板块有：")
                    .append(String.join("、", profile.getLikeSectors())).append("\n");
            hasMemory = true;
        }
        if (!profile.getImportantInfo().isEmpty()) {
            sb.append("- 你还提到过：")
                    .append(String.join("、", profile.getImportantInfo())).append("\n");
            hasMemory = true;
        }

        if (!hasMemory) {
            sb.append("  暂时还没有记住你的偏好，多和我聊聊我就更了解你啦~\n");
        }

        sb.append("请基于这些记忆，以及本次对话上下文，给出更贴合用户习惯的回应。\n");

        return sb.toString();
    }

    /**
     * 提取本轮对话中的重要信息，更新到长期记忆中
     */
    public void learnFromConversation(Long userId, String question, String answer) {
        UserProfile profile = getUserProfile(userId);
        extractPreference(question, profile);
        extractPreference(answer, profile);
    }

    @Data
    public static class UserProfile {

        private Long userId;

        // 风险偏好
        private String riskPreference;

        // 投资风格 定投 / 长线 / 短线 / 价值投资
        private String investStyle;

        // 喜欢的基金类型 股票型、混合型、ETF、债券型
        private Set<String> likeFundTypes = ConcurrentHashMap.newKeySet();

        // 喜欢的行业板块
        private Set<String> likeSectors = ConcurrentHashMap.newKeySet();

        // 重要信息 “我每月能投2000”、“拿不住3个月”、“最怕亏本金”
        private Set<String> importantInfo = ConcurrentHashMap.newKeySet();
    }
}