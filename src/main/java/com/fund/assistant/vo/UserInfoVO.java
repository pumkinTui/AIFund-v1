package com.fund.assistant.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserInfoVO {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 登录账号
     */
    private String username;

    /**
     * 加密密码
     */
    private String password;

    /**
     * 用户唯一展示ID
     */
    private String uid;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 个性签名
     */
    private String signature;


    /**
     * 关注数
     */
    private Integer followCount;

    /**
     * 粉丝数
     */
    private Integer fansCount;

    /**
     * 获赞总数
     */
    private Integer likeTotal;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}