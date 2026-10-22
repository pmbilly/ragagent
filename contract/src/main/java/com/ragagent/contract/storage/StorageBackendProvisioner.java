package com.ragagent.contract.storage;

import com.ragagent.contract.tenant.StorageEngineConfig;

/**
 * 租户开通时「落一行默认存储后端」的**跨域命令端口**（不是只读端口：带写方法与补偿方法）。
 *
 * <p>为什么需要它：{@code auth} 的建租户流程要按**进程环境快照**为租户建一行只读存储后端，
 * 把它的 id 回写到 {@code tenants.default_storage_backend_id}，并在回写失败时补偿删行。
 * 此前 auth 直接注入 {@code StorageBackendRepository} 并**自行持有** env → 后端实体 / config JSON
 * 的映射（约 150 行，含键序契约）——注册域因此既依赖存储仓储、又替存储域保管领域规则。</p>
 *
 * <p>端口把「如何从环境推导默认后端」收回存储域；auth 只保留**自己的**事务编排
 * （建 → 回写外键 → 失败补偿），两侧职责与改动前逐字对应。</p>
 */
public interface StorageBackendProvisioner {

    /**
     * 为租户落一行 env 快照的默认只读后端，返回其 id。
     *
     * <p>无法确定默认 provider 时抛 {@code IllegalStateException}
     * （消息固定为 {@code "no supported default storage backend is configured"}）；
     * 调用方负责租户行回滚。</p>
     */
    String provisionForTenant(long tenantId);

    /** 补偿动作：删除 {@code #provisionForTenant} 刚落的行（租户行回写失败时调用）。 */
    void deleteForTenant(long tenantId, String backendId);

    /**
     * 把租户旧 KV {@code storage-engine-config} 落成一行 legacy 后端，返回其 id。
     *
     * <p>用于启动迁移：KV 写接口已停用，存量配置必须在表里有一行才能继续被运行期读到。
     * 无法确定 provider（空配置 / 无对应段）时返回 {@code null}，调用方跳过。</p>
     */
    String provisionFromLegacy(long tenantId, StorageEngineConfig config);
}
