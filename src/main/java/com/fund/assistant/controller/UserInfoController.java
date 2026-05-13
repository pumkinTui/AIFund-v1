package com.fund.assistant.controller;

import com.fund.assistant.dto.UserFindPasswordDTO;
import com.fund.assistant.dto.UserLoginDTO;
import com.fund.assistant.dto.UserRegisterDTO;
import com.fund.assistant.dto.UserUpdateDTO;
import com.fund.assistant.service.UserInfoService;
import com.fund.assistant.util.OssService;
import com.fund.assistant.util.Result;
import com.fund.assistant.vo.UserInfoVO;
import com.fund.assistant.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserInfoController {

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private OssService ossService;

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

    // 上传头像（返回头像URL）
    @PostMapping("/avatar/upload")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("请选择要上传的图片");
        }
        String avatarUrl = ossService.uploadAvatar(file);
        log.info("用户上传头像成功：{}", avatarUrl);
        return Result.success(avatarUrl);
    }

    // 查询密保问题
    @GetMapping("/security-question")
    public Result<String> getSecurityQuestion(@RequestParam String username) {
        String question = userInfoService.getSecurityQuestion(username);
        return Result.success(question);
    }

    // 找回密码 验证密保答案 + 重置密码
    @PostMapping("/findPassword")
    public Result<Void> findPassword(@Validated @RequestBody UserFindPasswordDTO dto) {
        userInfoService.findPassword(dto);
        return Result.success(null, "密码重置成功");
    }

    // 修改密保 验证当前密码 + 更新密保问题和答案
    @PostMapping("/update/security")
    public Result<Void> updateSecurity(@RequestBody Map<String, String> body) {
        String pwd = body.get("password");
        String securityQ = body.get("securityQuestion");
        String securityA = body.get("securityAnswer");
        if (pwd == null || securityQ == null || securityA == null) return Result.error("参数不完整");
        userInfoService.updateSecurity(pwd, securityQ, securityA);
        return Result.success();
    }
}