package com.ragagent.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.auth.domain.TenantInvitation;
import com.ragagent.auth.mapper.TenantInvitationMapper;
import com.ragagent.platform.persistence.PageRequests;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 邀请仓储：邀请行的查询、创建、状态翻转与过期清扫。
 *
 * <p>状态常量（{@code pending} / {@code expired}）定义在这里，供查询条件与
 * service 侧业务判断共用同一份字面量。</p>
 */
@Repository
public class TenantInvitationRepository {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_EXPIRED = "expired";

    private final TenantInvitationMapper mapper;

    public TenantInvitationRepository(TenantInvitationMapper mapper) {
        this.mapper = mapper;
    }

    /** 按 id 窄查询（无 sweep），找不到返回 null。 */
    public TenantInvitation findById(long id) {
        return mapper.selectOne(new LambdaQueryWrapper<TenantInvitation>()
                .eq(TenantInvitation::getId, id)
                .isNull(TenantInvitation::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 租户侧邀请计数（不含 ORDER BY，避免 H2 的分组报错）。 */
    public long countTenant(long tenantId, boolean includeTerminal) {
        LambdaQueryWrapper<TenantInvitation> scope = new LambdaQueryWrapper<TenantInvitation>()
                .eq(TenantInvitation::getTenantId, tenantId)
                .isNull(TenantInvitation::getDeletedAt);
        if (!includeTerminal) {
            scope.eq(TenantInvitation::getStatus, STATUS_PENDING);
        }
        Long total = mapper.selectCount(scope);
        return total == null ? 0 : total;
    }

    /** 租户侧邀请分页：id DESC + pending 过滤。 */
    public List<TenantInvitation> listTenantPage(long tenantId, boolean includeTerminal, int page, int pageSize) {
        LambdaQueryWrapper<TenantInvitation> listScope = new LambdaQueryWrapper<TenantInvitation>()
                .eq(TenantInvitation::getTenantId, tenantId)
                .isNull(TenantInvitation::getDeletedAt)
                .orderByDesc(TenantInvitation::getId);
        if (!includeTerminal) {
            listScope.eq(TenantInvitation::getStatus, STATUS_PENDING);
        }
        return mapper.selectList(PageRequests.range(page, pageSize), listScope);
    }

    /** 跨空间收件箱：id DESC，无分页。 */
    public List<TenantInvitation> listByInvitee(String inviteeUserId, boolean includeTerminal) {
        LambdaQueryWrapper<TenantInvitation> q = new LambdaQueryWrapper<TenantInvitation>()
                .eq(TenantInvitation::getInviteeUserId, inviteeUserId)
                .isNull(TenantInvitation::getDeletedAt)
                .orderByDesc(TenantInvitation::getId);
        if (!includeTerminal) {
            q.eq(TenantInvitation::getStatus, STATUS_PENDING);
        }
        return mapper.selectList(q);
    }

    /** 收件箱 pending 计数。 */
    public long countPendingByInvitee(String inviteeUserId) {
        Long count = mapper.selectCount(new LambdaQueryWrapper<TenantInvitation>()
                .eq(TenantInvitation::getInviteeUserId, inviteeUserId)
                .eq(TenantInvitation::getStatus, STATUS_PENDING)
                .isNull(TenantInvitation::getDeletedAt));
        return count == null ? 0 : count;
    }

    /** 按明文 token 找 pending 行（空/未知返回 null，由调用方折叠成统一错误）。 */
    public TenantInvitation findPendingByToken(String token) {
        return mapper.selectOne(new LambdaQueryWrapper<TenantInvitation>()
                .eq(TenantInvitation::getToken, token)
                .eq(TenantInvitation::getStatus, STATUS_PENDING)
                .isNull(TenantInvitation::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** pending 唯一性查询：partial unique index 保证 ≤1 行。 */
    public TenantInvitation findPendingByPair(long tenantId, String inviteeUserId) {
        return mapper.selectOne(new LambdaQueryWrapper<TenantInvitation>()
                .eq(TenantInvitation::getTenantId, tenantId)
                .eq(TenantInvitation::getInviteeUserId, inviteeUserId)
                .eq(TenantInvitation::getStatus, STATUS_PENDING)
                .isNull(TenantInvitation::getDeletedAt)
                .orderByAsc(TenantInvitation::getId)
                .last("LIMIT 1"));
    }

    public void insert(TenantInvitation invitation) {
        mapper.insert(invitation);
    }

    /** accepted_count 原子 +1，返回影响行数（0 = 行已不存在）。 */
    public int bumpAcceptedCount(long id) {
        return mapper.update(null, new LambdaUpdateWrapper<TenantInvitation>()
                .eq(TenantInvitation::getId, id)
                .setSql("accepted_count = accepted_count + 1"));
    }

    /** {@code WHERE status='pending'} 的原子翻转，返回影响行数（0 = 并发输掉）。 */
    public int markStatusIfPending(long id, String status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return mapper.update(null, new LambdaUpdateWrapper<TenantInvitation>()
                .eq(TenantInvitation::getId, id)
                .eq(TenantInvitation::getStatus, STATUS_PENDING)
                .isNull(TenantInvitation::getDeletedAt)
                .set(TenantInvitation::getStatus, status)
                .set(TenantInvitation::getRespondedAt, now)
                .set(TenantInvitation::getUpdatedAt, now));
    }

    /** 惰性清扫：过期 pending 行批量翻成 expired。 */
    public int sweepExpired() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return mapper.update(null, new LambdaUpdateWrapper<TenantInvitation>()
                .eq(TenantInvitation::getStatus, STATUS_PENDING)
                .lt(TenantInvitation::getExpiresAt, now)
                .set(TenantInvitation::getStatus, STATUS_EXPIRED)
                .set(TenantInvitation::getRespondedAt, now)
                .set(TenantInvitation::getUpdatedAt, now));
    }
}
