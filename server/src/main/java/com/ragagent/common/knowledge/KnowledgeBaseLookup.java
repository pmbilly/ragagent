package com.ragagent.common.knowledge;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 知识库 / 知识条目的**只读端口**（B98/C2）。
 *
 * <p>{@code wiki} 域需要「按 id 取知识库」「判断知识条目是否已不可用」，但不应依赖
 * {@code knowledge} 的领域模型与 mapper。端口定义在中性包 {@code common.knowledge}，
 * 由 {@code knowledge} 侧实现（B94/B96/B97b 同法：接口进 common、实现留业务域）。</p>
 *
 * <p><b>本端口只覆盖读侧</b>：wiki 的 ingest 还会<b>写</b> knowledge 域（chunk/knowledge/span 落库、
 * 图片信息回填、embedding 配置派发）——那部分属"ingest 门面"（另批），不在本端口语义内。</p>
 */
public interface KnowledgeBaseLookup {

    /**
     * 按 id 取知识库（<b>软删过滤</b>：{@code deleted_at IS NULL}，{@code LIMIT 1}）。
     *
     * <p>调用方据此区分「库里没有」（404）与「不是你的」（403）——因此查询<b>不带空间过滤</b>，
     * 由调用方比对 {@link KnowledgeBaseView#getTenantId()}。</p>
     *
     * @return 视图；不存在返回 {@code null}
     */
    KnowledgeBaseView kbById(String kbId);

    /**
     * 按 id 取知识库（<b>不过滤软删</b>，{@code LIMIT 1}）——给健康检查类调用方用，
     * 与 {@code WikiLintService} 迁移前行为逐字一致。
     *
     * @return 视图；不存在返回 {@code null}
     */
    KnowledgeBaseView kbByIdIncludingDeleted(String kbId);

    /**
     * 知识条目是否**已不可用**：不存在 / 已软删 / 解析状态为删除中或已取消。
     *
     * <p>仓储缺位（装配裁剪）时返回 {@code false}（保守地当作"还在"）；查询异常亦按迁移前的
     * 语义处理（{@code true}）。wiki 侧的"墓碑表"判定不在此端口内（属 wiki 自己的存储）。</p>
     */
    boolean knowledgeGone(String knowledgeId);

    /**
     * 知识条目是否**仍存活**：存在且未软删（{@code deleted_at IS NULL}，{@code LIMIT 1}）。
     *
     * <p>与 {@link #knowledgeGone(String)} 的区别：本方法<b>不</b>看解析状态
     * （健康检查只关心"还在不在"），且仓储缺位时无法判定按 {@code false}（与迁移前的
     * {@code WikiLintService.knowledgeExistsByIdOnly} 逐字一致）。</p>
     */
    boolean knowledgeExists(String knowledgeId);

    /** 知识库只读视图。字段取 wiki 侧实际用到的投影（getter 风格，便于调用点零改写）。 */
    final class KnowledgeBaseView {

        private final String id;
        private final long tenantId;
        private final String creatorId;
        private final String summaryModelId;
        private final String embeddingModelId;
        private final boolean wikiEnabled;
        private final JsonNode wikiConfig;

        public KnowledgeBaseView(String id, long tenantId, String creatorId, String summaryModelId,
                                 String embeddingModelId, boolean wikiEnabled, JsonNode wikiConfig) {
            this.id = id;
            this.tenantId = tenantId;
            this.creatorId = creatorId;
            this.summaryModelId = summaryModelId;
            this.embeddingModelId = embeddingModelId;
            this.wikiEnabled = wikiEnabled;
            this.wikiConfig = wikiConfig;
        }

        public String getId() {
            return id;
        }

        public long getTenantId() {
            return tenantId;
        }

        public String getCreatorId() {
            return creatorId;
        }

        public String getSummaryModelId() {
            return summaryModelId;
        }

        public String getEmbeddingModelId() {
            return embeddingModelId;
        }

        /** {@code indexing_strategy.isWikiEnabled()} 的等价投影（wiki 型知识库判定）。 */
        public boolean isWikiEnabled() {
            return wikiEnabled;
        }

        public JsonNode getWikiConfig() {
            return wikiConfig;
        }
    }
}
