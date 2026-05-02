package com.fund.assistant.dto;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class UserLoginDTO {
    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}