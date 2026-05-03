package com.fund.assistant.controller;

import com.fund.assistant.dto.GroupCreateDTO;
import com.fund.assistant.service.FundUserGroupService;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.UserFundGroupVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 基金分组
 */
@Slf4j
@RestController
@RequestMapping("/user/group")
public class FundUserGroupController {

    @Autowired
    private FundUserGroupService fundUserGroupService;

    /**
     * 创建自选分组
     */
    @PostMapping("/create")
    public Result<Long> createFavoriteGroup(@Validated @RequestBody GroupCreateDTO dto) {
        log.info("创建自选分组的信息为：{}",dto);
        Long groupId = fundUserGroupService.createFavoriteGroup(dto);
        return Result.success(groupId);
    }

    /**
     * 重命名分组
     */
    @PostMapping("/rename/{groupId}")
    public Result<Void> renameGroup(@PathVariable Long groupId, @Validated @RequestBody GroupCreateDTO dto) {
        log.info("重命名自选分组的信息为：{}，分组id为：{}",dto,groupId);
        fundUserGroupService.renameGroup(groupId, dto);
        return Result.success();
    }

    /**
     * 删除分组
     */
    @PostMapping("/delete/{groupId}")
    public Result<Void> deleteGroup(@PathVariable Long groupId) {
        log.info("要删除的分组id为：{}",groupId);
        fundUserGroupService.deleteGroup(groupId);
        return Result.success();
    }

    /**
     * 查询当前用户所有自选分组
     */
    @GetMapping("/list")
    public Result<List<UserFundGroupVO>> getFavoriteGroupList() {
        List<UserFundGroupVO> groupList = fundUserGroupService.getFavoriteGroupList();
        return Result.success(groupList);
    }
}