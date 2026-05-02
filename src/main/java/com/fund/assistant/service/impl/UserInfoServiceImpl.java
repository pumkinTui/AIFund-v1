package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.dto.UserDTO;
import com.fund.assistant.dto.UserLoginDTO;
import com.fund.assistant.dto.UserRegisterDTO;
import com.fund.assistant.entity.UserInfo;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.UserInfoMapper;
import com.fund.assistant.service.UserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.util.JwtUtil;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.UserInfoVO;
import com.fund.assistant.vo.UserLoginVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * <p>
 * 用户主表 服务实现类
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

    // 注入密码加密器
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户注册
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterDTO dto) {
        // 1. 判断用户名是否已存在
        LambdaQueryWrapper<UserInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserInfo::getUsername, dto.getUsername());
        UserInfo existUser = this.getOne(queryWrapper);

        if (existUser != null) {
            throw new BusinessException("用户名已被注册");
        }

        // 2. 构建用户对象
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(dto.getUsername());
        // 密码加密
        userInfo.setPassword(passwordEncoder.encode(dto.getPassword()));
        // 生成唯一UID
        userInfo.setUid(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        // 默认昵称
        userInfo.setNickname("用户" + userInfo.getUid().substring(0,8));
        // 密保答案加密
        userInfo.setSecurityQuestion(dto.getSecurityQuestion());
        userInfo.setSecurityAnswer(passwordEncoder.encode(dto.getSecurityAnswer()));

        // 3. MP插入数据
        this.save(userInfo);
    }

    /**
     * 用户登录
     */
    @Override
    public UserLoginVO login(UserLoginDTO dto) {
        // 1.根据用户名查用户
        LambdaQueryWrapper<UserInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserInfo::getUsername, dto.getUsername());
        UserInfo userInfo = this.getOne(queryWrapper);

        if (userInfo == null) {
            throw new BusinessException("账号不存在");
        }

        // 2. 校验密码
        if (!passwordEncoder.matches(dto.getPassword(), userInfo.getPassword())) {
            throw new BusinessException("密码错误");
        }

        // 3. 生成JWT令牌
        String token = jwtUtil.generateToken(userInfo.getId(), userInfo.getUsername());

        // 4. 封装返回VO
        UserLoginVO loginVO = new UserLoginVO();
        loginVO.setUserId(userInfo.getId());
        loginVO.setUsername(userInfo.getUsername());
        loginVO.setNickname(userInfo.getNickname());
        loginVO.setAvatar(userInfo.getAvatar());
        loginVO.setToken(token);

        return loginVO;
    }

    /**
     * 获取当前登录用户信息
     * @return
     */
    @Override
    public UserInfoVO getCurrentUserInfo() {
        Long userId = UserContext.getUserId();
        UserInfo userInfo = getById(userId);
        //不存在
        if (userInfo == null){
            throw  new BusinessException("用户不存在");
        }
        //存在
        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(userInfo,userInfoVO);

        return userInfoVO;
    }
}