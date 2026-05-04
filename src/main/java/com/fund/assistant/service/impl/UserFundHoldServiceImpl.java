package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.dto.FundHoldBuyDTO;
import com.fund.assistant.dto.FundHoldSellDTO;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundTradeRecord;
import com.fund.assistant.entity.FundUserGroup;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.FundTradeRecordMapper;
import com.fund.assistant.mapper.UserFundHoldMapper;
import com.fund.assistant.service.FundUserGroupService;
import com.fund.assistant.service.UserFundHoldService;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.UserFundHoldVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserFundHoldServiceImpl extends ServiceImpl<UserFundHoldMapper, UserFundHold> implements UserFundHoldService {

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    @Autowired
    private FundUserGroupService fundUserGroupService;

    @Autowired
    private FundTradeRecordMapper fundTradeRecordMapper;

    // 小数精度
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    // 交易类型常量
    private static final byte TRADE_TYPE_ADD = 1; // 加仓
    private static final byte TRADE_TYPE_REDUCE = 2; // 减仓
    // 导入方式常量
    private static final byte IMPORT_TYPE_MANUAL = 0; // 手动

    /**
     * 基金买入
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void buyFund(FundHoldBuyDTO dto) {
        Long userId = UserContext.getUserId();

        // 1. 检查基金是否存在，并获取最新净值
        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.eq(FundBaseInfo::getFundCode, dto.getFundCode());
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(fundWrapper);
        if (fund == null) {
            throw new BusinessException("基金不存在");
        }
        BigDecimal latestNetValue = fund.getLatestNetValue();
        if (latestNetValue == null || latestNetValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("基金净值异常，无法买入");
        }

        // 2. 处理分组
        Long groupId = dto.getGroupId();
        if (groupId == null) {
            groupId = fundUserGroupService.getOrCreateDefaultHoldGroup(userId);
        } else {
            LambdaQueryWrapper<FundUserGroup> groupWrapper = new LambdaQueryWrapper<>();
            groupWrapper.eq(FundUserGroup::getId, groupId);
            groupWrapper.eq(FundUserGroup::getUserId, userId);
            groupWrapper.eq(FundUserGroup::getGroupType, (byte) 1);
            if (fundUserGroupService.count(groupWrapper) == 0) {
                throw new BusinessException("持仓分组不存在");
            }
        }

        // 3. 计算手续费和净买入金额
        BigDecimal chargeRate = dto.getChargeRate() != null ? dto.getChargeRate() : BigDecimal.ZERO;
        // 手续费 = 投入金额 * 交易费率 / 100
        BigDecimal chargeFee = dto.getInvestAmount().multiply(chargeRate).divide(new BigDecimal("100"), SCALE, ROUNDING_MODE);
        // 净买入金额 = 投入金额 - 手续费
        BigDecimal netBuyAmount = dto.getInvestAmount().subtract(chargeFee);
        // 买入份额 = 净买入金额 / 最新净值
        BigDecimal buyShares = netBuyAmount.divide(latestNetValue, SCALE, ROUNDING_MODE);

        // 4. 查询是否已有该基金的持仓
        LambdaQueryWrapper<UserFundHold> holdWrapper = new LambdaQueryWrapper<>();
        holdWrapper.eq(UserFundHold::getUserId, userId);
        holdWrapper.eq(UserFundHold::getFundCode, dto.getFundCode());
        UserFundHold existHold = this.getOne(holdWrapper);

        if (existHold != null) {
            // 已有持仓：更新份额、成本
            BigDecimal newTotalCost = existHold.getTotalCostAmount().add(netBuyAmount);
            BigDecimal newTotalShares = existHold.getHoldShares().add(buyShares);
            BigDecimal newCostPrice = newTotalCost.divide(newTotalShares, SCALE, ROUNDING_MODE);

            existHold.setHoldShares(newTotalShares);
            existHold.setCostPrice(newCostPrice);
            existHold.setTotalCostAmount(newTotalCost);
            existHold.setGroupId(groupId);
            this.updateById(existHold);
        } else {
            // 无持仓：新建持仓记录
            UserFundHold newHold = new UserFundHold();
            newHold.setUserId(userId);
            newHold.setFundCode(dto.getFundCode());
            newHold.setGroupId(groupId);
            newHold.setHoldShares(buyShares);
            newHold.setCostPrice(latestNetValue);
            newHold.setTotalCostAmount(netBuyAmount);
            this.save(newHold);
        }

        // 5. 生成交易记录（100%适配你的表结构）
        FundTradeRecord tradeRecord = new FundTradeRecord();
        tradeRecord.setUserId(userId);
        tradeRecord.setFundCode(dto.getFundCode());
        tradeRecord.setGroupId(groupId);
        tradeRecord.setTradeType(TRADE_TYPE_ADD); // 加仓
        tradeRecord.setTradeAmount(dto.getInvestAmount());
        tradeRecord.setTradeShares(buyShares);
        tradeRecord.setChargeRate(chargeRate);
        tradeRecord.setChargeFee(chargeFee);
        tradeRecord.setTradeDate(LocalDate.now());
        tradeRecord.setTradeTimeFlag(dto.getTradeTimeFlag());
        tradeRecord.setImportType(IMPORT_TYPE_MANUAL); // 手动
        fundTradeRecordMapper.insert(tradeRecord);
    }

    /**
     * 基金卖出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sellFund(FundHoldSellDTO dto) {
        Long userId = UserContext.getUserId();

        // 1. 查询持仓记录是否存在且属于当前用户
        UserFundHold hold = this.getById(dto.getHoldId());
        if (hold == null || !hold.getUserId().equals(userId)) {
            throw new BusinessException("持仓记录不存在");
        }

        // 2. 检查卖出份额是否大于持有份额
        if (dto.getSellShares().compareTo(hold.getHoldShares()) > 0) {
            throw new BusinessException("卖出份额不能大于持有份额");
        }

        // 3. 获取基金最新净值（后端获取）
        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.eq(FundBaseInfo::getFundCode, hold.getFundCode());
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(fundWrapper);
        if (fund == null || fund.getLatestNetValue() == null) {
            throw new BusinessException("基金净值异常，无法卖出");
        }
        BigDecimal latestNetValue = fund.getLatestNetValue();

        // 4. 计算赎回金额和手续费（适配charge_rate 和 charge_fee）
        // 赎回金额 = 卖出份额 * 最新净值
        BigDecimal redeemAmount = dto.getSellShares().multiply(latestNetValue).setScale(SCALE, ROUNDING_MODE);
        // 交易费率
        BigDecimal chargeRate = dto.getChargeRate() != null ? dto.getChargeRate() : BigDecimal.ZERO;
        // 手续费 = 赎回金额 * 交易费率 / 100
        BigDecimal chargeFee = redeemAmount.multiply(chargeRate).divide(new BigDecimal("100"), SCALE, ROUNDING_MODE);
        // 实际到账金额 = 赎回金额 - 手续费
        BigDecimal actualAmount = redeemAmount.subtract(chargeFee);

        // 5. 计算剩余份额、剩余成本
        BigDecimal remainShares = hold.getHoldShares().subtract(dto.getSellShares());
        BigDecimal remainCostAmount = remainShares.multiply(hold.getCostPrice()).setScale(SCALE, ROUNDING_MODE);

        if (remainShares.compareTo(BigDecimal.ZERO) == 0) {
            // 全部卖出：删除持仓记录
            this.removeById(dto.getHoldId());
        } else {
            // 部分卖出：更新持仓
            hold.setHoldShares(remainShares);
            hold.setTotalCostAmount(remainCostAmount);
            this.updateById(hold);
        }

        // 6. 生成交易记录
        FundTradeRecord tradeRecord = new FundTradeRecord();
        tradeRecord.setUserId(userId);
        tradeRecord.setFundCode(hold.getFundCode());
        tradeRecord.setGroupId(hold.getGroupId());
        tradeRecord.setTradeType(TRADE_TYPE_REDUCE); // 减仓
        tradeRecord.setTradeAmount(actualAmount);
        tradeRecord.setTradeShares(dto.getSellShares());
        tradeRecord.setChargeRate(chargeRate);
        tradeRecord.setChargeFee(chargeFee);
        tradeRecord.setTradeDate(LocalDate.now());
        tradeRecord.setTradeTimeFlag(dto.getTradeTimeFlag());
        tradeRecord.setImportType(IMPORT_TYPE_MANUAL); // 手动
        fundTradeRecordMapper.insert(tradeRecord);
    }

    /**
     * 查询当前用户所有持仓（按分组）
     */
    @Override
    public List<UserFundHoldVO> getHoldList(Long groupId) {
        Long userId = UserContext.getUserId();

        // 1. 查询持仓列表（只查份额>0的，已按分组隔离）
        LambdaQueryWrapper<UserFundHold> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFundHold::getUserId, userId);
        if (groupId != null) {
            wrapper.eq(UserFundHold::getGroupId, groupId);
        }
        wrapper.gt(UserFundHold::getHoldShares, BigDecimal.ZERO);
        wrapper.orderByDesc(UserFundHold::getUpdateTime);
        List<UserFundHold> holdList = this.list(wrapper);

        if (holdList.isEmpty()) {
            return List.of();
        }

        // 2. 批量查询基金信息
        List<String> fundCodeList = holdList.stream()
                .map(UserFundHold::getFundCode)
                .collect(Collectors.toList());
        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.in(FundBaseInfo::getFundCode, fundCodeList);
        List<FundBaseInfo> fundList = fundBaseInfoMapper.selectList(fundWrapper);
        Map<String, FundBaseInfo> fundMap = fundList.stream()
                .collect(Collectors.toMap(FundBaseInfo::getFundCode, fund -> fund));

        // 3. 批量查询分组信息
        List<Long> groupIdList = holdList.stream()
                .map(UserFundHold::getGroupId)
                .distinct()
                .collect(Collectors.toList());
        LambdaQueryWrapper<FundUserGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.in(FundUserGroup::getId, groupIdList);
        List<FundUserGroup> groupList = fundUserGroupService.list(groupWrapper);
        Map<Long, String> groupNameMap = groupList.stream()
                .collect(Collectors.toMap(FundUserGroup::getId, FundUserGroup::getGroupName));

        // 4. 转换为VO，计算收益
        return holdList.stream().map(hold -> {
            UserFundHoldVO vo = new UserFundHoldVO();
            BeanUtils.copyProperties(hold, vo);

            // 填充基金信息
            FundBaseInfo fund = fundMap.get(hold.getFundCode());
            if (fund != null) {
                vo.setFundName(fund.getFundName());
                vo.setFundShortName(fund.getFundShortName());
                vo.setLatestNetValue(fund.getLatestNetValue());
                vo.setLatestChangeRate(fund.getLatestChangeRate());

                // 计算当前市值、收益
                BigDecimal currentMarketValue = hold.getHoldShares().multiply(fund.getLatestNetValue()).setScale(SCALE, ROUNDING_MODE);
                BigDecimal profitAmount = currentMarketValue.subtract(hold.getTotalCostAmount()).setScale(SCALE, ROUNDING_MODE);
                BigDecimal profitRate = profitAmount.divide(hold.getTotalCostAmount(), 4, ROUNDING_MODE).multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE);

                vo.setCurrentMarketValue(currentMarketValue);
                vo.setProfitAmount(profitAmount);
                vo.setProfitRate(profitRate);
            }

            // 填充分组名称
            vo.setGroupName(groupNameMap.get(hold.getGroupId()));

            return vo;
        }).collect(Collectors.toList());
    }
    /**
     * 查询单只基金的持仓记录
     */
    @Override
    public UserFundHoldVO getHoldDetail(String fundCode) {
        Long userId = UserContext.getUserId();

        // 查询单只基金持仓（按用户隔离）
        LambdaQueryWrapper<UserFundHold> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFundHold::getUserId, userId);
        wrapper.eq(UserFundHold::getFundCode, fundCode);
        wrapper.gt(UserFundHold::getHoldShares, BigDecimal.ZERO);
        UserFundHold hold = this.getOne(wrapper);

        if (hold == null) {
            return null;
        }

        // 转换为VO
        UserFundHoldVO vo = new UserFundHoldVO();
        BeanUtils.copyProperties(hold, vo);

        // 填充基金信息和收益
        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.eq(FundBaseInfo::getFundCode, fundCode);
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(fundWrapper);
        if (fund != null) {
            vo.setFundName(fund.getFundName());
            vo.setFundShortName(fund.getFundShortName());
            vo.setLatestNetValue(fund.getLatestNetValue());
            vo.setLatestChangeRate(fund.getLatestChangeRate());

            BigDecimal currentMarketValue = hold.getHoldShares().multiply(fund.getLatestNetValue()).setScale(SCALE, ROUNDING_MODE);
            BigDecimal profitAmount = currentMarketValue.subtract(hold.getTotalCostAmount()).setScale(SCALE, ROUNDING_MODE);
            BigDecimal profitRate = profitAmount.divide(hold.getTotalCostAmount(), 4, ROUNDING_MODE).multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE);

            vo.setCurrentMarketValue(currentMarketValue);
            vo.setProfitAmount(profitAmount);
            vo.setProfitRate(profitRate);
        }

        return vo;
    }
}