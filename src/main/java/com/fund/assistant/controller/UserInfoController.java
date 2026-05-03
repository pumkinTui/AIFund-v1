package com.fund.assistant.controller;

import com.fund.assistant.dto.UserLoginDTO;
import com.fund.assistant.dto.UserRegisterDTO;
import com.fund.assistant.dto.UserUpdateDTO;
import com.fund.assistant.service.UserInfoService;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.UserInfoVO;
import com.fund.assistant.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserInfoController {

    @Autowired
    private UserInfoService userInfoService;

    // 注册
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody UserRegisterDTO dto){
        log.info("用户注册信息为：{}",dto);
        userInfoService.register(dto);
        return Result.success();
    }

    // 登录
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Validated @RequestBody UserLoginDTO dto){
        log.info("用户登录信息为：{}",dto);
        UserLoginVO vo = userInfoService.login(dto);
        return Result.success(vo);
    }

    //用户信息获取’
    @GetMapping("/current/info")
    public Result<UserInfoVO> getCurrentUserInfo() {
        UserInfoVO userInfo = userInfoService.getCurrentUserInfo();
        return Result.success(userInfo);
    }

    // 修改个人资料
    @PostMapping("/update/info")
    public Result<Void> updateUserInfo(@Validated @RequestBody UserUpdateDTO dto) {
        log.info("前端传入的修改信息：{}", dto);
        userInfoService.updateUserInfo(dto);
        return Result.success();
    }
}