package com.ragagent.knowledge.api;

import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.common.knowledge.KnowledgeCatalogPort;
import com.ragagent.common.knowledge.domain.Chunk;
import com.ragagent.common.knowledge.domain.ChunkPage;
import com.ragagent.common.knowledge.domain.FaqEntryPage;
import com.ragagent.common.knowledge.domain.Knowledge;
import com.ragagent.common.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.knowledge.service.faq.FaqEntryQueryService;
import com.ragagent.knowledge.service.management.KnowledgeBaseService;
import com.ragagent.knowledge.service.management.KnowledgeService;
import com.ragagent.knowledge.storage.TenantFileStorage;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.knowledge.mapper.KnowledgeMapper;
import com.ragagent.knowledge.mapper.KnowledgeBaseMapper;
import com.ragagent.knowledge.domain.access.KnowledgeAccessGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * {@link KnowledgeCatalogPort} 的知识域实现：读写逐字转发两个门面服务，
 * 不新增缓冲、不改异常口径（409 重复冲突等由服务原样抛出）。
 */
@Component
public class KnowledgeCatalogAdapter implements KnowledgeCatalogPort {

    private final KnowledgeService knowledgeService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeAccessGuard accessGuard;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final ChunkRepository chunkRepository;
    private final FaqEntryQueryService faqEntryQueryService;
    private final TenantFileStorage tenantFileStorage;

    public KnowledgeCatalogAdapter(KnowledgeService knowledgeService,
                                   KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeAccessGuard accessGuard,
                                   KnowledgeBaseMapper knowledgeBaseMapper,
                                   KnowledgeMapper knowledgeMapper,
                                   ChunkRepository chunkRepository,
                                   FaqEntryQueryService faqEntryQueryService,
                                   TenantFileStorage tenantFileStorage) {
        this.knowledgeService = knowledgeService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.accessGuard = accessGuard;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.chunkRepository = chunkRepository;
        this.faqEntryQueryService = faqEntryQueryService;
        this.tenantFileStorage = tenantFileStorage;
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
    public List<Chunk> listChunksById(long tenantId, List<String> ids) {
        return chunkRepository.listChunksById(tenantId, ids);
    }

    @Override
    public List<Chunk> listChunkByParentId(long tenantId, String parentId) {
        return chunkRepository.listChunkByParentId(tenantId, parentId);
    }

    @Override
    public List<Chunk> listChunksByParentIDs(long tenantId, List<String> parentIds) {
        return chunkRepository.listChunksByParentIDs(tenantId, parentIds);
    }

    @Override
    public ChunkPage listPagedChunksByKnowledgeId(long tenantId, String knowledgeId,
            int offset, int limit, List<String> chunkTypes, List<String> tagIds,
            String keyword, String searchField, String sortOrder,
            String knowledgeType, Boolean isEnabled) {
        return chunkRepository.listPagedChunksByKnowledgeId(tenantId, knowledgeId, offset,
                limit, chunkTypes, tagIds, keyword, searchField, sortOrder,
                knowledgeType, isEnabled);
    }

    @Override
    public FaqEntryPage listFaqEntries(String kbId, int page, int pageSize,
            List<String> tagUuids, long legacyTagSeqId,
            String keyword, String searchField, String sortOrder, Boolean isEnabled) {
        return faqEntryQueryService.listEntries(kbId, page, pageSize, tagUuids,
                legacyTagSeqId, keyword, searchField, sortOrder, isEnabled);
    }

    @Override
    public byte[] readKnowledgeFile(long tenantId, String filePath) {
        return tenantFileStorage.read(tenantId, filePath);
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

    @Override
    public KnowledgeBase requireKbAccess(String kbId) {
        return accessGuard.requireKbAccess(kbId);
    }

    @Override
    public String retrieveDriver() {
        return knowledgeBaseService.retrieveDriver();
    }

    @Override
    public void updateKnowledgeBase(KnowledgeBase kb) {
        knowledgeBaseMapper.updateById(kb);
    }

    @Override
    public boolean hasFiles(String kbId) {
        Long tid = TenantContext.currentTenantId();
        Long count = knowledgeMapper.selectCount(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getKnowledgeBaseId, kbId)
                .eq(tid != null, Knowledge::getTenantId, tid)
                .isNull(Knowledge::getDeletedAt));
        return count != null && count > 0;
    }
}
