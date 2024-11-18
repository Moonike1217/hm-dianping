package com.hmdp.service;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IFollowService extends IService<Follow> {

    void follow(Long id, Boolean isFollow);

    Boolean isFollow(Long id);

    List<UserDTO> followCommons(Long id);
}
