package com.hmdp.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author xiu
 * @create 2023-02-01 20:45
 */
@Component
public class reflashInterceptor implements HandlerInterceptor {
    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        获取token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)){
            return true;
        }
        String key =RedisConstants.LOGIN_USER_KEY+token;
//        获取reids的对应token的用户
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
//        判断是否存在
        if (entries.isEmpty()){
//        不存在，不刷新
            response.setStatus(401);
            return true;
        }else{
//            刷新token
            redisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
//            转换
            UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
//        存在，保存到threadlocal
            UserHolder.saveUser(userDTO);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
