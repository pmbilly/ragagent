package com.ragagent.common.knowledge;

import java.util.List;

/**
 * chunk 访问端口（B98/C2）：wiki ingest 对 chunk 的<b>读取、删除与图片富化</b>。
 *
 * <p>实现留在 {@code knowledge} 侧（{@code ChunkMapper}/{@code ChunkRepository}/
 * {@code ImageInfoEnricher}），过滤条件与迁移前的 wiki 直查逐字一致。</p>
 */
public interface ChunkPort {

    /**
     * 按知识条目取**文本类型** chunk，按 {@code chunk_index} 升序。
     *
     * <p>迁移前为 {@code WikiIngestBatchHandler.listTextChunksByKnowledgeID}（按租户 + 知识 id +
     * chunk_type=text）。<b>只取 text</b>：summary / parent_text / image 等其他类型
     * 走 {@link #chunksByIds(long, List)} 或富化路径。</p>
     */
    List<ChunkView> textChunks(long tenantId, String knowledgeId);

    /**
     * 按 id 批量取 chunk（带租户过滤，不排序）。
     *
     * <p>与迁移前 {@code WikiIngestCitePipeline} 的 {@code chunkMapper.selectList} 一致：
     * 调用方自行按需要顺序使用结果（缺 id 静默丢弃）。</p>
     */
    List<ChunkView> chunksByIds(long tenantId, List<String> ids);

    /** 删除单个 chunk（按租户 + id）。与迁移前 {@code DefaultWikiChunkCleaner} 一致。 */
    void deleteChunk(long tenantId, String chunkId);

    /**
     * 把文本 chunk 的图片信息（子 chunk 上的 OCR / caption）内联进正文。
     *
     * <p>迁移前为 {@code DefaultWikiImageEnricher#enrich}：先按 <b>父 chunk id</b> 收集图片信息
     * （{@code ChunkRepository.listChunksByParentIDs} + {@code ImageInfoEnricher} 三方法），
     * 合并后内联；无有效 id / 无图片信息时<b>原样返回正文</b>。</p>
     *
     * @param textChunkIds 文本 chunk 的 id（空 id 由实现忽略）
     */
    String enrichContentWithImageInfo(String content, long tenantId, List<String> textChunkIds);
}
