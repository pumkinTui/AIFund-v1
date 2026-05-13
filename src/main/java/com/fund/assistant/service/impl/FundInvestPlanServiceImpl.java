package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fund.assistant.dto.FundBuyResultDTO;
import com.fund.assistant.dto.FundHoldBuyDTO;
import com.fund.assistant.dto.FundInvestPlanCreateDTO;
import com.fund.assistant.dto.FundInvestPlanUpdateDTO;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundInvestExecRecord;
import com.fund.assistant.entity.FundInvestPlan;
import com.fund.assistant.entity.FundPendingTrade;
import com.fund.assistant.entity.FundUserGroup;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.FundInvestExecRecordMapper;
import com.fund.assistant.mapper.FundInvestPlanMapper;
import com.fund.assistant.mapper.FundPendingTradeMapper;
import com.fund.assistant.mapper.FundUserGroupMapper;
import com.fund.assistant.service.FundInvestPlanService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.service.UserFundHoldService;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.FundInvestExecRecordVO;
import com.fund.assistant.vo.FundInvestPlanVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 定投计划表 服务实现类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Slf4j
@Service
public class FundInvestPlanServiceImpl extends ServiceImpl<FundInvestPlanMapper, FundInvestPlan> implements FundInvestPlanService {

    @Autowired
    private UserFundHoldService userFundHoldService;

    @Autowired
    private FundInvestExecRecordMapper fundInvestExecRecordMapper;

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    @Autowired
    private FundUserGroupMapper fundUserGroupMapper;

    @Autowired
    private FundPendingTradeMapper fundPendingTradeMapper;

    // 周期常量
    private static final byte CYCLE_DAILY = 1;
    private static final byte CYCLE_WEEKLY = 2;
    private static final byte CYCLE_MONTHLY = 3;
    // 状态常量
    // 计划状态：0=暂停 1=正常 2=已结束
    private static final byte STATUS_RUNNING = 0;    // 进行中
    private static final byte STATUS_PAUSED = 1;     // 已暂停
    private static final byte STATUS_TERMINATED = 2; // 已终止
    // 执行状态常量
    private static final byte EXEC_SUCCESS = 0;
    private static final byte EXEC_FAIL = 1;
    // 默认费率
    private static final BigDecimal DEFAULT_CHARGE_RATE = new BigDecimal("0.0000");

    /**
     * 创建定投计划
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FundInvestPlanVO createPlan(FundInvestPlanCreateDTO dto) {
        Long userId = UserContext.getUserId();

        // 1. 校验基金是否存在
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, dto.getFundCode())
        );
        if (fund == null) {
            throw new BusinessException("基金不存在");
        }

        // 2. 校验分组是否存在
        if (dto.getGroupId() != null) {
            FundUserGroup group = fundUserGroupMapper.selectOne(
                    new LambdaQueryWrapper<FundUserGroup>()
                            .eq(FundUserGroup::getId, dto.getGroupId())
                            .eq(FundUserGroup::getUserId, userId)
            );
            if (group == null) {
                throw new BusinessException("持仓分组不存在");
            }
        }

        // 3. 计算下次扣款日期
        LocalDate startDate = dto.getStartDate() != null ? dto.getStartDate() : LocalDate.now();
        LocalDate nextDeductDate = calculateNextDeductDate(startDate, dto.getInvestCycle(), dto.getCycleDay());

        // 4. 创建定投计划
        FundInvestPlan plan = new FundInvestPlan();
        plan.setUserId(userId);
        plan.setFundCode(dto.getFundCode());
        plan.setGroupId(dto.getGroupId());
        plan.setInvestAmount(dto.getInvestAmount());
        plan.setChargeRate(dto.getChargeRate() != null ? dto.getChargeRate() : DEFAULT_CHARGE_RATE);
        plan.setInvestCycle(dto.getInvestCycle());
        plan.setCycleDay(dto.getCycleDay());
        plan.setNextDeductDate(nextDeductDate);
        plan.setTotalInvestPeriod(0);
        plan.setTotalInvestAmount(BigDecimal.ZERO);
        plan.setPlanStatus(STATUS_RUNNING);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        this.save(plan);

        log.info("用户 {} 创建定投计划成功，计划ID：{}", userId, plan.getId());
        return convertPlanToVO(plan);
    }


    /**
     * 修改定投计划
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FundInvestPlanVO updatePlan(FundInvestPlanUpdateDTO dto) {
        Long userId = UserContext.getUserId();

        // 1. 查询定投计划
        FundInvestPlan plan = this.getById(dto.getPlanId());
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException("定投计划不存在");
        }
        if (plan.getPlanStatus() == STATUS_TERMINATED) {
            throw new BusinessException("已终止的计划无法修改");
        }

        // 2. 更新计划
        if (dto.getInvestAmount() != null) {
            plan.setInvestAmount(dto.getInvestAmount());
        }
        if (dto.getChargeRate() != null) {
            plan.setChargeRate(dto.getChargeRate());
        }
        plan.setUpdateTime(LocalDateTime.now());
        this.updateById(plan);

        log.info("用户 {} 修改定投计划成功，计划ID：{}", userId, plan.getId());
        return convertPlanToVO(plan);
    }


    /**
     * 暂停定投计划
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pausePlan(Long planId) {
        Long userId = UserContext.getUserId();
        FundInvestPlan plan = this.getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException("定投计划不存在");
        }
        if (plan.getPlanStatus() != STATUS_RUNNING) {
            throw new BusinessException("只有进行中的计划才能暂停");
        }

        plan.setPlanStatus(STATUS_PAUSED);
        plan.setUpdateTime(LocalDateTime.now());
        this.updateById(plan);
        log.info("用户 {} 暂停定投计划成功，计划ID：{}", userId, planId);
    }

    /**
     * 恢复定投计划
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumePlan(Long planId) {
        Long userId = UserContext.getUserId();
        FundInvestPlan plan = this.getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException("定投计划不存在");
        }
        if (plan.getPlanStatus() != STATUS_PAUSED) {
            throw new BusinessException("只有已暂停的计划才能恢复");
        }

        // 重新计算下次扣款日期（用计划自带的 cycleDay）
        LocalDate nextDeductDate = calculateNextDeductDate(LocalDate.now(), plan.getInvestCycle(), plan.getCycleDay());
        plan.setPlanStatus(STATUS_RUNNING);
        plan.setNextDeductDate(nextDeductDate);
        plan.setUpdateTime(LocalDateTime.now());
        this.updateById(plan);
        log.info("用户 {} 恢复定投计划成功，计划ID：{}", userId, planId);
    }


    /**
     * 终止定投计划
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminatePlan(Long planId) {
        Long userId = UserContext.getUserId();
        FundInvestPlan plan = this.getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException("定投计划不存在");
        }
        if (plan.getPlanStatus() == STATUS_TERMINATED) {
            throw new BusinessException("计划已终止");
        }

        plan.setPlanStatus(STATUS_TERMINATED);
        plan.setUpdateTime(LocalDateTime.now());
        this.updateById(plan);
        log.info("用户 {} 终止定投计划成功，计划ID：{}", userId, planId);
    }

    /**
     * 查询我的定投计划列表
     */
    @Override
    public IPage<FundInvestPlanVO> getMyPlanList(Integer pageNum, Integer pageSize) {
        Long userId = UserContext.getUserId();
        Page<FundInvestPlan> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<FundInvestPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundInvestPlan::getUserId, userId);
        wrapper.orderByDesc(FundInvestPlan::getCreateTime);

        IPage<FundInvestPlan> planPage = this.page(page, wrapper);
        return convertPlanPageToVO(planPage);
    }

    /**
     * 查询定投计划详情
     */
    @Override
    public FundInvestPlanVO getPlanDetail(Long planId) {
        Long userId = UserContext.getUserId();
        FundInvestPlan plan = this.getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException("定投计划不存在");
        }
        return convertPlanToVO(plan);
    }

    /**
     * 查询定投计划的执行记录
     */
    @Override
    public List<FundInvestExecRecordVO> getPlanExecRecords(Long planId) {
        Long userId = UserContext.getUserId();
        // 校验计划归属
        FundInvestPlan plan = this.getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException("定投计划不存在");
        }
        // 查询执行记录
        List<FundInvestExecRecord> recordList = fundInvestExecRecordMapper.selectList(
                new LambdaQueryWrapper<FundInvestExecRecord>()
                        .eq(FundInvestExecRecord::getPlanId, planId)
                        .orderByDesc(FundInvestExecRecord::getCreateTime)
        );

        return convertRecordListToVO(recordList, plan.getFundCode());
    }


    /**
     * 手动执行定投（测试用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualExecutePlan(Long planId) {
        Long userId = UserContext.getUserId();
        FundInvestPlan plan = this.getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException("定投计划不存在");
        }
        if (plan.getPlanStatus() != STATUS_RUNNING) {
            throw new BusinessException("只有进行中的计划才能执行");
        }

        executeSinglePlan(plan);
    }

    /**
     * 手动触发批量执行（定时任务用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchExecuteDuePlans() {
        log.info("开始批量执行到期定投计划");
        LocalDate today = LocalDate.now();

        // 查询所有到期（≤今天）且状态为进行中的定投计划
        // 用 le 而非 eq，防止某天任务没跑到导致计划永远卡住
        List<FundInvestPlan> duePlans = this.list(
                new LambdaQueryWrapper<FundInvestPlan>()
                        .le(FundInvestPlan::getNextDeductDate, today)
                        .eq(FundInvestPlan::getPlanStatus, STATUS_RUNNING)
        );

        if (duePlans.isEmpty()) {
            log.info("今日无到期定投计划");
            return;
        }

        int success = 0, fail = 0;
        for (FundInvestPlan plan : duePlans) {
            try {
                executeSinglePlan(plan);
                success++;
            } catch (Exception e) {
                fail++;
                log.error("定投计划执行失败，计划ID：{}，错误：{}", plan.getId(), e.getMessage());
                // 记录失败执行记录
                saveExecRecord(plan, null, null, null, EXEC_FAIL, e.getMessage());
            }
        }

        log.info("批量执行定投计划完成：成功 {}，失败 {}", success, fail);
    }


    /**
     * 执行单个定投计划
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeSinglePlan(FundInvestPlan plan) {
        // 防重：同一个计划在今天只能产生一条待确认交易
        long todayCount = fundPendingTradeMapper.selectCount(
                new LambdaQueryWrapper<FundPendingTrade>()
                        .eq(FundPendingTrade::getRelatedPlanId, plan.getId())
                        .eq(FundPendingTrade::getStatus, (byte) 0)
                        .eq(FundPendingTrade::getTradeDate, LocalDate.now()));
        if (todayCount > 0) {
            log.warn("定投计划 {} 今日已有待确认交易，跳过重复执行", plan.getId());
            return;
        }

        // 1. 构建买入DTO，复用买入逻辑
        FundHoldBuyDTO buyDTO = new FundHoldBuyDTO();
        buyDTO.setFundCode(plan.getFundCode());
        buyDTO.setGroupId(plan.getGroupId());
        buyDTO.setInvestAmount(plan.getInvestAmount());
        buyDTO.setChargeRate(plan.getChargeRate());
        buyDTO.setTradeTimeFlag((byte) 1);
        buyDTO.setRelatedPlanId(plan.getId());

        // 定时任务线程无 HTTP 请求上下文，手动注入用户信息
        com.fund.assistant.dto.UserDTO userDTO = new com.fund.assistant.dto.UserDTO();
        userDTO.setId(plan.getUserId());
        com.fund.assistant.util.UserContext.saveUser(userDTO);
        try {
            // 2. 调用买入，创建待确认交易（不再立即计算份额）
            FundBuyResultDTO buyResult = userFundHoldService.buyFund(buyDTO);

            // 3. 保存执行记录（确认份额待确认后更新）
            saveExecRecord(plan, plan.getInvestAmount(), buyResult.getChargeFee(), BigDecimal.ZERO, EXEC_SUCCESS, null);
        } finally {
            com.fund.assistant.util.UserContext.removeUser();
        }

        // 4. 更新下次扣款日期
        plan.setNextDeductDate(calculateNextDeductDate(LocalDate.now(), plan.getInvestCycle(), plan.getCycleDay()));
        plan.setUpdateTime(LocalDateTime.now());
        this.updateById(plan);

        log.info("定投计划执行成功，计划ID：{}，定投金额：{}，下次扣款：{}", plan.getId(), plan.getInvestAmount(), plan.getNextDeductDate());
    }

    /**
     * 保存定投执行记录
     */
    private void saveExecRecord(FundInvestPlan plan, BigDecimal deductAmount,
                                BigDecimal chargeFee, BigDecimal confirmShares,
                                Byte execStatus, String failReason) {
        FundInvestExecRecord record = new FundInvestExecRecord();
        record.setPlanId(plan.getId());
        record.setUserId(plan.getUserId());
        record.setDeductDate(LocalDate.now());
        record.setDeductAmount(deductAmount != null ? deductAmount : plan.getInvestAmount());
        record.setChargeFee(chargeFee != null ? chargeFee : BigDecimal.ZERO);
        record.setConfirmShares(confirmShares != null ? confirmShares : BigDecimal.ZERO);
        record.setExecStatus(execStatus);
        record.setCreateTime(LocalDateTime.now());
        fundInvestExecRecordMapper.insert(record);
    }

    /**
     * 根据当前日期 + 定投周期，计算下一次扣款日期
     * @param baseDate   开始计算的基准日期
     * @param cycle      周期类型：1=每日 2=每周 3=每月
     * @param cycleDay   周几/几号扣款（每周1-7，每月1-28）
     * @return 下一次扣款日期
     */
    private LocalDate calculateNextDeductDate(LocalDate baseDate, Byte cycle, Byte cycleDay) {
        switch (cycle) {
            // 1. 每日定投
            case CYCLE_DAILY:
                return baseDate.plusDays(1);
            // 2. 每周定投
            case CYCLE_WEEKLY:
                int dayOfWeek = baseDate.getDayOfWeek().getValue();
                int targetWeekDay = (cycleDay != null ? cycleDay : 1);
                int daysToAdd = targetWeekDay - dayOfWeek;
                if (daysToAdd <= 0) daysToAdd += 7;
                return baseDate.plusDays(daysToAdd);

            // 3. 每月定投
            case CYCLE_MONTHLY:
                int dayOfMonth = baseDate.getDayOfMonth();
                int targetDay = cycleDay != null ? cycleDay : 1;

                if (targetDay <= dayOfMonth) {
                    LocalDate nextMonth = baseDate.plusMonths(1);
                    int actualDay = Math.min(targetDay, nextMonth.lengthOfMonth());
                    return nextMonth.withDayOfMonth(actualDay);
                } else {
                    int actualDay = Math.min(targetDay, baseDate.lengthOfMonth());
                    LocalDate result = baseDate.withDayOfMonth(actualDay);
                    // 如果 targetDay 超过本月天数，截断后可能 <= baseDate，推到下月
                    if (!result.isAfter(baseDate)) {
                        LocalDate nextMonth = baseDate.plusMonths(1);
                        int nextDay = Math.min(targetDay, nextMonth.lengthOfMonth());
                        result = nextMonth.withDayOfMonth(nextDay);
                    }
                    return result;
                }

            default:
                return baseDate.plusDays(1);
        }
    }

    /**
     * 定投计划转换为VO
     */
    private FundInvestPlanVO convertPlanToVO(FundInvestPlan plan) {
        FundInvestPlanVO vo = new FundInvestPlanVO();
        BeanUtils.copyProperties(plan, vo);
        // 补充基金信息
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, plan.getFundCode())
        );
        if (fund != null) {
            vo.setFundName(fund.getFundName());
            vo.setFundShortName(fund.getFundShortName());
        }
        // 补充分组信息
        if (plan.getGroupId() != null) {
            FundUserGroup group = fundUserGroupMapper.selectById(plan.getGroupId());
            if (group != null) {
                vo.setGroupName(group.getGroupName());
            }
        }
        // 补充描述
        vo.setInvestCycleDesc(getCycleDesc(plan.getInvestCycle()));
        vo.setPlanStatusDesc(getStatusDesc(plan.getPlanStatus()));

        return vo;
    }

    /**
     * 分页计划转换为VO
     */
    private IPage<FundInvestPlanVO> convertPlanPageToVO(IPage<FundInvestPlan> planPage) {
        if (planPage.getTotal() == 0) {
            return planPage.convert(p -> new FundInvestPlanVO());
        }

        // 批量查询基金信息
        List<String> fundCodeList = planPage.getRecords().stream()
                .map(FundInvestPlan::getFundCode)
                .distinct()
                .collect(Collectors.toList());
        List<FundBaseInfo> fundList = fundBaseInfoMapper.selectList(
                new LambdaQueryWrapper<FundBaseInfo>().in(FundBaseInfo::getFundCode, fundCodeList)
        );
        Map<String, FundBaseInfo> fundMap = fundList.stream()
                .collect(Collectors.toMap(FundBaseInfo::getFundCode, f -> f));

        // 批量查询分组信息
        List<Long> groupIdList = planPage.getRecords().stream()
                .map(FundInvestPlan::getGroupId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        List<FundUserGroup> groupList = fundUserGroupMapper.selectList(
                new LambdaQueryWrapper<FundUserGroup>().in(FundUserGroup::getId, groupIdList)
        );
        Map<Long, String> groupNameMap = groupList.stream()
                .collect(Collectors.toMap(FundUserGroup::getId, FundUserGroup::getGroupName));

        return planPage.convert(plan -> {
            FundInvestPlanVO vo = new FundInvestPlanVO();
            BeanUtils.copyProperties(plan, vo);

            FundBaseInfo fund = fundMap.get(plan.getFundCode());
            if (fund != null) {
                vo.setFundName(fund.getFundName());
                vo.setFundShortName(fund.getFundShortName());
            }

            if (plan.getGroupId() != null) {
                vo.setGroupName(groupNameMap.get(plan.getGroupId()));
            }

            vo.setInvestCycleDesc(getCycleDesc(plan.getInvestCycle()));
            vo.setPlanStatusDesc(getStatusDesc(plan.getPlanStatus()));
            return vo;
        });
    }

    /**
     * 执行记录转换为VO
     */
    private List<FundInvestExecRecordVO> convertRecordListToVO(List<FundInvestExecRecord> recordList, String fundCode) {
        if (recordList.isEmpty()) {
            return List.of();
        }

        // 补充基金信息
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, fundCode)
        );

        return recordList.stream().map(record -> {
            FundInvestExecRecordVO vo = new FundInvestExecRecordVO();
            BeanUtils.copyProperties(record, vo);
            vo.setFundCode(fundCode);
            if (fund != null) {
                vo.setFundName(fund.getFundName());
                vo.setFundShortName(fund.getFundShortName());
            }
            vo.setExecStatusDesc(record.getExecStatus() == EXEC_SUCCESS ? "成功" : "失败");
            return vo;
        }).collect(Collectors.toList());
    }

    private String getCycleDesc(Byte cycle) {
        return switch (cycle) {
            case CYCLE_DAILY -> "每日";
            case CYCLE_WEEKLY -> "每周";
            case CYCLE_MONTHLY -> "每月";
            default -> "未知";
        };
    }

    private String getStatusDesc(Byte status) {
        return switch (status) {
            case STATUS_RUNNING -> "进行中";
            case STATUS_PAUSED -> "已暂停";
            case STATUS_TERMINATED -> "已终止";
            default -> "未知";
        };
    }
}
