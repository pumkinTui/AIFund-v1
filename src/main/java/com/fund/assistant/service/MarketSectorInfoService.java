package com.fund.assistant.service;

import com.fund.assistant.entity.MarketSectorInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.vo.MarketSectorRealtimeVO;

import java.util.List;

/**
 * <p>
 * 行业板块资金&行情表 服务类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public interface MarketSectorInfoService extends IService<MarketSectorInfo> {

    /**
     * 获取行业板块涨跌排行
     * 优先查 Redis，没命中再调东方财富接口
     */
    List<MarketSectorRealtimeVO> getSectorRanking();

    /**
     * 强制刷新板块排行到 Redis（定时任务用）
     */
    void refreshSectorRanking();

    /**
     * 从东方财富同步板块数据到数据库
     */
    void syncSectorData();
}
