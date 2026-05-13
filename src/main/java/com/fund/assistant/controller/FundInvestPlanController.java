package com.fund.assistant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fund.assistant.dto.FundInvestPlanCreateDTO;
import com.fund.assistant.dto.FundInvestPlanUpdateDTO;
import com.fund.assistant.service.FundInvestPlanService;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.FundInvestExecRecordVO;
import com.fund.assistant.vo.FundInvestPlanVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/fund/invest-plan")
public class FundInvestPlanController {

    @Autowired
    private FundInvestPlanService fundInvestPlanService;

    /**
     * 创建定投计划
     */
    @PostMapping("/create")
    public Result<FundInvestPlanVO> createPlan(@RequestBody FundInvestPlanCreateDTO dto) {
        log.info("创建定投计划信息为：{}",dto);
        FundInvestPlanVO vo = fundInvestPlanService.createPlan(dto);
        return Result.success(vo);
    }

    /**
     * 修改定投计划
     */
    @PostMapping("/update")
    public Result<FundInvestPlanVO> updatePlan(@RequestBody FundInvestPlanUpdateDTO dto) {
        log.info("修改定投计划位：{}",dto);
        FundInvestPlanVO vo = fundInvestPlanService.updatePlan(dto);
        return Result.success(vo);
    }

    /**
     * 暂停定投计划
     */
    @PostMapping("/pause/{planId}")
    public Result<Void> pausePlan(@PathVariable Long planId) {
        log.info("要暂停定投计划的id为：{}",planId);
        fundInvestPlanService.pausePlan(planId);
        return Result.success();
    }

    /**
     * 恢复定投计划
     */
    @PostMapping("/resume/{planId}")
    public Result<Void> resumePlan(@PathVariable Long planId) {
        log.info("要回复定投计划的id为：{}",planId);
        fundInvestPlanService.resumePlan(planId);
        return Result.success();
    }

    /**
     * 终止定投计划
     */
    @PostMapping("/terminate/{planId}")
    public Result<Void> terminatePlan(@PathVariable Long planId) {
        log.info("要终止定投计划的id为：{}",planId);
        fundInvestPlanService.terminatePlan(planId);
        return Result.success();
    }

    /**
     * 查询我的定投计划列表
     */
    @GetMapping("/my-list")
    public Result<IPage<FundInvestPlanVO>> getMyPlanList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<FundInvestPlanVO> page = fundInvestPlanService.getMyPlanList(pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * 查询定投计划详情
     */
    @GetMapping("/detail/{planId}")
    public Result<FundInvestPlanVO> getPlanDetail(@PathVariable Long planId) {
        log.info("查询定投计划详情的id为：{}",planId);
        FundInvestPlanVO vo = fundInvestPlanService.getPlanDetail(planId);
        return Result.success(vo);
    }

    /**
     * 查询定投计划的执行记录
     */
    @GetMapping("/exec-records/{planId}")
    public Result<List<FundInvestExecRecordVO>> getPlanExecRecords(@PathVariable Long planId) {
        log.info("查询定投计划的执行记录的id为：{}",planId);
        List<FundInvestExecRecordVO> list = fundInvestPlanService.getPlanExecRecords(planId);
        return Result.success(list);
    }

    /**
     * 手动执行定投（测试用）
     */
    @PostMapping("/manual-execute/{planId}")
    public Result<Void> manualExecutePlan(@PathVariable Long planId) {
        fundInvestPlanService.manualExecutePlan(planId);
        return Result.success();
    }

    /**
     * 手动触发批量执行（定时任务用）
     */
    @PostMapping("/batch-execute")
    public Result<Void> batchExecuteDuePlans() {
        fundInvestPlanService.batchExecuteDuePlans();
        return Result.success();
    }
}