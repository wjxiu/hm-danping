package com.hmdp.utils;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author xiu
 * @create 2023-02-03 15:26
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    private static  final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        long secondsDuration = Convert.convertTime(time, timeUnit, TimeUnit.SECONDS);
        LocalDateTime localDateTime = LocalDateTime.now().plusSeconds(secondsDuration);
        redisData.setExpireTime(localDateTime);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    public <R> R  queryWithPassThrough(String keyPrefix,
                                       Serializable id,
                                       Class<R> type,
                                       Function<Serializable,R> dbFallback,
                                       Long time,
                                       TimeUnit timeUnit
                                       ) {
//        查询redis
        String key=keyPrefix+id;
        String jsonData = stringRedisTemplate.opsForValue().get(key);
//        存在，直接返回
        if (!StrUtil.isBlank(jsonData)) {
            return JSONUtil.toBean(jsonData, type);
        }
        if (jsonData != null) {
            return null;
        }
        // 不存在查询数据库
       R r = dbFallback.apply(id);
        log.info("查数据库了");
        if (r == null) {
//             缓存空对象
            stringRedisTemplate.opsForValue().set(key, "", time, timeUnit);
            return null;
        }
//        保存到redis中
        set(key,r,time,timeUnit);
        return r;
    }

    public <R> R queryWithLogicalExpire(String lockKeyPrefix,
                                        String cacheKeyPrefix,
                                        Serializable id,
                                        Class<R> type,
                                        Function<Serializable,R> dbFallback,
                                        Long time, TimeUnit timeUnit
                                        ) {
        String lockKey = lockKeyPrefix + id;
        String cacheKey = cacheKeyPrefix + id;
        String cache = stringRedisTemplate.opsForValue().get(cacheKey);
        //未命中，返回null
        if (StrUtil.isBlank(cache)){
            return null;
        }
        RedisData shopData = JSONUtil.toBean(cache, RedisData.class);
        JSONObject data = (JSONObject) shopData.getData();
        R r = JSONUtil.toBean(data, type);
//        缓存过期返回true
        boolean isExpire = LocalDateTime.now().isAfter(shopData.getExpireTime());
        if (!isExpire){
            return r;
        }
//      缓存过期，重建缓存
        Boolean isSuccess = false;
        isSuccess = tryLock(lockKey);
//      拿到锁
        if (isSuccess){
//            双重检查
            shopData = JSONUtil.toBean(cache, RedisData.class);
            data = (JSONObject) shopData.getData();
            r = JSONUtil.toBean(data, type);
            isExpire = LocalDateTime.now().isAfter(shopData.getExpireTime());
            if (!isExpire){
                return r;
            }
//      开启独立线程重建缓存，并返回旧数据
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
//                   查询数据库
                     R r1= dbFallback.apply(id);
//                    写入缓存
                    setWithLogicalExpire(cacheKey,r1,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    releaseLock(lockKey);
                }
            });
        }
        return r;

    }


    //    获取锁
    private Boolean tryLock(String key) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
    }

    //    释放锁
    private Boolean releaseLock(String key) {
        return stringRedisTemplate.delete(key);
    }




}
