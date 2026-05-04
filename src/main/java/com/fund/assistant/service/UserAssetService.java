package com.fund.assistant.service;

import com.fund.assistant.vo.UserAssetOverviewVO;

public interface UserAssetService {

    /**
     * 获取当前用户的资产总览
     */
    UserAssetOverviewVO getAssetOverview();
}