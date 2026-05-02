package com.fund.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户主表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("user_info")
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录账号
     */
    @TableField("username")
    private String username;

    /**
     * 加密密码
     */
    @TableField("password")
    private String password;

    /**
     * 用户唯一展示ID
     */
    @TableField("uid")
    private String uid;

    /**
     * 用户昵称
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 头像URL
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 个性签名
     */
    @TableField("signature")
    private String signature;

    /**
     * 密保问题
     */
    @TableField("security_question")
    private String securityQuestion;

    /**
     * 密保答案
     */
    @TableField("security_answer")
    private String securityAnswer;

    /**
     * 关注数
     */
    @TableField("follow_count")
    private Integer followCount;

    /**
     * 粉丝数
     */
    @TableField("fans_count")
    private Integer fansCount;

    /**
     * 获赞总数
     */
    @TableField("like_total")
    private Integer likeTotal;

    /**
     * 持仓隐私开关：0=私密 1=仅粉丝可见 2=完全公开
     */
    @TableField("hold_privacy")
    private Byte holdPrivacy;

    /**
     * 操作隐私开关：0=私密 1=仅粉丝可见 2=完全公开
     */
    @TableField("operate_privacy")
    private Byte operatePrivacy;

    /**
     * 逻辑删除标记：0=正常 1=已删除
     */
    @TableField("del_flag")
    private Byte delFlag;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
