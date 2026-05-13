package com.fund.assistant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.entity.FundPendingTrade;
import com.fund.assistant.vo.FundPendingTradeVO;

import java.util.List;

public interface FundPendingTradeService extends IService<FundPendingTrade> {
    List<FundPendingTradeVO> getPendingList(Long userId, Byte tradeType);
    void cancelPendingTrade(Long userId, Long tradeId);
}
