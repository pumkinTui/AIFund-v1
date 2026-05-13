package com.fund.assistant.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.entity.AiAlertRule;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.mapper.AiAlertRuleMapper;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.UserFundHoldMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class AiAlertRuleCheckSchedule {

    @Autowired
    private AiAlertRuleMapper aiAlertRuleMapper;

    @Autowired
    private UserFundHoldMapper userFundHoldMapper;

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    /**
     * 每天21点收盘后扫描止盈止损规则
     */
    @Scheduled(cron = "0 0 21 * * ?")
    public void checkAlertRules() {
        log.info("定时任务：开始扫描止盈止损提醒规则");

        List<AiAlertRule> rules = aiAlertRuleMapper.selectList(
                new LambdaQueryWrapper<AiAlertRule>()
                        .eq(AiAlertRule::getIsTriggered, (byte) 0)
        );

        if (rules.isEmpty()) {
            log.info("定时任务：无待检查的止盈止损规则");
            return;
        }

        int triggered = 0;
        for (AiAlertRule rule : rules) {
            try {
                boolean isTriggered = checkRule(rule);
                if (isTriggered) {
                    rule.setIsTriggered((byte) 1);
                    rule.setUpdateTime(LocalDateTime.now());
                    aiAlertRuleMapper.updateById(rule);
                    triggered++;
                    log.warn("⚠️ 止盈止损规则触发：用户{}，基金{}，阈值{}%，方向{}",
                            rule.getUserId(), rule.getFundCode(), rule.getThreshold(),
                            rule.getDirection() == 1 ? "止盈" : "止损");
                }
            } catch (Exception e) {
                log.error("检查止盈止损规则失败，规则ID：{}", rule.getId(), e);
            }
        }

        log.info("定时任务：止盈止损规则扫描完成，共触发 {} 条", triggered);
    }

    /**
     * 检查单条规则是否达到触发条件
     */
    private boolean checkRule(AiAlertRule rule) {
        // 查用户对该基金的持仓
        UserFundHold hold = userFundHoldMapper.selectOne(
                new LambdaQueryWrapper<UserFundHold>()
                        .eq(UserFundHold::getUserId, rule.getUserId())
                        .eq(UserFundHold::getFundCode, rule.getFundCode())
        );
        if (hold == null || hold.getHoldShares().compareTo(BigDecimal.ZERO) <= 0) {
            log.info("用户 {} 已无基金 {} 的持仓，跳过规则", rule.getUserId(), rule.getFundCode());
            return false;
        }

        // 查最新净值
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                new LambdaQueryWrapper<FundBaseInfo>()
                        .eq(FundBaseInfo::getFundCode, rule.getFundCode())
        );
        if (fund == null || fund.getLatestNetValue() == null
                || fund.getLatestNetValue().compareTo(BigDecimal.ZERO) <= 0) {
            log.info("基金 {} 无净值数据，跳过规则", rule.getFundCode());
            return false;
        }

        // 计算当前收益率
        BigDecimal currentValue = hold.getHoldShares().multiply(fund.getLatestNetValue())
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal cost = hold.getTotalCostAmount();
        if (cost.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal returnRate = currentValue.subtract(cost)
                .multiply(BigDecimal.valueOf(100))
                .divide(cost, 2, RoundingMode.HALF_UP);

        log.info("检查规则：用户{}，基金{}，成本{}，当前市值{}，收益率{}%，阈值{}%",
                rule.getUserId(), rule.getFundCode(), cost, currentValue, returnRate, rule.getThreshold());

        // 判断是否触发
        if (rule.getDirection() == 1) {
            // 止盈：收益率 >= 阈值
            return returnRate.compareTo(rule.getThreshold()) >= 0;
        } else {
            // 止损：收益率 <= -阈值
            return returnRate.compareTo(rule.getThreshold().negate()) <= 0;
        }
    }
}
