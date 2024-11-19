package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void follow(Long followUserId, Boolean isFollow) {
        if (UserHolder.getUser() == null) {
            throw new RuntimeException("请登录后再进行点赞操作~~");
        }
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 设置Redis中存储的key
        String key = RedisConstants.FOLLOWS_KEY + userId;
        // 判断执行的操作
        if (isFollow) {
            // 关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            follow.setCreateTime(LocalDateTime.now());
            // 将关注信息保存到数据库中
            boolean isSaveSuccess = save(follow);
            if (isSaveSuccess) {
                // 保存成功，将关注信息保存到Redis中
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 取关
            // 从数据库中删除关注信息
            boolean isRemoveSuccess = remove(
                    new QueryWrapper<Follow>()
                            .eq("follow_user_id", followUserId)
                            .eq("user_id", userId)

//                    new LambdaQueryWrapper<Follow>()
//                    .eq(Follow::getFollowUse Id, followUserId)
//                    .eq(Follow::getId, userId)
            );
            // 从Redis中删除关注信息
            if (isRemoveSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }

    }

    @Override
    public Boolean isFollow(Long id) {
        if (UserHolder.getUser() == null) {
            return false;
        }

        Long userId = UserHolder.getUser().getId();
        return query()
                .eq("user_id", userId)
                .eq("follow_user_id", id)
                .count() > 0;

    }

    @Override
    public List<UserDTO> followCommons(Long id) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 组装当前用户和要查询共同关注用户的RedisKey
        String key = RedisConstants.FOLLOWS_KEY + userId;
        String key2 = RedisConstants.FOLLOWS_KEY + id;
        // 查询两个集合的交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        // 如果交集为空则直接返回空集合
        if (intersect == null || intersect.isEmpty()) {
            return Collections.emptyList();
        }
        // 不为空
        // 通过stream流将String类型映射为Long类型
        List<Long> ids = intersect
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        // 最后返回的数据类型应该为List<UserDTO> 所以需要先查询出用户信息 然后进一步处理
        List<UserDTO> userDTOList = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return userDTOList;
    }

}
