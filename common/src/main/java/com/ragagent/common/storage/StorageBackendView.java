package com.ragagent.common.storage;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 存储后端**行视图**（L1）：{@code StorageBackendPort} 运维面（探测 / 清单 / 按 id 与 provider 读取）
 * 的载荷。
 *
 * <p><b>为什么有它</b>：实体 {@code StorageBackend} 已归位 `storage/domain`（HANDOFF §12.0 判据 1
 * ——持久化实体归各域）；跨域方（system 的连通性检测与系统信息、knowledge 的 KB 存储后端解析）
 * 只应经本视图。</p>
 *
 * <p><b>落点说明（登记 HANDOFF §12.5）</b>：{@code config} 是 {@code JsonNode}（存储配置 jsonb 原样透传），
 * 而 `:contract` 零依赖装不下 Jackson ⇒ 暂留 L1（与端口同处），登记为**待去 Jackson**；
 * 去 Jackson 后本视图与端口可整批入 `contract.storage`。</p>
 *
 * <p>访问器名与实体逐一相同（含 {@code isLegacyAlias()}）⇒ 消费方改类型即等价。</p>
 */
public class StorageBackendView {

    private String id;
    private Long tenantId;
    private String name;
    private String provider;
    private JsonNode config;
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

    public JsonNode getConfig() {
        return config;
    }

    public void setConfig(JsonNode v) {
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
