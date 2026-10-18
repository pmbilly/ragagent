package com.ragagent.contract.knowledge.projection;


import com.ragagent.contract.knowledge.ChunkFacts;
import com.ragagent.contract.knowledge.ChunkRowView;
import com.ragagent.contract.knowledge.ChunkView;
import java.util.ArrayList;
import java.util.List;

/**
 * chunk 实体 → L1 载荷（{@link ChunkFacts} / {@link ChunkView}）的**域内公用投影**。
 *
 * <p> 由 {@code com.ragagent.knowledge.api.ChunkPortAdapter} 的静态助手抽到本层（`support/`）：它被
 * `repository`（{@code ChunkRepository}）、`service`、`session`、`chatpipeline`、`agenttools`
 * 与测试夹具共用；留在适配器类里会让 **repository 反向依赖 service**（knowledge 四节点 SCC
 * 的一条真实成环边），落 `service.process` 则会把 `service.process` 也拖进环。放环外的
 * `support/` 后映射逻辑仍只有一份（不漂移），依赖方向回到 repository → support。</p>
 */
public final class ChunkProjectionAdapter {

    private ChunkProjectionAdapter() {
    }

    /**
     * 实体 → L1 **facts** 投影（{@link ChunkFacts}，16 字段；作为"实体→facts"的
     * 域内公用投影，供 chat 管线的 {@code PipelinePorts.ChunkRepository} 装配点复用，
     * 避免映射逻辑两处漂移）。仅带调用方真正读取的字段。
     */
    public static ChunkFacts factsOf(ChunkRowView c) {
        if (c == null) {
            return null;
        }
        return new ChunkFacts(c.getId(), c.getKnowledgeId(), c.getKnowledgeBaseId(), c.getContent(), c.getChunkType(),
                c.getIndexStatus(), c.isIsEnabled(), c.getChunkIndex(), c.getStartAt(),
                c.getEndAt(), c.getContentRevision(), c.getParentChunkId(), c.getPreChunkId(),
                c.getNextChunkId(), c.getRelationChunks(), c.getMetadata(), c.getImageInfo());
    }

    /** 批量投影（保持入参顺序；{@code null} 元素原样保留）。 */
    public static List<ChunkFacts> factsAll(List<ChunkRowView> rows) {
        List<ChunkFacts> out = new ArrayList<>();
        for (ChunkRowView c : rows) {
            out.add(factsOf(c));
        }
        return out;
    }

    public static ChunkView view(ChunkRowView c) {
        if (c == null) {
            return null;
        }
        ChunkView v = new ChunkView();
        v.setId(c.getId());
        v.setContent(c.getContent());
        v.setChunkType(c.getChunkType());
        v.setChunkIndex(c.getChunkIndex());
        v.setStartAt(c.getStartAt());
        v.setEndAt(c.getEndAt());
        return v;
    }

    public static List<ChunkView> viewAll(List<ChunkRowView> rows) {
        List<ChunkView> out = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (ChunkRowView c : rows) {
                out.add(view(c));
            }
        }
        return out;
    }
}
