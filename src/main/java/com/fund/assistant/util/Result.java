package com.fund.assistant.util;

import lombok.Data;

/**
 * 统一返回结果封装类
 * 作用：后端给前端返回数据时，统一格式（状态码+提示信息+数据）
 * 所有接口都用这个类返回，前端好处理、好解析
 */
@Data  // Lombok注解：自动生成get/set/toString等方法
public class Result<T> {

    // 响应状态码：200=成功 500=失败
    private Integer code;

    // 提示信息：给前端/用户看的文字（如：操作成功、用户名已存在）
    private String message;

    // 返回的数据：可以是任意类型（对象、列表、null）
    private T data;

    /**
     * 【成功响应 - 带数据】
     * 用于：查询成功、获取详情成功等
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = 200;          // 成功状态码
        result.message = "操作成功"; // 成功提示
        result.data = data;         // 返回具体数据
        return result;
    }

    /**
     * 【成功响应 - 不带数据】
     * 用于：新增、修改、删除成功（不需要返回数据）
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 【失败响应】
     * 用于：业务报错、参数错误、系统异常
     * 自定义返回错误信息
     */
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.code = 500;      // 失败状态码
        result.message = message; // 自定义错误提示
        return result;
    }
}