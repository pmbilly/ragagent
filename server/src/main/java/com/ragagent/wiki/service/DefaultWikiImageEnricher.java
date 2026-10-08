package com.ragagent.wiki.service;

import java.util.ArrayList;
import java.util.List;

import com.ragagent.common.knowledge.ChunkPort;
import com.ragagent.common.knowledge.ChunkView;
import org.springframework.stereotype.Component;

/**
 * {@link WikiImageEnricher} 的生产实现：
 *
 * <ol>
 *   <li>收集文本 chunk 的 ID（调用方已按 {@code ChunkType=text} 过滤）；</li>
 *   <li>{@code CollectImageInfoByChunkIDs} + {@code MergeImageInfoJSON} 汇总图片信息；</li>
 *   <li>合并结果非空时 {@code EnrichContentWithImageInfo} 把
 *       {@code <image>/<imageOcr>/<imageCaption>} 块内联进正文；否则原样返回。</li>
 * </ol>
 *
 * <p>没有实现 bean 时 {@code WikiIngestService} 恒走
 * {@link WikiImageEnricher#identity}，图片 /
 * 扫描件密集文档的 wiki 抽取会为空。底层能力（{@link ImageInfoEnricher}）
 * 已被 chatpipeline 与 KnowledgeService 使用，本类只补齐桥接，调用方零改动。</p>
 */
@Component
public class DefaultWikiImageEnricher implements WikiImageEnricher {

    private final ChunkPort chunkPort;

    public DefaultWikiImageEnricher(ChunkPort chunkPort) {
        this.chunkPort = chunkPort;
    }

    @Override
    public String enrich(String content, List<ChunkView> textChunks, long tenantId) {
        if (textChunks == null || textChunks.isEmpty()) {
            return content;
        }
        List<String> textChunkIds = new ArrayList<>(textChunks.size());
        for (ChunkView c : textChunks) {
            if (c != null && c.getId() != null && !c.getId().isEmpty()) {
                textChunkIds.add(c.getId());
            }
        }
        // 收集图片信息（父 chunk id → 子块 image_info）、合并、内联：整体在 knowledge 侧端口内完成，
        // 无有效 id / 无图片信息时由端口原样返回 content。
        return chunkPort.enrichContentWithImageInfo(content, tenantId, textChunkIds);
    }
}
