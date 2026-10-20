package com.ragagent.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.auth.domain.User;
import com.ragagent.auth.mapper.UserMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 用户仓储：按 id / 邮箱 / 用户名 / 租户查询，整行写回、插入与指针清理。 */
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

    /** 按邮箱取未软删的用户（id 升序取第一条）。 */
    public User findByEmail(String email) {
        return mapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .isNull(User::getDeletedAt)
                .orderByAsc(User::getId)
                .last("LIMIT 1"));
    }

    /** 按用户名取未软删的用户（id 升序取第一条）。 */
    public User findByUsername(String username) {
        return mapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .isNull(User::getDeletedAt)
                .orderByAsc(User::getId)
                .last("LIMIT 1"));
    }

    /** 取租户内 created_at 最早的用户（API Key 认证的身份兜底用）。 */
    public User findByTenantIdFirst(long tenantId) {
        return mapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .isNull(User::getDeletedAt)
                .orderByAsc(User::getCreatedAt)
                .last("LIMIT 1"));
    }

    /** 批量按 id 查未软删的用户。 */
    public List<User> listByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, ids)
                .isNull(User::getDeletedAt));
    }

    /** 按邮箱/用户名模糊匹配，只取 id（LIKE 由 SQL 的 LOWER 比较完成）。 */
    public List<String> listIdsByKeyword(String like) {
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<User>()
                .select(User::getId)
                .isNull(User::getDeletedAt)
                .apply("(LOWER(email) LIKE LOWER({0}) OR LOWER(username) LIKE LOWER({1}))", like, like);
        List<String> ids = new ArrayList<>();
        for (User u : mapper.selectList(w)) {
            ids.add(u.getId());
        }
        return ids;
    }

    public void insert(User user) {
        mapper.insert(user);
    }

    /**
     * 插入 tenantless 用户：省略 {@code tenant_id} 列（落 SQL NULL）。
     *
     * <p>写 0 会违反 {@code fk_users_tenant}，所以不能走普通 insert 再把 0 当"无租户"。</p>
     */
    public void insertTenantless(User user) {
        mapper.insertTenantless(user);
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

    /** 把 {@code tenant_id} 与 {@code updated_at} 一起显式写回（恢复 tenantless 身份）。 */
    public void restoreTenantless(String userId, OffsetDateTime updatedAt) {
        mapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getTenantId, null)
                .set(User::getUpdatedAt, updatedAt));
    }

    public void deleteById(String userId) {
        mapper.deleteById(userId);
    }
}
