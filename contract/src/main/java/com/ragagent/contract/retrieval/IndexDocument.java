package com.ragagent.contract.retrieval;

/**
 * 待索引条目（业务域 → 检索引擎的领域级形状）。
 *
 * <p> 契约化：业务域（知识索引 / FAQ / 迁移克隆）原先直接组装 {@code retrieval-engine} 的
 * 内部类 {@code EngineTypes.IndexInfo}（公有可变字段）——那件内部形状留在实现模块内，本记录
 * 只带"域侧真给"的字段；引擎适配器（{@code IndexEngineAdapter}）负责映射并补齐引擎侧
 * 生成字段（索引 id、推荐位等）。</p>
 *
 * <p>空串归一如内部件语义：字符串 {@code null} → {@code ""}。</p>
 */
public record IndexDocument(
        String sourceId,
        int sourceType,
        String chunkId,
        String knowledgeId,
        String knowledgeBaseId,
        String knowledgeType,
        String tagId,
        String content,
        boolean enabled,
        boolean recommended) {

    /** 常用形态：不带推荐位。 */
    public IndexDocument(String sourceId, int sourceType, String chunkId, String knowledgeId,
                         String knowledgeBaseId, String knowledgeType, String tagId,
                         String content, boolean enabled) {
        this(sourceId, sourceType, chunkId, knowledgeId, knowledgeBaseId, knowledgeType,
                tagId, content, enabled, false);
    }

    public IndexDocument {
        sourceId = sourceId == null ? "" : sourceId;
        chunkId = chunkId == null ? "" : chunkId;
        knowledgeId = knowledgeId == null ? "" : knowledgeId;
        knowledgeBaseId = knowledgeBaseId == null ? "" : knowledgeBaseId;
        knowledgeType = knowledgeType == null ? "" : knowledgeType;
        tagId = tagId == null ? "" : tagId;
        content = content == null ? "" : content;
    }
}
