package com.ragagent.common.tenant;

import com.ragagent.contract.tenant.APIPrincipalConfig;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 租户配置的**只读端口**。
 *
 * <p>检索域（{@code retrieval}）与模型域（{@code model}）只需要租户行里的三个 jsonb 配置，
 * 不需要认识 {@code auth} 的领域模型。端口定义在中性包 {@code common.tenant}，
 * 由 {@code auth.service.TenantService} 实现（它已有 {@code getTenantById}）——
 * 因此<b>没有新增 wiring</b>，Spring 照旧注入同一个 bean。</p>
 *
 * <p>语义：租户不存在或该配置为空时返回 {@code null}（调用方各自兜底默认值）；
 * 读取异常由调用方按原有 try/catch 处理（与迁移前一致）。</p>
 */
public interface TenantConfigLookup {

    /** 检索引擎配置（{@code tenants.retriever_engines}，形如 {@code {"engines":[…]}}）。 */
    JsonNode retrieverEngines(long tenantId);

    /** 检索参数配置（{@code tenants.retrieval_config}：rrf_k / rrf_vector_weight / rrf_keyword_weight）。 */
    JsonNode retrievalConfig(long tenantId);

    /** 记忆配置（{@code tenants.memory_config}：embeddingModelId / extractModelId 等）。 */
    JsonNode memoryConfig(long tenantId);

    /** 租户是否存在（存在性探针；避免为此读整行/拉实体）。 */
    boolean exists(long tenantId);

    /**
     * API 主体配置（{@code tenants.api_principal_config}，**契约类型**；租户不存在返回 {@code null}）。
     *
     * <p>只读侧专用：写侧仍在租户管理面（auth 的 {@code TenantAPIPrincipalController}）。</p>
     */
    APIPrincipalConfig apiPrincipalConfig(long tenantId);

    /** 网络搜索配置（{@code tenants.web_search_config}；租户不存在或配置为空返回 {@code null}）。 */
    JsonNode webSearchConfig(long tenantId);

    /** 聊天历史配置（{@code tenants.chat_history_config}；租户不存在或配置为空返回 {@code null}）。 */
    JsonNode chatHistoryConfig(long tenantId);

    /** 解析引擎配置（{@code tenants.parser_engine_config}；租户不存在或配置为空返回 {@code null}）。 */
    JsonNode parserEngineConfig(long tenantId);

    /**
     * 存储域只读视图（id + 默认后端 + 存储引擎配置）。租户不存在返回 {@code null}
     * （与 {@code getTenantById} 的语义一致；调用方据此回 401/400）。
     */
    TenantStorageView storageView(long tenantId);

    /** 存储域只读视图（三字段，够解析 provider 链）。 */
    record TenantStorageView(long tenantId, String defaultStorageBackendId, JsonNode storageEngineConfig) {
    }
}
