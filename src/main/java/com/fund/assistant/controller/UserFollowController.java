package com.fund.assistant.controller;

import com.fund.assistant.service.UserFollowRelationService;
import com.fund.assistant.util.Result;
import com.fund.assistant.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户关注/取关
 *
 */
@Slf4j
@RestController
@RequestMapping("/user/follow")
public class UserFollowController {

    @Autowired
    private UserFollowRelationService userFollowRelationService;

    /**
     * 关注 / 取关 用户
     * @param followedUserId 要关注/取关的目标用户ID
     * 规则：
     *  已经关注 → 调用就取关
     *  未关注   → 调用就关注
     */
    @PostMapping("/{followedUserId}")
    public Result<Void> followUser(@PathVariable Long followedUserId) {
        log.info("关注/取关的用户id为：{}",followedUserId);
        userFollowRelationService.followOrUnfollow(followedUserId);
        return Result.success();
    }
}