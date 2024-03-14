package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private RedisTemplate<String ,Object> redisTemplate;
    @Override
    public Result queryTypeListCache() {
        //查询redis
        List<Object> data = redisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        log.info("缓存结果{}",data);
//        存在，返回
        if (data!=null&&data.size()>0){
            return Result.ok(data.get(0));
        }
//        不存在，查询数据库,并且缓存
        List<ShopType> types = query().orderByAsc("sort").list();
        log.info("查询结果{}",types);
        redisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY,types);
        redisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_KEY,RedisConstants.CACHE_SHOP_TYPE_TTl, TimeUnit.MINUTES);
        return Result.ok(types);
    }
}
