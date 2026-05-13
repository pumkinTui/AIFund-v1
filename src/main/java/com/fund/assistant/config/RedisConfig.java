package com.fund.assistant.config;

import com.fund.assistant.vo.UserDailyProfitVO;
import com.fund.assistant.vo.UserFundDailyProfitVO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;

/**
 * redis 配置
 * 自定义 RedisTemplate，解决 Spring 默认序列化乱码问题
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        //设置连接工厂（自动读取 yml 里的 IP、端口、密码）
        template.setConnectionFactory(connectionFactory);
        //序列化配置
        // KEY 序列化为字符串
        template.setKeySerializer(new StringRedisSerializer());
        // VALUE 序列化为字符串
        template.setValueSerializer(new StringRedisSerializer());
        // Hash 类型 KEY 序列化为字符串
        template.setHashKeySerializer(new StringRedisSerializer());
        // Hash 类型 VALUE 序列化为字符串
        template.setHashValueSerializer(new StringRedisSerializer());
        //让上面的所有配置生效
        template.afterPropertiesSet();
        return template;
    }
}
