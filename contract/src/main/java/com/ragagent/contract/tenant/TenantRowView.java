package com.ragagent.contract.tenant;

import java.time.OffsetDateTime;


/**
 * 租户行**视图**（L1）：租户管理面与线格式（{@code auth.dto.TenantResponse}）的载荷。
 *
 * <p><b>为什么有它</b>：实体 {@code Tenant} 已归位 `tenant/domain`（HANDOFF 判据 1
 * ——持久化闭包与 `TenantMapper`/`TenantLookup` 同域）；auth 的租户管理面（计划面：
 * 租户/成员/RBAC 管理在 auth）经本视图读写，以维持已登记的 {@code auth→tenant} 属主边
 * **只由 `TenantService` 一个类承载**（R10 登记条件：读侧走 L1 端口、写侧集中在属主域）。</p>
 *
 * <p><b>口径</b>：镜像实体全部 21 字段（10 个 jsonb {@code JsonNode} + {@code APIPrincipalConfig}），
 * 访问器名逐字一致；{@code apiPrincipalConfig} 与实体同标 {@code @JsonIgnore} ⇒ JSON 等价。</p>
 *
 * <p><b>落点</b>：已**入 {@code :contract}**（{@code contract.tenant}）——
 * 9 个 jsonb 配置字段由 {@code JsonNode} 改为**纯 JDK 值**（对象→{@code Map}、数组→{@code List}、
 * 标量→包装类型），边界转换见 {@code platform.web.JsonMappers#toJavaValue}/{@code #toJsonNode}；
 * 序列化字节与直接序列化 {@code JsonNode} 一致（键序保留）。</p>
 */
public class TenantRowView {

    private Long id;
    private String name;
    private String description;
    private String status;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object retrieverEngines;
    private String business;
    private Long storageQuota;
    private Long storageUsed;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object contextConfig;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object webSearchConfig;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object parserEngineConfig;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object credentials;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object storageEngineConfig;
    private String defaultStorageBackendId;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object chatHistoryConfig;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object retrievalConfig;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object memoryConfig;
    /** API 主体配置：**非 getter 访问器**（{@code apiPrincipalConfigValue()}）⇒ 等价原
     * {@code @JsonIgnore}（默认只序列化 getter 命名的访问器），序列化/反序列化都不出面。 */
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

    public Object getRetrieverEngines() { return retrieverEngines; }

    public void setRetrieverEngines(Object v) { retrieverEngines = v; }

    public String getBusiness() { return business; }

    public void setBusiness(String v) { business = v; }

    public Long getStorageQuota() { return storageQuota; }

    public void setStorageQuota(Long v) { storageQuota = v; }

    public Long getStorageUsed() { return storageUsed; }

    public void setStorageUsed(Long v) { storageUsed = v; }

    public Object getContextConfig() { return contextConfig; }

    public void setContextConfig(Object v) { contextConfig = v; }

    public Object getWebSearchConfig() { return webSearchConfig; }

    public void setWebSearchConfig(Object v) { webSearchConfig = v; }

    public Object getParserEngineConfig() { return parserEngineConfig; }

    public void setParserEngineConfig(Object v) { parserEngineConfig = v; }

    public Object getCredentials() { return credentials; }

    public void setCredentials(Object v) { credentials = v; }

    public Object getStorageEngineConfig() { return storageEngineConfig; }

    public void setStorageEngineConfig(Object v) { storageEngineConfig = v; }

    public String getDefaultStorageBackendId() { return defaultStorageBackendId; }

    public void setDefaultStorageBackendId(String v) { defaultStorageBackendId = v; }

    public Object getChatHistoryConfig() { return chatHistoryConfig; }

    public void setChatHistoryConfig(Object v) { chatHistoryConfig = v; }

    public Object getRetrievalConfig() { return retrievalConfig; }

    public void setRetrievalConfig(Object v) { retrievalConfig = v; }

    public Object getMemoryConfig() { return memoryConfig; }

    public void setMemoryConfig(Object v) { memoryConfig = v; }

    public APIPrincipalConfig apiPrincipalConfigValue() { return apiPrincipalConfig; }

    public void setApiPrincipalConfigValue(APIPrincipalConfig v) { apiPrincipalConfig = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }

    public void setDeletedAt(OffsetDateTime v) { deletedAt = v; }
}
