package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redisson;

    private static final DefaultRedisScript<Long> secKillScript;

    private BlockingQueue<VoucherOrder> queue=new ArrayBlockingQueue<>(1024);
    private ExecutorService executorService= Executors.newSingleThreadExecutor();

    static {
        secKillScript=new DefaultRedisScript<>();
        secKillScript.setLocation(new ClassPathResource("seckill.lua"));
        secKillScript.setResultType(Long.class);
    }
    VoucherOrderServiceImpl proxy;
    @PostConstruct
    public void init(){
        executorService.submit(()->{
            while (true){
                try {
                    VoucherOrder take = queue.take();
                    handleVoucherOrder(take);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void handleVoucherOrder(VoucherOrder take) {
//        //      一人一单
        Long userId = take.getUserId();
        RLock lock1 = redisson.getLock("order:" + userId);
        boolean b = lock1.tryLock();
        if (!b){
            System.out.println("服务器繁忙");
        }
        lock1.lock();
        try {
            proxy.createVoucherOrder(take);
        } finally {
            lock1.unlock();
        }
    }
//异步保存订单方法
    private void createVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Integer count = query().eq("user_id", voucherOrder.getUserId()).eq("voucher_id", voucherId).count();
        if (count>0){
            System.out.println("重复下单");
        }
        boolean success = seckillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if (!success){
            System.out.println("抢购失败");
        }
        save(voucherOrder);
    }

    //使用阻塞队列的方法
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long res = redisTemplate.execute(secKillScript, Collections.emptyList(), voucherId.toString(), "1");
        if (res.intValue()==1){
            return Result.fail("库存不足");
        }
        if (res.intValue()==2){
            return Result.fail("请勿重复下单");
        }
        Long order = redisIdWorker.nextId("order");
        return Result.ok(order);
    }
//不适用阻塞队列的方法
//    @Override
//    public Result seckillVoucher(Long voucherId) {
////        查询信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if (voucher==null){
//            return Result.fail("没有这张优惠券");
//        }
////        判断是否开始或结束
//        LocalDateTime now = LocalDateTime.now();
//        boolean noStart = now.isBefore(voucher.getBeginTime());
//        boolean hasend = now.isAfter(voucher.getEndTime());
////        否，返回异常信息
//        if (noStart||hasend){
//            return Result.fail("抢购没开始或者已经结束");
//        }
////        是，判断是否库存充足
////        不充足，返回异常信息
//        if (voucher.getStock()<=0){
//            return Result.fail("库存不足");
//        }
//
//        //      一人一单
//        Long  userId = 1L;
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, redisTemplate);
//        RLock lock = redisson.getLock("order:" + userId);
//        boolean b = lock.tryLock();
//        if (!b){
//            return Result.fail("服务器繁忙");
//        }
//        try {
//            VoucherOrderServiceImpl proxy = (VoucherOrderServiceImpl)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId,userId);
//        } finally {
//            lock.unlock();
//        }
//    }

    @Transactional
    public Result createVoucherOrder(Long voucherId,Long userId){

            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count>0){
                return Result.fail("请勿重复抢购");
            }
        boolean success = seckillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if (!success){
            return Result.fail("抢购失败");
        }
//        充足，创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            Long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
//        UserDTO user = UserHolder.getUser();
//        todo 写死用户id
            voucherOrder.setUserId(1L);
            voucherOrder.setVoucherId(voucherId);

            save(voucherOrder);
//        返回订单id
            return Result.ok(orderId);
    }
}
