package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private IUserService userService;
    @Override
    public Result follow(Long followid, Boolean isFollow) {
        Boolean res;
        UserDTO user = UserHolder.getUser();
        String key= "Follow:"+user.getId();
        if (!isFollow){
            res = remove(new QueryWrapper<Follow>().eq("user_id", user.getId()).eq("follow_user_id", followid));
            if (res){
                redisTemplate.opsForSet().remove(key,user.getId().toString());
            }
        }else{
            Follow follow = new Follow();
            follow.setFollowUserId(followid);
            follow.setUserId(user.getId());
            follow.setCreateTime(LocalDateTime.now());
            res = save(follow);
            if (res){
                redisTemplate.opsForSet().add(key,followid.toString());
            }
        }
        return Result.ok(res);
    }

    @Override
    public Result isFollow(Long followid) {
        Integer count = query().eq("user_id", UserHolder.getUser().getId()).eq("follow_user_id", followid).count();
        return Result.ok(count>0);
    }

    @Override
    public Result followCommon(Long id) {
        String key= "Follow:";
        Long userId = UserHolder.getUser().getId();
        Set<String> set = redisTemplate.opsForSet().intersect(key + userId, key + id);
        if (set==null||set.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> collect = set.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(collect);
        List<UserDTO> common = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(common);
    }

}
