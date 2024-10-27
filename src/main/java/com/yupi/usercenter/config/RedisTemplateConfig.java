package com.yupi.usercenter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * RedisTemplate 配置
 *
 * @author <a href="https://github.com/Jasonzp">Jasonzp</a>
 * https://github.com/Jasonzp
 */
@Configuration
public class RedisTemplateConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        // 配置 Redis 的连接信息
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName("localhost");
        redisConfig.setPort(6379);
        redisConfig.setPassword("123456");  // 设置密码

        // 使用 Lettuce 连接工厂
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();  // 初始化连接

        // 创建 RedisTemplate 并设置序列化器
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(RedisSerializer.json());
        return redisTemplate;
    }
}
