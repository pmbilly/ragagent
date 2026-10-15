package com.ragagent.tenant.repository;
import com.ragagent.common.tenant.TenantRowView;

/**
 * 租户实体查询端口：按 id 取 {@code com.ragagent.tenant.domain.Tenant} 行。
 *
 * <p><b>为什么不在 {@code common.tenant}</b>：本方法返回<b>实体</b>，而 {@code common} 是被 L2
 * （如 {@code retrieval}）依赖的契约层——若把返回实体的方法留在 common，则实体必须留在 common；
 * 若把实体挪进本域而方法仍留 common，则 common 反向依赖本域（R5 会红）。因此按消费方分层拆开：</p>
 * <ul>
 *   <li>{@code common.tenant.TenantConfigLookup}（L1 安全）：只暴露 {@code JsonNode} 配置与
 *       {@code TenantStorageView}，供 L2/L3 共用；</li>
 *   <li>本端口（域侧）：返回实体，只允许 <b>L3 业务域</b>（storage / knowledge）使用。</li>
 * </ul>
 *
 * <p><b>已按预留路径收窄（2026-10-15，C-14a）</b>：返回类型改为 {@code common.tenant.TenantRowView}
 * （对齐 {@code ChunkView} 的做法）——实体已归位本域，视图让消费方无需认识实体。</p>
 */
public interface TenantLookup {

    TenantRowView tenantById(long tenantId);
}
