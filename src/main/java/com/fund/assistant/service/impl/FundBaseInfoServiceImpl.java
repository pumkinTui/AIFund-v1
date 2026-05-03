package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.dto.FundQueryDTO;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundNetValueHistory;
import com.fund.assistant.entity.FundStockHoldDetail;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.FundNetValueHistoryMapper;
import com.fund.assistant.mapper.FundStockHoldDetailMapper;
import com.fund.assistant.service.FundBaseInfoService;
import com.fund.assistant.vo.FundBaseInfoVO;
import com.fund.assistant.vo.FundNetValueVO;
import com.fund.assistant.vo.FundStockHoldVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FundBaseInfoServiceImpl extends ServiceImpl<FundBaseInfoMapper, FundBaseInfo> implements FundBaseInfoService {

    @Autowired
    private FundNetValueHistoryMapper fundNetValueHistoryMapper;

    @Autowired
    private FundStockHoldDetailMapper fundStockHoldDetailMapper;

    /**
     * 分页查询基金列表（支持搜索）
     */
    @Override
    public IPage<FundBaseInfoVO> getFundList(FundQueryDTO dto) {
        // 1. 构建分页对象
        Page<FundBaseInfo> page = new Page<>(dto.getPageNum(), dto.getPageSize());

        // 2. 构建查询条件（完全适配你的实体字段）
        LambdaQueryWrapper<FundBaseInfo> wrapper = new LambdaQueryWrapper<>();
        // 关键词搜索（基金全称/简称/代码，适配你的字段）
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.and(w -> w
                    .like(FundBaseInfo::getFundName, dto.getKeyword())
                    .or()
                    .like(FundBaseInfo::getFundShortName, dto.getKeyword())
                    .or()
                    .like(FundBaseInfo::getFundCode, dto.getKeyword())
            );
        }
        // 基金类型筛选
        if (StringUtils.hasText(dto.getFundType())) {
            wrapper.eq(FundBaseInfo::getFundType, dto.getFundType());
        }
        // 基金板块筛选
        if (StringUtils.hasText(dto.getFundPlate())) {
            wrapper.eq(FundBaseInfo::getFundPlate, dto.getFundPlate());
        }
        // 按数据更新时间倒序
        wrapper.orderByDesc(FundBaseInfo::getUpdateTime);

        // 3. 执行分页查询
        IPage<FundBaseInfo> fundPage = this.page(page, wrapper);

        // 4. 转换为VO返回
        IPage<FundBaseInfoVO> voPage = fundPage.convert(fund -> {
            FundBaseInfoVO vo = new FundBaseInfoVO();
            BeanUtils.copyProperties(fund, vo);
            return vo;
        });

        return voPage;
    }

    /**
     * 根据基金代码查询基金详情
     */
    @Override
    public FundBaseInfoVO getFundDetail(String fundCode) {
        // 根据基金代码查询
        LambdaQueryWrapper<FundBaseInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundBaseInfo::getFundCode, fundCode);
        FundBaseInfo fund = this.getOne(wrapper);

        if (fund == null) {
            throw new BusinessException("基金不存在");
        }

        FundBaseInfoVO vo = new FundBaseInfoVO();
        BeanUtils.copyProperties(fund, vo);
        return vo;
    }

    /**
     * 查询基金历史净值
     */
    @Override
    public List<FundNetValueVO> getFundNetValueHistory(String fundCode, LocalDate startDate, LocalDate endDate) {
        // 查询基金历史净值
        LambdaQueryWrapper<FundNetValueHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundNetValueHistory::getFundCode, fundCode);
        if (startDate == null && endDate == null) {
            startDate = LocalDate.now().minusMonths(1);
            endDate = LocalDate.now();
        }
        if (startDate != null) {
            wrapper.ge(FundNetValueHistory::getNetValueDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(FundNetValueHistory::getNetValueDate, endDate);
        }
        wrapper.orderByDesc(FundNetValueHistory::getNetValueDate);

        List<FundNetValueHistory> list = fundNetValueHistoryMapper.selectList(wrapper);

        // 转换为VO
        return list.stream().map(history -> {
            FundNetValueVO vo = new FundNetValueVO();
            BeanUtils.copyProperties(history, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 查询基金前十重仓股
     */
    @Override
    public List<FundStockHoldVO> getFundStockHold(String fundCode) {
        // 1. 先查最新报告日期
        LambdaQueryWrapper<FundStockHoldDetail> dateWrapper = new LambdaQueryWrapper<>();
        dateWrapper.select(FundStockHoldDetail::getReportDate)
                .eq(FundStockHoldDetail::getFundCode, fundCode)
                .orderByDesc(FundStockHoldDetail::getReportDate)
                .last("LIMIT 1");
        FundStockHoldDetail latest = fundStockHoldDetailMapper.selectOne(dateWrapper);
        if (latest == null) {
            return Collections.emptyList();
        }
        LocalDate latestDate = latest.getReportDate();

        // 2. 按最新报告日期过滤，按持仓占比倒序
        LambdaQueryWrapper<FundStockHoldDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundStockHoldDetail::getFundCode, fundCode)
                .eq(FundStockHoldDetail::getReportDate, latestDate)
                .orderByDesc(FundStockHoldDetail::getHoldRatio)
                .last("LIMIT 10");

        List<FundStockHoldDetail> list = fundStockHoldDetailMapper.selectList(wrapper);
        return list.stream().map(stock -> {
            FundStockHoldVO vo = new FundStockHoldVO();
            BeanUtils.copyProperties(stock, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}