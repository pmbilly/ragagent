package com.ragagent.tenant.api;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ragagent.contract.tenant.TenantStorageQuotaPort;
import com.ragagent.tenant.mapper.TenantMapper;
import com.ragagent.tenant.domain.Tenant;

/** {@link TenantStorageQuotaPort} 的租户域实现（配额读写都发生在租户表的属主域）。 */
@Component
public class TenantStorageQuotaAdapter implements TenantStorageQuotaPort {

    private final TenantMapper tenantMapper;
    private final JdbcTemplate jdbc;

    public TenantStorageQuotaAdapter(TenantMapper tenantMapper, JdbcTemplate jdbc) {
        this.tenantMapper = tenantMapper;
        this.jdbc = jdbc;
    }

    @Override
    public TenantQuotaView quotaView(long tenantId) {
        Tenant t = tenantMapper.selectById(tenantId);
        if (t == null) {
            return null;
        }
        long quota = t.getStorageQuota() == null ? 0L : t.getStorageQuota();
        long used = t.getStorageUsed() == null ? 0L : t.getStorageUsed();
        return new TenantQuotaView(tenantId, quota, used);
    }

    @Override
    public int applyDefaultStorageQuota(long quotaBytes) {
        return tenantMapper.applyDefaultStorageQuota(quotaBytes);
    }

    @Override
    public void adjustUsed(long tenantId, long delta) {
        jdbc.update("UPDATE tenants SET storage_used = GREATEST(COALESCE(storage_used, 0) + ?, 0), "
                + "updated_at = ? WHERE id = ?", delta, Timestamp.from(Instant.now()), tenantId);
    }
}
