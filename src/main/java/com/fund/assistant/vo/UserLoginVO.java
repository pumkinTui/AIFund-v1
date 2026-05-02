package com.fund.assistant.vo;
import lombok.Data;

@Data
public class UserLoginVO {
    private Long userId;
    private String username;
    //用户昵称
    private String nickname;
    //头像URL
    private String avatar;

    private String token;
}