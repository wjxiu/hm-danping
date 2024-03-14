package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheClient cacheClient;

    private static  final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    @Override
    public Result queryShopById(Long id) {
        //缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(
//                RedisConstants.CACHE_SHOP_KEY,
//                id,
//                Shop.class,
//                this::getById,
//                RedisConstants.CACHE_NULL_TTL,
//                TimeUnit.MINUTES);
//        缓存击穿，互斥锁
//        Shop shop = queryWithMutex(id);
//        缓存击穿，逻辑过期
//       Shop shop= queryWithLogicalExpire(id);

        Shop shop =   cacheClient.queryWithLogicalExpire(
          RedisConstants.LOCK_SHOP_KEY,
          RedisConstants.CACHE_SHOP_HOT_KEY,
          id,
          Shop.class,
          id2->getById(id),
          RedisConstants.CACHE_SHOP_TTL,
          TimeUnit.SECONDS
        );
        return Result.ok(shop);
    }

    /**
     * 防止缓存穿透的查询
     *
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
//        查询redis
        String shopJsonData = redisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        Shop shopData = null;
//        存在，直接返回
        if (!StrUtil.isBlank(shopJsonData)) {
            return JSONUtil.toBean(shopJsonData, Shop.class);
        }
        if (shopJsonData != null) {
            return null;
        }

        // 不存在查询数据库
        shopData = getById(id);
        log.info("查数据库了");
        if (shopData == null) {
//             缓存空对象
            redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        String cache = JSONUtil.toJsonStr(shopData);
//        保存到redis中
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, cache, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shopData;
    }

    /**
     * 防止缓存击穿，,互斥锁，非递归
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;

        Shop shopData = null;
        try {
//        查询redis
//        存在，直接返回，可以直接删除此片段
            shopData = getFromCache(cacheKey);
            if (shopData != null) {
                return shopData;
            }
//        缓存重建
//        判断是否能拿到锁
            Boolean isSuccess = false;
//        拿不到,休眠继续拿
            do {
                isSuccess = tryLock(lockKey);
                if (!isSuccess) {
                    Thread.sleep(50);
                }
                shopData = getFromCache(cacheKey);
                if (shopData != null) {
                    return shopData;
                }
            } while (!isSuccess);


            // 双重检查，防止重复构建缓存
            shopData = getFromCache(cacheKey);
            if (shopData != null) {
                return shopData;
            }
//        拿到锁,查询数据
            shopData = getById(id);
            Thread.sleep(200);
//        保存到redis中
            String cache = JSONUtil.toJsonStr(shopData);
            redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, cache, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            releaseLock(lockKey);
        }
        return shopData;
    }



    /**
     * 防止缓存击穿,逻辑锁
     *
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String cache = redisTemplate.opsForValue().get(cacheKey);
        //未命中，返回null
        if (StrUtil.isBlank(cache)){
            return null;
        }
        RedisData shopData = JSONUtil.toBean(cache, RedisData.class);
        JSONObject data = (JSONObject) shopData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
//        缓存过期返回true
        boolean isExpire = LocalDateTime.now().isAfter(shopData.getExpireTime());
        if (!isExpire){
            return shop;
        }
//      缓存过期，重建缓存
        Boolean isSuccess = false;
        isSuccess = tryLock(lockKey);
//      拿到锁
        if (isSuccess){
//            双重检查
             shopData = JSONUtil.toBean(cache, RedisData.class);
             data = (JSONObject) shopData.getData();
             shop = JSONUtil.toBean(data, Shop.class);
             isExpire = LocalDateTime.now().isAfter(shopData.getExpireTime());
            if (!isExpire){
                return shop;
            }
//      开启独立线程重建缓存，并返回旧数据
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    saveShop2Redis(id,20L);
                    System.out.println("重建缓存啦");
                } catch (Exception e) {
                   throw new RuntimeException(e);
                } finally {
                    releaseLock(lockKey);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        return shop;

    }




    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("id不存在");
        }
//        修改数据库
        updateById(shop);
//        删除缓存
        redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    /**
     * 查询同一类型的店铺，按照距离排序
     * @param typeId
     * @param pageNo 页码
     * @param x 精度
     * @param y 维度
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer pageNo, Double x, Double y) {
        if (x==null||y==null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(pageNo, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        int from=(pageNo-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end=pageNo*SystemConstants.DEFAULT_PAGE_SIZE;
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> res = redisTemplate.
                opsForGeo().
                search(key,
                GeoReference.fromCoordinate(new Point(x, y)),
                new Distance(5, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().sortAscending().includeDistance().limit(end)
        );
        if (res==null||res.getContent().size()<=0) return Result.ok();
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResults = res.getContent();
        if (from>res.getContent().size()) return Result.ok();
        ArrayList<Long> ids = new ArrayList<>(geoResults.size());
        HashMap<String, Distance> map = new HashMap<>();
        res.getContent().stream().skip(from).forEach(result->{
            String name = result.getContent().getName();
            Distance distance = result.getDistance();
            long id = Long.parseLong(name);
            ids.add(id);
            map.put(name,distance);
        });

        String idsStr = StrUtil.join(",", ids);
        List<Shop> shops =query().in("id",ids).last("order by field(id,"+idsStr+")").list();
        for (Shop shop : shops) {
            Distance distance = map.get(shop.getId().toString());
            shop.setDistance(distance.getValue());
        }
        return Result.ok(shops);
    }

    //    获取锁
    private Boolean tryLock(String key) {
        return redisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
    }

    //    释放锁
    private Boolean releaseLock(String key) {
        return redisTemplate.delete(key);
    }


    private Shop getFromCache(String key) {
        String shopJsonData = redisTemplate.opsForValue().get(key);
        // 存在，直接返回
        if (!StrUtil.isBlank(shopJsonData)) {
            Shop shopData = JSONUtil.toBean(shopJsonData, Shop.class);
            return shopData;
        }
        if (shopJsonData != null) {
            return null;
        }
        return null;
    }

    public void saveShop2Redis(Long id,Long expireSeconds){
//        获取店铺信息
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
//        设置逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
}
