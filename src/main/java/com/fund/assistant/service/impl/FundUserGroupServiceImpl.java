package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.dto.GroupCreateDTO;
import com.fund.assistant.entity.FundUserGroup;
import com.fund.assistant.entity.UserFundFavorite;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundUserGroupMapper;
import com.fund.assistant.mapper.UserFundFavoriteMapper;
import com.fund.assistant.mapper.UserFundHoldMapper;
import com.fund.assistant.service.FundUserGroupService;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.UserFundGroupVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FundUserGroupServiceImpl extends ServiceImpl<FundUserGroupMapper, FundUserGroup> implements FundUserGroupService {

    @Autowired
    private UserFundFavoriteMapper userFundFavoriteMapper;

    @Autowired
    private UserFundHoldMapper userFundHoldMapper;

    // 分组类型常量
    private static final byte GROUP_TYPE_HOLD = 1;
    private static final byte GROUP_TYPE_FAVORITE = 2;


    /**
     * 通用：重命名分组（自动判断分组类型）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameGroup(Long groupId, GroupCreateDTO dto) {
        Long userId = UserContext.getUserId();

        // 1. 检查分组是否存在且属于当前用户
        FundUserGroup group = this.getGroupByIdAndUserId(groupId, userId);
        if (group == null) {
            throw new BusinessException("分组不存在");
        }

        // 2. 检查新分组名是否重复（同一用户、同一类型下）
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getUserId, userId);
        wrapper.eq(FundUserGroup::getGroupType, group.getGroupType());
        wrapper.eq(FundUserGroup::getGroupName, dto.getGroupName());
        wrapper.ne(FundUserGroup::getId, groupId);
        if (this.count(wrapper) > 0) {
            throw new BusinessException("分组名称已存在");
        }

        // 3. 重命名
        group.setGroupName(dto.getGroupName());
        this.updateById(group);
    }

    /**
     * 通用：删除分组（自动判断分组类型，级联删除对应数据）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long groupId) {
        Long userId = UserContext.getUserId();

        // 1. 检查分组是否存在且属于当前用户
        FundUserGroup group = this.getGroupByIdAndUserId(groupId, userId);
        if (group == null) {
            throw new BusinessException("分组不存在");
        }

        // 2. 根据分组类型，级联删除对应数据
        if (group.getGroupType() == GROUP_TYPE_FAVORITE) {
            // 自选分组：删除分组下的所有自选基金
            LambdaQueryWrapper<UserFundFavorite> favoriteWrapper = new LambdaQueryWrapper<>();
            favoriteWrapper.eq(UserFundFavorite::getUserId, userId);
            favoriteWrapper.eq(UserFundFavorite::getGroupId, groupId);
            userFundFavoriteMapper.delete(favoriteWrapper);
        } else if (group.getGroupType() == GROUP_TYPE_HOLD) {
            // 持仓分组：删除分组下的所有持仓记录
            LambdaQueryWrapper<UserFundHold> holdWrapper = new LambdaQueryWrapper<>();
            holdWrapper.eq(UserFundHold::getUserId, userId);
            holdWrapper.eq(UserFundHold::getGroupId, groupId);
            userFundHoldMapper.delete(holdWrapper);
        }

        // 3. 删除分组本身
        this.removeById(groupId);
    }


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

        // 计算排序号
        int newSort = getNextSort(userId, GROUP_TYPE_FAVORITE);

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

    public Long getOrCreateDefaultFavoriteGroup(Long userId) {
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getUserId, userId);
        wrapper.eq(FundUserGroup::getGroupType, GROUP_TYPE_FAVORITE);
        wrapper.eq(FundUserGroup::getGroupName, "我的自选");
        wrapper.orderByAsc(FundUserGroup::getCreateTime);
        wrapper.last("LIMIT 1");
        FundUserGroup defaultGroup = this.getOne(wrapper);

        if (defaultGroup == null) {
            defaultGroup = FundUserGroup.builder()
                    .userId(userId)
                    .groupName("我的自选")
                    .groupType(GROUP_TYPE_FAVORITE)
                    .sort(0)
                    .build();
            this.save(defaultGroup);
        }

        return defaultGroup.getId();
    }



    @Override
    public Long createHoldGroup(GroupCreateDTO dto) {
        Long userId = UserContext.getUserId();

        // 检查持仓分组名是否重复
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getUserId, userId);
        wrapper.eq(FundUserGroup::getGroupType, GROUP_TYPE_HOLD);
        wrapper.eq(FundUserGroup::getGroupName, dto.getGroupName());
        if (this.count(wrapper) > 0) {
            throw new BusinessException("持仓分组名称已存在");
        }

        // 计算排序号
        int newSort = getNextSort(userId, GROUP_TYPE_HOLD);

        // 创建持仓分组
        FundUserGroup group = FundUserGroup.builder()
                .userId(userId)
                .groupName(dto.getGroupName())
                .groupType(GROUP_TYPE_HOLD)
                .sort(newSort)
                .build();
        this.save(group);
        return group.getId();
    }

    @Override
    public List<UserFundGroupVO> getHoldGroupList() {
        Long userId = UserContext.getUserId();

        // 查询当前用户所有持仓分组
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getUserId, userId);
        wrapper.eq(FundUserGroup::getGroupType, GROUP_TYPE_HOLD);
        wrapper.orderByAsc(FundUserGroup::getSort);
        wrapper.orderByDesc(FundUserGroup::getCreateTime);
        List<FundUserGroup> groupList = this.list(wrapper);

        // 转换为VO并统计每个分组的持仓数量
        return groupList.stream().map(group -> {
            UserFundGroupVO vo = new UserFundGroupVO();
            BeanUtils.copyProperties(group, vo);
            vo.setSortOrder(group.getSort());

            // 统计分组下的持仓数量（只统计份额>0的）
            LambdaQueryWrapper<UserFundHold> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(UserFundHold::getUserId, userId);
            countWrapper.eq(UserFundHold::getGroupId, group.getId());
            countWrapper.gt(UserFundHold::getHoldShares, BigDecimal.ZERO);
            Integer count = Math.toIntExact(userFundHoldMapper.selectCount(countWrapper));
            vo.setFavoriteCount(count); // 复用VO字段

            return vo;
        }).collect(Collectors.toList());
    }

    public Long getOrCreateDefaultHoldGroup(Long userId) {
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getUserId, userId);
        wrapper.eq(FundUserGroup::getGroupType, GROUP_TYPE_HOLD);
        wrapper.eq(FundUserGroup::getGroupName, "我的持仓");
        wrapper.orderByAsc(FundUserGroup::getCreateTime);
        wrapper.last("LIMIT 1");
        FundUserGroup defaultGroup = this.getOne(wrapper);

        if (defaultGroup == null) {
            defaultGroup = FundUserGroup.builder()
                    .userId(userId)
                    .groupName("我的持仓")
                    .groupType(GROUP_TYPE_HOLD)
                    .sort(0)
                    .build();
            this.save(defaultGroup);
        }

        return defaultGroup.getId();
    }


    /**
     * 根据分组ID和用户ID查询分组
     */
    private FundUserGroup getGroupByIdAndUserId(Long groupId, Long userId) {
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getId, groupId);
        wrapper.eq(FundUserGroup::getUserId, userId);
        return this.getOne(wrapper);
    }

    /**
     * 获取指定用户、指定分组类型的下一个排序号
     */
    private int getNextSort(Long userId, byte groupType) {
        LambdaQueryWrapper<FundUserGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundUserGroup::getUserId, userId);
        wrapper.eq(FundUserGroup::getGroupType, groupType);
        wrapper.orderByDesc(FundUserGroup::getSort);
        wrapper.last("LIMIT 1");
        FundUserGroup maxSortGroup = this.getOne(wrapper);
        return (maxSortGroup == null) ? 0 : maxSortGroup.getSort() + 1;
    }
}