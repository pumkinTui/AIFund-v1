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

/**
 * 基金基本信息
 */
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


    /**
     * 同步单只基金基础信息
     */
    FundBaseInfo syncFundBaseInfo(String fundCode);

    /**
     * 批量同步基金基础信息
     */
    void batchSyncFundBaseInfo(List<String> fundCodeList);

    /**
     * 从东方财富同步单只基金最新官方净值（收盘后调用）
     * @return true 表示今日净值已公布并同步成功，false 表示尚未公布
     */
    boolean syncLatestOfficialNav(String fundCode);
}