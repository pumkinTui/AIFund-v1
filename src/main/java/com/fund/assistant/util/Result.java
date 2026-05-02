package com.fund.assistant.util;

import lombok.Data;

/**
 * 统一返回结果封装类（升级版）
 * 支持：自定义状态码、自定义消息、任意返回数据
 */
@Data
public class Result<T> {

    // 响应状态码
    private Integer code;

    // 响应消息
    private String message;

    // 响应数据
    private T data;

    // ====================== 原来的用法完全保留 ======================

    /**
     * 成功（带数据）
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    /**
     * 成功（不带数据）
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 失败（默认500）
     */
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    /**
     * 失败（自定义状态码 + 消息）
     * 例如：
     * 400 参数错误
     * 401 未登录
     * 403 无权限
     * 404 不存在
     * 409 数据重复
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 通用自定义返回
     */
    public static <T> Result<T> build(int code, String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
}