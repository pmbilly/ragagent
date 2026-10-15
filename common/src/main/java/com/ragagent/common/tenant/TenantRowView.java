package com.ragagent.common.tenant;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.contract.tenant.APIPrincipalConfig;

/**
 * 租户行**视图**（L1）：租户管理面与线格式（{@code auth.dto.TenantResponse}）的载荷。
 *
 * <p><b>为什么有它</b>：实体 {@code Tenant} 已归位 `tenant/domain`（HANDOFF §12.0 判据 1
 * ——持久化闭包与 `TenantMapper`/`TenantLookup` 同域）；auth 的租户管理面（§8.9 计划面：
 * 租户/成员/RBAC 管理在 auth）经本视图读写，以维持已登记的 {@code auth→tenant} 属主边
 * **只由 `TenantService` 一个类承载**（R10 登记条件：读侧走 L1 端口、写侧集中在属主域）。</p>
 *
 * <p><b>口径</b>：镜像实体全部 21 字段（10 个 jsonb {@code JsonNode} + {@code APIPrincipalConfig}），
 * 访问器名逐字一致；{@code apiPrincipalConfig} 与实体同标 {@code @JsonIgnore} ⇒ JSON 等价。</p>
 *
 * <p><b>落点</b>：含 {@code JsonNode}/${@code APIPrincipalConfig} ⇒ L1（零依赖 :contract 装不下）；
 * 与 {@code TenantConfigLookup} 同包（{@code common.tenant}）。</p>
 */
public class TenantRowView {

    private Long id;
    private String name;
    private String description;
    private String status;
    private JsonNode retrieverEngines;
    private String business;
    private Long storageQuota;
    private Long storageUsed;
    private JsonNode contextConfig;
    private JsonNode webSearchConfig;
    private JsonNode parserEngineConfig;
    private JsonNode credentials;
    private JsonNode storageEngineConfig;
    private String defaultStorageBackendId;
    private JsonNode chatHistoryConfig;
    private JsonNode retrievalConfig;
    private JsonNode memoryConfig;
    @JsonIgnore
    private APIPrincipalConfig apiPrincipalConfig;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public Long getId() { return id; }

    public void setId(Long v) { id = v; }

    public String getName() { return name; }

    public void setName(String v) { name = v; }

    public String getDescription() { return description; }

    public void setDescription(String v) { description = v; }

    public String getStatus() { return status; }

    public void setStatus(String v) { status = v; }

    public JsonNode getRetrieverEngines() { return retrieverEngines; }

    public void setRetrieverEngines(JsonNode v) { retrieverEngines = v; }

    public String getBusiness() { return business; }

    public void setBusiness(String v) { business = v; }

    public Long getStorageQuota() { return storageQuota; }

    public void setStorageQuota(Long v) { storageQuota = v; }

    public Long getStorageUsed() { return storageUsed; }

    public void setStorageUsed(Long v) { storageUsed = v; }

    public JsonNode getContextConfig() { return contextConfig; }

    public void setContextConfig(JsonNode v) { contextConfig = v; }

    public JsonNode getWebSearchConfig() { return webSearchConfig; }

    public void setWebSearchConfig(JsonNode v) { webSearchConfig = v; }

    public JsonNode getParserEngineConfig() { return parserEngineConfig; }

    public void setParserEngineConfig(JsonNode v) { parserEngineConfig = v; }

    public JsonNode getCredentials() { return credentials; }

    public void setCredentials(JsonNode v) { credentials = v; }

    public JsonNode getStorageEngineConfig() { return storageEngineConfig; }

    public void setStorageEngineConfig(JsonNode v) { storageEngineConfig = v; }

    public String getDefaultStorageBackendId() { return defaultStorageBackendId; }

    public void setDefaultStorageBackendId(String v) { defaultStorageBackendId = v; }

    public JsonNode getChatHistoryConfig() { return chatHistoryConfig; }

    public void setChatHistoryConfig(JsonNode v) { chatHistoryConfig = v; }

    public JsonNode getRetrievalConfig() { return retrievalConfig; }

    public void setRetrievalConfig(JsonNode v) { retrievalConfig = v; }

    public JsonNode getMemoryConfig() { return memoryConfig; }

    public void setMemoryConfig(JsonNode v) { memoryConfig = v; }

    public APIPrincipalConfig getApiPrincipalConfig() { return apiPrincipalConfig; }

    public void setApiPrincipalConfig(APIPrincipalConfig v) { apiPrincipalConfig = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }

    public void setDeletedAt(OffsetDateTime v) { deletedAt = v; }
}
