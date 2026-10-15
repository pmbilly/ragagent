package com.ragagent.auth.api;

import org.springframework.stereotype.Component;

import com.ragagent.auth.mapper.UserMapper;
import com.ragagent.common.auth.UserDirectoryPort;
import com.ragagent.common.auth.domain.User;

/**
 * {@link UserDirectoryPort} 的 auth 域实现：按主键读用户行，逐字转发（不缓冲、不改口径）。
 */
@Component
public class UserDirectoryAdapter implements UserDirectoryPort {

    private final UserMapper userMapper;

    public UserDirectoryAdapter(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User userById(String id) {
        return userMapper.selectById(id);
    }
}
