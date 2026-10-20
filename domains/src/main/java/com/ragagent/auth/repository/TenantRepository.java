package com.ragagent.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.auth.domain.Tenant;
import com.ragagent.auth.mapper.TenantMapper;
import com.ragagent.platform.persistence.PageRequests;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 租户表的 MyBatis-Plus 门面：所有 {@code tenants} 条件构造收口在这里。 */
@Repository
public class TenantRepository {

    private final TenantMapper mapper;

    public TenantRepository(TenantMapper mapper) {
        this.mapper = mapper;
    }

    public Tenant byId(long id) {
        return mapper.selectOne(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getId, id)
                .isNull(Tenant::getDeletedAt)
                .last("LIMIT 1"));
    }

    public Tenant byIdIncludingDeleted(long id) {
        return mapper.selectById(id);
    }

    public List<Tenant> byIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<Tenant>()
                .in(Tenant::getId, ids)
                .isNull(Tenant::getDeletedAt));
    }

    public List<Tenant> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<Tenant>()
                .isNull(Tenant::getDeletedAt)
                .orderByDesc(Tenant::getCreatedAt));
    }

    public List<Tenant> listLive() {
        return mapper.selectList(new LambdaQueryWrapper<Tenant>()
                .isNull(Tenant::getDeletedAt));
    }

    public long count(String escapedKeyword, long tenantId) {
        Long total = mapper.selectCount(searchScope(escapedKeyword, tenantId));
        return total == null ? 0 : total;
    }

    public List<Tenant> search(String escapedKeyword, long tenantId, int page, int pageSize) {
        LambdaQueryWrapper<Tenant> wrapper = searchScope(escapedKeyword, tenantId)
                .orderByDesc(Tenant::getCreatedAt);
        // page<=0 是"不分页"语义（拉全量）；分页时 LIMIT/OFFSET 交给分页插件按方言生成
        return page > 0 && pageSize > 0
                ? mapper.selectList(PageRequests.range(page, pageSize), wrapper)
                : mapper.selectList(wrapper);
    }

    /**
     * 搜索条件：tenant_id&gt;0 且 keyword 非空 → (id=? OR name LIKE OR description LIKE)；
     * 仅 tenant_id → id=?；仅 keyword → name/description LIKE。
     *
     * <p>关键字由调用方先过 LIKE 转义（{@code \ % _} 转义）。</p>
     */
    private static LambdaQueryWrapper<Tenant> searchScope(String escapedKeyword, long tenantId) {
        String kw = escapedKeyword == null ? "" : escapedKeyword;
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<Tenant>()
                .isNull(Tenant::getDeletedAt);
        if (tenantId > 0 && !kw.isEmpty()) {
            final long id = tenantId;
            wrapper.and(w -> w.eq(Tenant::getId, id)
                    .or().like(Tenant::getName, kw)
                    .or().like(Tenant::getDescription, kw));
        } else if (tenantId > 0) {
            wrapper.eq(Tenant::getId, tenantId);
        } else if (!kw.isEmpty()) {
            wrapper.and(w -> w.like(Tenant::getName, kw)
                    .or().like(Tenant::getDescription, kw));
        }
        return wrapper;
    }

    public void insert(Tenant tenant) {
        mapper.insert(tenant);
    }

    public void updateById(Tenant tenant) {
        mapper.updateById(tenant);
    }

    public void deleteById(long id) {
        mapper.deleteById(id);
    }

    public void softDeleteById(long id, OffsetDateTime now) {
        mapper.update(null, new LambdaUpdateWrapper<Tenant>()
                .eq(Tenant::getId, id)
                .isNull(Tenant::getDeletedAt)
                .set(Tenant::getDeletedAt, now));
    }
}
