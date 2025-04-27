package Configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RedisConfiguration {
  @Bean
    public LettuceConnectionFactory  redisConnectionFactory() {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration()
              .master("mymaster")
              .sentinel("172.24.0.6", 26379)
              .sentinel("172.24.0.4", 26380)
              .sentinel("172.24.0.5", 26381);

        return new LettuceConnectionFactory(sentinelConfig);
    }
}

