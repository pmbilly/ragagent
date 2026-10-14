package com.ragagent.knowledge.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ragagent.common.knowledge.ChunkPort;
import com.ragagent.common.knowledge.ChunkView;
import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.support.ChunkProjectionAdapter;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.retrieval.support.ImageInfoEnricher;

/**
 * {@link ChunkPort} 的 knowledge 侧实现。
 *
 * <p>"实体 → L1 载荷"的公用投影已抽到
 * {@link com.ragagent.knowledge.support.ChunkProjectionAdapter#view}
 * /{@link com.ragagent.knowledge.support.ChunkProjectionAdapter#viewAll}
 * （B263②：原先作为静态助手挂在本类，令 `repository` 反向依赖 `service` ⇒ 现落环外 `support/`）。
 *
 * <p>四个方法的过滤条件/降级姿态与迁移前的 wiki 侧实现<b>逐字一致</b>：
 * {@code textChunks} 来自 {@code WikiIngestBatchHandler}、{@code chunksByIds} 来自
 * {@code WikiIngestCitePipeline}、{@code deleteChunk} 来自 {@code DefaultWikiChunkCleaner}、
 * {@code enrichContentWithImageInfo} 来自 {@code DefaultWikiImageEnricher}。</p>
 */
@Component
public class ChunkPortAdapter implements ChunkPort {

    private final ChunkRepository chunkRepository;

    public ChunkPortAdapter(ChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Override
    public List<ChunkView> textChunks(long tenantId, String knowledgeId) {
        List<Chunk> rows = chunkRepository.listTextChunks(tenantId, knowledgeId);
        return ChunkProjectionAdapter.viewAll(rows);
    }

    @Override
    public List<ChunkView> chunksByIds(long tenantId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Chunk> rows = chunkRepository.listChunksById(tenantId, ids);
        return ChunkProjectionAdapter.viewAll(rows);
    }

    @Override
    public void deleteChunk(long tenantId, String chunkId) {
        chunkRepository.deleteScoped(tenantId, chunkId);
    }

    @Override
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
                (tid, pids) -> ChunkProjectionAdapter.factsAll(chunkRepository.listChunksByParentIDs(tid, pids)),
                tenantId, ids);
        String mergedImageInfo = ImageInfoEnricher.mergeImageInfoJson(imageInfoMap);
        if (mergedImageInfo == null || mergedImageInfo.isEmpty()) {
            // 合并后没有图片信息 → 原样返回
            return content;
        }
        return ImageInfoEnricher.enrichContentWithImageInfo(content, mergedImageInfo);
    }
}
