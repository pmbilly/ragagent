package com.ragagent.contract.knowledge;


import com.ragagent.contract.knowledge.domain.AsrConfigView;
import com.ragagent.contract.knowledge.domain.ChunkingConfigView;
import com.ragagent.contract.knowledge.domain.ImageProcessingConfigView;
import com.ragagent.contract.knowledge.domain.IndexingStrategyView;
import com.ragagent.contract.knowledge.domain.VlmConfigView;
import java.time.OffsetDateTime;

/**
 * 知识库的对外响应体（详情 / 列表项 / 初始化回执统一用这一个形态）。
 *
 * <p>契约规则（见 {@code docs/knowledge-api-contract-v1.md}）：
 * <ul>
 *   <li>JSON 字段名 = Java 字段名（camelCase），不需要任何 {@code @JsonProperty}；</li>
 *   <li>可空字段显式输出 {@code null}，不用空串/0 代替，也不做"有时出现有时消失"的条件键；</li>
 *   <li>布尔字段不带 {@code is} 前缀（{@code pinned}/{@code processing}/{@code temporary}）；</li>
 *   <li>不输出内部字段：{@code tenantId}、{@code deletedAt}、{@code storageProviderConfig}、
 *       存储凭据配置（{@code storageConfig}，DB 列为 {@code storage_config}，含 secret 字段）；</li>
 *   <li>向量库绑定信息收敛为嵌套对象 {@link VectorStoreView}，无绑定时为 {@code null}。</li>
 * </ul>
 *
 * <p>嵌套的配置对象走 {@link ChunkingConfigView} / {@link ImageProcessingConfigView} 等视图类型（camelCase），
 * 与数据库 jsonb 列共用的领域类型解耦——领域类的 Jackson 注解仍决定落库格式。
 *
 */
public record KnowledgeBaseResponse(
        String id,
        String name,
        Type type,
        boolean temporary,
        String description,
        String creatorId,
        String creatorName,
        boolean pinned,
        OffsetDateTime pinnedAt,
        boolean processing,
        long knowledgeCount,
        long chunkCount,
        long processingCount,
        long shareCount,
        KnowledgeBaseRowView.Capabilities capabilities,
        ChunkingConfigView chunkingConfig,
        ImageProcessingConfigView imageProcessingConfig,
        String embeddingModelId,
        String summaryModelId,
        VlmConfigView vlmConfig,
        AsrConfigView asrConfig,
        String storageBackendId,
        String storageProvider,
        IndexingStrategyView indexingStrategy,
        /** jsonb 原样（对象/数组/标量）。 */
        Object extractConfig,
        /** jsonb 原样（对象/数组/标量）。 */
        Object faqConfig,
        /** jsonb 原样（对象/数组/标量）。 */
        Object questionGenerationConfig,
        /** jsonb 原样（对象/数组/标量）。 */
        Object autoTagConfig,
        /** jsonb 原样（对象/数组/标量）。 */
        Object wikiConfig,
        VectorStoreView vectorStore,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /** 知识库类型。库内取值只有这两种，非法值按 {@code #document} 处理。 */
    public enum Type {
        /** 常量名即线格式值（原 {@code @JsonValue value()} 的零依赖等价：Jackson 枚举默认按 name 序列化）。 */
        document,
        faq;

        public static Type from(String raw) {
            return faq.name().equals(raw) ? faq : document;
        }
    }

    /**
     * 向量库绑定的展示视图。
     *
     * @param id         绑定的向量库 ID；使用租户环境默认库时为 {@code null}
     * @param name       展示名，如 {@code "System default"}
     * @param source     绑定来源：{@code env}（环境默认）/ {@code user}（租户自建）/ {@code shared}（跨租户共享）
     * @param engineType 底层检索引擎类型，如 {@code postgres}；来源为 {@code shared} 时不下发（避免泄露库主信息）
     * @param status     当前可达性：{@code available} / {@code unavailable}
     */
    public record VectorStoreView(String id, String name, Source source, String engineType, Status status) {

        /** 向量库来源（env / custom）。 */
        /** 常量名即线格式值（同 {@code Type} 的去 Jackson 约定）。 */
        public enum Source {
            env,
            user,
            shared
        }

        /** 向量库可用状态。 */
        /** 常量名即线格式值（同 {@code Type} 的去 Jackson 约定）。 */
        public enum Status {
            available,
            unavailable
        }

        /** 本部署未配置任何向量库绑定（无绑定或回落环境默认）时的视图。 */
        public static VectorStoreView envDefault(String engineType) {
            return new VectorStoreView(null, "System default", Source.env, engineType, Status.available);
        }
    }

    /**
     * 由实体组装响应体；向量库视图按"有租户绑定 → user 来源，否则环境默认"自动推导。
     *
     * @param retrieveDriver 部署的检索引擎配置（{@code RETRIEVE_DRIVER}），用于推导引擎类型
     */
    public static KnowledgeBaseResponse from(KnowledgeBaseRowView kb, String retrieveDriver) {
        return from(kb, viewOf(kb, retrieveDriver));
    }

    /**
     * 向量库视图推导：
     * <ul>
     *   <li>有租户自建绑定 → {@code user} 来源，带上绑定 ID（库名与真实可达性待后续解析）；</li>
     *   <li>无绑定 → 环境默认（{@code source=env}，引擎类型取 {@code RETRIEVE_DRIVER} 首段）。</li>
     * </ul>
     */
    private static VectorStoreView viewOf(KnowledgeBaseRowView kb, String retrieveDriver) {
        String engineType = engineTypeOf(retrieveDriver);
        return kb.hasVectorStore()
                ? new VectorStoreView(kb.getVectorStoreId(), null, VectorStoreView.Source.user,
                        engineType, VectorStoreView.Status.available)
                : VectorStoreView.envDefault(engineType);
    }

    /** {@code RETRIEVE_DRIVER} 首段即检索引擎类型（如 {@code postgres}）；空值回落 {@code postgres}。 */
    public static String engineTypeOf(String retrieveDriver) {
        if (retrieveDriver == null || retrieveDriver.isBlank()) {
            return "postgres";
        }
        String first = retrieveDriver.split(",")[0].trim().toLowerCase();
        return first.isEmpty() ? "postgres" : first;
    }

    /**
     * 由实体组装响应体。
     *
     * @param storeView 向量库绑定视图；调用方决定其取值（环境默认 / 租户绑定 / 共享视图）
     */
    public static KnowledgeBaseResponse from(KnowledgeBaseRowView kb, VectorStoreView storeView) {
        return new KnowledgeBaseResponse(
                kb.getId(),
                kb.getName(),
                Type.from(kb.getType()),
                kb.isIsTemporary(),
                kb.getDescription(),
                emptyToNull(kb.getCreatorId()),
                emptyToNull(kb.getCreatorName()),
                kb.isIsPinned(),
                kb.getPinnedAt(),
                kb.isIsProcessing(),
                kb.getKnowledgeCount(),
                kb.getChunkCount(),
                kb.getProcessingCount(),
                kb.getShareCount(),
                kb.capabilities(),
                kb.getChunkingConfig(),
                kb.getImageProcessingConfig(),
                emptyToNull(kb.getEmbeddingModelId()),
                emptyToNull(kb.getSummaryModelId()),
                kb.getVlmConfig(),
                kb.getAsrConfig(),
                emptyToNull(kb.getStorageBackendId()),
                emptyToNull(kb.getStorageProvider()),
                kb.getIndexingStrategy(),
                kb.getExtractConfig(),
                kb.getFaqConfig(),
                kb.getQuestionGenerationConfig(),
                kb.getAutoTagConfig(),
                kb.getWikiConfig(),
                storeView,
                kb.getCreatedAt(),
                kb.getUpdatedAt());
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
