package com.fund.assistant.util;

import com.fund.assistant.dto.UserDTO;
import com.fund.assistant.exception.BusinessException;

/**
 * 用户上下文工具类
 * 基于ThreadLocal存储当前登录用户信息，保证线程安全
 */
public class UserContext {

    private static final ThreadLocal<UserDTO> TL = new ThreadLocal<>();

    public static void saveUser(UserDTO user) {
        TL.set(user);
    }

    public static UserDTO getUser() {
        return TL.get();
    }

    /**
     * 获取当前用户，未登录则抛异常（业务层主流用法）
     */
    public static UserDTO currentUser() {
        UserDTO user = getUser();
        if (user == null) {
            throw new BusinessException("用户未登录");
        }
        return user;
    }

    public static Long getUserId() {
        return currentUser().getId();
    }

    public static void removeUser() {
        TL.remove();
    }
}