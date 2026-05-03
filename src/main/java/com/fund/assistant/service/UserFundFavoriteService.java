package com.fund.assistant.service;

import com.fund.assistant.dto.FavoriteAddDTO;
import com.fund.assistant.entity.UserFundFavorite;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.vo.UserFundFavoriteVO;

import java.util.List;

/**
 * <p>
 * 用户自选基金表 服务类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public interface UserFundFavoriteService extends IService<UserFundFavorite> {

    /**
     * 添加自选
     */
    void addFavorite(FavoriteAddDTO dto);

    /**
     * 删除自选
     */
    void deleteFavorite(Long favoriteId);

    /**
     * 查询当前用户所有自选（按分组）
     */
    List<UserFundFavoriteVO> getFavoriteList(Long groupId);

    /**
     * 检查基金是否已自选
     */
    Boolean checkFavorite(String fundCode);

}
