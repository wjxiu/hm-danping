package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate StringRedisTemplate;
    @Autowired
    private IFollowService followService;
    @Override
    public Result queryblogById(Long id) {
        Blog blog = getById(id);
        if (blog==null){
            return Result.fail("笔记不存在");
        }
        queryBlobUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 判断当前用户是否对blog点过赞,点过将属性isliked设置为true
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user==null){
            return ;
        }
        String key=RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = StringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score!=null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryBlobUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 给博客点赞
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key=RedisConstants.BLOG_LIKED_KEY + id;
        Double score = StringRedisTemplate.opsForZSet().score(key, userId.toString());
//        没有点赞过
        if (score ==null){
//            数据库更新
            boolean isUpdated = update().setSql("liked =liked+1").eq("id", id).update();
            if (isUpdated){
                StringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
//            redis更新
        }else{
//            数据库更新
            boolean isUpdated = update().setSql("liked =liked-1").eq("id", id).update();
            if (isUpdated){
                StringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key=RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> range = StringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (range==null||range.size()<=0){
            return Result.ok(Collections.emptyList());
        }
        String idlist = StrUtil.join(",", range);
        List<User> users = userService.query().in("id",range).last("order by field(id,"+idlist+")").list();
        if (users==null||users.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> userDTOList = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        System.out.println(userDTOList);
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        save(blog);

//        获取全部粉丝id
        List<Follow> list = followService.query().eq("follow_user_id", blog.getUserId()).list();
//        推送到粉丝的收件箱
        for (Follow follow : list) {
            StringRedisTemplate.opsForZSet().add("feed:"+follow.getUserId(),blog.getId().toString(),System.currentTimeMillis());
        }
//
        return Result.ok(blog.getId());
    }

    /**
     * 查询关注用户的博客
     * 使用滚动分页
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFllow(Long max, Integer offset) {
//        获取当前用户
        Long userId = UserHolder.getUser().getId();
//        查询收件箱
        String key=RedisConstants.FEED_KEY+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = StringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if (typedTuples==null ||typedTuples.isEmpty()){
            return Result.ok();
        }
        ArrayList<Long> ids = new ArrayList<>();
        long minTime=0;
//        计数变量，获取最小值的个数
        int os=1;
//        解析数据
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            long id = Long.parseLong(typedTuple.getValue());
            ids.add(id);
            long time = typedTuple.getScore().longValue();
            if (os==minTime){
                os++;
            }else{
                os=1;
                minTime=time;
            }
        }
        String idstr = StrUtil.join(",", ids);
//        根据Id查询blog
        List<Blog> blogs = query().in("id",ids).last("order by field(id,"+idstr+")").list();
        for (Blog blog : blogs) {
            queryBlobUser(blog);
            isBlogLiked(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
//        返回
        return Result.ok(scrollResult);
    }

    private void queryBlobUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        isBlogLiked(blog);
    }
}
