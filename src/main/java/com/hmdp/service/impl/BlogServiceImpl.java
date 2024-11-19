package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public List<Blog> queryHotBlog(Integer current) {

        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        // 获取当前页数据
        List<Blog> records = page.getRecords();

        // 设置Blog的name和icon字段
        records.forEach(this::queryBlogUser);

        if (UserHolder.getUser() != null) {
            // 用户已经登录，设置blog的isLiked
            records.forEach(this::setBlogLiked);
        }
        return records;
    }

    @Override
    public Blog queryByBlogId(Long id) {
        // 查询博客
        Blog blog = getById(id);
        if (blog == null) {
            throw new RuntimeException("博客不存在!");
        }
        // 查询博客相关用户
        queryBlogUser(blog);
        // 查询是否点赞
        if (UserHolder.getUser() != null) {
            setBlogLiked(blog);
        }
        return blog;
    }

    private void setBlogLiked(Blog blog) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 判断用户是否已经点过赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public void likeBlog(Long id) {
        if (UserHolder.getUser() == null) {
            throw new RuntimeException("请登录后再进行点赞操作~~");
        }

        Long userId = UserHolder.getUser().getId();
        // 判断用户是否已经点过赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 没点赞过，可以点赞
            // 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // 将用户添加到zset集合中
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 已点赞过，再次点赞取消
            // 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                // 将用户移出set集合
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return;
    }

    @Override
    public List<UserDTO> queryLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 查询点赞top5
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Collections.emptyList();
        }
        // 将top5返回的Set<String>转换为List<Long>
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idsStr = StrUtil.join(",", ids);
        // 根据ids中的id数据来查询用户数据，最后返回List<UserDTO>
        // 为了让最后展示的结果按照我们传入的顺序 需要手写SQL语句
        return userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idsStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public Blog saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        blog.setUserId(userId);
        // 保存探店博文
        boolean isSaveSuccess = save(blog);
        // 获取blogId(注意:save操作执行以后才会有主键值 否则会报空指针错误)
        Long blogId = blog.getId();
        // 判断保存操作是否成功
        if (isSaveSuccess) {
            // 查询粉丝列表 select * from tb_follow where follow_user_id = ?
            List<Follow> followList = followService.query().eq("follow_user_id", userId).list();
            // 将笔记推送到粉丝的收件箱
            for (Follow follow : followList) {
                String key = RedisConstants.FEED_KEY + follow.getUserId().toString();
                stringRedisTemplate.opsForZSet().add(key, blogId.toString(), System.currentTimeMillis());
            }
            // 返回博客
            return blog;
        } else {
            throw new RuntimeException("新建博客失败！");
        }
    }

    /**
     * 查询关注的人的笔记
     *
     * @param max
     * @param offset
     * @return
     */
    @Override
    public ScrollResult queryBlogOfFollow(Long max, Integer offset) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 根据用户id查询用户收件箱里的所有博客id
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // ZSet非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return null;
        }
        // 解析数据: 笔记id、score(时间戳)、offset、size
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0L;
        int os = 1; //
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 解析出笔记id
            ids.add(Long.valueOf(typedTuple.getValue()));

            if(minTime == typedTuple.getScore().longValue()) {
                // 当前笔记时间戳与最小时间戳相同，则偏移量+1，下次查询需要跳过
                os++;
            } else {
                // 当前笔记时间戳为新的最小时间戳，偏移量归零
                minTime = typedTuple.getScore().longValue();
                os = 1;
            }
        }

        // 根据笔记id查询笔记
        /*
            注意这里不能直接使用listByIds，因为其底层实现是基于in () 的，返回时无序，但此处需要返回时有序(即需要插入ORDER BY)
         */
        String idsStr = StrUtil.join(",", ids);

        List<Blog> blogs = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idsStr + ")")
                .list()
                .stream()
                .peek(blog -> {
                    queryBlogUser(blog);
                    setBlogLiked(blog);
                }).collect(Collectors.toList());

        // 封装返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return scrollResult;
    }

    public void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
