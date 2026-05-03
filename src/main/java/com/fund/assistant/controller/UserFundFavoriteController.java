package com.fund.assistant.controller;

import com.fund.assistant.dto.FavoriteAddDTO;
import com.fund.assistant.service.UserFundFavoriteService;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.UserFundFavoriteVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户自选基金表 前端控制器
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Slf4j
@RestController
@RequestMapping("/user/favorite")
public class UserFundFavoriteController {

    @Autowired
    private UserFundFavoriteService userFundFavoriteService;

    /**
     * 添加自选
     */
    @PostMapping("/add")
    public Result<Void> addFavorite(@Validated @RequestBody FavoriteAddDTO dto) {
        log.info("添加自选基金信息为：{}",dto);
        userFundFavoriteService.addFavorite(dto);
        return Result.success();
    }

    /**
     * 删除自选
     */
    @PostMapping("/delete/{favoriteId}")
    public Result<Void> deleteFavorite(@PathVariable Long favoriteId) {
        log.info("要删除的自选基金id为：{}",favoriteId);
        userFundFavoriteService.deleteFavorite(favoriteId);
        return Result.success();
    }

    /**
     * 查询当前用户所有自选（按分组）
     */
    @GetMapping("/list")
    public Result<List<UserFundFavoriteVO>> getFavoriteList(@RequestParam(required = false) Long groupId) {
        log.info("要查询的自选基金分组id为：{}",groupId);
        List<UserFundFavoriteVO> favoriteList = userFundFavoriteService.getFavoriteList(groupId);
        return Result.success(favoriteList);
    }

    /**
     * 检查基金是否已自选
     */
    @GetMapping("/check/{fundCode}")
    public Result<Boolean> checkFavorite(@PathVariable String fundCode) {
        log.info("基金代码：{}",fundCode);
        Boolean isFavorite = userFundFavoriteService.checkFavorite(fundCode);
        return Result.success(isFavorite);
    }
}
