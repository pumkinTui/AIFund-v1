package com.fund.assistant.controller;

import com.fund.assistant.service.UserAssetService;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.UserAssetOverviewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产总览
 */
@Slf4j
@RestController
@RequestMapping("/user/asset")
public class UserAssetController {

    @Autowired
    private UserAssetService userAssetService;

    /**
     * 获取当前用户的资产总览
     */
    @GetMapping("/overview")
    public Result<UserAssetOverviewVO> getAssetOverview() {
        UserAssetOverviewVO overview = userAssetService.getAssetOverview();
        return Result.success(overview);
    }
}