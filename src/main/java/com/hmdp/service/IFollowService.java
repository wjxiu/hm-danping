package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 表示当前用户关注id为followid的用户，
     * @param followid
     * @param isFollow 表示关注标识，true为关注，false为取关
     * @return
     */
    Result follow(Long followid, Boolean isFollow);

    Result isFollow(Long id);

    Result followCommon(Long id);
}
