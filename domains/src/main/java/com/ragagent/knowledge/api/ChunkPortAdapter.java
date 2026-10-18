package com.ragagent.knowledge.api;

import com.ragagent.contract.knowledge.ChunkView;
import com.ragagent.contract.knowledge.projection.ChunkProjectionAdapter;
import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.knowledge.support.ChunkRowViews;
import com.ragagent.platform.knowledge.ImageInfoEnricher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * chunk 读写面（知识族内直接依赖：wiki 摄取与页面渲染注入本类）。
 *
 * <p>"实体 → 载荷"的公用投影在
 * {@link com.ragagent.contract.knowledge.projection.ChunkProjectionAdapter#view}
 * /{@link com.ragagent.contract.knowledge.projection.ChunkProjectionAdapter#viewAll}。
 *
 * <p>四个方法的过滤条件/降级姿态与迁移前的 wiki 侧实现<b>逐字一致</b>：
 * {@code textChunks} 来自 {@code WikiIngestBatchHandler}、{@code chunksByIds} 来自
 * {@code WikiIngestCitePipeline}、{@code deleteChunk} 来自 {@code DefaultWikiChunkCleaner}、
 * {@code enrichContentWithImageInfo} 来自 {@code DefaultWikiImageEnricher}。</p>
 */
@Component
public class ChunkPortAdapter {

    private final ChunkRepository chunkRepository;

    public ChunkPortAdapter(ChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    public List<ChunkView> textChunks(long tenantId, String knowledgeId) {
        List<Chunk> rows = chunkRepository.listTextChunks(tenantId, knowledgeId);
        return ChunkProjectionAdapter.viewAll(ChunkRowViews.rowViews(rows));
    }

    public List<ChunkView> chunksByIds(long tenantId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Chunk> rows = chunkRepository.listChunksById(tenantId, ids);
        return ChunkProjectionAdapter.viewAll(ChunkRowViews.rowViews(rows));
    }

    public void deleteChunk(long tenantId, String chunkId) {
        chunkRepository.deleteScoped(tenantId, chunkId);
    }

    public String enrichContentWithImageInfo(String content, long tenantId, List<String> textChunkIds) {
        if (textChunkIds == null || textChunkIds.isEmpty()) {
            return content;
        }
        List<String> ids = new ArrayList<>(textChunkIds.size());
        for (String id : textChunkIds) {
            if (id != null && !id.isEmpty()) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            // 没有任何有效文本 chunk ID → 原样返回
            return content;
        }
        Map<String, String> imageInfoMap = ImageInfoEnricher.collectImageInfoByChunkIds(
                (tid, pids) -> ChunkProjectionAdapter.factsAll(
                        ChunkRowViews.rowViews(chunkRepository.listChunksByParentIDs(tid, pids))),
                tenantId, ids);
        String mergedImageInfo = ImageInfoEnricher.mergeImageInfoJson(imageInfoMap);
        if (mergedImageInfo == null || mergedImageInfo.isEmpty()) {
            // 合并后没有图片信息 → 原样返回
            return content;
        }
        return ImageInfoEnricher.enrichContentWithImageInfo(content, mergedImageInfo);
    }
}
