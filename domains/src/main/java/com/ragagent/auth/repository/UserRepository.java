package com.ragagent.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.auth.domain.User;
import com.ragagent.auth.mapper.UserMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 用户仓储：按 id / 关键字查用户，整行写回与指针清理。 */
@Repository
public class UserRepository {

    private final UserMapper mapper;

    public UserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    /** 按 id 取未软删的用户（不存在返回 null）。 */
    public User findById(String userId) {
        return mapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .isNull(User::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 按邮箱/用户名模糊匹配，只取 id（LIKE 由 SQL 的 LOWER 比较完成）。 */
    public List<String> listIdsByKeyword(String like) {
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<User>()
                .select(User::getId)
                .apply("(LOWER(email) LIKE LOWER({0}) OR LOWER(username) LIKE LOWER({1}))", like, like);
        List<String> ids = new ArrayList<>();
        for (User u : mapper.selectList(w)) {
            ids.add(u.getId());
        }
        return ids;
    }

    public void updateById(User user) {
        mapper.updateById(user);
    }

    /** 把 {@code tenant_id} 显式写成 SQL NULL（FK 不认 0）。 */
    public void clearTenantId(String userId) {
        mapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getTenantId, null));
    }
}
