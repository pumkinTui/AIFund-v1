package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.dto.FavoriteAddDTO;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundUserGroup;
import com.fund.assistant.entity.UserFundFavorite;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.UserFundFavoriteMapper;
import com.fund.assistant.service.FundUserGroupService;
import com.fund.assistant.service.UserFundFavoriteService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.UserFundFavoriteVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户自选基金表 服务实现类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Service
public class UserFundFavoriteServiceImpl extends ServiceImpl<UserFundFavoriteMapper, UserFundFavorite> implements UserFundFavoriteService {

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    @Autowired
    private FundUserGroupService fundUserGroupService; // 这里改成注入新的分组 Service

    /**
     * 添加自选
     * @param dto
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addFavorite(FavoriteAddDTO dto) {
        Long userId = UserContext.getUserId();

        // 1. 检查基金是否存在
        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.eq(FundBaseInfo::getFundCode, dto.getFundCode());
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(fundWrapper);
        if (fund == null) {
            throw new BusinessException("基金不存在");
        }

        // 2. 检查是否已自选
        LambdaQueryWrapper<UserFundFavorite> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(UserFundFavorite::getUserId, userId);
        checkWrapper.eq(UserFundFavorite::getFundCode, dto.getFundCode());
        if (this.count(checkWrapper) > 0) {
            throw new BusinessException("该基金已在自选列表中");
        }

        // 3. 处理分组
        Long groupId = dto.getGroupId();
        if (groupId == null) {
            // 不传 groupId，调用分组 Service 获取或创建默认分组
            groupId = fundUserGroupService.getOrCreateDefaultFavoriteGroup(userId);
        } else {
            // 传了 groupId，检查分组是否存在且属于当前用户
            LambdaQueryWrapper<FundUserGroup> groupWrapper = new LambdaQueryWrapper<>();
            groupWrapper.eq(FundUserGroup::getId, groupId);
            groupWrapper.eq(FundUserGroup::getUserId, userId);
            // 这里直接用分组 Service 的 count 方法
            if (fundUserGroupService.count(groupWrapper) == 0) {
                throw new BusinessException("分组不存在");
            }
        }

        // 4. 添加自选
        UserFundFavorite favorite = new UserFundFavorite();
        favorite.setUserId(userId);
        favorite.setGroupId(groupId);
        favorite.setFundCode(dto.getFundCode());
        this.save(favorite);
    }

    /**
     * 删除自选
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFavorite(Long favoriteId) {
        Long userId = UserContext.getUserId();

        // 检查自选是否存在且属于当前用户
        LambdaQueryWrapper<UserFundFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFundFavorite::getId, favoriteId);
        wrapper.eq(UserFundFavorite::getUserId, userId);
        UserFundFavorite favorite = this.getOne(wrapper);
        if (favorite == null) {
            throw new BusinessException("自选记录不存在");
        }

        // 删除自选
        this.removeById(favoriteId);
    }
    /**
     * 查询当前用户所有自选（按分组）
     */
    @Override
    public List<UserFundFavoriteVO> getFavoriteList(Long groupId) {
        Long userId = UserContext.getUserId();

        // 1. 查询自选列表
        LambdaQueryWrapper<UserFundFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFundFavorite::getUserId, userId);
        if (groupId != null) {
            wrapper.eq(UserFundFavorite::getGroupId, groupId);
        }
        wrapper.orderByDesc(UserFundFavorite::getCreateTime);
        List<UserFundFavorite> favoriteList = this.list(wrapper);

        if (favoriteList.isEmpty()) {
            return List.of();
        }

        // 2. 批量查询基金信息
        List<String> fundCodeList = favoriteList.stream()
                .map(UserFundFavorite::getFundCode)
                .collect(Collectors.toList());
        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.in(FundBaseInfo::getFundCode, fundCodeList);
        List<FundBaseInfo> fundList = fundBaseInfoMapper.selectList(fundWrapper);
        Map<String, FundBaseInfo> fundMap = fundList.stream()
                .collect(Collectors.toMap(FundBaseInfo::getFundCode, fund -> fund));

        // 3. 批量查询分组信息（用分组 Service）
        List<Long> groupIdList = favoriteList.stream()
                .map(UserFundFavorite::getGroupId)
                .distinct()
                .collect(Collectors.toList());
        LambdaQueryWrapper<FundUserGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.in(FundUserGroup::getId, groupIdList);
        List<FundUserGroup> groupList = fundUserGroupService.list(groupWrapper); // 这里用分组 Service
        Map<Long, String> groupNameMap = groupList.stream()
                .collect(Collectors.toMap(FundUserGroup::getId, FundUserGroup::getGroupName));

        // 4. 转换为VO
        return favoriteList.stream().map(favorite -> {
            UserFundFavoriteVO vo = new UserFundFavoriteVO();
            BeanUtils.copyProperties(favorite, vo);

            // 填充基金信息
            FundBaseInfo fund = fundMap.get(favorite.getFundCode());
            if (fund != null) {
                vo.setFundName(fund.getFundName());
                vo.setFundShortName(fund.getFundShortName());
                vo.setLatestNetValue(fund.getLatestNetValue());
                vo.setLatestChangeRate(fund.getLatestChangeRate());
            }

            // 填充分组名称
            vo.setGroupName(groupNameMap.get(favorite.getGroupId()));

            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 检查基金是否已自选
     */
    @Override
    public Boolean checkFavorite(String fundCode) {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<UserFundFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFundFavorite::getUserId, userId);
        wrapper.eq(UserFundFavorite::getFundCode, fundCode);
        return this.count(wrapper) > 0;
    }
}
