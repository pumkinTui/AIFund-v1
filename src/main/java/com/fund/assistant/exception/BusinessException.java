package com.fund.assistant.exception;

import lombok.Getter;

/**
 * 自定义业务异常类
 * 作用：专门用来抛出【业务逻辑错误】
 * 例如：用户名已存在、密码错误、基金已添加、数据不存在等
 * 区别于系统异常（空指针、数据库报错等）
 */
@Getter
public class BusinessException extends RuntimeException {

    // 异常提示信息（给用户看的友好提示）
    private final String message;

    /**
     * 构造方法：抛出业务异常时，直接传入提示文字
     * @param message 错误提示信息（例如：该基金已存在分组中）
     */
    public BusinessException(String message) {
        super(message);  // 调用父类构造器
        this.message = message;
    }
}