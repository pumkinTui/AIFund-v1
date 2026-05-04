package com.fund.assistant.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fund.assistant.dto.TradeQueryDTO;
import com.fund.assistant.entity.FundTradeRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.vo.FundHoldProfitVO;
import com.fund.assistant.vo.TradeRecordVO;

import java.util.List;

/**
 * <p>
 * 基金交易记录表 服务类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public interface FundTradeRecordService extends IService<FundTradeRecord> {

    /**
     * 分页查询当前用户的交易记录
     */
    IPage<TradeRecordVO> getTradeRecordPage(TradeQueryDTO dto);

    /**
     * 查询当前用户所有交易记录（不分页，用于导出）
     */
    List<TradeRecordVO> getTradeRecordList(TradeQueryDTO dto);

    /**
     * 查询单只基金的收益明细
     */
    FundHoldProfitVO getFundProfitDetail(String fundCode,Long groupId);

    /**
     * 查询当前用户所有持仓基金的收益明细
     */
    List<FundHoldProfitVO> getAllHoldProfitList();
}
