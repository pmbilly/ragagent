package com.ragagent.wiki.service;

import com.ragagent.knowledge.api.ChunkPortAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link WikiChunkCleaner} 的生产实现：wiki 页面删除时同步删除其镜像 chunk
 * （id = {@code "wp-" + pageId}，谓词 tenant_id + id）。
 *
 * <p>此前该端口没有实现 bean，{@code DefaultWikiPageService.deletePage} 恒走"跳过"
 * 分支——被删页面的镜像 chunk 永久残留，可能被检索命中为幽灵来源。
 * chunk 清理失败只记 WARN，不让页面删除失败。</p>
 */
@Component
public class DefaultWikiChunkCleaner implements WikiChunkCleaner {

    private static final Logger log = LoggerFactory.getLogger(DefaultWikiChunkCleaner.class);

    private final ChunkPortAdapter chunkPort;

    public DefaultWikiChunkCleaner(ChunkPortAdapter chunkPort) {
        this.chunkPort = chunkPort;
    }

    @Override
    public void deleteWikiPageChunk(Long tenantId, String chunkId) {
        if (tenantId == null || chunkId == null || chunkId.isEmpty()) {
            return;
        }
        try {
            chunkPort.deleteChunk(tenantId, chunkId);
        } catch (RuntimeException e) {
            // 清理失败仅记 WARN：不阻断页面删除流程
            log.warn("wiki: failed to delete chunk {} for tenant {}", chunkId, tenantId, e);
        }
    }
}
