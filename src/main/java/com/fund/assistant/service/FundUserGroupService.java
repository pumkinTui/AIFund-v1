package com.fund.assistant.service;

import com.fund.assistant.dto.GroupCreateDTO;
import com.fund.assistant.entity.FundUserGroup;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.vo.UserFundGroupVO;

import java.util.List;

/**
 * <p>
 * 用户基金分组表 服务类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public interface FundUserGroupService extends IService<FundUserGroup> {

    /**
     * 创建自选分组
     */
    Long createFavoriteGroup(GroupCreateDTO dto);

    /**
     * 重命名分组
     */
    void renameGroup(Long groupId, GroupCreateDTO dto);

    /**
     * 删除分组
     */
    void deleteGroup(Long groupId);

    /**
     * 查询当前用户所有自选分组
     */
    List<UserFundGroupVO> getFavoriteGroupList();

    /**
     * 获得或者创建默认分组
     * @param userId
     * @return
     */
    Long getOrCreateDefaultFavoriteGroup(Long userId);
}
