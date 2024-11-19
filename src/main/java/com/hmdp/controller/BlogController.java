package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/blog")
@Slf4j
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        Blog savedBlog = blogService.saveBlog(blog);
        return Result.ok(savedBlog.getId());
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        blogService.likeBlog(id);
        return Result.ok();
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        log.info("查询热门博客: {}", current);
        List<Blog> blogs = blogService.queryHotBlog(current);
        return Result.ok(blogs);
    }

    @GetMapping("/{id}")
    public Result queryBlogById (@PathVariable("id") Long id) {
        log.info("查询博客详情: {}", id);
        Blog blog = blogService.queryByBlogId(id);
        return Result.ok(blog);
    }

    @GetMapping("/likes/{id}")
    public Result queryLikes (@PathVariable("id") Long id) {
        log.info("查询笔记 {} 的点赞排行", id);
        List<UserDTO> userDTOList = blogService.queryLikes(id);
        return Result.ok(userDTOList);
    }

    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId")Long max, @RequestParam(value = "offset", defaultValue = "0")Integer offset) {
        ScrollResult scrollResult = blogService.queryBlogOfFollow(max, offset);
        return Result.ok(scrollResult);
    }

}
