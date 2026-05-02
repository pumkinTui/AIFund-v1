package com.fund.assistant.service.impl;

import com.fund.assistant.entity.UserInfo;
import com.fund.assistant.mapper.UserInfoMapper;
import com.fund.assistant.service.UserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

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

}
