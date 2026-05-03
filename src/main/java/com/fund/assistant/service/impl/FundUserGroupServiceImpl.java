package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.dto.GroupCreateDTO;
import com.fund.assistant.entity.FundUserGroup;
import com.fund.assistant.entity.UserFundFavorite;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundUserGroupMapper;
import com.fund.assistant.mapper.UserFundFavoriteMapper;
import com.fund.assistant.service.FundUserGroupService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.UserFundGroupVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户基金分组表 服务实现类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Service
public class FundUserGroupServiceImpl extends ServiceImpl<FundUserGroupMapper, FundUserGroup> implements FundUserGroupService {

    @Autowired
    private UserFundFavoriteMapper userFundFavoriteMapper;

    // 分组类型常量：1=持仓分组 2=自选分组
    private static final byte GROUP_TYPE_HOLD = 1;
    private static final byte GROUP_TYPE_FAVORITE = 2;

    /**
     * 创建自选分组
     *
     * @param dto
     * @return
     */
    @Override
    public Long createFavoriteGroup(GroupCreateDTO dto) {
        Long userId = UserContext.getUserId();

        // 检查自选分组名是否重复
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getUserId, userId);
        wrapper.eq(FundUserGroup::getGroupType, GROUP_TYPE_FAVORITE);
        wrapper.eq(FundUserGroup::getGroupName, dto.getGroupName());
        if (this.count(wrapper) > 0) {
            throw new BusinessException("分组名称已存在");
        }

        // 1. 查询当前用户最大的 sort
        LambdaQueryWrapper<FundUserGroup> wrapperSort = new LambdaQueryWrapper<>();
        wrapperSort.eq(FundUserGroup::getUserId, userId);
        wrapperSort.eq(FundUserGroup::getGroupType, GROUP_TYPE_FAVORITE);
        wrapperSort.orderByDesc(FundUserGroup::getSort);
        wrapperSort.last("LIMIT 1");
        FundUserGroup maxSortGroup = this.getOne(wrapperSort);

        // 2. 计算新排序号
        int newSort = (maxSortGroup == null) ? 0 : maxSortGroup.getSort() + 1;

        // 创建自选分组
        FundUserGroup group = FundUserGroup.builder()
                .userId(userId)
                .groupName(dto.getGroupName())
                .groupType(GROUP_TYPE_FAVORITE)
                .sort(newSort)
                .build();
        this.save(group);
        return group.getId();
    }
    /**
     * 重命名分组
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameGroup(Long groupId, GroupCreateDTO dto) {
        Long userId = UserContext.getUserId();

        // 检查分组是否存在且属于当前用户
        FundUserGroup group = this.getGroupByIdAndUserId(groupId, userId);
        if (group == null) {
            throw new BusinessException("分组不存在");
        }

        // 检查新分组名是否重复（同一类型下）
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getUserId, userId);
        wrapper.eq(FundUserGroup::getGroupType, group.getGroupType());
        wrapper.eq(FundUserGroup::getGroupName, dto.getGroupName());
        wrapper.ne(FundUserGroup::getId, groupId);
        if (this.count(wrapper) > 0) {
            throw new BusinessException("分组名称已存在");
        }

        // 重命名
        group.setGroupName(dto.getGroupName());
        this.updateById(group);
    }

    /**
     * 删除分组
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long groupId) {
        Long userId = UserContext.getUserId();

        // 检查分组是否存在且属于当前用户
        FundUserGroup group = this.getGroupByIdAndUserId(groupId, userId);
        if (group == null) {
            throw new BusinessException("分组不存在");
        }

        // 删除分组下的所有自选
        LambdaQueryWrapper<UserFundFavorite> favoriteWrapper = new LambdaQueryWrapper<>();
        favoriteWrapper.eq(UserFundFavorite::getUserId, userId);
        favoriteWrapper.eq(UserFundFavorite::getGroupId, groupId);
        userFundFavoriteMapper.delete(favoriteWrapper);

        // 删除分组
        this.removeById(groupId);
    }
    /**
     * 查询当前用户所有自选分组
     */
    @Override
    public List<UserFundGroupVO> getFavoriteGroupList() {
        Long userId = UserContext.getUserId();

        // 查询当前用户所有自选分组
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getUserId, userId);
        wrapper.eq(FundUserGroup::getGroupType, GROUP_TYPE_FAVORITE);
        wrapper.orderByAsc(FundUserGroup::getSort);
        wrapper.orderByDesc(FundUserGroup::getCreateTime);
        List<FundUserGroup> groupList = this.list(wrapper);

        // 转换为VO并统计每个分组的自选数量
        return groupList.stream().map(group -> {
            UserFundGroupVO vo = new UserFundGroupVO();
            BeanUtils.copyProperties(group, vo);
            vo.setSortOrder(group.getSort());

            // 统计分组下的自选数量
            LambdaQueryWrapper<UserFundFavorite> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(UserFundFavorite::getUserId, userId);
            countWrapper.eq(UserFundFavorite::getGroupId, group.getId());
            Integer count = Math.toIntExact(userFundFavoriteMapper.selectCount(countWrapper));
            vo.setFavoriteCount(count);

            return vo;
        }).collect(Collectors.toList());
    }

    /**
     *根据分组ID和用户ID查询分组
     */
    private FundUserGroup getGroupByIdAndUserId(Long groupId, Long userId) {
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getId, groupId);
        wrapper.eq(FundUserGroup::getUserId, userId);
        return this.getOne(wrapper);
    }

    /**
     * 取或创建默认自选分组
     */
    public Long getOrCreateDefaultFavoriteGroup(Long userId) {
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getUserId, userId);
        wrapper.eq(FundUserGroup::getGroupType, GROUP_TYPE_FAVORITE);
        wrapper.eq(FundUserGroup::getGroupName, "我的自选");
        wrapper.orderByAsc(FundUserGroup::getCreateTime);
        wrapper.last("LIMIT 1");
        FundUserGroup defaultGroup = this.getOne(wrapper);

        if (defaultGroup == null) {
            // 创建默认自选分组
            defaultGroup = FundUserGroup.builder()
                    .userId(userId)
                    .groupName("我的自选")
                    .groupType(GROUP_TYPE_FAVORITE)
                    .sort(0)
                    .build();
   /*         defaultGroup = new FundUserGroup();
            defaultGroup.setUserId(userId);
            defaultGroup.setGroupName("我的自选");
            defaultGroup.setGroupType(GROUP_TYPE_FAVORITE);
            defaultGroup.setSort(0);*/
            this.save(defaultGroup);
        }

        return defaultGroup.getId();
    }
}
