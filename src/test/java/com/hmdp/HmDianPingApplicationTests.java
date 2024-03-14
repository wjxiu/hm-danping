package com.hmdp;

import cn.hutool.core.convert.Convert;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    RedisTemplate<String ,Object> redisTemplate;
    @Autowired
    StringRedisTemplate stringredisTemplate;
    @Resource
    ShopServiceImpl shopService;

    @Test
    void put(){
        ArrayList<Integer> integers = new ArrayList<>();
        integers.add(1);
        integers.add(2);
        integers.add(3);
        List<Object> listkk = redisTemplate.opsForList().range("listkk", 0, -1);
        System.out.println(listkk.get(0));
    }

    @Test
    void putHotKey() {
        long secondsDuration = Convert.convertTime(1, TimeUnit.HOURS, TimeUnit.SECONDS);
        LocalDateTime localDateTime = LocalDateTime.now().plusSeconds(secondsDuration);
        System.out.println(localDateTime);
    }

    @Autowired
    RedisIdWorker redisIdWorker;

    private ExecutorService  es=Executors.newFixedThreadPool(500);

    @Test
    void testId() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task=()->{
            for (int i = 0; i < 200; i++) {
                Long order = redisIdWorker.nextId("order");
                System.out.println(order);
            }
            countDownLatch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        System.out.println(System.currentTimeMillis()-start);
    }

    @Test
    void putall() {
        List<Shop> list = shopService.list();
        HashMap<Long, List<Shop>> map = new HashMap<>();

        for (Shop shop : list) {
            Long typeId = shop.getTypeId();
            List<Shop> shops = map.get(typeId);
            if (shops==null||shops.isEmpty()){
                shops=new ArrayList<Shop>();
            }
            String key="shop:type:"+shop.getTypeId();
//            shops.add(shop.getId());
            stringredisTemplate.opsForGeo().add(key,
                    new Point(shop.getX(),shop.getY()),
                    shop.getId().toString());
            map.put(typeId,shops);
        }
        System.out.println(list);
    }

    @Test
    void testor() {
       int num=23;
        int count=0;
        while (true){
            long signornot= num&1;
            if (signornot==0L){
                break;
            }
            count++;
            num=num>>1;
        }
        System.out.println(count);
    }

    @Test
    void testhyperlog() {
       String[] users= new String[1000];
        for (int i = 0; i < 100000; i++) {
            users[i%1000]="user"+i;
            if (i%1000==999){
                redisTemplate.opsForHyperLogLog().add("hl2",users);
            }
        }
    }


}
