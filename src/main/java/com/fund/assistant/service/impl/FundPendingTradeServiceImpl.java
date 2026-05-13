package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundPendingTrade;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.FundPendingTradeMapper;
import com.fund.assistant.mapper.UserFundHoldMapper;
import com.fund.assistant.service.FundPendingTradeService;
import com.fund.assistant.vo.FundPendingTradeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FundPendingTradeServiceImpl extends ServiceImpl<FundPendingTradeMapper, FundPendingTrade>
        implements FundPendingTradeService {

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    @Autowired
    private UserFundHoldMapper userFundHoldMapper;

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Override
    public List<FundPendingTradeVO> getPendingList(Long userId, Byte tradeType) {
        LambdaQueryWrapper<FundPendingTrade> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundPendingTrade::getUserId, userId);
        if (tradeType != null) wrapper.eq(FundPendingTrade::getTradeType, tradeType);
        wrapper.orderByDesc(FundPendingTrade::getCreateTime);

        List<FundPendingTrade> list = this.list(wrapper);
        if (list.isEmpty()) return List.of();

        List<String> codes = list.stream().map(FundPendingTrade::getFundCode).distinct().collect(Collectors.toList());
        Map<String, FundBaseInfo> fundMap = fundBaseInfoMapper.selectList(
                new LambdaQueryWrapper<FundBaseInfo>().in(FundBaseInfo::getFundCode, codes)
        ).stream().collect(Collectors.toMap(FundBaseInfo::getFundCode, f -> f));

        return list.stream().map(t -> {
            FundPendingTradeVO vo = new FundPendingTradeVO();
            BeanUtils.copyProperties(t, vo);
            FundBaseInfo f = fundMap.get(t.getFundCode());
            if (f != null) { vo.setFundName(f.getFundName()); vo.setFundShortName(f.getFundShortName()); }
            vo.setTradeTypeDesc(switch (t.getTradeType()) { case 1 -> "买入"; case 2 -> "定投"; case 3 -> "卖出"; default -> "未知"; });
            vo.setStatusDesc(switch (t.getStatus()) { case 0 -> "待确认"; case 1 -> "已确认"; case 2 -> "已取消"; default -> "未知"; });
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPendingTrade(Long userId, Long tradeId) {
        FundPendingTrade trade = this.getById(tradeId);
        if (trade == null || !trade.getUserId().equals(userId)) throw new BusinessException("交易记录不存在");
        if (trade.getStatus() != 0) throw new BusinessException("只有待确认的交易才能取消");

        byte tradeType = trade.getTradeType();
        BigDecimal tradeShares = trade.getTradeShares();
        BigDecimal netAmount = trade.getTradeAmount() != null && trade.getChargeFee() != null
                ? trade.getTradeAmount().subtract(trade.getChargeFee()) : BigDecimal.ZERO;

        // 撤回预建持仓（买入/定投：回退份额+成本+冻结；卖出：只回退冻结）
        if ((tradeType == 1 || tradeType == 2) && tradeShares != null && tradeShares.compareTo(BigDecimal.ZERO) > 0) {
            UserFundHold hold = userFundHoldMapper.selectOne(
                    new LambdaQueryWrapper<UserFundHold>()
                            .eq(UserFundHold::getUserId, userId)
                            .eq(UserFundHold::getFundCode, trade.getFundCode())
                            .gt(UserFundHold::getHoldShares, BigDecimal.ZERO)
                            .last("LIMIT 1"));
            if (hold != null) {
                BigDecimal newShares = hold.getHoldShares().subtract(tradeShares).max(BigDecimal.ZERO);
                BigDecimal newFrozen = (hold.getFrozenShares() != null ? hold.getFrozenShares() : BigDecimal.ZERO)
                        .subtract(tradeShares).max(BigDecimal.ZERO);
                BigDecimal newCost = hold.getTotalCostAmount() != null
                        ? hold.getTotalCostAmount().subtract(netAmount).max(BigDecimal.ZERO) : BigDecimal.ZERO;
                hold.setHoldShares(newShares);
                hold.setFrozenShares(newFrozen);
                hold.setTotalCostAmount(newCost);
                if (newShares.compareTo(BigDecimal.ZERO) > 0) {
                    hold.setCostPrice(newCost.divide(newShares, SCALE, ROUNDING_MODE));
                }
                hold.setUpdateTime(LocalDateTime.now());
                userFundHoldMapper.updateById(hold);
            }
        } else if (tradeType == 3 && tradeShares != null && tradeShares.compareTo(BigDecimal.ZERO) > 0) {
            // 卖出取消：解冻
            UserFundHold hold = userFundHoldMapper.selectOne(
                    new LambdaQueryWrapper<UserFundHold>()
                            .eq(UserFundHold::getUserId, userId)
                            .eq(UserFundHold::getFundCode, trade.getFundCode())
                            .gt(UserFundHold::getHoldShares, BigDecimal.ZERO)
                            .last("LIMIT 1"));
            if (hold != null) {
                BigDecimal newFrozen = (hold.getFrozenShares() != null ? hold.getFrozenShares() : BigDecimal.ZERO)
                        .subtract(tradeShares).max(BigDecimal.ZERO);
                hold.setFrozenShares(newFrozen);
                hold.setUpdateTime(LocalDateTime.now());
                userFundHoldMapper.updateById(hold);
            }
        }

        trade.setStatus((byte) 2);
        trade.setUpdateTime(LocalDateTime.now());
        this.updateById(trade);
    }
}
