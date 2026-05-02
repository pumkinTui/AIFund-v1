package com.fund.assistant.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 用户注册 DTO
 */
@Data
public class UserRegisterDTO {

    /**
     * 用户账号
     * @NotBlank：不能为空、不能只传空格
     * message = "账号不能为空"：校验失败时返回的提示信息
     */
    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 16, message = "账号长度必须在 4-16 位")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "账号必须以字母开头，可包含字母、数字、下划线")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在 6-20 位")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
            message = "密码必须包含（大写字母+小写字母+数字）")
    private String password;

    /**
     * 密保问题（找回密码用）
     */
    @NotBlank(message = "密保问题不能为空")
    private String securityQuestion;

    /**
     * 密保答案（找回密码用）
     */
    @NotBlank(message = "密保答案不能为空")
    private String securityAnswer;
}