package com.fund.assistant.service;

import com.fund.assistant.entity.FundRealtimeValuation;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.vo.FundRealtimeValuationVO;

import java.util.List;

/**
 * <p>
 * 基金实时估值结果表 服务类
 * </p>
 *
 * @author jhshen
 * @since 2026-05-05
 */
public interface FundRealtimeValuationService extends IService<FundRealtimeValuation> {

    /**
     * 计算单只基金的实时估值
     */
    FundRealtimeValuationVO calculateFundRealtimeValuation(String fundCode);

    /**
     * 批量计算所有基金的实时估值（定时任务用）
     */
    void batchCalculateAllFundValuation();

    /**
     * 查询单只基金最新估值
     */
    FundRealtimeValuationVO getLatestValuationByFundCode(String fundCode);

    /**
     * 查询用户自选基金最新估值列表
     */
    List<FundRealtimeValuationVO> getFavoriteFundValuationList();

    /**
     * 查询用户持仓基金最新估值列表
     */
    List<FundRealtimeValuationVO> getHoldFundValuationList();

    /**
     * 查询某只基金当天走势图数据（从 Redis 获取）
     */
    List<FundRealtimeValuationVO> getTimeline(String fundCode);
}
