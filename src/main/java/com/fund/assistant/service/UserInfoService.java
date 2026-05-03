package com.fund.assistant.service;

import com.fund.assistant.dto.UserLoginDTO;
import com.fund.assistant.dto.UserRegisterDTO;
import com.fund.assistant.dto.UserUpdateDTO;
import com.fund.assistant.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fund.assistant.vo.UserInfoVO;
import com.fund.assistant.vo.UserLoginVO;

/**
 * <p>
 * 用户主表 服务类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public interface UserInfoService extends IService<UserInfo> {

    /**
     * 用户注册
     */
    void register(UserRegisterDTO dto);


    /**
     * 用户登录
     */
    UserLoginVO login(UserLoginDTO dto);

    /**
     * 获取当前登录用户信息
     * @return
     */
    UserInfoVO getCurrentUserInfo();

    /**
     * 用户信息的修改
     * @param dto
     */
    void updateUserInfo(UserUpdateDTO dto);
}
