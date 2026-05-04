package com.fund.assistant.service;

import com.fund.assistant.dto.FundHoldBuyDTO;
import com.fund.assistant.dto.FundHoldSellDTO;
import com.fund.assistant.entity.UserFundHold;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.vo.UserFundHoldVO;

import java.util.List;

/**
 * <p>
 * 用户持仓表 服务类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public interface UserFundHoldService extends IService<UserFundHold> {


    /**
     * 基金买入
     */
    void buyFund(FundHoldBuyDTO dto);

    /**
     * 基金卖出
     */
    void sellFund(FundHoldSellDTO dto);

    /**
     * 查询当前用户所有持仓（按分组）
     */
    List<UserFundHoldVO> getHoldList(Long groupId);

    /**
     * 查询单只基金的持仓记录
     */
    UserFundHoldVO getHoldDetail(String fundCode);

}
