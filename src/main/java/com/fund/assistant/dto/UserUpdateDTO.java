package com.fund.assistant.dto;

import lombok.Data;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
public class UserUpdateDTO {
    // 昵称
    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;

    // 头像URL
    private String avatar;

    // 个性签名
    @Size(max = 100, message = "签名长度不能超过100")
    private String signature;

    // 持仓隐私：0=私密 1=仅粉丝可见 2=完全公开
    @Min(value = 0, message = "隐私类型错误")
    @Max(value = 2, message = "隐私类型错误")
    private Byte holdPrivacy;

    // 操作隐私：0=私密 1=仅粉丝可见 2=完全公开
    @Min(value = 0, message = "隐私类型错误")
    @Max(value = 2, message = "隐私类型错误")
    private Byte operatePrivacy;
}