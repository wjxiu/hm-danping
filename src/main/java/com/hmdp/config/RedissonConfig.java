package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author xiu
 * @create 2023-02-08 15:09
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redisClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.205.3:6379");
//                .setPassword("ga5lhnaoecfu");
        return Redisson.create(config);
    }
}
