package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        校验
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid) {
            return Result.fail("手机号不对");
        }

//        生成随机数
        String code = RandomUtil.randomNumbers(6);
//        发送验证码
        log.info("{}的验证码为{}",phone,code);
//        保存到redis中
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        session.setAttribute(phone+":code", code);
//      返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String code = loginForm.getCode();
        String phone = loginForm.getPhone();
//        校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return  Result.fail("手机号无效");
        }
//        校验验证码，失败退出
        if (RegexUtils.isCodeInvalid(code)) {
            return  Result.fail("验证码出错");
        }
//        redis获取验证码
        if (redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+ phone)==null||phone.equals(code)){
            return  Result.fail("验证码错误或无效");
        }
//        根据电话查询用户
        User loginUser = query().eq("phone", phone).one();
        if (loginUser==null){
//        不存在，注册用户
            loginUser= createUserByPhone(phone);
        }
//        保存用户信息到REDIS
//        随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
//          对象转hash
        UserDTO userDTO = BeanUtil.copyProperties(loginUser, UserDTO.class);
        Map<String, Object> objectMap = BeanUtil.beanToMap(userDTO);
        String  idStr = objectMap.get("id").toString();
        objectMap.put("id",idStr);
        String loginKey = RedisConstants.LOGIN_USER_KEY + token;
//         存储对象到redis
        redisTemplate.opsForHash().putAll(loginKey,objectMap);
        redisTemplate.expire(loginKey,RedisConstants.LOGIN_USER_TTL,TimeUnit.SECONDS);
//         返回token
        return Result.ok(token);
    }

    /**
     * 签到功能
     * @return
     */
    @Override
    public Result sign() {
        Long id = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + id + ":" + yyyyMM;
        int dayOfMonth = now.getDayOfMonth();
        redisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    /**
     * 统计连续签到天数
     * @return
     */
    @Override
    public Result signCount() {
        Long id = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + id + ":" + yyyyMM;
        int dayOfMonth = now.getDayOfMonth();
        List<Long> res = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.
                        create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).
                        valueAt(0)
        );
        if (res==null||res.isEmpty()){
            return Result.ok(0);
        }
        Long num = res.get(0);
        if (num==null||num==0){
            return Result.ok(0);
        }
        int count=0;
        while (true){
            long signornot= num&1;
            if (signornot==0L){
                break;
            }
            count++;
            num=num>>1;
        }
        return Result.ok(count);
    }

    private User createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }

}
