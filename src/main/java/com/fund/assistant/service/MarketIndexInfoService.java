package com.fund.assistant.service;

import com.fund.assistant.entity.MarketIndexInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.vo.MarketIndexRealtimeVO;

import java.util.List;

/**
 * <p>
 * 大盘指数基础信息表 服务类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public interface MarketIndexInfoService extends IService<MarketIndexInfo> {

    /**
     * 获取大盘指数实时行情（用户端调用，始终返回 Redis 缓存）
     */
    List<MarketIndexRealtimeVO> getRealtimeQuotes();

    /**
     * 强制刷新大盘指数实时行情到 Redis（定时任务用，保留兼容）
     */
    void refreshRealtimeQuotes();

    /**
     * 仅刷新 A 股 + 港股指数（定时任务用，交易时段）
     */
    void refreshChinaIndices();

    /**
     * 仅刷新美股指数（定时任务用，含夜晚时段）
     */
    void refreshUSIndices();
}
