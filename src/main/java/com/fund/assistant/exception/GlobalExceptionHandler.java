package com.fund.assistant.exception;

import com.fund.assistant.util.Result;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一捕获项目所有异常，统一返回标准 JSON 格式给前端
 * 不让前端看到报错堆栈、不让系统直接500
 */
@RestControllerAdvice // 全局捕获异常 + 返回 JSON
public class GlobalExceptionHandler {

    /**
     * 捕获 自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        return Result.error(e.getMessage());
    }

    /**
     * 捕获 JSON 格式参数校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError.getDefaultMessage();
        return Result.error(msg);
    }

    /**
     * 捕获 表单格式参数校验失败
     */
    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e) {
        FieldError fieldError = e.getFieldError();
        String msg = fieldError.getDefaultMessage();
        return Result.error(msg);
    }

    /**
     * 捕获其他所有异常
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        e.printStackTrace();
        return Result.error("系统异常，请联系管理员");
    }
}