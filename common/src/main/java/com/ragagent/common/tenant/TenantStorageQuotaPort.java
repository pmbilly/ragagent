package com.ragagent.common.tenant;

/**
 * 租户存储配额的读/写端口（L1）。租户行的 {@code storage_quota}/{@code storage_used} 归
 * <b>auth/tenant 侧</b>所有：读＝配额与已用，写＝按增量调整已用（负数钳 0）。
 *
 * <p><b>为什么是端口</b>：knowledge 的 FAQ 索引写入要"先查配额、后记账"，此前**直连
 * {@code TenantMapper} 并裸 SQL {@code UPDATE tenants}**（跨域直连持久层 ✗）；本端口把读/写
 * 都收回租户域实现（{@code tenant/api/TenantStorageQuotaAdapter}），调用方只 import 本端口。</p>
 */
public interface TenantStorageQuotaPort {

    /** 配额视图；租户不存在返回 {@code null}（调用方按 0 兜底）。 */
    TenantQuotaView quotaView(long tenantId);

    /** {@code storage_used += delta}（负数钳 0）。 */
    void adjustUsed(long tenantId, long delta);

    /** 配额视图（三字段）。 */
    record TenantQuotaView(long tenantId, long quota, long used) {
    }
}
