package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fund.assistant.dto.TradeQueryDTO;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundTradeRecord;
import com.fund.assistant.entity.FundUserGroup;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.FundTradeRecordMapper;
import com.fund.assistant.mapper.FundUserGroupMapper;
import com.fund.assistant.mapper.UserFundHoldMapper;
import com.fund.assistant.service.FundTradeRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.FundHoldProfitVO;
import com.fund.assistant.vo.TradeRecordVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 基金交易记录表 服务实现类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Service
public class FundTradeRecordServiceImpl extends ServiceImpl<FundTradeRecordMapper, FundTradeRecord> implements FundTradeRecordService {

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    @Autowired
    private FundUserGroupMapper fundUserGroupMapper;

    @Autowired
    private UserFundHoldMapper userFundHoldMapper;

    // 小数精度
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    // 交易类型常量
    private static final byte TRADE_TYPE_ADD = 1;
    private static final byte TRADE_TYPE_REDUCE = 2;
    private static final byte TRADE_TYPE_DIVIDEND_CASH = 3;
    private static final byte TRADE_TYPE_DIVIDEND_REINVEST = 4;
    // 交易时点常量
    private static final byte TIME_FLAG_BEFORE_15 = 1;
    private static final byte TIME_FLAG_AFTER_15 = 2;

    /**
     * 分页查询当前用户的交易记录
     */
    @Override
    public IPage<TradeRecordVO> getTradeRecordPage(TradeQueryDTO dto) {
        Long userId = UserContext.getUserId();

        // 1. 构建分页对象
        Page<FundTradeRecord> page = new Page<>(dto.getPageNum(), dto.getPageSize());

        // 2. 构建查询条件
        LambdaQueryWrapper<FundTradeRecord> wrapper = buildTradeQueryWrapper(dto, userId);
        // 按交易日期倒序，最新的在最前面
        wrapper.orderByDesc(FundTradeRecord::getTradeDate);
        wrapper.orderByDesc(FundTradeRecord::getCreateTime);

        // 3. 执行分页查询
        IPage<FundTradeRecord> recordPage = this.page(page, wrapper);

        // 4. 转换为VO
        return convertRecordPageToVO(recordPage);
    }
    /**
     * 查询当前用户所有交易记录（不分页，用于导出）
     */

    @Override
    public List<TradeRecordVO> getTradeRecordList(TradeQueryDTO dto) {
        Long userId = UserContext.getUserId();

        // 构建查询条件
        LambdaQueryWrapper<FundTradeRecord> wrapper = buildTradeQueryWrapper(dto, userId);
        wrapper.orderByDesc(FundTradeRecord::getTradeDate);
        wrapper.orderByDesc(FundTradeRecord::getCreateTime);

        List<FundTradeRecord> recordList = this.list(wrapper);
        return convertRecordListToVO(recordList);
    }

    /**
     * 查询单只基金的收益明细
     */
    @Override
    public FundHoldProfitVO getFundProfitDetail(String fundCode,Long groupId) {
        Long userId = UserContext.getUserId();

        // 1. 查询基金基础信息
        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.eq(FundBaseInfo::getFundCode, fundCode);
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(fundWrapper);
        if (fund == null) {
            return null;
        }

        // 2. 查询当前持仓
        LambdaQueryWrapper<UserFundHold> holdWrapper = new LambdaQueryWrapper<>();
        holdWrapper.eq(UserFundHold::getUserId, userId);
        holdWrapper.eq(UserFundHold::getFundCode, fundCode);
        holdWrapper.eq(UserFundHold::getGroupId, groupId);
        holdWrapper.gt(UserFundHold::getHoldShares, BigDecimal.ZERO);
        UserFundHold hold = userFundHoldMapper.selectOne(holdWrapper);

        // 3. 查询该基金所有交易记录
        LambdaQueryWrapper<FundTradeRecord> tradeWrapper = new LambdaQueryWrapper<>();
        tradeWrapper.eq(FundTradeRecord::getUserId, userId);
        tradeWrapper.eq(FundTradeRecord::getFundCode, fundCode);
        List<FundTradeRecord> tradeList = this.list(tradeWrapper);

        // 4. 计算收益数据
        return calculateFundProfit(fund, hold, tradeList);
    }

    /**
     * 查询当前用户所有持仓基金的收益明细
     */
    @Override
    public List<FundHoldProfitVO> getAllHoldProfitList() {
        Long userId = UserContext.getUserId();

        // 1. 查询当前用户所有持仓
        LambdaQueryWrapper<UserFundHold> holdWrapper = new LambdaQueryWrapper<>();
        holdWrapper.eq(UserFundHold::getUserId, userId);
        holdWrapper.gt(UserFundHold::getHoldShares, BigDecimal.ZERO);
        List<UserFundHold> holdList = userFundHoldMapper.selectList(holdWrapper);

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
                .collect(Collectors.toMap(FundBaseInfo::getFundCode, f -> f));

        // 3. 批量查询交易记录
        LambdaQueryWrapper<FundTradeRecord> tradeWrapper = new LambdaQueryWrapper<>();
        tradeWrapper.eq(FundTradeRecord::getUserId, userId);
        tradeWrapper.in(FundTradeRecord::getFundCode, fundCodeList);
        List<FundTradeRecord> allTradeList = this.list(tradeWrapper);
        Map<String, List<FundTradeRecord>> tradeMap = allTradeList.stream()
                .collect(Collectors.groupingBy(FundTradeRecord::getFundCode));

        // 4. 逐个计算收益
        return holdList.stream().map(hold -> {
            FundBaseInfo fund = fundMap.get(hold.getFundCode());
            List<FundTradeRecord> tradeList = tradeMap.getOrDefault(hold.getFundCode(), List.of());
            return calculateFundProfit(fund, hold, tradeList);
        }).collect(Collectors.toList());
    }


    /**
     * 构建交易记录查询条件
     */
    private LambdaQueryWrapper<FundTradeRecord> buildTradeQueryWrapper(TradeQueryDTO dto, Long userId) {
        LambdaQueryWrapper<FundTradeRecord> wrapper = new LambdaQueryWrapper<>();
        // 强制用户隔离
        wrapper.eq(FundTradeRecord::getUserId, userId);

        // 基金代码筛选
        if (StringUtils.hasText(dto.getFundCode())) {
            wrapper.eq(FundTradeRecord::getFundCode, dto.getFundCode());
        }

        // 交易类型筛选
        if (dto.getTradeType() != null) {
            wrapper.eq(FundTradeRecord::getTradeType, dto.getTradeType());
        }

        // 交易日期范围筛选
        if (dto.getStartDate() != null) {
            wrapper.ge(FundTradeRecord::getTradeDate, dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            wrapper.le(FundTradeRecord::getTradeDate, dto.getEndDate());
        }

        return wrapper;
    }

    /**
     * 分页交易记录转换为VO
     */
    private IPage<TradeRecordVO> convertRecordPageToVO(IPage<FundTradeRecord> recordPage) {
        if (recordPage.getTotal() == 0) {
            return recordPage.convert(r -> new TradeRecordVO());
        }

        // 批量查询基金信息
        List<String> fundCodeList = recordPage.getRecords().stream()
                .map(FundTradeRecord::getFundCode)
                .distinct()
                .collect(Collectors.toList());
        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.in(FundBaseInfo::getFundCode, fundCodeList);
        List<FundBaseInfo> fundList = fundBaseInfoMapper.selectList(fundWrapper);
        Map<String, FundBaseInfo> fundMap = fundList.stream()
                .collect(Collectors.toMap(FundBaseInfo::getFundCode, f -> f));

        // 批量查询分组信息
        List<Long> groupIdList = recordPage.getRecords().stream()
                .map(FundTradeRecord::getGroupId)
                .distinct()
                .collect(Collectors.toList());
        LambdaQueryWrapper<FundUserGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.in(FundUserGroup::getId, groupIdList);
        List<FundUserGroup> groupList = fundUserGroupMapper.selectList(groupWrapper);
        Map<Long, String> groupNameMap = groupList.stream()
                .collect(Collectors.toMap(FundUserGroup::getId, FundUserGroup::getGroupName));

        // 转换为VO
        return recordPage.convert(record -> {
            TradeRecordVO vo = new TradeRecordVO();
            BeanUtils.copyProperties(record, vo);

            // 填充基金信息
            FundBaseInfo fund = fundMap.get(record.getFundCode());
            if (fund != null) {
                vo.setFundName(fund.getFundName());
                vo.setFundShortName(fund.getFundShortName());
            }

            // 填充分组名称
            vo.setGroupName(groupNameMap.get(record.getGroupId()));

            // 填充交易类型中文描述
            vo.setTradeTypeDesc(getTradeTypeDesc(record.getTradeType()));

            // 填充交易时点中文描述
            vo.setTradeTimeFlagDesc(getTimeFlagDesc(record.getTradeTimeFlag()));

            return vo;
        });
    }

    /**
     * 列表交易记录转换为VO
     */
    private List<TradeRecordVO> convertRecordListToVO(List<FundTradeRecord> recordList) {
        if (recordList.isEmpty()) {
            return List.of();
        }

        // 批量查询基金信息
        List<String> fundCodeList = recordList.stream()
                .map(FundTradeRecord::getFundCode)
                .distinct()
                .collect(Collectors.toList());
        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.in(FundBaseInfo::getFundCode, fundCodeList);
        List<FundBaseInfo> fundList = fundBaseInfoMapper.selectList(fundWrapper);
        Map<String, FundBaseInfo> fundMap = fundList.stream()
                .collect(Collectors.toMap(FundBaseInfo::getFundCode, f -> f));

        // 批量查询分组信息
        List<Long> groupIdList = recordList.stream()
                .map(FundTradeRecord::getGroupId)
                .distinct()
                .collect(Collectors.toList());
        LambdaQueryWrapper<FundUserGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.in(FundUserGroup::getId, groupIdList);
        List<FundUserGroup> groupList = fundUserGroupMapper.selectList(groupWrapper);
        Map<Long, String> groupNameMap = groupList.stream()
                .collect(Collectors.toMap(FundUserGroup::getId, FundUserGroup::getGroupName));

        // 转换为VO
        return recordList.stream().map(record -> {
            TradeRecordVO vo = new TradeRecordVO();
            BeanUtils.copyProperties(record, vo);

            FundBaseInfo fund = fundMap.get(record.getFundCode());
            if (fund != null) {
                vo.setFundName(fund.getFundName());
                vo.setFundShortName(fund.getFundShortName());
            }

            vo.setGroupName(groupNameMap.get(record.getGroupId()));
            vo.setTradeTypeDesc(getTradeTypeDesc(record.getTradeType()));
            vo.setTradeTimeFlagDesc(getTimeFlagDesc(record.getTradeTimeFlag()));

            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 计算单只基金的收益明细
     */
    private FundHoldProfitVO calculateFundProfit(FundBaseInfo fund, UserFundHold hold, List<FundTradeRecord> tradeList) {
        FundHoldProfitVO vo = new FundHoldProfitVO();
        vo.setFundCode(fund.getFundCode());
        vo.setFundName(fund.getFundName());
        vo.setFundShortName(fund.getFundShortName());

        // 基础持仓数据
        BigDecimal holdShares = hold != null ? hold.getHoldShares() : BigDecimal.ZERO;
        BigDecimal totalCostAmount = hold != null ? hold.getTotalCostAmount() : BigDecimal.ZERO;
        vo.setHoldShares(holdShares);
        vo.setTotalCostAmount(totalCostAmount);

        // 计算累计卖出回款、累计手续费、已实现收益
        BigDecimal totalSellAmount = BigDecimal.ZERO; // 累计卖出回款 + 现金分红
        BigDecimal totalChargeFee = BigDecimal.ZERO; // 累计总手续费
        BigDecimal totalAddAmount = BigDecimal.ZERO; // 【修复】累计净投入（买入+红利再投资 - 手续费）

        for (FundTradeRecord record : tradeList) {
            // 1. 累加所有手续费
            totalChargeFee = totalChargeFee.add(record.getChargeFee());

            // 2. 买入 + 红利再投资 → 算净投入（交易金额 - 手续费）
            if (record.getTradeType() == TRADE_TYPE_ADD || record.getTradeType() == TRADE_TYPE_DIVIDEND_REINVEST) {
                BigDecimal netAmount = record.getTradeAmount().subtract(record.getChargeFee());
                totalAddAmount = totalAddAmount.add(netAmount);
            }

            // 3. 卖出 + 现金分红 → 算总回款
            else if (record.getTradeType() == TRADE_TYPE_REDUCE || record.getTradeType() == TRADE_TYPE_DIVIDEND_CASH) {
                totalSellAmount = totalSellAmount.add(record.getTradeAmount());
            }
        }

        vo.setTotalSellAmount(totalSellAmount);
        vo.setTotalChargeFee(totalChargeFee);

        // 计算当前市值、浮盈
        BigDecimal currentMarketValue = holdShares.multiply(fund.getLatestNetValue()).setScale(SCALE, ROUNDING_MODE);
        BigDecimal holdFloatProfit = currentMarketValue.subtract(totalCostAmount).setScale(SCALE, ROUNDING_MODE);
        vo.setCurrentMarketValue(currentMarketValue);
        vo.setHoldFloatProfit(holdFloatProfit);

        // 计算已实现收益、总收益
        BigDecimal sellRealProfit = totalSellAmount.subtract(totalAddAmount.subtract(totalCostAmount)).setScale(SCALE, ROUNDING_MODE);
        BigDecimal totalProfitAmount = holdFloatProfit.add(sellRealProfit).setScale(SCALE, ROUNDING_MODE);
        vo.setSellRealProfit(sellRealProfit);
        vo.setTotalProfitAmount(totalProfitAmount);

        // 计算总收益率（用净投入计算，更准确）
        BigDecimal totalInput = totalAddAmount;
        if (totalInput.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalProfitRate = totalProfitAmount.divide(totalInput, 4, ROUNDING_MODE).multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE);
            vo.setTotalProfitRate(totalProfitRate);
        } else {
            vo.setTotalProfitRate(BigDecimal.ZERO);
        }

        return vo;
    }

    /**
     * 获取交易类型中文描述
     */
    private String getTradeTypeDesc(Byte tradeType) {
        return switch (tradeType) {
            case TRADE_TYPE_ADD -> "加仓";
            case TRADE_TYPE_REDUCE -> "减仓";
            case TRADE_TYPE_DIVIDEND_CASH -> "现金分红";
            case TRADE_TYPE_DIVIDEND_REINVEST -> "红利再投资";
            default -> "未知类型";
        };
    }

    @Override
    public void deleteTradeRecord(Long userId, Long tradeId) {
        FundTradeRecord record = this.getById(tradeId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException("交易记录不存在");
        }
        this.removeById(tradeId);
    }

    /**
     * 获取交易时点中文描述
     */
    private String getTimeFlagDesc(Byte timeFlag) {
        return switch (timeFlag) {
            case TIME_FLAG_BEFORE_15 -> "15点前";
            case TIME_FLAG_AFTER_15 -> "15点后/非交易日";
            default -> "未知";
        };
    }
}
