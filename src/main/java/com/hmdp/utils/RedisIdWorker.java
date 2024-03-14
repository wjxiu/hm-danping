package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author xiu
 * @create 2023-02-03 19:26
 */
@Component
public class RedisIdWorker {
    private final  Long BASIC_STAMP= 1675452554L;
    private final  int BIt_COUNTS= 32;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

   public Long nextId(String keyPrefix){
       LocalDateTime now = LocalDateTime.now();
       long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
       long timestamp =nowSecond=BASIC_STAMP;
       String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
       long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":"+date);
       return (timestamp<<BIt_COUNTS)|count;
   }
}
