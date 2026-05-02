package com.fund.assistant.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String username;   // 登录名
    private String nickName;   // 展示昵称
    private String uid;        // 用户唯一标识（如果业务需要）
    private String icon;
    // 将来可扩展 role, email 等
}