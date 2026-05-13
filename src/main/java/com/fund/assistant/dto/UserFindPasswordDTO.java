package com.fund.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserFindPasswordDTO {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密保答案不能为空")
    private String securityAnswer;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在 6-20 位")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
            message = "密码必须包含（大写字母+小写字母+数字）")
    private String newPassword;
}
