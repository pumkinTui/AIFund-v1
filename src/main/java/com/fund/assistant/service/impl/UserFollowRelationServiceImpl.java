package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fund.assistant.entity.UserFollowRelation;
import com.fund.assistant.entity.UserInfo;
import com.fund.assistant.mapper.UserFollowRelationMapper;
import com.fund.assistant.service.UserFollowRelationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.service.UserInfoService;
import com.fund.assistant.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 用户关注&粉丝关联表 服务实现类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Service
public class UserFollowRelationServiceImpl extends ServiceImpl<UserFollowRelationMapper, UserFollowRelation> implements UserFollowRelationService {

    @Autowired
    private UserInfoService userInfoService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void followOrUnfollow(Long followedUserId) {
        Long userId = UserContext.getUserId();

        // 1. 判断是否已关注（当前登录用户 是否已经关注了 目标用户）
        LambdaQueryWrapper<UserFollowRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollowRelation::getUserId, userId);
        wrapper.eq(UserFollowRelation::getFollowedUserId, followedUserId);
        UserFollowRelation relation = this.getOne(wrapper);

        if (relation == null) {
            // 未关注 -> 执行关注
            UserFollowRelation followRelation = UserFollowRelation.builder()
                    .userId(userId)
                    .followedUserId(followedUserId)
                    .build();
            this.save(followRelation);

            // 1. 自己的关注数 +1
            LambdaUpdateWrapper<UserInfo> userUpdateWrapper = new LambdaUpdateWrapper<>();
            userUpdateWrapper.eq(UserInfo::getId, userId)
                    .setSql("follow_count = follow_count + 1");
            userInfoService.update(userUpdateWrapper);

            // 2. 对方的粉丝数 +1
            LambdaUpdateWrapper<UserInfo> followedUserUpdateWrapper = new LambdaUpdateWrapper<>();
            followedUserUpdateWrapper.eq(UserInfo::getId, followedUserId)
                    .setSql("fans_count = fans_count + 1");
            userInfoService.update(followedUserUpdateWrapper);
        } else {
            // 已关注 -> 执行取关
            this.remove(wrapper);

            // 我的关注数 -1
            LambdaUpdateWrapper<UserInfo> userUpdateWrapper = new LambdaUpdateWrapper<>();
            userUpdateWrapper.eq(UserInfo::getId, userId)
                    .setSql("follow_count = follow_count - 1");
            userInfoService.update(userUpdateWrapper);

            // 对方粉丝数 -1
            LambdaUpdateWrapper<UserInfo> followedUserUpdateWrapper = new LambdaUpdateWrapper<>();
            followedUserUpdateWrapper.eq(UserInfo::getId, followedUserId)
                    .setSql("fans_count = fans_count - 1");
            userInfoService.update(followedUserUpdateWrapper);
        }
    }
}
