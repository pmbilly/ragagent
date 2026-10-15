package com.ragagent.knowledge.api;

import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.common.knowledge.KnowledgeCatalogPort;
import com.ragagent.common.knowledge.domain.Chunk;
import com.ragagent.common.knowledge.domain.Knowledge;
import com.ragagent.common.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.knowledge.service.management.KnowledgeBaseService;
import com.ragagent.knowledge.service.management.KnowledgeService;

/**
 * {@link KnowledgeCatalogPort} 的知识域实现：读写逐字转发两个门面服务，
 * 不新增缓冲、不改异常口径（409 重复冲突等由服务原样抛出）。
 */
@Component
public class KnowledgeCatalogAdapter implements KnowledgeCatalogPort {

    private final KnowledgeService knowledgeService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChunkRepository chunkRepository;

    public KnowledgeCatalogAdapter(KnowledgeService knowledgeService,
                                   KnowledgeBaseService knowledgeBaseService,
                                   ChunkRepository chunkRepository) {
        this.knowledgeService = knowledgeService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.chunkRepository = chunkRepository;
    }

    @Override
    public Knowledge getKnowledgeByIdOnly(String id) {
        return knowledgeService.getKnowledgeByIdOnly(id);
    }

    @Override
    public List<Knowledge> getKnowledgeBatch(long tenantId, List<String> ids) {
        return knowledgeService.getKnowledgeBatch(tenantId, ids);
    }

    @Override
    public List<Knowledge> getKnowledgeBatchWithSharedAccess(long tenantId, List<String> ids) {
        return knowledgeService.getKnowledgeBatchWithSharedAccess(tenantId, ids);
    }

    @Override
    public Knowledge getKnowledgeInTenant(long tenantId, String id) {
        return knowledgeService.getKnowledgeInTenant(tenantId, id);
    }

    @Override
    public Page<Knowledge> listKnowledge(String kbId, long page, long pageSize,
            String keyword, String parseStatus, String fileType,
            String folderPath, boolean folderPresent) {
        return knowledgeService.listKnowledge(kbId, page, pageSize, keyword, parseStatus,
                fileType, folderPath, folderPresent);
    }

    @Override
    public String deleteKnowledge(String id) {
        return knowledgeService.deleteKnowledge(id);
    }

    @Override
    public Knowledge createFromPassageSync(String kbId, List<String> passages, String channel) {
        return knowledgeService.createFromPassageSync(kbId, passages, channel);
    }

    @Override
    public Knowledge createFromFile(String kbId, byte[] fileContent, String fileName,
            String displayName, JsonNode customMetadata, String channel) {
        return knowledgeService.createFromFile(kbId, fileContent, fileName, displayName,
                customMetadata, channel);
    }

    @Override
    public Chunk getChunkByIdOnly(String id) {
        return chunkRepository.getChunkByIdOnly(id);
    }

    @Override
    public KnowledgeBase getKnowledgeBase(String id) {
        return knowledgeBaseService.getKnowledgeBase(id);
    }

    @Override
    public KnowledgeBase createKnowledgeBase(KnowledgeBase kb) {
        return knowledgeBaseService.createKnowledgeBase(kb);
    }

    @Override
    public List<KnowledgeBase> listKnowledgeBases(String creator) {
        return knowledgeBaseService.listKnowledgeBases(creator);
    }

    @Override
    public KnowledgeBase getAllTenantById(String id) {
        return knowledgeBaseService.getAllTenantById(id);
    }

    @Override
    public void deleteKnowledgeBase(String id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
    }
}
