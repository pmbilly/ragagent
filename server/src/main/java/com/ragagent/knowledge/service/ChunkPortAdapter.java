package com.ragagent.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.common.knowledge.ChunkPort;
import com.ragagent.common.knowledge.ChunkView;
import com.ragagent.common.pipeline.ChunkTypes;
import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.mapper.ChunkMapper;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.knowledge.support.ImageInfoEnricher;

/**
 * {@link ChunkPort} 的 knowledge 侧实现（B98/C2）。
 *
 * <p>四个方法的过滤条件/降级姿态与迁移前的 wiki 侧实现<b>逐字一致</b>：
 * {@code textChunks} 来自 {@code WikiIngestBatchHandler}、{@code chunksByIds} 来自
 * {@code WikiIngestCitePipeline}、{@code deleteChunk} 来自 {@code DefaultWikiChunkCleaner}、
 * {@code enrichContentWithImageInfo} 来自 {@code DefaultWikiImageEnricher}。</p>
 */
@Component
public class ChunkPortAdapter implements ChunkPort {

    private final ChunkMapper chunkMapper;
    private final ChunkRepository chunkRepository;

    public ChunkPortAdapter(ChunkMapper chunkMapper, ChunkRepository chunkRepository) {
        this.chunkMapper = chunkMapper;
        this.chunkRepository = chunkRepository;
    }

    @Override
    public List<ChunkView> textChunks(long tenantId, String knowledgeId) {
        List<Chunk> rows = chunkMapper.selectList(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getTenantId, tenantId)
                .eq(Chunk::getKnowledgeId, knowledgeId)
                .eq(Chunk::getChunkType, ChunkTypes.TEXT)
                .orderByAsc(Chunk::getChunkIndex));
        return viewAll(rows);
    }

    @Override
    public List<ChunkView> chunksByIds(long tenantId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Chunk> rows = chunkMapper.selectList(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getTenantId, tenantId)
                .in(Chunk::getId, ids));
        return viewAll(rows);
    }

    @Override
    public void deleteChunk(long tenantId, String chunkId) {
        chunkMapper.delete(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getTenantId, tenantId)
                .eq(Chunk::getId, chunkId));
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
                chunkRepository::listChunksByParentIDs, tenantId, ids);
        String mergedImageInfo = ImageInfoEnricher.mergeImageInfoJson(imageInfoMap);
        if (mergedImageInfo == null || mergedImageInfo.isEmpty()) {
            // 合并后没有图片信息 → 原样返回
            return content;
        }
        return ImageInfoEnricher.enrichContentWithImageInfo(content, mergedImageInfo);
    }

    static ChunkView view(Chunk c) {
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

    static List<ChunkView> viewAll(List<Chunk> rows) {
        List<ChunkView> out = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (Chunk c : rows) {
                out.add(view(c));
            }
        }
        return out;
    }
}
