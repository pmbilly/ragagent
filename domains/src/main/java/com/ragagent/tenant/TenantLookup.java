package com.ragagent.tenant;

/**
 * 租户实体查询端口（B104）：按 id 取 {@link Tenant} 行。
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
 * <p>后续可选：把本方法的返回收窄成视图（对齐 C2 的 {@code ChunkView} 做法），届时可并回 common。</p>
 */
public interface TenantLookup {

    Tenant tenantById(long tenantId);
}
