package com.fund.assistant.exception;

import com.fund.assistant.util.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 作用：统一捕获项目中所有报错，统一返回友好格式给前端
 * 不让前端看到丑陋的报错页面/红色日志
 */
@RestControllerAdvice  // 全局捕获异常注解（SpringBoot标配）
public class GlobalExceptionHandler {

    /**
     * 捕获【自定义业务异常 BusinessException】
     * 专门处理我们主动抛出的业务错误（如：基金已存在、数据不存在）
     */
    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        // 返回我们自定义的友好提示
        return Result.error(e.getMessage());
    }

    /**
     * 捕获【系统其他所有异常】
     * 处理未知错误：空指针、数据库报错、网络异常等
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        e.printStackTrace();  // 后台打印日志，方便排查问题
        // 给前端返回统一的系统错误提示
        return Result.error("系统异常，请联系管理员");
    }
}