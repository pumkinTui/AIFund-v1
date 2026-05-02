package com.fund.assistant.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * 作用：配置 MyBatis-Plus 的核心功能（分页、插件、扫描 Mapper 等）
 */
@Configuration  // 声明这是一个 Spring 配置类
@MapperScan("com.fund.assistant.mapper")  // 自动扫描 Mapper 接口
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 分页插件
     * 作用：让分页查询可以正常使用（必须配置，否则分页不生效）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建 MyBatis-Plus 插件拦截器
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 添加【MySQL 分页插件】
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        
        return interceptor;
    }
}