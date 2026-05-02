package com.fund.assistant.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充配置类
 * 功能：新增 / 修改数据时，**自动赋值时间字段**，不用手动 set
 * 作用字段：createTime（创建时间）、updateTime（更新时间）
 */
@Component  // 交给 Spring 管理，让配置生效
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 【插入数据时自动填充】
     * 执行新增操作（insert）时，自动给字段赋值
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 新增时，自动填充 createTime = 当前时间
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        
        // 新增时，自动填充 updateTime = 当前时间
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 【更新数据时自动填充】
     * 执行修改操作（update）时，自动给字段赋值
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时，只自动更新 updateTime 为最新时间
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}