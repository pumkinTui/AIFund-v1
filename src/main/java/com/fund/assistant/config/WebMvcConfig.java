package com.fund.assistant.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SpringMVC 配置类
 * 作用：注册【登录拦截器】，控制哪些接口需要登录、哪些可以直接访问
 */
@Configuration // 声明这是 Spring 配置类，项目启动时自动加载
public class WebMvcConfig implements WebMvcConfigurer {

    // 注入我们自己写的 登录拦截器
    @Autowired
    private LoginInterceptor loginInterceptor;

    /**
     * 重写方法：添加拦截器
     * 配置拦截规则：拦截谁、放行谁
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor) // 注册登录拦截器
                
                .addPathPatterns("/**") // 【拦截规则】拦截项目中 所有接口请求
                
                .excludePathPatterns( // 【放行规则】这些接口 不需要登录就能访问
                        "/user/register",        // 用户注册
                        "/user/login",           // 用户登录
                        "/user/findPassword",    // 找回密码
                        "/fund/list",
                        "/fund/detail/**",
                        "/fund/net-value/**",
                        "/fund/stock-hold/**",
                        "/market/**",            // 市场行情（全部放行）
                        "/doc.html",             // Knife4j 接口文档
                        "/swagger-ui/**",        // Swagger 文档
                        "/v3/api-docs/**",       // Swagger 接口文档
                        "/webjars/**"            // Swagger 静态资源
                );
    }
}