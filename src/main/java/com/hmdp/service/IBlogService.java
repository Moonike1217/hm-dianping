package com.hmdp.service;

import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface IBlogService extends IService<Blog> {

    /**
     * 查询热门博客
     * @param current
     * @return
     */
    List<Blog> queryHotBlog(Integer current);

    /**
     * 查询博客详情
     *
     * @param id
     * @return
     */
    Blog queryByBlogId(Long id);

    /**
     * 点赞blog
     * @param id
     */
    void likeBlog(Long id);

    /**
     * 查询点赞排行
     * @param id
     * @return
     */
    List<UserDTO> queryLikes(Long id);

    /**
     * 保存博客
     * @param blog
     * @return
     */
    Blog saveBlog(Blog blog);

    ScrollResult queryBlogOfFollow(Long max, Integer offset);
}
