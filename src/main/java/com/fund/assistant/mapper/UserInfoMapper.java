package com.fund.assistant.mapper;

import com.fund.assistant.entity.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 用户主表 Mapper 接口
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    /**
     * 动态更新用户信息
     */
    int updateUserInfoById(@Param("user") UserInfo userInfo);
}
