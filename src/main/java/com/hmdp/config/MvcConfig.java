package com.hmdp.config;

import com.hmdp.Interceptor.loginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author xiu
 * @create 2023-02-01 20:51
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    HandlerInterceptor loginInterceptor;
    @Resource
    HandlerInterceptor reflashInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(reflashInterceptor).addPathPatterns("/**").excludePathPatterns(
                "/user/code/**",
                "/user/login"
        ).order(0);


        registry.addInterceptor(loginInterceptor).excludePathPatterns(
                "/user/code/**",
                "/shop-type/**",
                "/user/login",
                "/blog/hot",
                "/shop/**",
                "/upload/**",
                "/voucher/**",
                "/"
        ).order(1);
    }
}
