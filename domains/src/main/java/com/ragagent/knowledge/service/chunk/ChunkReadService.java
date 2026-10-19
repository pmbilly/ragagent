package com.ragagent.knowledge.service.chunk;

import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.domain.ChunkPage;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.dto.chunk.ChunkResponse;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.knowledge.repository.KnowledgeRepository;
import com.ragagent.platform.security.LogSanitizer;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * chunk 读取门面：控制器只与本类交互，不直接碰仓储。
 *
 * <p>分层的意义在这里很直观——分页查询有一串透传参数（类型过滤、排序键等）、
 * 单行查询有"查不到抛什么异常"的区别、摘要重载是 best-effort。把这些细节留在本层，
 * 控制器只表达"我要什么"，仓储则只表达"怎么查"。</p>
 */
@Service
public class ChunkReadService {

    private final ChunkRepository chunkRepository;
    private final KnowledgeRepository knowledgeRepository;

    public ChunkReadService(ChunkRepository chunkRepository, KnowledgeRepository knowledgeRepository) {
        this.chunkRepository = chunkRepository;
        this.knowledgeRepository = knowledgeRepository;
    }

    /** 分页结果（items 恒非 null）。 */
    public record ChunkPageView(List<Chunk> items, long total) {
    }

    /**
     * 按文档分页取 chunk。
     *
     * @param types chunk 类型过滤（调用方已填默认值，如 {@code text}）
     */
    public ChunkPageView listPagedChunks(long tenantId, String knowledgeId, int offset, int limit,
                                        List<String> types) {
        ChunkPage page = chunkRepository.listPagedChunksByKnowledgeId(
                tenantId, knowledgeId, offset, limit, types, null, "", "", "", "", null);
        return new ChunkPageView(page.items(), page.total());
    }

    /** 按 chunk id 取（不存在 → {@code ChunkNotFoundException}，由调用方决定 404 文案）。 */
    public Chunk getChunkByIdOnly(String chunkId) {
        return chunkRepository.getChunkByIdOnly(chunkId);
    }

    public ChunkResponse getChunkResponseByIdOnly(String chunkId) {
        return ChunkResponse.from(getChunkByIdOnly(chunkId));
    }

    /** 按租户 + chunk id 取（不存在 → {@code ChunkNotFoundException}）。 */
    public Chunk getChunkById(long tenantId, String chunkId) {
        return chunkRepository.getChunkById(tenantId, chunkId);
    }

    /**
     * 摘要状态重载用：取文档行；异常交给调用方处理（调用点只 WARN，不阻断主流程）。
     */
    public Knowledge findForSummaryReload(String knowledgeId, long tenantId) {
        return knowledgeRepository.findById(tenantId, LogSanitizer.sanitize(knowledgeId));
    }

    /** 摘要状态投影：控制器不再 import Knowledge。 */
    public record SummaryReload(String description, String summaryStatus) {
    }

    public SummaryReload findSummaryReloadView(String knowledgeId, long tenantId) {
        var knowledge = findForSummaryReload(knowledgeId, tenantId);
        return knowledge == null ? null
                : new SummaryReload(knowledge.getDescription(), knowledge.getSummaryStatus());
    }

    /** 返回 chunk id；chunk 不属于该知识时返回 null（控制器映射 403）。 */
    public String chunkIdIfOwned(long tenantId, String knowledgeId, String chunkId) {
        var chunk = getChunkById(tenantId, chunkId);
        return chunk.getKnowledgeId().equals(knowledgeId) ? chunk.getId() : null;
    }
}
