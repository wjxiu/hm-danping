package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author xiu
 * @create 2023-02-07 20:06
 */
public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate redisTemplate;
    private static final String prefix = "lock:";
    private final String id_prefix = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> unlock_script;
    static {
        unlock_script=new DefaultRedisScript<>();
//        设置脚本位置，并且初始化
        unlock_script.setLocation(new ClassPathResource("unlock.lua"));
        unlock_script.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(Long timeOutSec) {
        long threadId = Thread.currentThread().getId();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(prefix + name, id_prefix + threadId + "", timeOutSec, TimeUnit.SECONDS);
        System.out.println("锁成功啦");
        System.out.println(prefix + name);
        return Boolean.TRUE.equals(success);
    }

    //    @Override
//    public void unlock() {
//        String id=id_prefix+Thread.currentThread().getId();
//        String s = redisTemplate.opsForValue().get(prefix + name);
//        System.out.println(id);
//        if (id.equals(s)){
//            redisTemplate.delete(prefix + name);
//        }
//    }
    @Override
    public void unlock() {
        redisTemplate.execute(unlock_script,
                Collections.singletonList(prefix + name),
        id_prefix + Thread.currentThread().getId());
        System.out.println("删除成功");
    }
}
