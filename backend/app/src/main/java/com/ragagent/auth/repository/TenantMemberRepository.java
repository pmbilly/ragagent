package com.ragagent.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.auth.domain.TenantMember;
import com.ragagent.auth.mapper.TenantMemberMapper;
import com.ragagent.platform.persistence.PageRequests;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 成员仓储：成员行的查询、分页、角色变更与软删。
 *
 * <p>只暴露调用方需要的窄操作；"最后 Owner 判定"等业务规则留在 service。</p>
 */
@Repository
public class TenantMemberRepository {

    private final TenantMemberMapper mapper;

    public TenantMemberRepository(TenantMemberMapper mapper) {
        this.mapper = mapper;
    }

    /** 单条成员查询（按 id 升序取第一条；找不到返回 null）。 */
    public TenantMember findMembership(String userId, long tenantId) {
        return mapper.selectOne(new LambdaQueryWrapper<TenantMember>()
                .eq(TenantMember::getUserId, userId)
                .eq(TenantMember::getTenantId, tenantId)
                .isNull(TenantMember::getDeletedAt)
                .orderByAsc(TenantMember::getId)
                .last("LIMIT 1"));
    }

    /** 用户的成员行列表：joined_at、id 升序（稳定序）。 */
    public List<TenantMember> listByUser(String userId) {
        return mapper.selectList(new LambdaQueryWrapper<TenantMember>()
                .eq(TenantMember::getUserId, userId)
                .isNull(TenantMember::getDeletedAt)
                .orderByAsc(TenantMember::getJoinedAt)
                .orderByAsc(TenantMember::getId));
    }

    /** 空间内指定状态的成员数。 */
    public long countByStatus(long tenantId, String status) {
        Long count = mapper.selectCount(new LambdaQueryWrapper<TenantMember>()
                .eq(TenantMember::getTenantId, tenantId)
                .eq(TenantMember::getStatus, status)
                .isNull(TenantMember::getDeletedAt));
        return count == null ? 0 : count;
    }

    /** 除指定用户外的指定角色成员数。 */
    public long countByRoleExcludingUser(long tenantId, String role, String excludeUserId, String status) {
        Long count = mapper.selectCount(new LambdaQueryWrapper<TenantMember>()
                .eq(TenantMember::getTenantId, tenantId)
                .ne(TenantMember::getUserId, excludeUserId)
                .eq(TenantMember::getRole, role)
                .eq(TenantMember::getStatus, status)
                .isNull(TenantMember::getDeletedAt));
        return count == null ? 0 : count;
    }

    public void insert(TenantMember member) {
        mapper.insert(member);
    }

    /** 分页命中数；{@code userIds} 为 null 表示不加用户过滤。 */
    public long countPage(long tenantId, Collection<String> userIds) {
        Long total = mapper.selectCount(pageScope(tenantId, userIds));
        return total == null ? 0 : total;
    }

    /** 分页列表：joined_at、id 升序。 */
    public List<TenantMember> listPage(long tenantId, Collection<String> userIds, int page, int pageSize) {
        LambdaQueryWrapper<TenantMember> wrapper = pageScope(tenantId, userIds)
                .orderByAsc(TenantMember::getJoinedAt)
                .orderByAsc(TenantMember::getId);
        return mapper.selectList(PageRequests.range(page, pageSize), wrapper);
    }

    private static LambdaQueryWrapper<TenantMember> pageScope(long tenantId, Collection<String> userIds) {
        LambdaQueryWrapper<TenantMember> wrapper = new LambdaQueryWrapper<TenantMember>()
                .eq(TenantMember::getTenantId, tenantId)
                .isNull(TenantMember::getDeletedAt);
        if (userIds != null) {
            wrapper.in(TenantMember::getUserId, userIds);
        }
        return wrapper;
    }

    /** 改角色并刷新 updated_at。 */
    public void updateRole(String userId, long tenantId, String role, OffsetDateTime now) {
        mapper.update(null, new LambdaUpdateWrapper<TenantMember>()
                .eq(TenantMember::getUserId, userId)
                .eq(TenantMember::getTenantId, tenantId)
                .isNull(TenantMember::getDeletedAt)
                .set(TenantMember::getRole, role)
                .set(TenantMember::getUpdatedAt, now));
    }

    /** 软删单个成员。 */
    public void softDelete(String userId, long tenantId, OffsetDateTime now) {
        mapper.update(null, new LambdaUpdateWrapper<TenantMember>()
                .eq(TenantMember::getUserId, userId)
                .eq(TenantMember::getTenantId, tenantId)
                .isNull(TenantMember::getDeletedAt)
                .set(TenantMember::getDeletedAt, now));
    }

    /** 软删整空间的全部成员（租户删除路径）。 */
    public void softDeleteByTenant(long tenantId, OffsetDateTime now) {
        mapper.update(null, new LambdaUpdateWrapper<TenantMember>()
                .eq(TenantMember::getTenantId, tenantId)
                .isNull(TenantMember::getDeletedAt)
                .set(TenantMember::getDeletedAt, now));
    }
}
