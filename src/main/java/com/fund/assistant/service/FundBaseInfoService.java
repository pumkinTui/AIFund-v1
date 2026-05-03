package com.fund.assistant.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.dto.FundQueryDTO;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.vo.FundBaseInfoVO;
import com.fund.assistant.vo.FundNetValueVO;
import com.fund.assistant.vo.FundStockHoldVO;

import java.time.LocalDate;
import java.util.List;

public interface FundBaseInfoService extends IService<FundBaseInfo> {

    /**
     * 分页查询基金列表（支持搜索）
     */
    IPage<FundBaseInfoVO> getFundList(FundQueryDTO dto);

    /**
     * 根据基金代码查询基金详情
     */
    FundBaseInfoVO getFundDetail(String fundCode);

    /**
     * 查询基金历史净值
     */
    List<FundNetValueVO> getFundNetValueHistory(String fundCode, LocalDate startDate, LocalDate endDate);

    /**
     * 查询基金前十重仓股
     */
    List<FundStockHoldVO> getFundStockHold(String fundCode);
}