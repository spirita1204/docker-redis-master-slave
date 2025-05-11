package com.practice.redis_master_slave;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;


@Configuration
public class RedisConfiguration {
  @Bean
  public LettuceConnectionFactory redisConnectionFactory() {
    // Sentinel 配置
    RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration().master("mymaster") // 主節點名稱
        .sentinel("127.0.0.1", 26379) // Sentinel 節點 1
        .sentinel("127.0.0.1", 26380) // Sentinel 節點 2
        .sentinel("127.0.0.1", 26381); // Sentinel 節點 3

    return new LettuceConnectionFactory(sentinelConfig);
  }

  @Bean
  public StringRedisTemplate redisTemplate(LettuceConnectionFactory factory) {
    return new StringRedisTemplate(factory);
  }
}

