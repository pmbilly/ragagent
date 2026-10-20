package com.ragagent.knowledge.support;

import com.ragagent.contract.knowledge.ChunkPageView;
import com.ragagent.contract.knowledge.ChunkRowView;
import com.ragagent.contract.pagination.PageResult;
import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.platform.web.JsonMappers;
import java.util.ArrayList;
import java.util.List;

/**
 * 分块实体 → 行视图的**域内单一映射**（供知识域的三个端口适配器与仓储共用）。
 *
 * <p>实体已归位 `knowledge/domain`；L1 的 {@code ChunkProjectionAdapter} 已改吃 {@code ChunkRowView}
 * ⇒ 域内需要"喂实体给投影器"的点，先经本类映射（避免映射逻辑多处漂移，同 {@code TagViews} 先例）。</p>
 */
public final class ChunkRowViews {

    private ChunkRowViews() {
    }

    /** 实体 → 行视图（逐字段搬运；null 透传）。 */
    public static ChunkRowView rowView(Chunk c) {
        if (c == null) {
            return null;
        }
        ChunkRowView v = new ChunkRowView();
        v.setId(c.getId());
        v.setSeqId(c.getSeqId());
        v.setTenantId(c.getTenantId());
        v.setKnowledgeId(c.getKnowledgeId());
        v.setKnowledgeBaseId(c.getKnowledgeBaseId());
        v.setTagId(c.getTagId());
        v.setContent(c.getContent());
        v.setSourceContentValue(c.getSourceContent());
        v.setContentRevision(c.getContentRevision());
        v.setIndexStatus(c.getIndexStatus());
        v.setLastEditorId(c.getLastEditorId());
        v.setIsEnabled(c.isIsEnabled());
        v.setChunkIndex(c.getChunkIndex());
        v.setFlags(c.getFlags());
        v.setStatus(c.getStatus());
        v.setStartAt(c.getStartAt());
        v.setEndAt(c.getEndAt());
        v.setPreChunkId(c.getPreChunkId());
        v.setNextChunkId(c.getNextChunkId());
        v.setChunkType(c.getChunkType());
        v.setParentChunkId(c.getParentChunkId());
        v.setRelationChunks(JsonMappers.toJavaValue(c.getRelationChunks()));
        v.setIndirectRelationChunks(JsonMappers.toJavaValue(c.getIndirectRelationChunks()));
        v.setMetadata(JsonMappers.toJavaValue(c.getMetadata()));
        v.setContentHash(c.getContentHash());
        v.setImageInfo(c.getImageInfo());
        v.setContextHeaderValue(c.getContextHeader());
        v.setCreatedAt(c.getCreatedAt());
        v.setUpdatedAt(c.getUpdatedAt());
        v.setDeletedAt(c.getDeletedAt());
        return v;
    }

    /** 批量（null 安全）。 */
    public static List<ChunkRowView> rowViews(List<Chunk> rows) {
        if (rows == null) {
            return List.of();
        }
        List<ChunkRowView> out = new ArrayList<>(rows.size());
        for (Chunk c : rows) {
            out.add(rowView(c));
        }
        return out;
    }

    /** 分页（域内 `ChunkPage` → L1 `ChunkPageView`；null 透传）。 */
    public static ChunkPageView pageView(PageResult<Chunk> p) {
        if (p == null) {
            return null;
        }
        return new ChunkPageView(rowViews(p.items()), p.total());
    }
}
