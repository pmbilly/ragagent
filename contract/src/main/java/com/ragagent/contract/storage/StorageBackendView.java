package com.ragagent.contract.storage;

import java.time.OffsetDateTime;

/**
 * 存储后端**行视图**（L1）：{@code StorageBackendPort} 运维面（探测 / 清单 / 按 id 与 provider 读取）
 * 的载荷。
 *
 * <p><b>为什么有它</b>：实体 {@code StorageBackend} 已归位 `storage/domain`（HANDOFF 判据 1
 * ——持久化实体归各域）；跨域方（system 的连通性检测与系统信息、knowledge 的 KB 存储后端解析）
 * 只应经本视图。</p>
 *
 * <p><b>落点</b>：本视图与 {@link StorageBackendPort} 已**入 {@code :contract}**（去 Jackson 后，
 *）：{@code config} 由 {@code JsonNode} 改为**纯 JDK 值**（对象→{@code Map}、数组→{@code List}、
 * 标量→包装类型），边界转换见 {@code platform.web.JsonMappers#toJavaValue}/{@code #toJsonNode}；
 * 序列化字节与直接序列化 {@code JsonNode} 一致（键序保留）。</p>
 *
 * <p>访问器名与实体逐一相同（含 {@code isLegacyAlias()}）⇒ 消费方改类型即等价。</p>
 */
public class StorageBackendView {

    private String id;
    private Long tenantId;
    private String name;
    private String provider;
    /** 存储配置 jsonb **原样透传**（对象/数组/标量；{@code null} 表示无配置）。 */
    private Object config;
    private String source;
    private String status;
    private boolean legacyAlias;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public String getId() {
        return id;
    }

    public void setId(String v) {
        id = v;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long v) {
        tenantId = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String v) {
        provider = v;
    }

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object v) {
        config = v;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String v) {
        source = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        status = v;
    }

    public boolean isLegacyAlias() {
        return legacyAlias;
    }

    public void setLegacyAlias(boolean v) {
        legacyAlias = v;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime v) {
        createdAt = v;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime v) {
        updatedAt = v;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime v) {
        deletedAt = v;
    }
}
