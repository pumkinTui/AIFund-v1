package com.fund.assistant.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fund.assistant.dto.FundInvestPlanCreateDTO;
import com.fund.assistant.dto.FundInvestPlanUpdateDTO;
import com.fund.assistant.entity.FundInvestPlan;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.vo.FundInvestExecRecordVO;
import com.fund.assistant.vo.FundInvestPlanVO;

import java.util.List;

/**
 * <p>
 * 定投计划表 服务类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public interface FundInvestPlanService extends IService<FundInvestPlan> {


    /**
     * 创建定投计划
     */
    FundInvestPlanVO createPlan(FundInvestPlanCreateDTO dto);

    /**
     * 修改定投计划
     */
    FundInvestPlanVO updatePlan(FundInvestPlanUpdateDTO dto);

    /**
     * 暂停定投计划
     */
    void pausePlan(Long planId);

    /**
     * 恢复定投计划
     */
    void resumePlan(Long planId);

    /**
     * 终止定投计划
     */
    void terminatePlan(Long planId);

    /**
     * 查询我的定投计划列表
     */
    IPage<FundInvestPlanVO> getMyPlanList(Integer pageNum, Integer pageSize);

    /**
     * 查询定投计划详情
     */
    FundInvestPlanVO getPlanDetail(Long planId);

    /**
     * 查询定投计划的执行记录
     */
    List<FundInvestExecRecordVO> getPlanExecRecords(Long planId);

    /**
     * 手动执行定投（测试用）
     */
    void manualExecutePlan(Long planId);

    /**
     * 定时任务：批量执行到期定投计划
     */
    void batchExecuteDuePlans();
}
