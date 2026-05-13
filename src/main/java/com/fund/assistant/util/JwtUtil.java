package com.fund.assistant.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JWT 工具类
 * 核心功能：
 * 1. 生成 Token（登录成功后生成）
 * 2. 解析 Token（获取用户ID）
 * 3. 校验 Token（是否合法、过期）
 */
@Component
public class JwtUtil {

    /**
     * 密钥（用于签名Token，防止伪造）
     * 注意：生产环境必须使用更长、更安全的随机字符串
     */
    private static final String SECRET_KEY = "fundValuationAssistant12345678901234567890";

    /**
     * Token 过期时间
     * 7天 = 7 * 24小时 * 60分钟 * 60秒 * 1000毫秒
     */
    private static final long EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;

    /**
     * 根据密钥生成 JWT 所需的加密 Key
     * 固定写法，不用改
     */
    private final Key key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());

    // 生成 Token
    /**
     * 生成 JWT Token
     * @param userId    用户ID（存在Token里）
     * @param username  用户名（存在Token里）
     * @return 生成好的 Token 字符串
     */
    public String generateToken(Long userId, String username) {
        return Jwts.builder()
                .setSubject(userId.toString())    // 设置主题：存储用户ID
                .claim("username", username)       // 自定义内容：存储用户名
                .setIssuedAt(new Date())           // 设置签发时间：当前时间
                .setExpiration(new Date(
                        System.currentTimeMillis() + EXPIRE_TIME)) // 设置过期时间
                .signWith(key, SignatureAlgorithm.HS256) // 签名：使用密钥+HS256算法加密
                .compact(); // 压缩生成最终的Token字符串
    }

    // 解析 Token，获取用户ID
    /**
     * 从 Token 中解析出 用户ID
     * @param token 前端传过来的Token
     * @return userId
     */
    public Long getUserIdFromToken(String token) {
        // 解析Token，获取里面的载荷（Claims）
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)        // 设置解密密钥（必须和加密时一致）
                .build()
                .parseClaimsJws(token)     // 解析Token
                .getBody();                // 获取存储的数据

        // 从Subject中取出用户ID并返回
        return Long.valueOf(claims.getSubject());
    }

    //校验 Token 是否有效
    /**
     * 校验Token是否合法（未过期、未被篡改）
     * @param token 前端传过来的Token
     * @return true=有效，false=无效/过期/伪造
     */
    public boolean validateToken(String token) {
        try {
            // 尝试解析Token，如果不抛异常说明有效
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        }
        // 捕获所有JWT异常：过期、签名错误、格式非法等
        catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 解析token，获取用户名
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("username", String.class);
    }
}